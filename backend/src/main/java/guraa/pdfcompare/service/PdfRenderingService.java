package guraa.pdfcompare.service;

import guraa.pdfcompare.model.PdfDocument;
import guraa.pdfcompare.util.FileUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Advanced PDF Rendering Service with enhanced error handling, retry logic,
 * aggressive caching, and parallel processing capabilities.
 */
@Slf4j
@Service
public class PdfRenderingService {

    private final ExecutorService executorService;
    private final ConcurrentHashMap<String, PDDocument> documentCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> renderedPageCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> pageRenderLocks = new ConcurrentHashMap<>();

    // Keep track of documents being loaded to prevent concurrent loading of the same document
    private final ConcurrentHashMap<String, CompletableFuture<PDDocument>> documentLoadingTasks = new ConcurrentHashMap<>();

    // Track rendering tasks in progress to prevent duplicates
    private final ConcurrentHashMap<String, CompletableFuture<File>> renderingTasks = new ConcurrentHashMap<>();

    // Configuration parameters
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

    @Value("${app.rendering.fast-mode:true}")
    private boolean fastMode;

    @Value("${app.rendering.fast-mode-dpi:150}")
    private float fastModeDpi;

    @Value("${app.rendering.compression-quality:0.9}")
    private float compressionQuality = 0.9f;

    @Value("${app.rendering.max-retries:3}")
    private int maxRetries = 3;

    @Value("${app.rendering.retry-delay-ms:100}")
    private int retryDelayMs = 100;

    @Value("${app.rendering.timeout-seconds:15}")
    private int timeoutSeconds = 15;

