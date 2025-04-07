package guraa.pdfcompare.util;

import guraa.pdfcompare.model.PdfDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.tools.imageio.ImageIOUtil;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class PdfProcessor {

    private final TextExtractor textExtractor;
    private final ImageExtractor imageExtractor;
    private final FontAnalyzer fontAnalyzer;

    @Qualifier("pdfPageProcessingExecutor")
    private final ExecutorService pdfPageProcessingExecutor;

    private static final int DEFAULT_DPI = 150;
    private static final int THUMBNAIL_DPI = 72;
    private static final int MAX_RETRIES = 3;

    // Global flags for problem detection
    private final ThreadLocal<Boolean> hasRenderingProblems = ThreadLocal.withInitial(() -> false);
    private final ThreadLocal<Boolean> hasStreamProblems = ThreadLocal.withInitial(() -> false);
    private final ThreadLocal<Boolean> hasFontProblems = ThreadLocal.withInitial(() -> false);

    /**
     * Process a PDF file and extract its metadata and structure with enhanced error handling.
     *
     * @param uploadedFile The uploaded PDF file
     * @return PdfDocument object with extracted metadata
     * @throws IOException If there's an error processing the file
     */
    public PdfDocument processPdf(File uploadedFile) throws IOException {
        String fileId = UUID.randomUUID().toString();
        String fileName = uploadedFile.getName();

        // Create output directory for this document
        Path documentsDir = Paths.get("uploads", "documents", fileId);
        Files.createDirectories(documentsDir);

        // Copy the original file to our storage
        File storedFile = documentsDir.resolve("original.pdf").toFile();
        FileUtils.copyFile(uploadedFile, storedFile);

        // Calculate MD5 hash for the document
        String contentHash = calculateMD5(storedFile);

        // Reset problem flags
        hasRenderingProblems.set(false);
        hasStreamProblems.set(false);
        hasFontProblems.set(false);

        try (PDDocument document = PDDocument.load(storedFile)) {
            // Create a robust renderer for this document
            PdfRenderer robustRenderer = new PdfRenderer(document)
                    .setDefaultDPI(DEFAULT_DPI)
                    .setFallbackDPI(THUMBNAIL_DPI)
                    .setImageType(ImageType.RGB);

            // Extract metadata
            Map<String, String> metadata = extractMetadata(document);

            // Create directories for rendered pages
            Path pagesDir = documentsDir.resolve("pages");
            Files.createDirectories(pagesDir);

            // Create directory for rendered page thumbnails
            Path thumbnailsDir = documentsDir.resolve("thumbnails");
            Files.createDirectories(thumbnailsDir);

            // Create directory for extracted text
            Path textDir = documentsDir.resolve("text");
            Files.createDirectories(textDir);

            // Create directory for extracted images
            Path imagesDir = documentsDir.resolve("images");
            Files.createDirectories(imagesDir);

            // Create directory for fonts
            Path fontsDir = documentsDir.resolve("fonts");
            Files.createDirectories(fontsDir);

            // Get page count
            int pageCount = document.getNumberOfPages();

            log.info("Processing PDF with {} pages using parallel execution", pageCount);

            // Create a list to hold all the futures
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            // Track successful and failed pages
            AtomicInteger successfulPages = new AtomicInteger(0);
            AtomicInteger failedPages = new AtomicInteger(0);

            // Process each page in parallel
            for (int i = 0; i < pageCount; i++) {
                final int pageIndex = i;

                // Create a CompletableFuture for processing this page
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    int retries = 0;
                    boolean success = false;

                    while (retries < MAX_RETRIES && !success) {
                        try {
                            if (retries > 0) {
                                log.info("Retry #{} for page {}", retries, pageIndex + 1);
                            }

                            processPageWithRobustHandling(
                                    document,
                                    robustRenderer,
                                    pageIndex,
                                    pagesDir,
                                    thumbnailsDir,
                                    textDir,
                                    imagesDir,
                                    fontsDir
                            );

                            success = true;
                            successfulPages.incrementAndGet();

                        } catch (Exception e) {
                            retries++;

                            if (retries >= MAX_RETRIES) {
                                log.error("Failed to process page {} after {} retries: {}",
                                        pageIndex + 1, MAX_RETRIES, e.getMessage());
                                failedPages.incrementAndGet();

                                // Create placeholder files for failed page
                                createPlaceholderFiles(pageIndex, pagesDir, thumbnailsDir, textDir);
                            } else {
                                log.warn("Error processing page {}, will retry: {}",
                                        pageIndex + 1, e.getMessage());
                            }
                        }
                    }
                }, pdfPageProcessingExecutor);

                futures.add(future);
            }

            // Wait for all page processing to complete with timeout
            try {
                // Convert CompletableFuture array to array with timeout
                CompletableFuture<?>[] futuresArray = futures.toArray(new CompletableFuture[0]);
                
                // Use a timeout for the overall operation
                CompletableFuture<Void> allFutures = CompletableFuture.allOf(futuresArray);
                
                try {
                    // Wait for completion with a timeout of 5 minutes
                    allFutures.orTimeout(5, java.util.concurrent.TimeUnit.MINUTES).join();
                    
                    log.info("PDF processing completed: {} pages processed successfully, {} pages failed",
                            successfulPages.get(), failedPages.get());
                } catch (java.util.concurrent.CompletionException e) {
                    // Handle timeout or other completion exceptions
                    if (e.getCause() instanceof java.util.concurrent.TimeoutException) {
                        log.error("PDF processing timed out after 5 minutes. Some pages may not be processed.");
                        metadata.put("processingWarning", "Processing timed out, results may be incomplete");
                        
                        // Cancel any remaining futures
                        for (CompletableFuture<?> future : futuresArray) {
                            if (!future.isDone()) {
                                future.cancel(true);
                            }
                        }
                    } else {
                        log.error("Error during parallel PDF processing", e);
                        metadata.put("processingError", "Document processing encountered errors");
                    }
                }
                
                // Add processing flags to metadata if problems were detected
                if (hasRenderingProblems.get()) {
                    metadata.put("processingNote", "Document has rendering issues that were handled");
                }
                if (hasStreamProblems.get()) {
                    metadata.put("streamWarning", "Document contains problematic data streams");
                }
                if (hasFontProblems.get()) {
                    metadata.put("fontWarning", "Document contains problematic fonts");
                }

            } catch (Exception e) {
                log.error("Unexpected error during PDF processing", e);
                metadata.put("processingError", "Document processing encountered unexpected errors");
                
                // Check for known PDFBox errors
                if (isKnownPdfBoxIssue(e)) {
                    log.warn("Detected PDF with known processing issue. Continuing with partial results.");
                } else {
                    throw new IOException("Failed to process PDF: " + e.getMessage(), e);
                }
            }

            // Build and return the PdfDocument object
            return PdfDocument.builder()
                    .fileId(fileId)
                    .fileName(fileName)
                    .filePath(storedFile.getAbsolutePath())
                    .fileSize(storedFile.length())
                    .pageCount(pageCount)
                    .metadata(metadata)
                    .uploadDate(LocalDateTime.now())
                    .processedDate(LocalDateTime.now())
                    .processed(true)
                    .contentHash(contentHash)
                    .build();
        }
    }

    /**
     * Process a page with robust error handling for each component.
     */
    private void processPageWithRobustHandling(
            PDDocument document,
            PdfRenderer robustRenderer,
            int pageIndex,
            Path pagesDir,
            Path thumbnailsDir,
            Path textDir,
            Path imagesDir,
            Path fontsDir) throws IOException {

        int pageNumber = pageIndex + 1;
        log.debug("Processing page {} in thread {}", pageNumber, Thread.currentThread().getName());

        // 1. Render the page with robust handling
        try {
            // Use our robust renderer
            BufferedImage image = robustRenderer.renderPage(pageIndex);

            // Save the rendered page
            File pageImageFile = pagesDir.resolve(String.format("page_%d.png", pageNumber)).toFile();
            ImageIOUtil.writeImage(image, pageImageFile.getAbsolutePath(), DEFAULT_DPI);

            // Generate a thumbnail
            // Scale down the main image for the thumbnail rather than re-rendering
            BufferedImage thumbnail = createThumbnail(image);
            File thumbnailFile = thumbnailsDir.resolve(String.format("page_%d_thumbnail.png", pageNumber)).toFile();
            ImageIOUtil.writeImage(thumbnail, thumbnailFile.getAbsolutePath(), THUMBNAIL_DPI);
        } catch (Exception e) {
            log.warn("Error rendering page {} with robust renderer: {}", pageNumber, e.getMessage());
            hasRenderingProblems.set(true);

            // Create placeholder images
            createPlaceholderImage(pagesDir.resolve(String.format("page_%d.png", pageNumber)).toFile(),
                    "Error rendering page " + pageNumber, 800, 1000);
            createPlaceholderImage(thumbnailsDir.resolve(String.format("page_%d_thumbnail.png", pageNumber)).toFile(),
                    "Error", 200, 250);
        }

        // 2. Extract text using robust methods
        try {
            // Try multiple text extraction approaches
            String pageText = extractTextWithFallbacks(document, pageIndex);

            File pageTextFile = textDir.resolve(String.format("page_%d.txt", pageNumber)).toFile();
            FileUtils.writeStringToFile(pageTextFile, pageText, "UTF-8");

            // Extract detailed text elements - don't fail if this errors
            List<TextElement> textElements = new ArrayList<>();
            try {
                textElements = textExtractor.extractTextElementsFromPage(document, pageIndex);
            } catch (Exception e) {
                log.warn("Error extracting text elements from page {}: {}", pageNumber, e.getMessage());
                hasStreamProblems.set(true);
            }

            if (!textElements.isEmpty()) {
                File textElementsFile = textDir.resolve(String.format("page_%d_elements.json", pageNumber)).toFile();
                FileUtils.writeStringToFile(textElementsFile, convertElementsToJson(textElements), "UTF-8");
            }
        } catch (Exception e) {
            log.error("Complete text extraction failure for page {}: {}", pageNumber, e.getMessage());
            hasStreamProblems.set(true);

            // Create minimal text file
            try {
                File pageTextFile = textDir.resolve(String.format("page_%d.txt", pageNumber)).toFile();
                FileUtils.writeStringToFile(pageTextFile, "[Text extraction failed for this page]", "UTF-8");
            } catch (IOException ioe) {
                log.error("Error creating placeholder text file", ioe);
            }
        }

        // 3. Extract images with error handling
        try {
            try {
                // Use a try-catch block just for this operation
                imageExtractor.extractImagesFromPage(document, pageIndex, imagesDir);
            } catch (Exception e) {
                log.warn("Error extracting images from page {}: {}", pageNumber, e.getMessage());
                hasStreamProblems.set(true);

                // Create a placeholder image file to indicate there was an extraction attempt
                File placeholderFile = imagesDir.resolve(String.format("page_%d_image_error.txt", pageNumber)).toFile();
                FileUtils.writeStringToFile(placeholderFile, "Image extraction failed: " + e.getMessage(), "UTF-8");
            }
        } catch (Exception e) {
            log.error("Unexpected error during image extraction for page {}: {}", pageNumber, e.getMessage());
        }

        // 4. Extract fonts with error handling
        try {
            try {
                // Use robust font extractor to analyze fonts and save to JSON
                List<FontAnalyzer.FontInfo> fontInfoList = fontAnalyzer.analyzeFontsOnPage(document, pageIndex, fontsDir);
                
                // Save the font information as JSON for later reuse during comparison
                if (fontInfoList != null && !fontInfoList.isEmpty()) {
                    try {
                        // Create a JSON file with the font information
                        File fontInfoFile = fontsDir.resolve(String.format("page_%d_fonts.json", pageNumber)).toFile();
                        
                        // Convert the font info list to JSON and save it
                        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        mapper.writeValue(fontInfoFile, fontInfoList);
                        
                        log.debug("Saved font information for page {} to {}", pageNumber, fontInfoFile.getPath());
                    } catch (Exception jsonEx) {
                        log.warn("Error saving font information as JSON for page {}: {}", pageNumber, jsonEx.getMessage());
                    }
                }
            } catch (Exception e) {
                log.warn("Error analyzing fonts on page {}: {}", pageNumber, e.getMessage());
                hasFontProblems.set(true);

                // Create a placeholder font info file
                File placeholderFile = fontsDir.resolve(String.format("page_%d_fonts_error.txt", pageNumber)).toFile();
                FileUtils.writeStringToFile(placeholderFile, "Font analysis failed: " + e.getMessage(), "UTF-8");
            }
        } catch (Exception e) {
            log.error("Unexpected error during font analysis for page {}: {}", pageNumber, e.getMessage());
        }
    }

    /**
     * Try multiple text extraction approaches with fallbacks.
     */
    private String extractTextWithFallbacks(PDDocument document, int pageIndex) {
        String pageText = null;

        // Attempt 1: Use the regular text extractor
        try {
            pageText = textExtractor.extractTextFromPage(document, pageIndex);
            if (pageText != null && !pageText.trim().isEmpty()) {
                return pageText;
            }
        } catch (Exception e) {
            log.warn("Primary text extraction failed for page {}: {}", pageIndex + 1, e.getMessage());
        }

        // Attempt 2: Use the fallback text extractor
        try {
            PDFTextExtractorFallback fallbackExtractor = new PDFTextExtractorFallback();
            pageText = fallbackExtractor.extractTextFromPage(document, pageIndex);
            if (pageText != null && !pageText.trim().isEmpty()) {
                hasStreamProblems.set(true); // Mark that we had to use fallback
                return pageText;
            }
        } catch (Exception e) {
            log.warn("Fallback text extraction failed for page {}: {}", pageIndex + 1, e.getMessage());
        }

        // Attempt 3: Try with an extremely simplified approach, just get character data
        try {
            // This uses a very basic approach that just gets character data
            StringBuilder sb = new StringBuilder();
            PDPage page = document.getPage(pageIndex);

            // Extract characters using a specialized extractor
            // This is a minimalist implementation that should work even with damaged PDFs
            CharacterExtractor extractor = new CharacterExtractor();
            String basicText = extractor.extractBasicText(page);

            if (basicText != null && !basicText.isEmpty()) {
                sb.append("[Warning: Using emergency text extraction due to PDF issues]\n\n");
                sb.append(basicText);
                hasStreamProblems.set(true);
                return sb.toString();
            }
        } catch (Exception e) {
            log.warn("Emergency text extraction failed for page {}: {}", pageIndex + 1, e.getMessage());
        }

        // If all attempts fail, return a placeholder
        return "[This page contains no extractable text or text extraction failed]";
    }

    /**
     * Creates placeholder files for a page that failed to process.
     */
    private void createPlaceholderFiles(int pageIndex, Path pagesDir, Path thumbnailsDir, Path textDir) {
        try {
            int pageNumber = pageIndex + 1;

            // Create a placeholder text file
            File textFile = textDir.resolve("page_" + pageNumber + ".txt").toFile();
            FileUtils.writeStringToFile(textFile,
                    "This page could not be processed due to PDF parsing errors.", "UTF-8");

            // Create placeholder image files
            createPlaceholderImage(pagesDir.resolve("page_" + pageNumber + ".png").toFile(),
                    "Page " + pageNumber + " could not be rendered", 800, 1000);

            createPlaceholderImage(thumbnailsDir.resolve("page_" + pageNumber + "_thumbnail.png").toFile(),
                    "Page " + pageNumber, 200, 250);

        } catch (Exception e) {
            log.error("Error creating placeholder files for failed page {}", pageIndex + 1, e);
        }
    }

    /**
     * Creates a simple placeholder image with an error message.
     */
    private void createPlaceholderImage(File outputFile, String message, int width, int height) {
        try {
            // Create a blank white image
            BufferedImage placeholderImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g2d = placeholderImage.createGraphics();

            // Fill with white background
            g2d.setColor(java.awt.Color.WHITE);
            g2d.fillRect(0, 0, width, height);

            // Add text
            g2d.setColor(java.awt.Color.RED);
            g2d.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 16));

            java.awt.FontMetrics fm = g2d.getFontMetrics();
            int textWidth = fm.stringWidth(message);
            int textHeight = fm.getHeight();

            g2d.drawString(message, (width - textWidth) / 2, height / 2);

            g2d.dispose();

            // Save image
            javax.imageio.ImageIO.write(placeholderImage, "PNG", outputFile);

        } catch (Exception e) {
            log.error("Error creating placeholder image", e);
        }
    }

    /**
     * Create a thumbnail from a full-size image.
     *
     * @param image The full-size image
     * @return A scaled-down thumbnail
     */
    private BufferedImage createThumbnail(BufferedImage image) {
        int maxWidth = 300;
        int maxHeight = 400;

        double scale = Math.min(
                (double)maxWidth / image.getWidth(),
                (double)maxHeight / image.getHeight()
        );

        int scaledWidth = (int)(image.getWidth() * scale);
        int scaledHeight = (int)(image.getHeight() * scale);

        BufferedImage thumbnail = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = thumbnail.createGraphics();

        // Configure for quality scaling
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,
                java.awt.RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                java.awt.RenderingHints.VALUE_ANTIALIAS_ON);

        g.drawImage(image, 0, 0, scaledWidth, scaledHeight, null);
        g.dispose();

        return thumbnail;
    }

    /**
     * Extract metadata from a PDF document.
     *
     * @param document The PDF document
     * @return Map of metadata key-value pairs
     */
    private Map<String, String> extractMetadata(PDDocument document) {
        Map<String, String> metadata = new HashMap<>();
        PDDocumentInformation info = document.getDocumentInformation();

        if (info.getTitle() != null) metadata.put("title", truncateMetadata(info.getTitle()));
        if (info.getAuthor() != null) metadata.put("author", truncateMetadata(info.getAuthor()));
        if (info.getSubject() != null) metadata.put("subject", truncateMetadata(info.getSubject()));
        if (info.getKeywords() != null) metadata.put("keywords", truncateMetadata(info.getKeywords()));
        if (info.getCreator() != null) metadata.put("creator", truncateMetadata(info.getCreator()));
        if (info.getProducer() != null) metadata.put("producer", truncateMetadata(info.getProducer()));

        // Format dates as ISO strings to prevent Calendar object serialization issues
        if (info.getCreationDate() != null) {
            try {
                metadata.put("creationDate", info.getCreationDate().getTime().toString());
            } catch (Exception e) {
                log.warn("Could not format creation date", e);
            }
        }

        if (info.getModificationDate() != null) {
            try {
                metadata.put("modificationDate", info.getModificationDate().getTime().toString());
            } catch (Exception e) {
                log.warn("Could not format modification date", e);
            }
        }

        // Extract custom metadata with length validation
        for (String key : info.getMetadataKeys()) {
            if (!metadata.containsKey(key) && info.getCustomMetadataValue(key) != null) {
                String value = info.getCustomMetadataValue(key);
                // Truncate if too long for the database
                metadata.put(key, truncateMetadata(value));
            }
        }

        return metadata;
    }

    /**
     * Truncate metadata value to prevent database column overflow.
     *
     * @param value The metadata value
     * @return Truncated value
     */
    private String truncateMetadata(String value) {
        if (value == null) return null;
        // Limit to 3900 characters to be safe (column is 4000)
        return value.length() > 3900 ? value.substring(0, 3900) + "..." : value;
    }

    /**
     * Calculate MD5 hash of a file.
     *
     * @param file The file to hash
     * @return MD5 hash string
     */
    private String calculateMD5(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }
            byte[] hash = md.digest();

            // Convert byte array to hex string
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("Failed to calculate MD5 hash", e);
            throw new IOException("Failed to calculate MD5 hash", e);
        }
    }

    /**
     * Convert TextElement list to JSON string.
     *
     * @param elements List of text elements
     * @return JSON string representation
     */
    private String convertElementsToJson(List<TextElement> elements) {
        StringBuilder json = new StringBuilder("[\n");
        for (int i = 0; i < elements.size(); i++) {
            TextElement element = elements.get(i);
            json.append("  {\n");
            json.append("    \"id\": \"").append(element.getId()).append("\",\n");
            json.append("    \"text\": \"").append(escapeJson(element.getText())).append("\",\n");
            json.append("    \"x\": ").append(element.getX()).append(",\n");
            json.append("    \"y\": ").append(element.getY()).append(",\n");
            json.append("    \"width\": ").append(element.getWidth()).append(",\n");
            json.append("    \"height\": ").append(element.getHeight()).append(",\n");
            json.append("    \"fontSize\": ").append(element.getFontSize()).append(",\n");
            json.append("    \"fontName\": \"").append(escapeJson(element.getFontName())).append("\",\n");
            if (element.getColor() != null) {
                json.append("    \"color\": \"").append(escapeJson(element.getColor())).append("\",\n");
            }
            json.append("    \"rotation\": ").append(element.getRotation()).append(",\n");
            json.append("    \"isBold\": ").append(element.isBold()).append(",\n");
            json.append("    \"isItalic\": ").append(element.isItalic()).append(",\n");
            json.append("    \"isEmbedded\": ").append(element.isEmbedded()).append(",\n");
            json.append("    \"lineId\": \"").append(element.getLineId()).append("\",\n");
            json.append("    \"wordId\": \"").append(element.getWordId()).append("\"\n");
            json.append("  }").append(i < elements.size() - 1 ? ",\n" : "\n");
        }
        json.append("]");
        return json.toString();
    }

    /**
     * Escape special characters in JSON strings.
     *
     * @param str The string to escape
     * @return Escaped string
     */
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Check if the exception is from a known PDFBox issue that we can safely continue with.
     */
    private boolean isKnownPdfBoxIssue(Exception e) {
        if (e == null) return false;

        // Check recursion
        if (e.getMessage() != null && e.getMessage().contains("recursion")) {
            return true;
        }

        // Check for ASCII85 stream errors
        if (e.getMessage() != null && e.getMessage().contains("Ascii85")) {
            return true;
        }

        // Check embedded font errors
        if (e.getMessage() != null && e.getMessage().contains("embedded")) {
            return true;
        }

        // Check FlateFilter errors
        if (e.getMessage() != null && (
                e.getMessage().contains("FlateFilter") ||
                        e.getMessage().contains("premature end of stream") ||
                        e.getMessage().contains("DataFormatException"))) {
            return true;
        }

        // Check array index bounds errors
        if (e.getMessage() != null && e.getMessage().contains("Index") &&
                e.getMessage().contains("out of bounds")) {
            return true;
        }

        // Check font-related errors in the cause chain
        Throwable cause = e.getCause();
        while (cause != null) {
            if (cause.getMessage() != null &&
                    (cause.getMessage().contains("font") ||
                            cause.getMessage().contains("Ascii85") ||
                            cause.getMessage().contains("embedded") ||
                            cause.getMessage().contains("FlateFilter") ||
                            cause.getMessage().contains("DataFormatException") ||
                            cause.getMessage().contains("Index") &&
                                    cause.getMessage().contains("out of bounds"))) {
                return true;
            }
            cause = cause.getCause();
        }

        // Check for ArrayIndexOutOfBoundsException
        if (isArrayIndexOutOfBoundsException(e)) {
            return true;
        }

        // Check for other PDFBox rendering issues
        if (isPdfBoxRenderingError(e)) {
            return true;
        }

        return false;
    }

    /**
     * Check if the exception is or contains an ArrayIndexOutOfBoundsException
     */
    private boolean isArrayIndexOutOfBoundsException(Exception e) {
        if (e instanceof ArrayIndexOutOfBoundsException) {
            return true;
        }

        // Check if the cause is an ArrayIndexOutOfBoundsException
        Throwable cause = e.getCause();
        while (cause != null) {
            if (cause instanceof ArrayIndexOutOfBoundsException) {
                return true;
            }

            // Check if the message contains "ArrayIndexOutOfBoundsException"
            if (cause.getMessage() != null && cause.getMessage().contains("ArrayIndexOutOfBoundsException")) {
                return true;
            }

            cause = cause.getCause();
        }

        return false;
    }

    /**
     * Check if the exception is related to PDFBox rendering
     */
    private boolean isPdfBoxRenderingError(Exception e) {
        // Check if the exception or any of its causes is from PDFBox rendering
        Throwable cause = e;
        while (cause != null) {
            // Check if the stack trace contains PDFBox rendering classes
            StackTraceElement[] stackTrace = cause.getStackTrace();
            for (StackTraceElement element : stackTrace) {
                String className = element.getClassName();
                if (className.startsWith("org.apache.pdfbox.rendering") ||
                        className.startsWith("org.apache.pdfbox.pdmodel") ||
                        className.startsWith("org.apache.pdfbox.contentstream") ||
                        className.startsWith("org.apache.pdfbox.filter")) {
                    return true;
                }
            }

            cause = cause.getCause();
        }

        return false;
    }
}
