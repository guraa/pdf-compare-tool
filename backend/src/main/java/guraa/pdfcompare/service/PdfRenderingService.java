package guraa.pdfcompare.service;

import guraa.pdfcompare.model.PdfDocument;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Service for rendering PDF pages with improved stability and error handling.
 */
@Slf4j
@Service
public class PdfRenderingService {

    private final ExecutorService executorService;
    private final ConcurrentHashMap<String, SoftReference<BufferedImage>> imageCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> pageRenderLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<File>> renderingTasks = new ConcurrentHashMap<>();

    // Reduce concurrent rendering operations
    private static final int MAX_RENDERING_THREADS = 2;
    private final Semaphore renderingSemaphore;

    // Counter for active rendering tasks
    private final AtomicInteger activeRenderingTasks = new AtomicInteger(0);

    @Value("${app.rendering.dpi:100}")
    private float renderingDpi = 100;

    @Value("${app.rendering.format:png}")
    private String renderingFormat = "png";

    @Value("${app.rendering.timeout-seconds:60}")
    private int timeoutSeconds = 60;

    @Value("${app.rendering.max-retries:3}")
    private int maxRetries = 3;

    @Value("${app.rendering.retry-delay-ms:50}")
    private int retryDelayMs = 50;

    @Value("${app.rendering.compression-quality:0.6}")
    private float compressionQuality = 0.6f;

    public PdfRenderingService(
            @Qualifier("renderingExecutor") ExecutorService executorService) {
        this.executorService = executorService;
        this.renderingSemaphore = new Semaphore(MAX_RENDERING_THREADS);

        // Log configuration
        log.info("PDF Rendering Service initialized with dpi={}, format={}, timeout={}s, max-retries={}",
                renderingDpi, renderingFormat, timeoutSeconds, maxRetries);
        log.info("Maximum concurrent rendering threads: {}", MAX_RENDERING_THREADS);
    }

