package guraa.pdfcompare.service;

import guraa.pdfcompare.model.Comparison;
import guraa.pdfcompare.model.PdfDocument;
import guraa.pdfcompare.repository.ComparisonRepository;
import guraa.pdfcompare.repository.PdfRepository;
import guraa.pdfcompare.util.PdfProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
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

    // Required service dependencies - add these fields
    private final PdfRepository pdfRepository;
    private final ComparisonRepository comparisonRepository;
    private final PdfProcessor pdfProcessor;
    private final ComparisonService comparisonService;
    private final ThumbnailService thumbnailService;

    /**
     * Store a PDF file and process it.
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

            // Process the PDF
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
            // Clean up temporary directory
            tempFile.delete();
            tempDir.toFile().delete();
        }
    }

    /**
     * Process a PDF file and store it.
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

            // Process the PDF
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
     * Get a rendered page image for a PDF document.
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
            }

            throw new IllegalArgumentException("Page not found or could not be rendered: " + pageNumber);
        }

        return new FileSystemResource(pageFile);
    }

    /**
     * Generate a page image for a PDF document.
     *
     * @param document The PDF document
     * @param pageNumber The page number (1-based)
     * @throws IOException If there's an error generating the image
     */
    private void generatePageImage(PdfDocument document, int pageNumber) throws IOException {
        // Implementation depends on your PDF processing logic
        // Should create the page image file at the expected location

        // Example implementation:
        try (PDDocument pdfDocument = PDDocument.load(new File(document.getFilePath()))) {
            // Check page bounds
            if (pageNumber < 1 || pageNumber > pdfDocument.getNumberOfPages()) {
                throw new IllegalArgumentException("Invalid page number: " + pageNumber);
            }

            // Create renderer
            PDFRenderer renderer = new PDFRenderer(pdfDocument);

            // Render page (0-based page index)
            BufferedImage image = renderer.renderImageWithDPI(pageNumber - 1, 150, ImageType.RGB);

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

        // Save options to the comparison if provided
        if (options != null) {
            // Convert Map<String, Object> to Map<String, String>
            Map<String, String> stringOptions = new HashMap<>();
            options.forEach((key, value) -> {
                if (value != null) {
                    stringOptions.put(key, value.toString());
                }
            });
            comparison.setOptions(stringOptions);
        }

        // Save the comparison
        comparisonRepository.save(comparison);

        // Start the comparison process asynchronously
        startComparisonProcess(comparisonId);

        return comparisonId;
    }

    /**
     * Start the comparison process asynchronously.
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
                comparison.setStatusMessage("Comparison failed: " + e.getMessage());
                comparison.setCompletionTime(LocalDateTime.now());
                comparisonRepository.save(comparison);
            }
        }
    }
}