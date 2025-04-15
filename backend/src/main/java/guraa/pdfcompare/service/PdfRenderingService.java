package guraa.pdfcompare.service;

import guraa.pdfcompare.model.PdfDocument;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.rendering.RenderDestination;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Optimized service for rendering PDF pages with improved error handling, caching, and timeout management.
 */
@Slf4j
@Service
public class PdfRenderingService {

    private final ExecutorService executorService;

    // Cache for PDDocument instances to avoid repeatedly loading the same document
    private final Map<String, PDDocument> documentCache = new ConcurrentHashMap<>();

    // Limit the number of documents in the cache to avoid memory issues
    private static final int MAX_CACHED_DOCUMENTS = 5;

    // Lock objects for synchronized access to documents by file ID
    private final Map<String, Object> documentLocks = new ConcurrentHashMap<>();

    // Keep track of when documents were last accessed for cache eviction
    private final Map<String, Long> documentLastAccessed = new ConcurrentHashMap<>();

    // Cache of already rendered pages to avoid redundant work
    private final Map<String, Boolean> renderedPageCache = new ConcurrentHashMap<>();

    @Value("${app.rendering.dpi:300}")
    private float renderingDpi;

    @Value("${app.rendering.thumbnail-dpi:72}")
    private float thumbnailDpi;

    @Value("${app.rendering.image-type:RGB}")
    private String renderingImageType;

    @Value("${app.rendering.format:png}")
    private String renderingFormat;

    @Value("${app.rendering.thumbnail-width:200}")
    private int thumbnailWidth;

    @Value("${app.rendering.thumbnail-height:280}")
    private int thumbnailHeight;

    // Batch size for parallel rendering
    private static final int BATCH_SIZE = 4;

    // Maximum retries for file operations
    private static final int MAX_RETRIES = 3;

    // Delay between retries in milliseconds
    private static final int RETRY_DELAY_MS = 100;

    // Timeout for rendering operations in seconds
    private static final int RENDERING_TIMEOUT_SECONDS = 30;

    public PdfRenderingService(
            @Qualifier("renderingExecutor") ExecutorService executorService) {
        this.executorService = executorService;
    }

    /**
     * Render a specific page of a PDF document with robust error handling and retries.
     *
     * @param document   The document
     * @param pageNumber The page number (1-based)
     * @return The rendered page file
     * @throws IOException If there is an error rendering the page
     */
    public File renderPage(PdfDocument document, int pageNumber) throws IOException {
        String renderedPagePath = document.getRenderedPagePath(pageNumber);
        File renderedPage = new File(renderedPagePath);
        String cacheKey = document.getFileId() + "_" + pageNumber;

        // Check if the page is already rendered and cached
        if (renderedPageCache.containsKey(cacheKey) && renderedPage.exists() && renderedPage.length() > 0) {
            return renderedPage;
        }

        // Ensure the parent directory exists
        Files.createDirectories(renderedPage.getParentFile().toPath());

        // Create a unique temporary file name to avoid conflicts
        String tempFileName = "render_" + UUID.randomUUID().toString() + "." + renderingFormat;
        Path tempFilePath = Files.createTempFile(renderedPage.getParentFile().toPath(), "render_", "." + renderingFormat);

        // Get a cached document or load it with error handling
        PDDocument pdDocument = null;
        try {
            pdDocument = getPDDocument(document);

            // Verify page number is valid
            if (pageNumber < 1 || pageNumber > pdDocument.getNumberOfPages()) {
                throw new IllegalArgumentException("Invalid page number: " + pageNumber +
                        " for document with " + pdDocument.getNumberOfPages() + " pages");
            }

            // Use a try-with-resources to ensure PDDocument is properly closed if we're using a standalone instance
            try {
                // Create a renderer for this document
                PDFRenderer renderer = new PDFRenderer(pdDocument);

                // Render with error handling for corrupt images
                BufferedImage image = renderPageSafely(renderer, pageNumber - 1);

                // Write to temp file
                ImageIO.write(image, renderingFormat, tempFilePath.toFile());

                // Try to move with retries for file access conflicts
                moveFileWithRetry(tempFilePath, renderedPage.toPath());

                // Cache the fact that this page is rendered
                renderedPageCache.put(cacheKey, true);

                return renderedPage;
            }
        } catch (Exception e) {
            log.error("Error rendering page {} of document {}: {}",
                    pageNumber, document.getFileId(), e.getMessage(), e);
            throw new IOException("Failed to render page " + pageNumber, e);
        } finally {
            // Delete temp file if it still exists
            try {
                Files.deleteIfExists(tempFilePath);
            } catch (Exception e) {
                log.warn("Failed to delete temporary file: {}", tempFilePath);
            }
        }
    }

