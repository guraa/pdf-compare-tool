package guraa.pdfcompare.util;

import guraa.pdfcompare.model.PdfDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.rendering.RenderDestination;
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

    // Track problematic fonts to avoid repeated errors
    private final Map<String, Boolean> problematicFonts = new HashMap<>();

    /**
     * Process a PDF file and extract its metadata and structure.
     * This implementation uses parallel processing for page operations.
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

        try (PDDocument document = PdfLoader.loadDocument(storedFile)) {
            // Detect problematic fonts that might cause issues later
            detectProblematicFonts(document);

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

            // Create a PDFRenderer with error handling options
            PDFRenderer renderer = new PDFRenderer(document);
            renderer.setSubsamplingAllowed(true); // Allow subsampling for problematic pages

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

                            // Process with lower DPI on retries
                            int dpi = retries == 0 ? DEFAULT_DPI : (DEFAULT_DPI / (retries + 1));
                            int thumbDpi = retries == 0 ? THUMBNAIL_DPI : (THUMBNAIL_DPI / (retries + 1));

                            processPage(
                                    document,
                                    renderer,
                                    pageIndex,
                                    pagesDir,
                                    thumbnailsDir,
                                    textDir,
                                    imagesDir,
                                    fontsDir,
                                    dpi,
                                    thumbDpi
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

            // Wait for all page processing to complete
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

                log.info("PDF processing completed: {} pages processed successfully, {} pages failed",
                        successfulPages.get(), failedPages.get());

            } catch (Exception e) {
                log.error("Error during parallel PDF processing", e);

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
     * Creates placeholder files for a page that failed to process.
     */
    private void createPlaceholderFiles(int pageIndex, Path pagesDir, Path thumbnailsDir, Path textDir) {
        try {
            int pageNumber = pageIndex + 1;

            // Create a placeholder text file
            File textFile = textDir.resolve("page_" + pageNumber + ".txt").toFile();
            FileUtils.writeStringToFile(textFile,
                    "This page could not be processed due to PDF parsing errors.", "UTF-8");

            // Create placeholder image files if needed
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
     * Create rendering hints for more robust PDF rendering.
     */
    private Map<String, Object> createRobustRenderingHints() {
        Map<String, Object> hints = new HashMap<>();

        // Add rendering hints to improve robustness
        hints.put(RenderDestination.EXPORT.toString(), Boolean.TRUE);  // Optimize for export quality
        hints.put("org.apache.pdfbox.rendering.UsePureJavaCMYKConversion", Boolean.TRUE);  // More reliable color conversion

        return hints;
    }

    /**
     * Detect problematic fonts in the document to avoid repeated errors.
     */
    private void detectProblematicFonts(PDDocument document) {
        problematicFonts.clear();

        try {
            // Sample a few pages to detect font issues
            int pageCount = document.getNumberOfPages();
            int sampleSize = Math.min(3, pageCount);

            for (int i = 0; i < sampleSize; i++) {
                PDPage page = document.getPage(i);
                try {
                    if (page.getResources() != null) {
                        // Get font names using the getFontNames method
                        Iterable<COSName> fontNames = page.getResources().getFontNames();

                        for (COSName fontName : fontNames) {
                            try {
                                // Try to get the font - this may throw exceptions for problematic fonts
                                PDFont font = page.getResources().getFont(fontName);

                                if (font != null) {
                                    String fontNameStr = font.getName();

                                    // Try a basic operation that might trigger embedded font issues
                                    font.isEmbedded();

                                    // Font seems okay
                                    problematicFonts.put(fontNameStr, false);
                                }
                            } catch (Exception e) {
                                // If we get Ascii85 errors or other font issues, mark as problematic
                                if (e.getMessage() != null &&
                                        (e.getMessage().contains("Ascii85") ||
                                                e.getMessage().contains("embedded") ||
                                                e.getMessage().contains("NullPointerException"))) {

                                    String fontNameStr = fontName.getName();
                                    log.warn("Detected problematic font: {} - {}", fontNameStr, e.getMessage());
                                    problematicFonts.put(fontNameStr, true);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Error checking fonts on page {}: {}", i, e.getMessage());
                }
            }

            // Log how many problematic fonts were detected
            int problemCount = 0;
            for (Boolean isProblematic : problematicFonts.values()) {
                if (isProblematic) problemCount++;
            }

            if (problemCount > 0) {
                log.warn("Detected {} problematic fonts in the document that may cause rendering issues",
                        problemCount);
            }

        } catch (Exception e) {
            log.warn("Error detecting problematic fonts: {}", e.getMessage());
        }
    }

    /**
     * Process a single page of a PDF document with error handling.
     */
    private void processPage(
            PDDocument document,
            PDFRenderer renderer,
            int pageIndex,
            Path pagesDir,
            Path thumbnailsDir,
            Path textDir,
            Path imagesDir,
            Path fontsDir,
            int dpi,
            int thumbDpi) throws IOException {

        int pageNumber = pageIndex + 1;
        log.debug("Processing page {} in thread {}", pageNumber, Thread.currentThread().getName());

        try {
            // Render the page to an image (full resolution)
            BufferedImage image = renderer.renderImageWithDPI(pageIndex, dpi, ImageType.RGB);

            // Save the rendered page
            File pageImageFile = pagesDir.resolve(String.format("page_%d.png", pageNumber)).toFile();
            ImageIOUtil.writeImage(image, pageImageFile.getAbsolutePath(), dpi);

            // Generate a thumbnail for quick preview
            BufferedImage thumbnail = renderer.renderImageWithDPI(pageIndex, thumbDpi, ImageType.RGB);
            File thumbnailFile = thumbnailsDir.resolve(String.format("page_%d_thumbnail.png", pageNumber)).toFile();
            ImageIOUtil.writeImage(thumbnail, thumbnailFile.getAbsolutePath(), thumbDpi);
        } catch (Exception e) {
            log.warn("Error rendering page {}: {}", pageNumber, e.getMessage());
            // Create placeholder image for failed rendering
            createPlaceholderImage(pagesDir.resolve(String.format("page_%d.png", pageNumber)).toFile(),
                    "Error rendering page " + pageNumber, 800, 1000);
            createPlaceholderImage(thumbnailsDir.resolve(String.format("page_%d_thumbnail.png", pageNumber)).toFile(),
                    "Error", 200, 250);
        }

        try {
            // Extract text from the page - with retry on error
            String pageText = null;
            try {
                pageText = textExtractor.extractTextFromPage(document, pageIndex);
            } catch (Exception e) {
                log.warn("Error extracting text from page {}, using fallback method: {}", pageNumber, e.getMessage());
                // Try fallback text extraction
                PDFTextExtractorFallback fallbackExtractor = new PDFTextExtractorFallback();
                pageText = fallbackExtractor.extractTextFromPage(document, pageIndex);
            }

            // If still null, use empty string
            if (pageText == null) {
                pageText = "[Text extraction failed for this page]";
            }

            File pageTextFile = textDir.resolve(String.format("page_%d.txt", pageNumber)).toFile();
            FileUtils.writeStringToFile(pageTextFile, pageText, "UTF-8");

            // Extract detailed text elements with position information - don't fail if it errors
            List<TextElement> textElements = new ArrayList<>();
            try {
                textElements = textExtractor.extractTextElementsFromPage(document, pageIndex);
            } catch (Exception e) {
                log.warn("Error extracting text elements from page {}: {}", pageNumber, e.getMessage());
            }

            if (!textElements.isEmpty()) {
                File textElementsFile = textDir.resolve(String.format("page_%d_elements.json", pageNumber)).toFile();
                FileUtils.writeStringToFile(textElementsFile, convertElementsToJson(textElements), "UTF-8");
            }
        } catch (Exception e) {
            log.error("Error processing text for page {}: {}", pageNumber, e.getMessage());
            // Create minimal text file
            try {
                File pageTextFile = textDir.resolve(String.format("page_%d.txt", pageNumber)).toFile();
                FileUtils.writeStringToFile(pageTextFile, "[Text extraction failed]", "UTF-8");
            } catch (IOException ioe) {
                log.error("Error creating placeholder text file", ioe);
            }
        }

        try {
            // Extract images from the page - continue if it fails
            try {
                imageExtractor.extractImagesFromPage(document, pageIndex, imagesDir);
            } catch (Exception e) {
                log.warn("Error extracting images from page {}: {}", pageNumber, e.getMessage());
            }

            // Extract fonts from the page - continue if it fails
            try {
                fontAnalyzer.analyzeFontsOnPage(document, pageIndex, fontsDir);
            } catch (Exception e) {
                log.warn("Error analyzing fonts on page {}: {}", pageNumber, e.getMessage());
            }
        } catch (Exception e) {
            log.error("Complete failure processing page {}: {}", pageNumber, e.getMessage());
            throw e; // Propagate to retry logic
        }
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

        // Check font-related errors in the cause chain
        Throwable cause = e.getCause();
        while (cause != null) {
            if (cause.getMessage() != null &&
                    (cause.getMessage().contains("font") ||
                            cause.getMessage().contains("Ascii85") ||
                            cause.getMessage().contains("embedded"))) {
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
     * Get a rendered page image for a PDF document.
     *
     * @param pdfDocument The PDF document
     * @param pageNumber The page number (1-based index)
     * @return File reference to the rendered page image
     */
    public File getRenderedPage(PdfDocument pdfDocument, int pageNumber) {
        Path pageImagePath = Paths.get("uploads", "documents", pdfDocument.getFileId(),
                "pages", String.format("page_%d.png", pageNumber));
        return pageImagePath.toFile();
    }

    /**
     * Get a rendered page thumbnail for a PDF document.
     *
     * @param pdfDocument The PDF document
     * @param pageNumber The page number (1-based index)
     * @return File reference to the rendered page thumbnail
     */
    public File getPageThumbnail(PdfDocument pdfDocument, int pageNumber) {
        Path thumbnailPath = Paths.get("uploads", "documents", pdfDocument.getFileId(),
                "thumbnails", String.format("page_%d_thumbnail.png", pageNumber));
        return thumbnailPath.toFile();
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
}