    // Concurrent processing parameters
    private static final int MAX_CACHED_DOCUMENTS = 5;
    private static final int MAX_RENDERING_THREADS = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);

    // Counter for active rendering tasks
    private final AtomicInteger activeRenderingTasks = new AtomicInteger(0);
    private final Semaphore renderingSemaphore;

    public PdfRenderingService(
            @Qualifier("renderingExecutor") ExecutorService executorService) {
        this.executorService = executorService;
        this.renderingSemaphore = new Semaphore(MAX_RENDERING_THREADS);

        // Log configuration
        log.info("PDF Rendering Service initialized with dpi={}, fast-mode={}, fast-mode-dpi={}, format={}, compression={}",
                renderingDpi, fastMode, fastModeDpi, renderingFormat, compressionQuality);
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
        if (renderedPageCache.containsKey(cacheKey) && renderedPage.exists() && renderedPage.length() > 0) {
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
                    if (renderedPageCache.containsKey(cacheKey) && renderedPage.exists() && renderedPage.length() > 0) {
                        return renderedPage;
                    }

                    // Ensure parent directories exist
                    FileUtils.createDirectories(renderedPage.getParentFile());

                    // Try rendering with retries
                    Exception lastException = null;
                    for (int retry = 0; retry < maxRetries; retry++) {
                        try {
                            // Track active rendering tasks
                            int active = activeRenderingTasks.incrementAndGet();
                            log.debug("Starting page render (active: {}): document {}, page {}",
                                    active, document.getFileId(), pageNumber);

                            // Acquire semaphore to limit concurrent rendering
                            if (!renderingSemaphore.tryAcquire(timeoutSeconds, TimeUnit.SECONDS)) {
                                throw new IOException("Timed out waiting for rendering resources");
                            }

                            try {
                                // Create a temporary file for rendering
                                Path tempFile = Files.createTempFile(renderedPage.getParentFile().toPath(),
                                        "render_", "." + renderingFormat);

                                try {
                                    // Load and render the page
                                    PDDocument pdDocument = loadDocument(document);
                                    try {
                                        // Validate page number
                                        validatePageNumber(pdDocument, pageNumber);

                                        // Render with timeout protection
                                        BufferedImage image = renderWithTimeout(pdDocument, pageNumber - 1);

                                        // Optimize and write image with compression
                                        writeCompressedImage(image, tempFile.toFile());

                                        // Move temporary file to final location
                                        Files.move(tempFile, renderedPage.toPath(), StandardCopyOption.REPLACE_EXISTING);

                                        // Mark as cached
                                        renderedPageCache.put(cacheKey, true);

                                        return renderedPage;
                                    } finally {
                                        // Don't close if from cache to avoid concurrent modification
                                        if (!documentCache.containsValue(pdDocument)) {
                                            pdDocument.close();
                                        }
                                    }
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

                    // All retries failed
                    if (lastException != null) {
                        if (lastException instanceof IOException) {
                            throw (IOException)lastException;
                        } else {
                            throw new IOException("Rendering failed", lastException);
                        }
                    } else {
                        throw new IOException("Rendering failed after " + maxRetries + " attempts");
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
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
    private BufferedImage renderWithTimeout(PDDocument document, int pageIndex) throws Exception {
        // Submit rendering task with timeout
        Future<BufferedImage> renderFuture = executorService.submit(() -> {
            try {
                PDFRenderer renderer = new PDFRenderer(document);
                ImageType imageType = determineImageType();

                // Use fast mode DPI if enabled
                float dpi = fastMode ? fastModeDpi : renderingDpi;

                // Try rendering with PDFBox
                return renderer.renderImageWithDPI(pageIndex, dpi, imageType);
            } catch (Exception e) {
                log.warn("Standard rendering failed, using fallback: {}", e.getMessage());
                return createFallbackImage(document, pageIndex);
            }
        });

        try {
            // Wait with timeout
            return renderFuture.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            renderFuture.cancel(true);
            throw new IOException("Rendering timeout after " + timeoutSeconds + " seconds");
        } catch (InterruptedException e) {
            renderFuture.cancel(true);
            Thread.currentThread().interrupt();
            throw e;
        } catch (ExecutionException e) {
            throw new IOException("Rendering error: " + e.getCause().getMessage(), e.getCause());
        }
    }

    /**
     * Create a fallback page file when normal rendering fails.
     */
    private File createFallbackPageFile(PdfDocument document, int pageNumber, File targetFile) throws IOException {
        // Create parent directories if needed
        FileUtils.createDirectories(targetFile.getParentFile());

        // Create a blank white image
        int width = 612;  // Standard letter width in points
        int height = 792; // Standard letter height in points

        if (fastMode) {
            // Scale down dimensions in fast mode
            width = (int)(width * fastModeDpi / 72);
            height = (int)(height * fastModeDpi / 72);
        } else {
            // Use standard rendering DPI
            width = (int)(width * renderingDpi / 72);
            height = (int)(height * renderingDpi / 72);
        }

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setBackground(Color.WHITE);
        g.clearRect(0, 0, width, height);

        // Add text indicating this is a fallback
        g.setColor(Color.BLACK);
        g.setFont(new Font("SansSerif", Font.BOLD, 24));
        String text = "Page " + pageNumber + " (Rendering Failed)";
        FontMetrics fm = g.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        g.drawString(text, (width - textWidth) / 2, height / 2);

        g.dispose();

        // Write the fallback image
        writeCompressedImage(image, targetFile);

        // Mark as cached to prevent repeated failures
        String cacheKey = generateCacheKey(document, pageNumber);
        renderedPageCache.put(cacheKey, true);

        return targetFile;
    }

    /**
     * Write an image to a file with compression optimization.
     */
    private void writeCompressedImage(BufferedImage image, File outputFile) throws IOException {
        if ("jpg".equalsIgnoreCase(renderingFormat) || "jpeg".equalsIgnoreCase(renderingFormat)) {
            // Use JPEG compression
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
            if (!writers.hasNext()) throw new IOException("No JPEG writer found");

            ImageWriter writer = writers.next();
            try (FileImageOutputStream output = new FileImageOutputStream(outputFile)) {
                writer.setOutput(output);

                ImageWriteParam param = writer.getDefaultWriteParam();
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(compressionQuality);

                writer.write(null, new IIOImage(image, null, null), param);
            } finally {
                writer.dispose();
            }
        } else {
            // Use standard output for other formats
            ImageIO.write(image, renderingFormat, outputFile);
        }
    }

    /**
     * Parallel pre-rendering of all document pages with improved task management.
     */
    public void preRenderAllPages(PdfDocument document) throws IOException {
        int pageCount = document.getPageCount();
        log.info("Pre-rendering all pages of document {} ({} pages)", document.getFileId(), pageCount);

        // Use limited batch size to prevent resource exhaustion
        int batchSize = Math.min(5, pageCount);
        int numBatches = (pageCount + batchSize - 1) / batchSize;

        // Pre-render in batches
        for (int batchIndex = 0; batchIndex < numBatches; batchIndex++) {
            int startPage = batchIndex * batchSize + 1;
            int endPage = Math.min(startPage + batchSize - 1, pageCount);

            List<CompletableFuture<Void>> batchTasks = new ArrayList<>();

            for (int pageNumber = startPage; pageNumber <= endPage; pageNumber++) {
                final int currentPage = pageNumber;

                CompletableFuture<Void> task = CompletableFuture.runAsync(() -> {
                    try {
                        renderPage(document, currentPage);
                    } catch (Exception e) {
                        log.warn("Failed to pre-render page {} of document {}: {}",
                                currentPage, document.getFileId(), e.getMessage());
                    }
                }, executorService);

                batchTasks.add(task);
            }

            // Wait for batch to complete before starting next batch
            try {
                CompletableFuture.allOf(batchTasks.toArray(new CompletableFuture[0]))
                        .get(timeoutSeconds * 2, TimeUnit.SECONDS);

                log.info("Completed pre-rendering batch {}/{} for document {}",
                        batchIndex + 1, numBatches, document.getFileId());
            } catch (Exception e) {
                log.warn("Timeout waiting for pre-rendering batch {}/{} for document {}: {}",
                        batchIndex + 1, numBatches, document.getFileId(), e.getMessage());
                // Continue with next batch anyway
            }
        }

        log.info("Completed pre-rendering request for document {}", document.getFileId());
    }

    /**
     * Generate a thumbnail for a specific page.
     */
    public FileSystemResource getThumbnail(PdfDocument document, int pageNumber) throws IOException {
        String thumbnailPath = document.getThumbnailPath(pageNumber);
        File thumbnailFile = new File(thumbnailPath);
        String cacheKey = generateCacheKey(document, pageNumber) + "_thumbnail";

        // Check cached thumbnail
        if (renderedPageCache.containsKey(cacheKey) && thumbnailFile.exists() && thumbnailFile.length() > 0) {
            return new FileSystemResource(thumbnailFile);
        }

        // Get a lock for this page
        ReentrantLock pageLock = pageRenderLocks.computeIfAbsent(cacheKey, k -> new ReentrantLock());

        // Try to acquire the lock with timeout
        try {
            if (!pageLock.tryLock(5, TimeUnit.SECONDS)) {
                // If we can't get the lock but the file exists, just return it
                if (thumbnailFile.exists() && thumbnailFile.length() > 0) {
                    return new FileSystemResource(thumbnailFile);
                }
                throw new IOException("Failed to acquire lock for thumbnail generation");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Thread interrupted while waiting for thumbnail lock");
        }

        try {
            // Double-check after acquiring lock
            if (renderedPageCache.containsKey(cacheKey) && thumbnailFile.exists() && thumbnailFile.length() > 0) {
                return new FileSystemResource(thumbnailFile);
            }

            // Ensure directory exists
            FileUtils.createDirectories(thumbnailFile.getParentFile());

            // Acquire semaphore to limit concurrent operations
            if (!renderingSemaphore.tryAcquire(timeoutSeconds, TimeUnit.SECONDS)) {
                throw new IOException("Timed out waiting for rendering resources");
            }

            try {
                // Create temporary file
                Path tempFile = Files.createTempFile(thumbnailFile.getParentFile().toPath(),
                        "thumb_", "." + renderingFormat);

                try {
                    // Option 1: Try creating thumbnail from rendered page if it exists
                    File renderedPage = new File(document.getRenderedPagePath(pageNumber));
                    if (renderedPage.exists() && renderedPage.length() > 0) {
                        // Create thumbnail from existing rendered page
                        BufferedImage originalImage = ImageIO.read(renderedPage);
                        if (originalImage != null) {
                            BufferedImage thumbnailImage = resizeThumbnail(originalImage);
                            writeCompressedImage(thumbnailImage, tempFile.toFile());
                            Files.move(tempFile, thumbnailFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            renderedPageCache.put(cacheKey, true);
                            return new FileSystemResource(thumbnailFile);
                        }
                    }

                    // Option 2: Render directly from PDF at thumbnail resolution
                    PDDocument pdDocument = loadDocument(document);
                    try {
                        validatePageNumber(pdDocument, pageNumber);

                        // Render at thumbnail resolution
                        PDFRenderer renderer = new PDFRenderer(pdDocument);
                        BufferedImage thumbnailImage;

                        try {
                            thumbnailImage = renderer.renderImageWithDPI(pageNumber - 1, thumbnailDpi,
                                    determineImageType());
                        } catch (Exception e) {
                            // Fallback to generic thumbnail
                            log.warn("Thumbnail rendering failed, using fallback: {}", e.getMessage());
                            thumbnailImage = createFallbackImage(pdDocument, pageNumber - 1);
                            thumbnailImage = resizeThumbnail(thumbnailImage);
                        }

                        // Ensure correct size
                        if (thumbnailImage.getWidth() != thumbnailWidth || thumbnailImage.getHeight() != thumbnailHeight) {
                            thumbnailImage = resizeThumbnail(thumbnailImage);
                        }

                        // Write thumbnail
                        writeCompressedImage(thumbnailImage, tempFile.toFile());
                        Files.move(tempFile, thumbnailFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        renderedPageCache.put(cacheKey, true);

                        return new FileSystemResource(thumbnailFile);
                    } finally {
                        // Don't close if from cache to avoid concurrent modification
                        if (!documentCache.containsValue(pdDocument)) {
                            pdDocument.close();
                        }
                    }
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
                renderingSemaphore.release();
            }
        } catch (IOException e) {
            log.error("Thumbnail generation failed for page {} of document {}: {}",
                    pageNumber, document.getFileId(), e.getMessage(), e);
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Thread interrupted while waiting for rendering resources");
        } finally {
            pageLock.unlock();
        }
    }

    /**
     * Retrieve rendered page as FileSystemResource.
     */
    public FileSystemResource getRenderedPage(PdfDocument document, int pageNumber) throws IOException {
        File renderedPage = renderPage(document, pageNumber);
        return new FileSystemResource(renderedPage);
    }

    /**
     * Load a PDF document with shared caching and concurrency control.
     */
    private PDDocument loadDocument(PdfDocument document) throws IOException {
        String documentId = document.getFileId();

        // Try to get from cache first
        PDDocument cachedDocument = documentCache.get(documentId);
        if (cachedDocument != null) {
            return cachedDocument;
        }

        // Create or join document loading task
        return documentLoadingTasks.computeIfAbsent(documentId, id -> {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    PDDocument pdDocument = PDDocument.load(new File(document.getFilePath()));

                    // Cache the document if there's room
                    if (documentCache.size() < MAX_CACHED_DOCUMENTS) {
                        documentCache.put(documentId, pdDocument);
                        log.debug("Cached document: {}", documentId);
                    }

                    return pdDocument;
                } catch (IOException e) {
                    log.error("Failed to load document {}: {}", documentId, e.getMessage());
                    throw new CompletionException(e);
                } finally {
                    // Remove task when complete
                    documentLoadingTasks.remove(documentId);
                }
            }, executorService);
        }).join(); // Wait for the task to complete
    }

    /**
     * Determine the ImageType to use for rendering.
     */
    private ImageType determineImageType() {
        try {
            return ImageType.valueOf(renderingImageType.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid renderingImageType '{}' configured. Defaulting to RGB.", renderingImageType);
            return ImageType.RGB;
        }
    }

    /**
     * Validate the page number against the document.
     */
    private void validatePageNumber(PDDocument document, int pageNumber) throws IOException {
        if (pageNumber < 1 || pageNumber > document.getNumberOfPages()) {
            throw new IOException("Invalid page number: " + pageNumber +
                    ". Document has " + document.getNumberOfPages() + " pages.");
        }
    }

    /**
     * Create a fallback image when standard rendering fails.
     */
    private BufferedImage createFallbackImage(PDDocument document, int pageIndex) throws IOException {
        PDPage page = document.getPage(pageIndex);
        float width = page.getMediaBox().getWidth();
        float height = page.getMediaBox().getHeight();

        // Use fast mode DPI if enabled
        float dpi = fastMode ? fastModeDpi : renderingDpi;

        int pixelWidth = Math.round(width * dpi / 72);
        int pixelHeight = Math.round(height * dpi / 72);

        BufferedImage image = new BufferedImage(pixelWidth, pixelHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setBackground(Color.WHITE);
        g.clearRect(0, 0, pixelWidth, pixelHeight);

        // Add page number text
        g.setColor(Color.BLACK);
        g.setFont(new Font("SansSerif", Font.BOLD, 24));
        String text = "Page " + (pageIndex + 1);
        FontMetrics fm = g.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        g.drawString(text, (pixelWidth - textWidth) / 2, pixelHeight / 2);

        g.dispose();

        return image;
    }

    /**
     * Resize an image to thumbnail dimensions.
     */
    private BufferedImage resizeThumbnail(BufferedImage originalImage) {
        BufferedImage thumbnailImage = new BufferedImage(thumbnailWidth, thumbnailHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = thumbnailImage.createGraphics();

        // Use high quality rendering
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.drawImage(originalImage, 0, 0, thumbnailWidth, thumbnailHeight, null);
        g.dispose();

        return thumbnailImage;
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

        // Close all cached documents
        for (PDDocument document : documentCache.values()) {
            try {
                document.close();
            } catch (IOException e) {
                log.warn("Error closing cached PDF document: {}", e.getMessage());
            }
        }

        renderedPageCache.clear();
        documentCache.clear();
        pageRenderLocks.clear();

        // Request garbage collection
        System.gc();

        log.info("PDF rendering caches cleared");
    }

    /**
     * Get the current status of the service.
     */
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("activeTasks", activeRenderingTasks.get());
        status.put("availablePermits", renderingSemaphore.availablePermits());
        status.put("cachedDocuments", documentCache.size());
        status.put("cachedPages", renderedPageCache.size());
        status.put("renderingTasksInProgress", renderingTasks.size());
        status.put("documentLoadingTasksInProgress", documentLoadingTasks.size());
        return status;
    }
}