    /**
     * Pre-render all pages of a PDF document in parallel with improved error handling and batching.
     *
     * @param document The document to pre-render
     * @throws IOException If there is an error rendering the pages
     */
    public void preRenderAllPages(PdfDocument document) throws IOException {
        log.info("Pre-rendering all pages for document: {}", document.getFileId());

        long startTime = System.currentTimeMillis();
        int pageCount = document.getPageCount();
        AtomicInteger completedPages = new AtomicInteger(0);

        // Divide pages into batches for parallel processing
        List<List<Integer>> batches = createBatches(pageCount, BATCH_SIZE);

        // Get a cached document or load it
        PDDocument pdDocument = getPDDocument(document);

        // Check if document is corrupted before proceeding
        if (pdDocument == null) {
            throw new IOException("Cannot render pages - failed to load document: " + document.getFileId());
        }

        // Create tasks for parallel execution
        List<CompletableFuture<Void>> batchFutures = new ArrayList<>();

        for (List<Integer> batch : batches) {
            CompletableFuture<Void> batchFuture = CompletableFuture.runAsync(() -> {
                PDFRenderer renderer = new PDFRenderer(pdDocument);

                for (int pageNumber : batch) {
                    String cacheKey = document.getFileId() + "_" + pageNumber;

                    try {
                        File renderedPage = new File(document.getRenderedPagePath(pageNumber));

                        // Skip if already rendered and cached
                        if (renderedPageCache.containsKey(cacheKey) && renderedPage.exists() && renderedPage.length() > 0) {
                            int completed = completedPages.incrementAndGet();
                            logProgress(document.getFileId(), completed, pageCount);
                            continue;
                        }

                        // Ensure directory exists
                        Files.createDirectories(renderedPage.getParentFile().toPath());

                        // Create a unique temporary file name
                        String tempFileName = "render_batch_" + UUID.randomUUID().toString() + "." + renderingFormat;
                        Path tempFile = Files.createTempFile(renderedPage.getParentFile().toPath(), "render_batch_", "." + renderingFormat);

                        try {
                            // Render the page with error handling for corrupt images
                            BufferedImage image = renderPageSafely(renderer, pageNumber - 1);

                            // Write to temp file
                            ImageIO.write(image, renderingFormat, tempFile.toFile());

                            // Move to final location with retries
                            moveFileWithRetry(tempFile, renderedPage.toPath());

                            // Cache the fact that this page is rendered
                            renderedPageCache.put(cacheKey, true);
                        } catch (Exception e) {
                            log.error("Error pre-rendering page {} of document {}: {}",
                                    pageNumber, document.getFileId(), e.getMessage(), e);
                        } finally {
                            // Ensure temp file is deleted
                            try {
                                Files.deleteIfExists(tempFile);
                            } catch (Exception e) {
                                log.warn("Failed to delete temporary file: {}", tempFile);
                            }
                        }

                        int completed = completedPages.incrementAndGet();
                        logProgress(document.getFileId(), completed, pageCount);
                    } catch (Exception e) {
                        log.error("Error pre-rendering page {} of document {}: {}",
                                pageNumber, document.getFileId(), e.getMessage(), e);
                        // Continue with other pages even if one fails
                        completedPages.incrementAndGet();
                    }
                }
            }, executorService);

            batchFutures.add(batchFuture);
        }

        // Wait for all batches to complete with timeout
        try {
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                    batchFutures.toArray(new CompletableFuture[0]));

            allFutures.get(RENDERING_TIMEOUT_SECONDS * batchFutures.size(), TimeUnit.SECONDS);

            long endTime = System.currentTimeMillis();
            log.info("Successfully pre-rendered {} pages for document: {} in {}ms",
                    completedPages.get(), document.getFileId(), (endTime - startTime));
        } catch (TimeoutException e) {
            log.warn("Timeout waiting for page rendering to complete. Rendered {} of {} pages for document: {}",
                    completedPages.get(), pageCount, document.getFileId());
        } catch (Exception e) {
            log.error("Error waiting for page rendering to complete: {}", e.getMessage(), e);
            throw new IOException("Pre-rendering failed", e);
        }
    }

    /**
     * Render a page safely, handling exceptions that might occur with corrupted images.
     *
     * @param renderer The PDF renderer
     * @param pageIndex The zero-based page index
     * @return The rendered image
     * @throws IOException If rendering fails completely
     */
    private BufferedImage renderPageSafely(PDFRenderer renderer, int pageIndex) throws IOException {
        try {
            // First try normal rendering
            return renderer.renderImageWithDPI(pageIndex, renderingDpi, getImageType());
        } catch (ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            // If we get array bounds errors (common with corrupt images), try a more robust approach
            log.warn("Error during standard rendering, trying safer approach for page {}: {}",
                    pageIndex + 1, e.getMessage());

            try {
                // Try with a different render destination that can be more forgiving
                return renderer.renderImageWithDPI(pageIndex, renderingDpi, getImageType(), RenderDestination.EXPORT);
            } catch (Exception e2) {
                // If that also fails, try getting just a page preview without images
                log.warn("Safer rendering approach failed, trying fallback for page {}: {}",
                        pageIndex + 1, e2.getMessage());

                // Create a blank white image as fallback
                BufferedImage fallbackImage = createFallbackImage(renderer, pageIndex);
                return fallbackImage;
            }
        }
    }

    /**
     * Create a fallback image for cases where normal rendering fails.
     *
     * @param renderer The PDF renderer
     * @param pageIndex The zero-based page index
     * @return A fallback image
     */
    private BufferedImage createFallbackImage(PDFRenderer renderer, int pageIndex) {
        try {
            // Try to get page dimensions
            PDPage page = renderer.getDocument().getPage(pageIndex);
            float width = page.getMediaBox().getWidth();
            float height = page.getMediaBox().getHeight();

            // Create a blank white image with page dimensions
            int pixelWidth = Math.round(width * renderingDpi / 72);
            int pixelHeight = Math.round(height * renderingDpi / 72);

            BufferedImage image = new BufferedImage(
                    pixelWidth, pixelHeight, BufferedImage.TYPE_INT_RGB);

            // Fill with white background
            Graphics2D g = image.createGraphics();
            g.setBackground(Color.WHITE);
            g.clearRect(0, 0, pixelWidth, pixelHeight);
            g.dispose();

            log.info("Created fallback image for page {}", pageIndex + 1);
            return image;
        } catch (Exception e) {
            // If even that fails, create a minimal blank image
            log.error("Failed to create proper fallback, using minimal image for page {}", pageIndex + 1);
            BufferedImage image = new BufferedImage(612, 792, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            g.setBackground(Color.WHITE);
            g.clearRect(0, 0, 612, 792);
            g.dispose();
            return image;
        }
    }

    /**
     * Move a file with retry logic for handling access conflicts.
     *
     * @param source The source file path
     * @param target The target file path
     * @throws IOException If the move operation fails after all retries
     */
    private void moveFileWithRetry(Path source, Path target) throws IOException {
        IOException lastException = null;

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                return; // Success
            } catch (IOException e) {
                lastException = e;
                log.debug("File move attempt {} failed: {}", attempt + 1, e.getMessage());

                if (attempt < MAX_RETRIES - 1) {
                    try {
                        // Wait before retrying
                        Thread.sleep(RETRY_DELAY_MS * (attempt + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Thread interrupted during retry delay", ie);
                    }
                }
            }
        }

        // If we got here, all retries failed
        if (lastException != null) {
            throw new IOException("Failed to move file after " + MAX_RETRIES + " attempts", lastException);
        }
    }

    /**
     * Log progress during pre-rendering.
     *
     * @param documentId The document ID
     * @param completed The number of completed pages
     * @param total The total number of pages
     */
    private void logProgress(String documentId, int completed, int total) {
        if (completed % 5 == 0 || completed == total) {
            log.info("Pre-rendering progress for document {}: {}/{} pages ({} %)",
                    documentId, completed, total, (completed * 100) / total);
        }
    }

    /**
     * Get a rendered page as a FileSystemResource.
     *
     * @param document   The document
     * @param pageNumber The page number (1-based)
     * @return The rendered page as a FileSystemResource
     * @throws IOException If there is an error rendering the page
     */
    public FileSystemResource getRenderedPage(PdfDocument document, int pageNumber) throws IOException {
        // Render the page if it's not already rendered
        File renderedPage = renderPage(document, pageNumber);

        // Verify the file was rendered successfully
        if (!renderedPage.exists() || renderedPage.length() == 0) {
            throw new IOException("Failed to render page " + pageNumber + " of document " + document.getFileId());
        }

        return new FileSystemResource(renderedPage);
    }

    /**
     * Generate a thumbnail for a specific page of a PDF document.
     *
     * @param document   The document
     * @param pageNumber The page number (1-based)
     * @return The thumbnail as a FileSystemResource
     * @throws IOException If there is an error generating the thumbnail
     */
    public FileSystemResource getThumbnail(PdfDocument document, int pageNumber) throws IOException {
        String thumbnailPath = document.getThumbnailPath(pageNumber);
        File thumbnailFile = new File(thumbnailPath);
        String cacheKey = document.getFileId() + "_thumbnail_" + pageNumber;

        // If the file exists and is not empty, return it
        if (renderedPageCache.containsKey(cacheKey) && thumbnailFile.exists() && thumbnailFile.length() > 0) {
            return new FileSystemResource(thumbnailFile);
        }

        // Ensure the directory exists
        Files.createDirectories(thumbnailFile.getParentFile().toPath());

        // Generate the thumbnail
        try {
            // Get a cached document or load it
            PDDocument pdDocument = getPDDocument(document);

            if (pdDocument == null) {
                throw new IOException("Cannot generate thumbnail - failed to load document");
            }

            // Check if the page number is valid
            if (pageNumber < 1 || pageNumber > pdDocument.getNumberOfPages()) {
                throw new IOException("Invalid page number: " + pageNumber +
                        ". Document has " + pdDocument.getNumberOfPages() + " pages.");
            }

            // Create a temporary file
            Path tempFile = Files.createTempFile(thumbnailFile.getParentFile().toPath(),
                    "thumbnail_", "." + renderingFormat);

            try {
                // Render the page at a lower DPI
                PDFRenderer renderer = new PDFRenderer(pdDocument);
                BufferedImage image = renderPageSafely(renderer, pageNumber - 1);

                // Resize to the desired thumbnail dimensions
                BufferedImage thumbnailImage = resizeImage(image, thumbnailWidth, thumbnailHeight);

                // Save the thumbnail to the temp file
                if (!ImageIO.write(thumbnailImage, renderingFormat, tempFile.toFile())) {
                    throw new IOException("Failed to write thumbnail in " + renderingFormat + " format");
                }

                // Move to final location with retries
                moveFileWithRetry(tempFile, thumbnailFile.toPath());

                // Cache the fact that this thumbnail is generated
                renderedPageCache.put(cacheKey, true);
            } finally {
                // Ensure temp file is deleted
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Exception e) {
                    log.warn("Failed to delete temporary thumbnail file: {}", tempFile);
                }
            }
        } catch (IOException e) {
            log.error("Failed to generate thumbnail for page {} of document {}: {}",
                    pageNumber, document.getFileId(), e.getMessage(), e);
            throw e;
        }

        // Verify the thumbnail was generated successfully
        if (!thumbnailFile.exists() || thumbnailFile.length() == 0) {
            throw new IOException("Failed to generate thumbnail for page " + pageNumber +
                    " of document " + document.getFileId());
        }

        return new FileSystemResource(thumbnailFile);
    }

    /**
     * Resize an image to the specified dimensions.
     *
     * @param image  The image to resize
     * @param width  The target width
     * @param height The target height
     * @return The resized image
     */
    private BufferedImage resizeImage(BufferedImage image, int width, int height) {
        if (width <= 0 || height <= 0) {
            width = Math.max(1, width);
            height = Math.max(1, height);
        }

        BufferedImage resizedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resizedImage.createGraphics();

        try {
            // Set rendering hints for better quality
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Draw the original image scaled to the new dimensions
            g.drawImage(image, 0, 0, width, height, null);

            return resizedImage;
        } finally {
            g.dispose();
        }
    }

    /**
     * Get the image type for rendering.
     *
     * @return The image type
     */
    private ImageType getImageType() {
        switch (renderingImageType.toUpperCase()) {
            case "BINARY":
                return ImageType.BINARY;
            case "GRAY":
                return ImageType.GRAY;
            case "ARGB":
                return ImageType.ARGB;
            case "RGB":
            default:
                return ImageType.RGB;
        }
    }

    /**
     * Create batches of page numbers for parallel processing.
     *
     * @param pageCount The total number of pages
     * @param batchSize The batch size
     * @return A list of batches, where each batch is a list of page numbers
     */
    private List<List<Integer>> createBatches(int pageCount, int batchSize) {
        List<List<Integer>> batches = new ArrayList<>();

        for (int start = 0; start < pageCount; start += batchSize) {
            List<Integer> batch = new ArrayList<>();

            for (int i = start; i < Math.min(start + batchSize, pageCount); i++) {
                // Page numbers are 1-based
                batch.add(i + 1);
            }

            batches.add(batch);
        }

        return batches;
    }

    /**
     * Get a PDDocument instance for a document, using cache when possible.
     *
     * @param document The document
     * @return The PDDocument instance, or null if loading fails
     */
    private PDDocument getPDDocument(PdfDocument document) {
        String fileId = document.getFileId();

        // Get or create a lock object for this document
        Object lock = documentLocks.computeIfAbsent(fileId, k -> new Object());

        synchronized (lock) {
            // Update last accessed time
            documentLastAccessed.put(fileId, System.currentTimeMillis());

            // Check if the document is already cached
            PDDocument pdDocument = documentCache.get(fileId);

            if (pdDocument != null) {
                return pdDocument;
            }

            // Evict oldest document if cache is full
            if (documentCache.size() >= MAX_CACHED_DOCUMENTS) {
                evictOldestDocument();
            }

            // Load the document with error handling
            try {
                // Use PDFBox Loader instead of PDDocument.load for better handling of corrupt documents
                pdDocument = Loader.loadPDF(new File(document.getFilePath()));

                // Cache the document
                documentCache.put(fileId, pdDocument);

                return pdDocument;
            } catch (Exception e) {
                log.error("Failed to load document {}: {}", fileId, e.getMessage(), e);
                return null;
            }
        }
    }

    /**
     * Evict the oldest document from the cache.
     */
    private void evictOldestDocument() {
        if (documentCache.isEmpty()) {
            return;
        }

        // Find the oldest document
        String oldestId = null;
        long oldestTime = Long.MAX_VALUE;

        for (Map.Entry<String, Long> entry : documentLastAccessed.entrySet()) {
            if (entry.getValue() < oldestTime && documentCache.containsKey(entry.getKey())) {
                oldestTime = entry.getValue();
                oldestId = entry.getKey();
            }
        }

        if (oldestId != null) {
            // Close and remove the document from the cache
            synchronized (documentLocks.computeIfAbsent(oldestId, k -> new Object())) {
                PDDocument document = documentCache.remove(oldestId);
                if (document != null) {
                    try {
                        document.close();
                        log.debug("Evicted document {} from cache", oldestId);
                    } catch (IOException e) {
                        log.warn("Error closing document {}: {}", oldestId, e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Close all cached documents.
     */
    public void closeAllDocuments() {
        for (Map.Entry<String, PDDocument> entry : documentCache.entrySet()) {
            String fileId = entry.getKey();
            PDDocument document = entry.getValue();

            synchronized (documentLocks.computeIfAbsent(fileId, k -> new Object())) {
                try {
                    document.close();
                    log.debug("Closed document {}", fileId);
                } catch (IOException e) {
                    log.warn("Error closing document {}: {}", fileId, e.getMessage());
                }
            }
        }

        documentCache.clear();
        documentLastAccessed.clear();
    }
}