    /**
     * Render a specific page of a PDF document with improved error handling and retry logic.
     */
    public File renderPage(PdfDocument document, int pageNumber) throws IOException {
        String renderedPagePath = document.getRenderedPagePath(pageNumber);
        File renderedPage = new File(renderedPagePath);
        String cacheKey = generateCacheKey(document, pageNumber);

        // Quick check if already rendered
        if (renderedPage.exists() && renderedPage.length() > 0) {
            return renderedPage;
        }

        // Get or create a task for rendering this page
        return renderingTasks.computeIfAbsent(cacheKey, k -> {
            // Submit rendering task
            return CompletableFuture.supplyAsync(() -> {
                ReentrantLock pageLock = pageRenderLocks.computeIfAbsent(cacheKey, l -> new ReentrantLock());

                // Try to acquire lock, but don't wait too long
                try {
                    if (!pageLock.tryLock(5, TimeUnit.SECONDS)) {
                        // If we can't get the lock but the file exists, just return it
                        if (renderedPage.exists() && renderedPage.length() > 0) {
                            return renderedPage;
                        }
                        log.warn("Failed to acquire lock for rendering page {} of document {}",
                                pageNumber, document.getFileId());
                        throw new IOException("Failed to acquire rendering lock");
                    }
                } catch (InterruptedException | IOException e) {
                    Thread.currentThread().interrupt();
                    throw new CompletionException(new IOException("Thread interrupted while waiting for rendering lock"));
                }

                try {
                    // Double-check if already rendered after acquiring lock
                    if (renderedPage.exists() && renderedPage.length() > 0) {
                        return renderedPage;
                    }

                    // Ensure parent directories exist
                    File parentDir = renderedPage.getParentFile();
                    if (parentDir != null && !parentDir.exists()) {
                        if (!parentDir.mkdirs()) {
                            throw new IOException("Failed to create directory: " + parentDir.getAbsolutePath());
                        }
                    }

                    // Try rendering with retries
                    Exception lastException = null;
                    for (int retry = 0; retry < maxRetries; retry++) {
                        try {
                            // Track active rendering tasks
                            int active = activeRenderingTasks.incrementAndGet();
                            log.debug("Starting page render (active: {}): document {}, page {}",
                                    active, document.getFileId(), pageNumber);

                            // Acquire semaphore to limit concurrent rendering
                            if (!renderingSemaphore.tryAcquire(10, TimeUnit.SECONDS)) {
                                log.warn("Timed out waiting for rendering resources, will retry");
                                activeRenderingTasks.decrementAndGet();

                                if (retry < maxRetries - 1) {
                                    continue; // Try again after delay
                                } else {
                                    throw new IOException("Timed out waiting for rendering resources");
                                }
                            }

                            try {
                                // Create a temporary file for rendering
                                Path tempFile = Files.createTempFile(renderedPage.getParentFile().toPath(),
                                        "render_", "." + renderingFormat);

                                try {
                                    // Render with timeout protection
                                    BufferedImage image = renderWithTimeout(document, pageNumber);

                                    if (image == null) {
                                        // If rendering failed but we have more retries, continue
                                        if (retry < maxRetries - 1) {
                                            log.warn("Rendering returned null for page {} of document {}, retry {}/{}",
                                                    pageNumber, document.getFileId(), retry + 1, maxRetries);
                                            continue;
                                        }

                                        // On last retry, create a fallback image
                                        log.warn("All rendering attempts returned null, creating fallback for page {} of {}",
                                                pageNumber, document.getFileId());
                                        image = createFallbackImage(document, pageNumber);
                                    }

                                    // Write image to file
                                    ImageIO.write(image, renderingFormat, tempFile.toFile());

                                    // Move temporary file to final location
                                    Files.move(tempFile, renderedPage.toPath(), StandardCopyOption.REPLACE_EXISTING);

                                    // Clean up resources
                                    image.flush();

                                    return renderedPage;
                                } catch (Exception e) {
                                    // Clean up temporary file
                                    try {
                                        Files.deleteIfExists(tempFile);
                                    } catch (Exception ignored) {
                                        // Ignore cleanup errors
                                    }
                                    throw e;
                                }
                            } finally {
                                // Always release semaphore
                                renderingSemaphore.release();
                                // Update active count
                                activeRenderingTasks.decrementAndGet();
                            }
                        } catch (Exception e) {
                            lastException = e;
                            log.warn("Render attempt {} failed for page {} of document {}: {}",
                                    retry + 1, pageNumber, document.getFileId(), e.getMessage());

                            // Stop if thread interrupted
                            if (Thread.currentThread().isInterrupted()) {
                                break;
                            }

                            // Wait before retrying with exponential backoff
                            if (retry < maxRetries - 1) {
                                try {
                                    Thread.sleep(retryDelayMs * (1 << retry));
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                    break;
                                }
                            }
                        }
                    }

                    // All retries failed - create fallback
                    log.warn("All rendering attempts failed, creating fallback for page {} of {}",
                            pageNumber, document.getFileId());
                    return createFallbackPageFile(document, pageNumber, renderedPage);
                } catch (IOException e) {
                    throw new CompletionException(e);
                } finally {
                    pageLock.unlock();
                    // Remove from active tasks
                    renderingTasks.remove(cacheKey);
                }
            }, executorService).exceptionally(ex -> {
                // Handle exceptions
                log.error("Fatal error rendering page {} of document {}: {}",
                        pageNumber, document.getFileId(), ex.getMessage());

                // Create fallback image when actual rendering fails
                try {
                    return createFallbackPageFile(document, pageNumber, renderedPage);
                } catch (Exception e) {
                    log.error("Failed to create fallback for page {} of document {}: {}",
                            pageNumber, document.getFileId(), e.getMessage());
                    throw new CompletionException(ex);
                }
            });
        }).join(); // Wait for the task to complete
    }

    /**
     * Render a page with a timeout to prevent stuck rendering.
     */
    private BufferedImage renderWithTimeout(PdfDocument document, int pageNumber) {
        // Submit rendering task with timeout
        Future<BufferedImage> renderFuture = executorService.submit(() -> {
            try (PDDocument pdDocument = PDDocument.load(new File(document.getFilePath()))) {
                // Validate page number
                if (pageNumber < 1 || pageNumber > pdDocument.getNumberOfPages()) {
                    throw new IOException("Invalid page number: " + pageNumber +
                            ". Document has " + pdDocument.getNumberOfPages() + " pages.");
                }

                PDFRenderer renderer = new PDFRenderer(pdDocument);
                return renderer.renderImageWithDPI(pageNumber - 1, renderingDpi, ImageType.RGB);
            } catch (Exception e) {
                log.warn("Standard rendering failed: {}", e.getMessage());
                return null;
            }
        });

        try {
            // Wait with timeout
            return renderFuture.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            renderFuture.cancel(true);
            log.warn("Rendering timeout after {} seconds for page {} of {}",
                    timeoutSeconds, pageNumber, document.getFileId());
            return null;
        } catch (InterruptedException e) {
            renderFuture.cancel(true);
            Thread.currentThread().interrupt();
            log.warn("Thread interrupted during rendering of page {} of {}",
                    pageNumber, document.getFileId());
            return null;
        } catch (ExecutionException e) {
            log.error("Rendering error for page {} of {}: {}",
                    pageNumber, document.getFileId(), e.getCause().getMessage());
            return null;
        }
    }

