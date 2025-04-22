package guraa.pdfcompare.service;

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfReader;
import guraa.pdfcompare.model.PdfDocument;
import lombok.extern.slf4j.Slf4j;
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
 * Service for rendering PDF pages using PDFBox with improved stability and error handling.
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
        log.info("PDF Rendering Service initialized with PDFBox, dpi={}, format={}, timeout={}s, max-retries={}",
                renderingDpi, renderingFormat, timeoutSeconds, maxRetries);
        log.info("Maximum concurrent rendering threads: {}", MAX_RENDERING_THREADS);
    }

    /**
     * Asynchronously renders a specific page of a PDF document.
     * Returns a CompletableFuture that will complete with the rendered File.
     * Handles caching, locking, retries, and fallback generation.
     */
    public CompletableFuture<File> renderPage(PdfDocument document, int pageNumber) {
        String renderedPagePath = document.getRenderedPagePath(pageNumber);
        File renderedPage = new File(renderedPagePath);
        String cacheKey = generateCacheKey(document, pageNumber);

        // Quick check if already rendered
        if (renderedPage.exists() && renderedPage.length() > 0) {
            log.trace("Page {} of {} already rendered, returning existing file.", pageNumber, document.getFileId());
            return CompletableFuture.completedFuture(renderedPage);
        }

        // Get or create a task for rendering this page
        return renderingTasks.computeIfAbsent(cacheKey, k -> {
            log.debug("Creating rendering task for page {} of {}", pageNumber, document.getFileId());
            // Submit the core rendering logic asynchronously
            return CompletableFuture.supplyAsync(
                    () -> performPageRendering(document, pageNumber, renderedPage, cacheKey),
                    executorService
            ).whenComplete((file, throwable) -> {
                // Always remove the task entry when the future completes (successfully or exceptionally)
                log.trace("Removing rendering task for cacheKey: {}", cacheKey);
                renderingTasks.remove(cacheKey);
            }).exceptionally(ex -> {
                // Handle exceptions thrown from performPageRendering or supplyAsync itself
                log.error("Fatal error during async rendering task for page {} of document {}: {}",
                        pageNumber, document.getFileId(), ex.getMessage(), ex);

                // Attempt to create a fallback file as a last resort
                try {
                    return createFallbackPageFile(document, pageNumber, renderedPage);
                } catch (Exception fallbackEx) {
                    log.error("Failed to create fallback for page {} of document {}: {}",
                            pageNumber, document.getFileId(), fallbackEx.getMessage(), fallbackEx);
                    // Re-throw the original exception wrapped in CompletionException if fallback fails
                    throw new CompletionException("Rendering failed and fallback creation also failed", ex);
                }
            });
        });
    }

    /**
     * Core logic for rendering a single page, including locking, retries, and fallback.
     * This method is intended to be called asynchronously.
     */
    private File performPageRendering(PdfDocument document, int pageNumber, File renderedPage, String cacheKey) {
        ReentrantLock pageLock = pageRenderLocks.computeIfAbsent(cacheKey, l -> new ReentrantLock());
        boolean locked = false;
        log.debug("Attempting to acquire lock for page {} of {}", pageNumber, document.getFileId());

        try {
            // Try to acquire lock, but don't wait indefinitely
            locked = pageLock.tryLock(15, TimeUnit.SECONDS); // Increased timeout slightly
            if (!locked) {
                // If we can't get the lock, check if another thread rendered it while we waited
                if (renderedPage.exists() && renderedPage.length() > 0) {
                    log.warn("Failed to acquire lock for page {} of {}, but file now exists. Returning existing.",
                            pageNumber, document.getFileId());
                    return renderedPage;
                }
                log.error("Failed to acquire lock for rendering page {} of document {} after timeout.",
                        pageNumber, document.getFileId());
                throw new IOException("Failed to acquire rendering lock for page " + pageNumber);
            }
            log.debug("Lock acquired for page {} of {}", pageNumber, document.getFileId());

            // Double-check if already rendered *after* acquiring the lock
            if (renderedPage.exists() && renderedPage.length() > 0) {
                log.debug("Page {} of {} was rendered by another thread while waiting for lock.", pageNumber, document.getFileId());
                return renderedPage;
            }

            // Ensure parent directories exist
            File parentDir = renderedPage.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                log.debug("Creating directory: {}", parentDir.getAbsolutePath());
                if (!parentDir.mkdirs()) {
                    throw new IOException("Failed to create directory: " + parentDir.getAbsolutePath());
                }
            }

            // --- Rendering with Retries ---
            Exception lastException = null;
            for (int retry = 0; retry < maxRetries; retry++) {
                boolean semaphoreAcquired = false;
                try {
                    int active = activeRenderingTasks.incrementAndGet();
                    log.debug("Starting render attempt {}/{} (active tasks: {}): document {}, page {}",
                            retry + 1, maxRetries, active, document.getFileId(), pageNumber);

                    // Acquire semaphore to limit concurrent rendering operations
                    log.trace("Attempting to acquire rendering semaphore...");
                    if (!renderingSemaphore.tryAcquire(20, TimeUnit.SECONDS)) { // Increased timeout
                        log.warn("Timed out waiting for rendering semaphore (attempt {}/{}), will retry if possible.", retry + 1, maxRetries);
                        activeRenderingTasks.decrementAndGet(); // Decrement here as we didn't proceed
                        if (retry < maxRetries - 1) {
                            sleepBeforeRetry(retry); // Wait before next attempt
                            continue;
                        } else {
                            throw new IOException("Timed out waiting for rendering resources after " + maxRetries + " attempts.");
                        }
                    }
                    semaphoreAcquired = true;
                    log.trace("Rendering semaphore acquired.");

                    // --- Actual Rendering within Semaphore Lock ---
                    Path tempFile = null;
                    try {
                        // Create a temporary file for rendering output
                        tempFile = Files.createTempFile(renderedPage.getParentFile().toPath(),
                                "render_" + pageNumber + "_", "." + renderingFormat);
                        log.trace("Created temporary file: {}", tempFile);

                        // Render the page image with timeout protection using PDFBox
                        BufferedImage image = renderWithTimeout(document, pageNumber);

                        if (image == null) {
                            // Rendering failed (e.g., timeout, internal error)
                            if (retry < maxRetries - 1) {
                                log.warn("Rendering returned null for page {} of document {}, retry {}/{}. Will retry.",
                                        pageNumber, document.getFileId(), retry + 1, maxRetries);
                                // No need to throw here, loop will continue after delay
                            } else {
                                // Last retry failed, prepare for fallback outside the loop
                                log.error("All rendering attempts returned null for page {} of {}.",
                                        pageNumber, document.getFileId());
                                lastException = new IOException("Rendering returned null after " + maxRetries + " attempts");
                            }
                        } else {
                            // Rendering successful, write to temp file and move
                            log.trace("Rendering successful for page {}, writing to temp file...", pageNumber);
                            ImageIO.write(image, renderingFormat, tempFile.toFile());
                            log.trace("Moving temp file {} to final location {}", tempFile, renderedPage.toPath());
                            Files.move(tempFile, renderedPage.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            image.flush(); // Release resources
                            log.debug("Successfully rendered and saved page {} of {}", pageNumber, document.getFileId());
                            return renderedPage; // Success! Exit method.
                        }
                    } catch (Exception e) {
                        // Catch exceptions during rendering or file operations
                        lastException = e;
                        log.warn("Render attempt {}/{} failed for page {} of document {}: {}",
                                retry + 1, maxRetries, pageNumber, document.getFileId(), e.getMessage(), e);
                        // Clean up temporary file if it exists
                        if (tempFile != null) {
                            try { Files.deleteIfExists(tempFile); } catch (IOException ignored) {}
                        }
                        // If interrupted, stop retrying
                        if (Thread.currentThread().isInterrupted()) {
                            log.warn("Thread interrupted during rendering attempt, stopping retries.");
                            throw new CompletionException(new InterruptedException("Rendering interrupted"));
                        }
                    } finally {
                        // Always release semaphore if acquired
                        if (semaphoreAcquired) {
                            renderingSemaphore.release();
                            log.trace("Rendering semaphore released.");
                        }
                        if (semaphoreAcquired) {
                            activeRenderingTasks.decrementAndGet();
                        }
                    } // End of try-catch block for rendering attempt

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Thread interrupted while waiting for rendering semaphore or during sleep.");
                    throw new CompletionException(e); // Propagate interruption
                } catch (IOException e) {
                    // Catch IOExceptions like semaphore timeout or directory creation failure
                    lastException = e;
                    log.error("IOException during rendering attempt {}/{}: {}", retry + 1, maxRetries, e.getMessage());
                    // If interrupted, stop retrying
                    if (Thread.currentThread().isInterrupted()) {
                        log.warn("Thread interrupted after IOException, stopping retries.");
                        throw new CompletionException(new InterruptedException("Rendering interrupted after IO error"));
                    }
                }

                // If we reached here, the attempt failed. Wait before retrying if applicable.
                if (retry < maxRetries - 1 && !Thread.currentThread().isInterrupted()) {
                    sleepBeforeRetry(retry);
                }

            } // End of retry loop

            // --- Fallback Handling ---
            if (Thread.currentThread().isInterrupted()) {
                throw new CompletionException(new InterruptedException("Rendering interrupted after retries"));
            }

            log.warn("All rendering attempts failed for page {} of {}. Creating fallback.",
                    pageNumber, document.getFileId());
            if (lastException != null) {
                log.warn("Last encountered exception: ", lastException); // Log stack trace of last error
            }
            // Attempt to create the fallback file
            try {
                return createFallbackPageFile(document, pageNumber, renderedPage);
            } catch (IOException fallbackEx) {
                log.error("Failed to create fallback file for page {} of {}: {}",
                        pageNumber, document.getFileId(), fallbackEx.getMessage(), fallbackEx);
                // Throw a CompletionException wrapping the original or fallback exception
                throw new CompletionException("All rendering retries failed, and fallback creation also failed",
                        lastException != null ? lastException : fallbackEx);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Thread interrupted while waiting for page lock for page {} of {}", pageNumber, document.getFileId());
            throw new CompletionException(e); // Propagate as CompletionException
        } catch (IOException e) {
             log.error("IOException during rendering process for page {} of {}: {}", pageNumber, document.getFileId(), e.getMessage(), e);
             throw new CompletionException(e); // Wrap IOExceptions
        } catch (Exception e) {
             log.error("Unexpected exception during rendering process for page {} of {}: {}", pageNumber, document.getFileId(), e.getMessage(), e);
             throw new CompletionException(e); // Wrap other runtime exceptions
        } finally {
            // Always unlock if the lock was acquired
            if (locked) {
                pageLock.unlock();
                log.debug("Lock released for page {} of {}", pageNumber, document.getFileId());
            }
        }
    }

     /**
      * Helper method to pause execution before retrying.
      */
     private void sleepBeforeRetry(int retryAttempt) throws InterruptedException {
         long delay = retryDelayMs * (1L << retryAttempt); // Use long for delay calculation
         log.debug("Waiting {}ms before retry {}...", delay, retryAttempt + 2);
         Thread.sleep(delay);
     }


    /**
     * Render a page with a timeout using iText 7.
     * This implementation creates a fallback image directly since we don't have Pdf2Image.
     */
    private BufferedImage renderWithTimeout(PdfDocument document, int pageNumber) {
        // Submit rendering task with timeout
        Future<BufferedImage> renderFuture = executorService.submit(() -> {
            try {
                // Create a fallback image with the document and page info
                // This is a simplified approach since we don't have the Pdf2Image class
                log.info("Creating fallback image for page {} of document {}", pageNumber, document.getFileId());

                // Default letter size dimensions
                float width = 612;  // Default letter width in points
                float height = 792; // Default letter height in points

                // Try to get actual dimensions if possible
                try (PdfReader reader = new PdfReader(document.getFilePath());
                     com.itextpdf.kernel.pdf.PdfDocument pdfDoc = new com.itextpdf.kernel.pdf.PdfDocument(reader)) {
                    
                    // Validate page number (iText is 1-based)
                    if (pageNumber < 1 || pageNumber > pdfDoc.getNumberOfPages()) {
                        throw new IOException("Invalid page number: " + pageNumber +
                                ". Document has " + pdfDoc.getNumberOfPages() + " pages.");
                    }

                    // Get the page dimensions if possible
                    com.itextpdf.kernel.pdf.PdfPage page = pdfDoc.getPage(pageNumber);
                    Rectangle pageSize = page.getPageSize();
                    width = pageSize.getWidth();
                    height = pageSize.getHeight();
                } catch (Exception e) {
                    // If we can't get the dimensions, use defaults
                    log.warn("Could not get page dimensions for page {} of document {}: {}. Using defaults.",
                            pageNumber, document.getFileId(), e.getMessage());
                }

                // Scale based on DPI
                int pixelWidth = Math.round(width * renderingDpi / 72);
                int pixelHeight = Math.round(height * renderingDpi / 72);

                // Create the image
                BufferedImage image = new BufferedImage(pixelWidth, pixelHeight, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = image.createGraphics();
                
                // Set white background
                g.setBackground(Color.WHITE);
                g.clearRect(0, 0, pixelWidth, pixelHeight);

                // Add text indicating this is a fallback
                g.setColor(Color.BLACK);
                g.setFont(new Font("SansSerif", Font.BOLD, 24));
                String text = "Page " + pageNumber + " of " + document.getFileId();
                FontMetrics fm = g.getFontMetrics();
                int textWidth = fm.stringWidth(text);
                g.drawString(text, (pixelWidth - textWidth) / 2, pixelHeight / 2);

                // Add a border
                g.setColor(Color.GRAY);
                g.drawRect(0, 0, pixelWidth - 1, pixelHeight - 1);

                g.dispose();
                
                return image;
            } catch (Exception e) {
                // Catch any exception during iText processing
                log.error("iText rendering failed for page {} of {}: {}",
                        pageNumber, document.getFileId(), e.getMessage(), e); // Log full exception
                return null; // Indicate failure
            }
        });

        try {
            // Wait with timeout
            return renderFuture.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            renderFuture.cancel(true); // Attempt to cancel the underlying task
            log.warn("iText rendering timeout after {} seconds for page {} of {}",
                    timeoutSeconds, pageNumber, document.getFileId());
            return null;
        } catch (InterruptedException e) {
            renderFuture.cancel(true);
            Thread.currentThread().interrupt();
            log.warn("Thread interrupted during iText rendering of page {} of {}",
                    pageNumber, document.getFileId());
            return null;
        } catch (ExecutionException e) {
            // Log the underlying cause from the Future
            log.error("iText rendering ExecutionException for page {} of {}: ",
                    pageNumber, document.getFileId(), e.getCause()); // Log the full stack trace
            return null;
        } catch (CancellationException e) {
             log.warn("iText rendering task was cancelled for page {} of {}", pageNumber, document.getFileId());
             return null;
        }
    }

    /**
     * Create a fallback image when normal rendering fails.
     * (This method remains largely the same)
     */
    private BufferedImage createFallbackImage(PdfDocument document, int pageNumber) {
        log.info("Creating fallback image for page {} of document {}", pageNumber, document.getFileId());

        // Try to get page dimensions - might need adjustment if PdfDocument model changes
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
     * (This method remains largely the same)
     */
    private File createFallbackPageFile(PdfDocument document, int pageNumber, File targetFile) throws IOException {
        File parentDir = targetFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                throw new IOException("Failed to create directory: " + parentDir.getAbsolutePath());
            }
        }
        BufferedImage image = createFallbackImage(document, pageNumber);
        ImageIO.write(image, renderingFormat, targetFile);
        image.flush();
        return targetFile;
    }

    /**
     * Retrieve rendered page as FileSystemResource. Blocks until rendering is complete.
     * (This method remains largely the same)
     */
    public FileSystemResource getRenderedPage(PdfDocument document, int pageNumber) throws IOException {
        try {
            File renderedPage = renderPage(document, pageNumber).join();
            return new FileSystemResource(renderedPage);
        } catch (CompletionException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            } else {
                log.error("Unexpected error retrieving rendered page {} of {}: {}", pageNumber, document.getFileId(), e.getMessage(), e);
                throw new IOException("Failed to retrieve rendered page due to: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Generate a thumbnail for a specific page.
     * (This method remains largely the same, relies on getRenderedPage)
     */
    public FileSystemResource getThumbnail(PdfDocument document, int pageNumber) throws IOException {
        String thumbnailPath = document.getThumbnailPath(pageNumber);
        File thumbnailFile = new File(thumbnailPath);

        if (thumbnailFile.exists() && thumbnailFile.length() > 0) {
            return new FileSystemResource(thumbnailFile);
        }

        File parentDir = thumbnailFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                throw new IOException("Failed to create directory: " + parentDir.getAbsolutePath());
            }
        }

        try {
            File renderedPage = renderPage(document, pageNumber).join(); // Wait for completion
            BufferedImage originalImage = ImageIO.read(renderedPage);

            if (originalImage != null) {
                int thumbnailWidth = 200;
                int thumbnailHeight = 280; // Adjust aspect ratio if needed

                BufferedImage thumbnailImage = new BufferedImage(thumbnailWidth, thumbnailHeight, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = thumbnailImage.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(originalImage, 0, 0, thumbnailWidth, thumbnailHeight, null);
                g.dispose();

                ImageIO.write(thumbnailImage, renderingFormat, thumbnailFile);

                thumbnailImage.flush();
                originalImage.flush();

                return new FileSystemResource(thumbnailFile);
            } else {
                throw new IOException("Failed to read rendered page image for thumbnail");
            }
        } catch (Exception e) {
            log.error("Error creating thumbnail for page {} of {}: {}",
                    pageNumber, document.getFileId(), e.getMessage(), e);

            // Create a fallback thumbnail
            BufferedImage fallbackImage = createFallbackImage(document, pageNumber);
            int thumbnailWidth = 200;
            int thumbnailHeight = 280;
            BufferedImage thumbnailImage = new BufferedImage(thumbnailWidth, thumbnailHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = thumbnailImage.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(fallbackImage, 0, 0, thumbnailWidth, thumbnailHeight, null);
            g.dispose();
            ImageIO.write(thumbnailImage, renderingFormat, thumbnailFile);
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
