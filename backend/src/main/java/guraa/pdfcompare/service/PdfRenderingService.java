package guraa.pdfcompare.service;

import guraa.pdfcompare.model.PdfDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CompletionException;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Service for rendering PDF pages as images with enhanced concurrency control.
 * This service provides methods for rendering PDF pages as images and thumbnails.
 */
@Slf4j
@Service
public class PdfRenderingService {

    private final ExecutorService executorService;
    
    /**
     * Constructor with qualifier to specify which executor service to use.
     * 
     * @param executorService The executor service for rendering operations
     */
    public PdfRenderingService(@Qualifier("renderingExecutor") ExecutorService executorService) {
        this.executorService = executorService;
    }

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

    @Value("${app.rendering.max-concurrent-renders:2}")
    private int maxConcurrentRenders;

    @Value("${app.rendering.render-timeout-seconds:30}")
    private int renderTimeoutSeconds;

    @Value("${app.rendering.retry-count:3}")
    private int retryCount;

    @Value("${app.rendering.retry-delay-ms:100}")
    private int retryDelayMs;

    // Track which PDF files are currently open to avoid concurrent access
    private final ConcurrentHashMap<String, ReentrantLock> pdfLocks = new ConcurrentHashMap<>();

    // Limit the number of concurrent renders
    private final Semaphore renderSemaphore = new Semaphore(maxConcurrentRenders);

    /**
     * Get the rendered page image for a document.
     *
     * @param document   The document
     * @param pageNumber The page number (1-based)
     * @return The rendered page as a FileSystemResource
     * @throws IOException If there is an error rendering the page
     */
    public FileSystemResource getRenderedPage(PdfDocument document, int pageNumber) throws IOException {
        // Check if the page is already rendered
        File renderedPage = new File(document.getRenderedPagePath(pageNumber));

        // If the file doesn't exist or is empty, render it
        if (!renderedPage.exists() || renderedPage.length() == 0) {
            // Ensure the directory exists
            Path renderedPageDir = renderedPage.getParentFile().toPath();
            if (!Files.exists(renderedPageDir)) {
                Files.createDirectories(renderedPageDir);
            }

            renderPage(document, pageNumber);
        }

        // Verify the file was rendered successfully
        if (!renderedPage.exists() || renderedPage.length() == 0) {
            throw new IOException("Failed to render page " + pageNumber + " of document " + document.getFileId());
        }

        return new FileSystemResource(renderedPage);
    }

    /**
     * Get the thumbnail image for a document page.
     *
     * @param document   The document
     * @param pageNumber The page number (1-based)
     * @return The thumbnail as a FileSystemResource
     * @throws IOException If there is an error generating the thumbnail
     */
    public FileSystemResource getThumbnail(PdfDocument document, int pageNumber) throws IOException {
        // Check if the thumbnail already exists
        File thumbnailFile = new File(document.getThumbnailPath(pageNumber));

        // If the file doesn't exist or is empty, generate it
        if (!thumbnailFile.exists() || thumbnailFile.length() == 0) {
            // Ensure the directory exists
            Path thumbnailDir = thumbnailFile.getParentFile().toPath();
            if (!Files.exists(thumbnailDir)) {
                Files.createDirectories(thumbnailDir);
            }

            generateThumbnail(document, pageNumber);
        }

        // Verify the thumbnail was generated successfully
        if (!thumbnailFile.exists() || thumbnailFile.length() == 0) {
            throw new IOException("Failed to generate thumbnail for page " + pageNumber + " of document " + document.getFileId());
        }

        return new FileSystemResource(thumbnailFile);
    }

