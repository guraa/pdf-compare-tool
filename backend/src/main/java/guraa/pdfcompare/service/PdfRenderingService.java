package guraa.pdfcompare.service;

import guraa.pdfcompare.model.PdfDocument;
import guraa.pdfcompare.util.FileUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Qualifier;
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
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * PDF Rendering Service with consistent DPI settings.
 * This service ensures that all pages are rendered at the same DPI value.
 */
@Slf4j
@Service
public class PdfRenderingService {

    private final ExecutorService executorService;
    private final ConcurrentHashMap<String, PDDocument> documentCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> renderedPageCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> pageRenderLocks = new ConcurrentHashMap<>();

    // Fixed configuration parameters
    private static final float RENDERING_DPI = 150f;
    private static final float THUMBNAIL_DPI = 72f;
    private static final String RENDERING_FORMAT = "png";
    private static final ImageType RENDERING_IMAGE_TYPE = ImageType.RGB;
    private static final int THUMBNAIL_WIDTH = 200;
    private static final int THUMBNAIL_HEIGHT = 280;

    // Concurrent processing parameters
    private static final int BATCH_SIZE = 4;
    private static final int MAX_RENDERING_THREADS = Runtime.getRuntime().availableProcessors();
    private static final long RENDERING_TIMEOUT_MS = 30000; // 30 seconds

    public PdfRenderingService(
            @Qualifier("renderingExecutor") ExecutorService executorService) {
        this.executorService = executorService;

        log.info("Initialized PdfRenderingService with fixed DPI settings: rendering={}dpi, thumbnail={}dpi",
                RENDERING_DPI, THUMBNAIL_DPI);
    }

    /**
     * Render a specific page of a PDF document with consistent DPI.
     *
     * @param document   The PDF document
     * @param pageNumber The page number (1-based)
     * @return The rendered page file
     * @throws IOException If rendering fails
     */
    public File renderPage(PdfDocument document, int pageNumber) throws IOException {
        String renderedPagePath = document.getRenderedPagePath(pageNumber);
        File renderedPage = new File(renderedPagePath);
        String cacheKey = generateCacheKey(document, pageNumber);

        // Check cache and existing file
        if (renderedPageCache.containsKey(cacheKey) && renderedPage.exists()) {
            return renderedPage;
        }

        // Get or create lock for this specific page
        ReentrantLock pageLock = pageRenderLocks.computeIfAbsent(cacheKey, k -> new ReentrantLock());

        // Synchronize rendering and saving for this page
        pageLock.lock();
        try {
            // Double-check cache inside synchronized block
            if (renderedPageCache.containsKey(cacheKey) && renderedPage.exists()) {
                return renderedPage;
            }

            // Ensure directory exists
            FileUtils.createDirectories(renderedPage.getParentFile());

            // Create a temporary file for rendering
            Path tempFile = null;
            try {
                tempFile = Files.createTempFile(renderedPage.getParentFile().toPath(), "render_", "." + RENDERING_FORMAT);

                try (PDDocument pdDocument = loadDocument(document)) {
                    // Validate page number
                    validatePageNumber(pdDocument, pageNumber);

                    PDFRenderer renderer = new PDFRenderer(pdDocument);

                    // Use consistent DPI setting
                    BufferedImage image = renderImageSafely(pdDocument, renderer, pageNumber - 1, RENDERING_DPI);

                    // Write image to temporary file
                    ImageIO.write(image, RENDERING_FORMAT, tempFile.toFile());

                    // Move temporary file to final location
                    Files.move(tempFile, renderedPage.toPath(), StandardCopyOption.REPLACE_EXISTING);

                    // Mark as cached
                    renderedPageCache.put(cacheKey, true);

                    return renderedPage;
                }
            } catch (Exception e) {
                log.error("Failed to render page {} of document {}: {}",
                        pageNumber, document.getFileId(), e.getMessage(), e);

                // Clean up temporary file if it exists
                if (tempFile != null) {
                    try {
                        Files.deleteIfExists(tempFile);
                    } catch (IOException cleanupEx) {
                        log.error("Failed to delete temporary render file {}: {}", tempFile, cleanupEx.getMessage());
                    }
                }
                throw new IOException("Rendering failed", e);
            }
        } finally {
            pageLock.unlock();
        }
    }

