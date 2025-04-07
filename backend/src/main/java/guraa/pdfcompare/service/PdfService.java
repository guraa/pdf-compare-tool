package guraa.pdfcompare.service;

import guraa.pdfcompare.model.Comparison;
import guraa.pdfcompare.model.PdfDocument;
import guraa.pdfcompare.repository.ComparisonRepository;
import guraa.pdfcompare.repository.PdfRepository;
import guraa.pdfcompare.util.PdfProcessor;
import guraa.pdfcompare.util.PdfLoader;
import guraa.pdfcompare.util.PdfRenderer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.springframework.core.io.FileSystemResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.transaction.Transactional;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfService {

    private final PdfRepository pdfRepository;
    private final ComparisonRepository comparisonRepository;
    private final PdfProcessor pdfProcessor;
    private final ComparisonService comparisonService;
    private final ThumbnailService thumbnailService;

    /**
     * Store a PDF file and process it with enhanced error handling.
     *
     * @param file The uploaded file
     * @return The processed PDF document
     * @throws IOException If there's an error processing the file
     */
    @Transactional
    public PdfDocument storePdf(MultipartFile file) throws IOException {
        // Create a temporary file
        Path tempDir = Files.createTempDirectory("pdf-upload");
        File tempFile = tempDir.resolve(file.getOriginalFilename()).toFile();

        try {
            // Write the uploaded file to the temporary file
            file.transferTo(tempFile);

            // Process the PDF with enhanced error handling
            PdfDocument document = pdfProcessor.processPdf(tempFile);

            // Check if this document already exists by content hash
            Optional<PdfDocument> existingDocument = pdfRepository.findByContentHash(document.getContentHash());

            if (existingDocument.isPresent()) {
                log.info("Document with same content hash already exists, reusing: {}", existingDocument.get().getFileId());
                return existingDocument.get();
            } else {
                // Save to repository
                return pdfRepository.save(document);
            }
        } finally {
            try {
                // Clean up temporary directory
                tempFile.delete();
                tempDir.toFile().delete();
            } catch (Exception e) {
                log.warn("Failed to clean up temporary files: {}", e.getMessage());
            }
        }
    }

    /**
     * Process a PDF file and store it with enhanced error handling.
     * This is a variant of the storePdf method that works with an already saved file.
     *
     * @param file The PDF file
     * @param originalFileName The original file name
     * @return The processed PDF document
     * @throws IOException If there's an error processing the file
     */
    @Transactional
    public PdfDocument processPdfFile(File file, String originalFileName) throws IOException {
        try {
            // Check if the file is a PDF
            if (!originalFileName.toLowerCase().endsWith(".pdf")) {
                throw new IllegalArgumentException("Only PDF files are allowed");
            }

            // Process the PDF with enhanced error handling
            PdfDocument document = pdfProcessor.processPdf(file);
            document.setFileName(originalFileName);

            // Check if this document already exists by content hash
            Optional<PdfDocument> existingDocument = pdfRepository.findByContentHash(document.getContentHash());

            if (existingDocument.isPresent()) {
                log.info("Document with same content hash already exists, reusing: {}", existingDocument.get().getFileId());
                return existingDocument.get();
            }

            // Generate thumbnails for all pages
            try {
                thumbnailService.generateThumbnails(document);
            } catch (Exception e) {
                log.warn("Error generating thumbnails: {}", e.getMessage());
                // Continue without thumbnails - they'll be generated on demand
            }

            // Save to repository
            return pdfRepository.save(document);
        } catch (IOException e) {
            log.error("Failed to process PDF file", e);
            throw e;
        }
    }

    /**
     * Get a PDF document by its file ID.
     *
     * @param fileId The file ID
     * @return The PDF document
     * @throws IllegalArgumentException If the document is not found
     */
    public PdfDocument getDocumentById(String fileId) {
        return pdfRepository.findByFileId(fileId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + fileId));
    }

    /**
     * Get a rendered page image for a PDF document with enhanced error handling.
     *
     * @param fileId The file ID
     * @param pageNumber The page number (1-based)
     * @return FileSystemResource for the rendered page image
     */
    public FileSystemResource getRenderedPage(String fileId, int pageNumber) {
        PdfDocument document = getDocumentById(fileId);

        // Get the path to the rendered page
        Path pagePath = Paths.get("uploads", "documents", fileId, "pages", "page_" + pageNumber + ".png");
        File pageFile = pagePath.toFile();

        // Check if the file exists
        if (!pageFile.exists()) {
            // Try to generate the page if it doesn't exist
            try {
                generatePageImage(document, pageNumber);
                if (pageFile.exists()) {
                    return new FileSystemResource(pageFile);
                }
            } catch (Exception e) {
                log.error("Error generating page image: {}", e.getMessage());

                // Try one more time with a more robust approach
                try {
                    generatePageWithRobustRenderer(document, pageNumber);
                    if (pageFile.exists()) {
                        return new FileSystemResource(pageFile);
                    }
                } catch (Exception e2) {
                    log.error("Final attempt to generate page image failed: {}", e2.getMessage());
                }
            }

            throw new IllegalArgumentException("Page not found or could not be rendered: " + pageNumber);
        }

        return new FileSystemResource(pageFile);
    }

    /**
     * Generate a page image for a PDF document with standard rendering.
     *
     * @param document The PDF document
     * @param pageNumber The page number (1-based)
     * @throws IOException If there's an error generating the image
     */
    private void generatePageImage(PdfDocument document, int pageNumber) throws IOException {
        try (PDDocument pdfDocument = PDDocument.load(new File(document.getFilePath()))) {
            // Check page bounds
            if (pageNumber < 1 || pageNumber > pdfDocument.getNumberOfPages()) {
                throw new IllegalArgumentException("Invalid page number: " + pageNumber);
            }

            // Create renderer
            PdfRenderer robustRenderer = new PdfRenderer(pdfDocument)
                    .setDefaultDPI(150)
                    .setImageType(ImageType.RGB);

            // Render page (0-based page index)
            BufferedImage image = robustRenderer.renderPage(pageNumber - 1);

            // Ensure directories exist
            Path pagesDir = Paths.get("uploads", "documents", document.getFileId(), "pages");
            Files.createDirectories(pagesDir);

            // Save image
            File outputFile = pagesDir.resolve("page_" + pageNumber + ".png").toFile();
            javax.imageio.ImageIO.write(image, "PNG", outputFile);
        }
    }

    /**
     * Generate a page image with maximum resilience against errors.
     * Used as a last resort when standard rendering fails.
     */
    private void generatePageWithRobustRenderer(PdfDocument document, int pageNumber) throws IOException {
        // Try with even more robust approach
        try (PDDocument pdfDocument = PdfLoader.loadDocumentWithFallbackOptions(new File(document.getFilePath()))) {
            // Check page bounds
            if (pageNumber < 1 || pageNumber > pdfDocument.getNumberOfPages()) {
                throw new IllegalArgumentException("Invalid page number: " + pageNumber);
            }

            // Create an extremely robust renderer with minimal settings
            PdfRenderer renderer = new PdfRenderer(pdfDocument)
                    .setDefaultDPI(72) // Use a lower DPI for better reliability
                    .setFallbackDPI(36) // Very low DPI for extreme fallback
                    .setImageType(ImageType.RGB);

            // Try to render with all fallbacks enabled
            BufferedImage image = renderer.renderPage(pageNumber - 1);

            // Ensure directories exist
            Path pagesDir = Paths.get("uploads", "documents", document.getFileId(), "pages");
            Files.createDirectories(pagesDir);

            // Save image
            File outputFile = pagesDir.resolve("page_" + pageNumber + ".png").toFile();
            javax.imageio.ImageIO.write(image, "PNG", outputFile);
        }
    }

    /**
     * Compare two PDF documents.
     *
     * @param baseFileId The base file ID
     * @param compareFileId The comparison file ID
     * @param options Comparison options
     * @return The comparison ID
     */
    @Transactional
    public String comparePdfs(String baseFileId, String compareFileId, Map<String, Object> options) {
        // Validate file IDs
        PdfDocument baseDocument = getDocumentById(baseFileId);
        PdfDocument compareDocument = getDocumentById(compareFileId);

        // Set up default options if not provided
        boolean smartMatching = true;  // Default to true
        String textComparisonMethod = "smart";
        String differenceThreshold = "normal";

        // Override with provided options if available
        if (options != null) {
            if (options.containsKey("smartMatching")) {
                smartMatching = Boolean.TRUE.equals(options.get("smartMatching"));
            }

            if (options.containsKey("textComparisonMethod")) {
                textComparisonMethod = (String) options.get("textComparisonMethod");
            }

            if (options.containsKey("differenceThreshold")) {
                differenceThreshold = (String) options.get("differenceThreshold");
            }

            // Add a robustMode option with special parsing for problematic PDFs
            if (!options.containsKey("robustMode")) {
                options = new HashMap<>(options);
                options.put("robustMode", true);
            }
        } else {
            // Create options map with defaults and robustMode
            options = new HashMap<>();
            options.put("smartMatching", smartMatching);
            options.put("textComparisonMethod", textComparisonMethod);
            options.put("differenceThreshold", differenceThreshold);
            options.put("robustMode", true);
        }

        // Generate a new comparison ID
        String comparisonId = UUID.randomUUID().toString();

        // Create a comparison record
        Comparison comparison = Comparison.builder()
                .comparisonId(comparisonId)
                .baseDocument(baseDocument)
                .compareDocument(compareDocument)
                .startTime(LocalDateTime.now())
                .status(Comparison.ComparisonStatus.PENDING)
                .smartMatching(smartMatching)
                .textComparisonMethod(textComparisonMethod)
                .differenceThreshold(differenceThreshold)
                .build();

        // Save options to the comparison
        // Convert Map<String, Object> to Map<String, String>
        Map<String, String> stringOptions = new HashMap<>();
        options.forEach((key, value) -> {
            if (value != null) {
                stringOptions.put(key, value.toString());
            }
        });
        comparison.setOptions(stringOptions);

        // Save the comparison
        comparisonRepository.save(comparison);

        // Start the comparison process asynchronously
        startComparisonProcess(comparisonId);

        return comparisonId;
    }

    /**
     * Start the comparison process asynchronously with improved error handling.
     *
     * @param comparisonId The comparison ID
     */
    @Async("comparisonExecutor")
    protected void startComparisonProcess(String comparisonId) {
        try {
            comparisonService.processComparison(comparisonId);
        } catch (Exception e) {
            log.error("Error processing comparison: {}", comparisonId, e);

            // Update comparison status to failed
            Comparison comparison = comparisonRepository.findByComparisonId(comparisonId)
                    .orElse(null);

            if (comparison != null) {
                comparison.setStatus(Comparison.ComparisonStatus.FAILED);

                // Create a more descriptive error message
                String errorMessage = "Comparison failed: " + e.getMessage();
                if (isPdfStreamError(e)) {
                    errorMessage = "PDF stream errors detected during comparison. The documents may contain damaged or problematic content.";
                } else if (isRenderingError(e)) {
                    errorMessage = "PDF rendering errors detected during comparison. Some pages may not be fully compared.";
                } else if (isOutOfMemoryError(e)) {
                    errorMessage = "Out of memory error during comparison. The PDF files may be too large or complex.";
                }

                comparison.setStatusMessage(errorMessage);
                comparison.setCompletionTime(LocalDateTime.now());
                comparisonRepository.save(comparison);
            }
        }
    }

    /**
     * Check if the error is related to PDF stream issues
     */
    private boolean isPdfStreamError(Exception e) {
        String message = e.getMessage();
        if (message == null) return false;

        return message.contains("Ascii85") ||
                message.contains("FlateFilter") ||
                message.contains("stream") ||
                message.contains("DataFormatException");
    }

    /**
     * Check if the error is related to rendering issues
     */
    private boolean isRenderingError(Exception e) {
        String message = e.getMessage();
        if (message == null) return false;

        return message.contains("rendering") ||
                message.contains("bounds") ||
                message.contains("ArrayIndexOutOfBoundsException") ||
                message.contains("graphics");
    }

    /**
     * Check if the error is related to memory issues
     */

    private boolean isOutOfMemoryError(Throwable e) {
        return e instanceof OutOfMemoryError ||
                (e.getCause() instanceof OutOfMemoryError) ||
                (e.getMessage() != null && e.getMessage().contains("OutOfMemory"));
    }
}