    /**
     * Render a specific page of a PDF document with retry mechanism.
     *
     * @param document   The document
     * @param pageNumber The page number (1-based)
     * @return The rendered page file
     * @throws IOException If there is an error rendering the page
     */
    public File renderPage(PdfDocument document, int pageNumber) throws IOException {
        File renderedPage = new File(document.getRenderedPagePath(pageNumber));

        // Create parent directories if they don't exist
        Path renderedPageDir = renderedPage.getParentFile().toPath();
        if (!Files.exists(renderedPageDir)) {
            Files.createDirectories(renderedPageDir);
        }

        // Get or create a lock for this PDF file
        ReentrantLock pdfLock = pdfLocks.computeIfAbsent(document.getFilePath(), k -> new ReentrantLock());

        // Implement retry logic
        IOException lastException = null;
        for (int attempt = 0; attempt < retryCount; attempt++) {
            try {
                // Acquire semaphore to limit concurrent renders
                if (!renderSemaphore.tryAcquire(renderTimeoutSeconds, TimeUnit.SECONDS)) {
                    throw new IOException("Timed out waiting for rendering resources");
                }

                try {
                    // Use a temporary file first to avoid partial writes
                    Path tempFile = Files.createTempFile("render_", "." + renderingFormat);

                    // Try to acquire the lock for this PDF
                    if (!pdfLock.tryLock(renderTimeoutSeconds, TimeUnit.SECONDS)) {
                        throw new IOException("Timed out waiting for PDF file access");
                    }

                    try {
                        // Open the PDF document
                        try (PDDocument pdDocument = PDDocument.load(new File(document.getFilePath()))) {
                            log.debug("Rendering page {} of document {}", pageNumber, document.getFileId());

                            PDFRenderer renderer = new PDFRenderer(pdDocument);

                            // Check if the page number is valid
                            if (pageNumber < 1 || pageNumber > pdDocument.getNumberOfPages()) {
                                throw new IOException("Invalid page number: " + pageNumber +
                                        ". Document has " + pdDocument.getNumberOfPages() + " pages.");
                            }

                            // Render the page
                            BufferedImage image = renderer.renderImageWithDPI(pageNumber - 1, renderingDpi, getImageType());

                            // Save the image to the temporary file
                            if (!ImageIO.write(image, renderingFormat, tempFile.toFile())) {
                                throw new IOException("Failed to write image in " + renderingFormat + " format");
                            }

                            // Move the temp file to the final location
                            Files.move(tempFile, renderedPage.toPath(), StandardCopyOption.REPLACE_EXISTING);

                            log.debug("Successfully rendered page {} of document {}", pageNumber, document.getFileId());

                            return renderedPage;
                        }
                    } finally {
                        pdfLock.unlock();

                        // Clean up temp file if it still exists
                        try {
                            Files.deleteIfExists(tempFile);
                        } catch (Exception e) {
                            log.warn("Failed to delete temporary file: {}", e.getMessage());
                        }
                    }
                } finally {
                    renderSemaphore.release();
                }
            } catch (IOException e) {
                lastException = e;
                log.warn("Attempt {} failed to render page {} of document {}: {}",
                        attempt + 1, pageNumber, document.getFileId(), e.getMessage());

                if (attempt < retryCount - 1) {
                    try {
                        Thread.sleep(retryDelayMs * (attempt + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Thread interrupted during retry delay", ie);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Thread interrupted while waiting for rendering resources", e);
            }
        }

    // All retries failed
        throw new IOException("Failed to render page after " + retryCount + " attempts", lastException);
    }

    /**
     * Pre-render all pages of a PDF document in parallel.
     *
     * @param document The document to pre-render
     * @throws IOException If there is an error rendering the pages
     */
    public void preRenderAllPages(PdfDocument document) throws IOException {
        log.info("Pre-rendering all pages for document: {}", document.getFileId());
        
        int pageCount = document.getPageCount();
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        AtomicInteger completedPages = new AtomicInteger(0);
        
        // Submit tasks to render each page
        for (int pageNumber = 1; pageNumber <= pageCount; pageNumber++) {
            final int pageNum = pageNumber;
            
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    // Check if the page is already rendered
                    File renderedPage = new File(document.getRenderedPagePath(pageNum));
                    if (!renderedPage.exists() || renderedPage.length() == 0) {
                        renderPage(document, pageNum);
                    }
                    
                    // Log progress periodically
                    int completed = completedPages.incrementAndGet();
                    if (completed % 5 == 0 || completed == pageCount) {
                        log.info("Pre-rendering progress for document {}: {}/{} pages ({} %)",
                                document.getFileId(), completed, pageCount, (completed * 100) / pageCount);
                    }
                } catch (IOException e) {
                    log.error("Error pre-rendering page {} of document {}: {}",
                            pageNum, document.getFileId(), e.getMessage(), e);
                    throw new CompletionException(e);
                }
            }, executorService);
            
            futures.add(future);
        }
        
        // Wait for all tasks to complete
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            log.info("Successfully pre-rendered all {} pages for document: {}", pageCount, document.getFileId());
        } catch (Exception e) {
            log.error("Error during pre-rendering of document {}: {}", document.getFileId(), e.getMessage(), e);
            throw new IOException("Failed to pre-render all pages", e);
        }
    }

    /**
     * Generate a thumbnail for a specific page of a PDF document.
     *
     * @param document   The document
     * @param pageNumber The page number (1-based)
     * @throws IOException If there is an error generating the thumbnail
     */
    private void generateThumbnail(PdfDocument document, int pageNumber) throws IOException {
        File thumbnailFile = new File(document.getThumbnailPath(pageNumber));

        // Get or create a lock for this PDF file
        ReentrantLock pdfLock = pdfLocks.computeIfAbsent(document.getFilePath(), k -> new ReentrantLock());

        // Implement retry logic
        IOException lastException = null;
        for (int attempt = 0; attempt < retryCount; attempt++) {
            try {
                // Acquire semaphore to limit concurrent renders
                if (!renderSemaphore.tryAcquire(renderTimeoutSeconds, TimeUnit.SECONDS)) {
                    throw new IOException("Timed out waiting for rendering resources");
                }

                try {
                    // Use a temporary file first to avoid partial writes
                    Path tempFile = Files.createTempFile("thumbnail_", "." + renderingFormat);

                    // Try to acquire the lock for this PDF
                    if (!pdfLock.tryLock(renderTimeoutSeconds, TimeUnit.SECONDS)) {
                        throw new IOException("Timed out waiting for PDF file access");
                    }

                    try {
                        // Open the PDF document
                        try (PDDocument pdDocument = PDDocument.load(new File(document.getFilePath()))) {
                            log.debug("Generating thumbnail for page {} of document {}", pageNumber, document.getFileId());

                            PDFRenderer renderer = new PDFRenderer(pdDocument);

                            // Check if the page number is valid
                            if (pageNumber < 1 || pageNumber > pdDocument.getNumberOfPages()) {
                                throw new IOException("Invalid page number: " + pageNumber +
                                        ". Document has " + pdDocument.getNumberOfPages() + " pages.");
                            }

                            // Render the page at a lower DPI
                            BufferedImage image = renderer.renderImageWithDPI(pageNumber - 1, thumbnailDpi, getImageType());

                            // Resize to the desired thumbnail dimensions
                            BufferedImage thumbnailImage = resizeImage(image, thumbnailWidth, thumbnailHeight);

                            // Save the thumbnail to the temporary file
                            if (!ImageIO.write(thumbnailImage, renderingFormat, tempFile.toFile())) {
                                throw new IOException("Failed to write thumbnail in " + renderingFormat + " format");
                            }

                            // Move the temp file to the final location
                            Files.move(tempFile, thumbnailFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

                            log.debug("Successfully generated thumbnail for page {} of document {}",
                                    pageNumber, document.getFileId());

                            return;
                        }
                    } finally {
                        pdfLock.unlock();

                        // Clean up temp file if it still exists
                        try {
                            Files.deleteIfExists(tempFile);
                        } catch (Exception e) {
                            log.warn("Failed to delete temporary file: {}", e.getMessage());
                        }
                    }
                } finally {
                    renderSemaphore.release();
                }
            } catch (IOException e) {
                lastException = e;
                log.warn("Attempt {} failed to generate thumbnail for page {} of document {}: {}",
                        attempt + 1, pageNumber, document.getFileId(), e.getMessage());

                if (attempt < retryCount - 1) {
                    try {
                        Thread.sleep(retryDelayMs * (attempt + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Thread interrupted during retry delay", ie);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Thread interrupted while waiting for rendering resources", e);
            }
        }

        // All retries failed
        throw new IOException("Failed to generate thumbnail after " + retryCount + " attempts", lastException);
    }
    
    /**
     * Resize an image to the specified dimensions.
     *
     * @param image  The image to resize
     * @param width  The target width
     * @param height The target height
     * @return The resized image
     * @throws IOException If there is an error resizing the image
     */
    private BufferedImage resizeImage(BufferedImage image, int width, int height) throws IOException {
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
}