    /**
     * Parallel pre-rendering of all document pages.
     *
     * @param document The PDF document to pre-render
     * @throws IOException If pre-rendering fails
     */
    public void preRenderAllPages(PdfDocument document) throws IOException {
        int pageCount = document.getPageCount();

        try (PDDocument pdDocument = loadDocument(document)) {
            PDFRenderer renderer = new PDFRenderer(pdDocument);

            List<CompletableFuture<Void>> renderTasks = new ArrayList<>();

            for (int pageNumber = 1; pageNumber <= pageCount; pageNumber++) {
                final int currentPage = pageNumber;

                CompletableFuture<Void> renderTask = CompletableFuture.runAsync(() -> {
                    try {
                        renderPage(document, currentPage);
                    } catch (Exception e) {
                        log.error("Failed to pre-render page {} of document {}: {}",
                                currentPage, document.getFileId(), e.getMessage());
                    }
                }, executorService);

                renderTasks.add(renderTask);
            }

            // Wait for all rendering tasks
            CompletableFuture.allOf(renderTasks.toArray(new CompletableFuture[0]))
                    .orTimeout(RENDERING_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .join();
        }
    }

    /**
     * Generate a thumbnail for a specific page.
     *
     * @param document   The PDF document
     * @param pageNumber The page number (1-based)
     * @return FileSystemResource containing the thumbnail
     * @throws IOException If thumbnail generation fails
     */
    public FileSystemResource getThumbnail(PdfDocument document, int pageNumber) throws IOException {
        String thumbnailPath = document.getThumbnailPath(pageNumber);
        File thumbnailFile = new File(thumbnailPath);
        String cacheKey = generateCacheKey(document, pageNumber) + "_thumbnail";

        // Check cached thumbnail
        if (renderedPageCache.containsKey(cacheKey) && thumbnailFile.exists()) {
            return new FileSystemResource(thumbnailFile);
        }

        // Ensure directory exists
        FileUtils.createDirectories(thumbnailFile.getParentFile());

        try (PDDocument pdDocument = loadDocument(document)) {
            validatePageNumber(pdDocument, pageNumber);

            PDFRenderer renderer = new PDFRenderer(pdDocument);

            // Use consistent thumbnail DPI
            BufferedImage originalImage = renderImageSafely(pdDocument, renderer, pageNumber - 1, THUMBNAIL_DPI);
            BufferedImage thumbnailImage = resizeThumbnail(originalImage);

            // Write thumbnail
            ImageIO.write(thumbnailImage, RENDERING_FORMAT, thumbnailFile);

            // Mark as cached
            renderedPageCache.put(cacheKey, true);

            return new FileSystemResource(thumbnailFile);
        } catch (Exception e) {
            log.error("Thumbnail generation failed for page {} of document {}: {}",
                    pageNumber, document.getFileId(), e.getMessage(), e);
            throw new IOException("Thumbnail generation failed", e);
        }
    }

    /**
     * Retrieve rendered page as FileSystemResource.
     *
     * @param document   The PDF document
     * @param pageNumber The page number (1-based)
     * @return FileSystemResource of the rendered page
     * @throws IOException If rendering fails
     */
    public FileSystemResource getRenderedPage(PdfDocument document, int pageNumber) throws IOException {
        File renderedPage = renderPage(document, pageNumber);
        return new FileSystemResource(renderedPage);
    }

    // Private helper methods

    private PDDocument loadDocument(PdfDocument document) throws IOException {
        try {
            return PDDocument.load(new File(document.getFilePath()));
        } catch (IOException e) {
            log.error("Failed to load document {}: {}", document.getFileId(), e.getMessage());
            throw e;
        }
    }

    private void validatePageNumber(PDDocument document, int pageNumber) throws IOException {
        if (pageNumber < 1 || pageNumber > document.getNumberOfPages()) {
            throw new IOException("Invalid page number: " + pageNumber +
                    ". Document has " + document.getNumberOfPages() + " pages.");
        }
    }

    private BufferedImage renderImageSafely(PDDocument document, PDFRenderer renderer, int pageIndex, float dpi) throws IOException {
        try {
            // Always use the specified DPI and image type for consistency
            return renderer.renderImageWithDPI(pageIndex, dpi, RENDERING_IMAGE_TYPE);
        } catch (Exception e) {
            log.warn("Standard rendering failed using ImageType {}, attempting fallback: {}",
                    RENDERING_IMAGE_TYPE, e.getMessage());
            return createFallbackImage(document, pageIndex, dpi);
        }
    }

    private BufferedImage createFallbackImage(PDDocument document, int pageIndex, float dpi) throws IOException {
        PDPage page = document.getPage(pageIndex);
        float width = page.getMediaBox().getWidth();
        float height = page.getMediaBox().getHeight();

        int pixelWidth = Math.round(width * dpi / 72);
        int pixelHeight = Math.round(height * dpi / 72);

        BufferedImage image = new BufferedImage(pixelWidth, pixelHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setBackground(Color.WHITE);
        g.clearRect(0, 0, pixelWidth, pixelHeight);
        g.dispose();

        return image;
    }

    private BufferedImage resizeThumbnail(BufferedImage originalImage) {
        BufferedImage thumbnailImage = new BufferedImage(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = thumbnailImage.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(originalImage, 0, 0, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT, null);
        g.dispose();

        return thumbnailImage;
    }

    private String generateCacheKey(PdfDocument document, int pageNumber) {
        return document.getFileId() + "_page_" + pageNumber;
    }

    /**
     * Clear rendering caches.
     */
    public void clearCache() {
        renderedPageCache.clear();
        documentCache.clear();
    }
}