    /**
     * Create a fallback image when normal rendering fails.
     */
    private BufferedImage createFallbackImage(PdfDocument document, int pageNumber) {
        log.info("Creating fallback image for page {} of document {}", pageNumber, document.getFileId());

        // Try to get page dimensions from document metadata if available
        float width = 612;  // Default letter width in points
        float height = 792; // Default letter height in points

        // Scale based on DPI
        int pixelWidth = Math.round(width * renderingDpi / 72);
        int pixelHeight = Math.round(height * renderingDpi / 72);

        BufferedImage image = new BufferedImage(pixelWidth, pixelHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setBackground(Color.WHITE);
        g.clearRect(0, 0, pixelWidth, pixelHeight);

        // Add text indicating this is a fallback
        g.setColor(Color.BLACK);
        g.setFont(new Font("SansSerif", Font.BOLD, 24));
        String text = "Page " + pageNumber + " (Rendering Failed)";
        FontMetrics fm = g.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        g.drawString(text, (pixelWidth - textWidth) / 2, pixelHeight / 2);

        g.dispose();

        return image;
    }

    /**
     * Create a fallback page file when normal rendering fails.
     */
    private File createFallbackPageFile(PdfDocument document, int pageNumber, File targetFile) throws IOException {
        // Create parent directories if needed
        File parentDir = targetFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                throw new IOException("Failed to create directory: " + parentDir.getAbsolutePath());
            }
        }

        // Create a fallback image
        BufferedImage image = createFallbackImage(document, pageNumber);

        // Write the image to the file
        ImageIO.write(image, renderingFormat, targetFile);

        // Clean up
        image.flush();

        return targetFile;
    }

    /**
     * Retrieve rendered page as FileSystemResource.
     */
    public FileSystemResource getRenderedPage(PdfDocument document, int pageNumber) throws IOException {
        File renderedPage = renderPage(document, pageNumber);
        return new FileSystemResource(renderedPage);
    }

    /**
     * Generate a thumbnail for a specific page.
     */
    public FileSystemResource getThumbnail(PdfDocument document, int pageNumber) throws IOException {
        String thumbnailPath = document.getThumbnailPath(pageNumber);
        File thumbnailFile = new File(thumbnailPath);

        // Check if thumbnail already exists
        if (thumbnailFile.exists() && thumbnailFile.length() > 0) {
            return new FileSystemResource(thumbnailFile);
        }

        // Create parent directories if needed
        File parentDir = thumbnailFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                throw new IOException("Failed to create directory: " + parentDir.getAbsolutePath());
            }
        }

        // Try to get the rendered page
        try {
            File renderedPage = renderPage(document, pageNumber);

            // Create thumbnail from rendered page
            BufferedImage originalImage = ImageIO.read(renderedPage);

            if (originalImage != null) {
                // Create thumbnail - simple scaling for now
                int thumbnailWidth = 200;
                int thumbnailHeight = 280;

                BufferedImage thumbnailImage = new BufferedImage(thumbnailWidth, thumbnailHeight, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = thumbnailImage.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(originalImage, 0, 0, thumbnailWidth, thumbnailHeight, null);
                g.dispose();

                ImageIO.write(thumbnailImage, renderingFormat, thumbnailFile);

                // Clean up
                thumbnailImage.flush();
                originalImage.flush();

                return new FileSystemResource(thumbnailFile);
            } else {
                throw new IOException("Failed to read rendered page image");
            }
        } catch (Exception e) {
            log.error("Error creating thumbnail for page {} of {}: {}",
                    pageNumber, document.getFileId(), e.getMessage());

            // Create a fallback thumbnail
            BufferedImage fallbackImage = createFallbackImage(document, pageNumber);

            // Simple scaling
            int thumbnailWidth = 200;
            int thumbnailHeight = 280;

            BufferedImage thumbnailImage = new BufferedImage(thumbnailWidth, thumbnailHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = thumbnailImage.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(fallbackImage, 0, 0, thumbnailWidth, thumbnailHeight, null);
            g.dispose();

            ImageIO.write(thumbnailImage, renderingFormat, thumbnailFile);

            // Clean up
            thumbnailImage.flush();
            fallbackImage.flush();

            return new FileSystemResource(thumbnailFile);
        }
    }

    /**
     * Generate a cache key for a document page.
     */
    private String generateCacheKey(PdfDocument document, int pageNumber) {
        return document.getFileId() + "_page_" + pageNumber;
    }

    /**
     * Clear rendering caches.
     */
    public void clearCache() {
        log.info("Clearing PDF rendering caches");
        imageCache.clear();
        System.gc();
        log.info("PDF rendering cache cleared");
    }

    /**
     * Get the current status of the service.
     */
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("activeTasks", activeRenderingTasks.get());
        status.put("availablePermits", renderingSemaphore.availablePermits());
        status.put("cachedImages", imageCache.size());
        status.put("renderingTasksInProgress", renderingTasks.size());
        return status;
    }
}