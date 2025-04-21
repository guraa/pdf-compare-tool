package guraa.pdfcompare.visual;

import guraa.pdfcompare.model.PdfDocument;
import guraa.pdfcompare.service.PagePair;
import guraa.pdfcompare.service.PdfRenderingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ultra-optimized visual matcher for PDF pages with turbo mode for maximum speed.
 */
@Slf4j
@Component
public class EnhancedVisualMatcher implements VisualMatcher {

    private final SSIMCalculator ssimCalculator;
    private final PdfRenderingService pdfRenderingService;
    private final ExecutorService executorService;

    @Autowired
    private ApplicationContext applicationContext;

    // Cache of rendered pages - use soft references for better memory management
    private final Map<String, SoftReference<BufferedImage>> imageCache = new ConcurrentHashMap<>();

    /**
     * Get the ComparisonService bean from the application context.
     */
    private Object getComparisonService() {
        try {
            return applicationContext.getBean("comparisonService");
        } catch (Exception e) {
            log.warn("Failed to get ComparisonService bean: {}", e.getMessage());
            return null;
        }
    }

    // Set a maximum size for the cache to avoid memory issues
    private static final int MAX_CACHE_SIZE = 20;

    /**
     * Constructor with qualifiers to specify which beans to use.
     */
    public EnhancedVisualMatcher(
            SSIMCalculator ssimCalculator,
            PdfRenderingService pdfRenderingService,
            @Qualifier("comparisonExecutor") ExecutorService executorService) {
        this.ssimCalculator = ssimCalculator;
        this.pdfRenderingService = pdfRenderingService;
        this.executorService = executorService;
    }

    @Value("${app.matching.visual-similarity-threshold:0.7}")
    private double visualSimilarityThreshold;

    @Value("${app.matching.max-candidates-per-page:5}")
    private int maxCandidatesPerPage;

    @Value("${app.matching.max-concurrent-comparisons:10}")
    private int maxConcurrentComparisons;

    @Value("${app.matching.retry-count:2}")
    private int retryCount;

    @Value("${app.matching.retry-delay-ms:50}")
    private int retryDelayMs;

    @Value("${app.matching.max-page-gap:3}")
    private int maxPageGap = 3;

    @Value("${app.matching.batch-timeout-seconds:20}")
    private int batchTimeoutSeconds = 20;

    @Value("${app.matching.early-stopping-enabled:true}")
    private boolean earlyStoppingEnabled = true;

    @Value("${app.matching.early-stopping-threshold:0.5}")
    private double earlyStoppingThreshold = 0.5;

    @Value("${app.matching.fast-similarity-metrics:true}")
    private boolean fastSimilarityMetrics = true;

    @Value("${app.comparison.turbo-mode:false}")
    private boolean turboMode = false;

    // Cache of similarity scores
    private final ConcurrentHashMap<String, Double> similarityCache = new ConcurrentHashMap<>();

    @Override
    public List<PagePair> matchPages(PdfDocument baseDocument, PdfDocument compareDocument) throws IOException {
        return matchPages(baseDocument, compareDocument, null);
    }

    /**
     * Match pages between two documents with progress tracking.
     */
    public List<PagePair> matchPages(PdfDocument baseDocument, PdfDocument compareDocument, String comparisonId) throws IOException {
        String logPrefix = "[" + baseDocument.getFileId() + "_" + compareDocument.getFileId() + "] ";
        log.info(logPrefix + "Starting visual matching between documents: {} and {}",
                baseDocument.getFileId(), compareDocument.getFileId());

        long startTime = System.currentTimeMillis();

        try {
            // Only pre-render a minimal number of pages to get started quickly
            CompletableFuture<Void> initialRenderingFuture = preRenderInitialPages(baseDocument, compareDocument);

            // Wait briefly for initial rendering
            try {
                initialRenderingFuture.get(5, TimeUnit.SECONDS);
                log.info(logPrefix + "Initial page rendering completed");
            } catch (Exception e) {
                log.warn(logPrefix + "Continuing with partial initial rendering");
            }

            // Calculate similarity scores with optimized page pair selection
            Map<String, Double> similarityScores = calculateSimilarityScoresOptimized(
                    baseDocument, compareDocument, comparisonId);

            // Match pages using the Hungarian algorithm with available similarity scores
            List<PagePair> pagePairs = matchPagesUsingHungarian(baseDocument, compareDocument, similarityScores);

            long endTime = System.currentTimeMillis();
            long processingTime = endTime - startTime;
            log.info(logPrefix + "Completed visual matching between documents: {} and {} in {}ms",
                    baseDocument.getFileId(), compareDocument.getFileId(), processingTime);

            // Clear the image cache after matching to free memory
            log.info(logPrefix + "Clear the image cache after matching to free memory");
            clearImageCache();

            // If processing took too long, log a warning for future optimizations
            if (processingTime > 60000) { // Over 1 minute
                log.warn(logPrefix + "Visual matching took too long ({}ms). Consider enabling turbo mode.", processingTime);
            }

            return pagePairs;
        } catch (Exception e) {
            log.error(logPrefix + "Error during visual matching: {}", e.getMessage(), e);
            throw new IOException("Visual matching failed", e);
        }
    }

    /**
     * Pre-render initial pages of both documents.
     * In turbo mode, we only pre-render the first few same-numbered pages to speed up the process.
     */
    private CompletableFuture<Void> preRenderInitialPages(PdfDocument baseDocument, PdfDocument compareDocument) {
        // In turbo mode, only pre-render the first 2-3 pages
        int initialPageCount = turboMode ?
                Math.min(3, Math.min(baseDocument.getPageCount(), compareDocument.getPageCount())) :
                Math.min(5, Math.min(baseDocument.getPageCount(), compareDocument.getPageCount()));

        List<CompletableFuture<Void>> renderTasks = new ArrayList<>();

        // Submit tasks to pre-render initial pages of base document
        for (int i = 1; i <= initialPageCount; i++) {
            final int pageNumber = i;
            CompletableFuture<Void> task = CompletableFuture.runAsync(() -> {
                try {
                    pdfRenderingService.renderPage(baseDocument, pageNumber);
                } catch (Exception e) {
                    log.warn("Error pre-rendering base document page {}: {}", pageNumber, e.getMessage());
                }
            }, executorService);
            renderTasks.add(task);
        }

        // Only pre-render the same pages in compare document (most likely to match)
        for (int i = 1; i <= initialPageCount; i++) {
            final int pageNumber = i;
            CompletableFuture<Void> task = CompletableFuture.runAsync(() -> {
                try {
                    pdfRenderingService.renderPage(compareDocument, pageNumber);
                } catch (Exception e) {
                    log.warn("Error pre-rendering compare document page {}: {}", pageNumber, e.getMessage());
                }
            }, executorService);
            renderTasks.add(task);
        }

        // Return a combined future
        return CompletableFuture.allOf(renderTasks.toArray(new CompletableFuture[0]));
    }

    /**
     * Calculate similarity scores with ultra-optimized page pair selection.
     * In turbo mode, we dramatically reduce the number of comparisons.
     */
    private Map<String, Double> calculateSimilarityScoresOptimized(
            PdfDocument baseDocument, PdfDocument compareDocument, String comparisonId) {

        String logPrefix = "[" + baseDocument.getFileId() + "_" + compareDocument.getFileId() + "] ";
        Map<String, Double> similarityScores = new ConcurrentHashMap<>();

        // Track progress for logging
        AtomicInteger completedComparisons = new AtomicInteger(0);

        // Create a list of page pairs with turbo-optimized selection
        List<PagePairTask> pagePairTasks = createOptimizedPagePairTasks(
                baseDocument, compareDocument, similarityCache);

        int totalComparisons = pagePairTasks.size();
        log.info(logPrefix + "Processing {} prioritized page pairs instead of {} potential pairs",
                totalComparisons, baseDocument.getPageCount() * compareDocument.getPageCount());

        // Get exact matches first (same page numbers) - these are highest priority
        int exactMatchCount = 0;
        for (PagePairTask task : pagePairTasks) {
            if (task.basePageNumber == task.comparePageNumber) {
                exactMatchCount++;
            }
        }

        if (exactMatchCount > 0) {
            log.info(logPrefix + "Processing {} exact page number matches first", exactMatchCount);

            List<PagePairTask> exactMatches = new ArrayList<>();
            List<PagePairTask> otherPairs = new ArrayList<>();

            // Split into exact matches and others
            for (PagePairTask task : pagePairTasks) {
                if (task.basePageNumber == task.comparePageNumber) {
                    exactMatches.add(task);
                } else {
                    otherPairs.add(task);
                }
            }

            // Process exact matches with higher timeout
            int exactTimeout = turboMode ? 15 : 30;
            processPagePairBatch(exactMatches, similarityScores, completedComparisons,
                    totalComparisons, comparisonId, "Exact Matches", exactTimeout);

            // If we got enough good matches from exact matches, we might skip other comparisons in turbo mode
            if (earlyStoppingEnabled && turboMode) {
                int goodMatches = countGoodMatches(similarityScores, visualSimilarityThreshold);
                // If we got matches for at least half of both documents, we can stop
                int minDocSize = Math.min(baseDocument.getPageCount(), compareDocument.getPageCount());
                if (goodMatches >= minDocSize * earlyStoppingThreshold) {
                    log.info(logPrefix + "Early stopping: found {} good matches from exact page numbers (threshold: {})",
                            goodMatches, minDocSize * earlyStoppingThreshold);
                    return similarityScores;
                }
            }

            // Process remaining pairs in small batches
            if (!otherPairs.isEmpty()) {
                int batchSize = turboMode ? 8 : 12;
                int numBatches = (otherPairs.size() + batchSize - 1) / batchSize;

                log.info(logPrefix + "Processing remaining {} pairs in {} batches",
                        otherPairs.size(), numBatches);

                for (int i = 0; i < numBatches; i++) {
                    int start = i * batchSize;
                    int end = Math.min(start + batchSize, otherPairs.size());
                    if (start >= otherPairs.size()) break;

                    List<PagePairTask> batch = otherPairs.subList(start, end);
                    int remainingTimeout = turboMode ? 10 : 20;

                    processPagePairBatch(batch, similarityScores, completedComparisons,
                            totalComparisons, comparisonId, "Batch " + (i+1) + "/" + numBatches, remainingTimeout);

                    // Check for early stopping after each batch
                    if (earlyStoppingEnabled) {
                        int goodMatches = countGoodMatches(similarityScores, visualSimilarityThreshold);
                        int minDocSize = Math.min(baseDocument.getPageCount(), compareDocument.getPageCount());
                        if (goodMatches >= minDocSize * earlyStoppingThreshold) {
                            log.info(logPrefix + "Early stopping after batch {}/{}: found {} good matches",
                                    i+1, numBatches, goodMatches);
                            break;
                        }
                    }
                }
            }
        } else {
            // No exact matches available, process all pairs in batches
            int batchSize = turboMode ? 8 : 12;
            int numBatches = (pagePairTasks.size() + batchSize - 1) / batchSize;

            log.info(logPrefix + "No exact page matches, processing {} pairs in {} batches",
                    pagePairTasks.size(), numBatches);

            for (int i = 0; i < numBatches; i++) {
                int start = i * batchSize;
                int end = Math.min(start + batchSize, pagePairTasks.size());
                if (start >= pagePairTasks.size()) break;

                List<PagePairTask> batch = pagePairTasks.subList(start, end);
                int timeout = turboMode ? 15 : 30;

                processPagePairBatch(batch, similarityScores, completedComparisons,
                        totalComparisons, comparisonId, "Batch " + (i+1) + "/" + numBatches, timeout);

                // Check for early stopping
                if (earlyStoppingEnabled) {
                    int goodMatches = countGoodMatches(similarityScores, visualSimilarityThreshold);
                    int minDocSize = Math.min(baseDocument.getPageCount(), compareDocument.getPageCount());
                    if (goodMatches >= minDocSize * earlyStoppingThreshold) {
                        log.info(logPrefix + "Early stopping after batch {}/{}: found {} good matches",
                                i+1, numBatches, goodMatches);
                        break;
                    }
                }
            }
        }

        return similarityScores;
    }

    /**
     * Count the number of "good" matches (above threshold) in the similarity scores.
     */
    private int countGoodMatches(Map<String, Double> similarityScores, double threshold) {
        int count = 0;
        for (Double score : similarityScores.values()) {
            if (score >= threshold) {
                count++;
            }
        }
        return count;
    }

    /**
     * Create super-optimized page pair tasks focusing on most likely matches.
     * In turbo mode, we drastically reduce the number of comparisons by only comparing
     * pages with identical numbers and a very small number of nearby pages.
     */
    private List<PagePairTask> createOptimizedPagePairTasks(
            PdfDocument baseDocument, PdfDocument compareDocument,
            Map<String, Double> similarityCache) {

        List<PagePairTask> tasks = new ArrayList<>();

        // In turbo mode, use a smaller page gap
        int effectiveMaxPageGap = turboMode ?
                Math.min(2, maxPageGap) : // Much smaller gap in turbo mode
                Math.min(maxPageGap, Math.max(1, (int)(Math.min(baseDocument.getPageCount(),
                        compareDocument.getPageCount()) * 0.1)));

        log.info("Using maximum page gap of {} for documents with {} and {} pages",
                effectiveMaxPageGap, baseDocument.getPageCount(), compareDocument.getPageCount());

        // 1. First priority: Pages with the same page number
        for (int i = 1; i <= Math.min(baseDocument.getPageCount(), compareDocument.getPageCount()); i++) {
            String key = baseDocument.getFileId() + "_" + i + "_" +
                    compareDocument.getFileId() + "_" + i;

            // Skip if already in cache
            if (similarityCache.containsKey(key)) {
                continue;
            }

            tasks.add(new PagePairTask(
                    baseDocument, compareDocument, i, i, key, 0));
        }

        // 2. Second priority: Pages within the maximum gap
        // In turbo mode, we might skip this if we want ultra-fast matching
        if (!turboMode || effectiveMaxPageGap > 0) {
            for (int basePageNumber = 1; basePageNumber <= baseDocument.getPageCount(); basePageNumber++) {
                for (int gap = 1; gap <= effectiveMaxPageGap; gap++) {
                    // Try page number + gap
                    int comparePagePlus = basePageNumber + gap;
                    if (comparePagePlus <= compareDocument.getPageCount()) {
                        String key = baseDocument.getFileId() + "_" + basePageNumber + "_" +
                                compareDocument.getFileId() + "_" + comparePagePlus;

                        // Skip if already in cache
                        if (similarityCache.containsKey(key)) {
                            continue;
                        }

                        tasks.add(new PagePairTask(
                                baseDocument, compareDocument, basePageNumber, comparePagePlus, key, gap));
                    }

                    // Try page number - gap
                    int comparePageMinus = basePageNumber - gap;
                    if (comparePageMinus >= 1) {
                        String key = baseDocument.getFileId() + "_" + basePageNumber + "_" +
                                compareDocument.getFileId() + "_" + comparePageMinus;

                        // Skip if already in cache
                        if (similarityCache.containsKey(key)) {
                            continue;
                        }

                        tasks.add(new PagePairTask(
                                baseDocument, compareDocument, basePageNumber, comparePageMinus, key, gap));
                    }
                }
            }
        }

        // 3. In non-turbo mode, we might add a few random samples for robustness
        // Skip in turbo mode to maximize speed
        if (!turboMode && tasks.size() < 50) {
            int sampleCount = Math.min(10, baseDocument.getPageCount() * compareDocument.getPageCount() / 20);
            Random random = new Random();

            for (int i = 0; i < sampleCount; i++) {
                int basePage = random.nextInt(baseDocument.getPageCount()) + 1;
                int comparePage = random.nextInt(compareDocument.getPageCount()) + 1;

                // Skip if it's a duplicate
                String key = baseDocument.getFileId() + "_" + basePage + "_" +
                        compareDocument.getFileId() + "_" + comparePage;
                if (similarityCache.containsKey(key)) {
                    continue;
                }

                // Check if this pair already exists in our tasks
                boolean exists = false;
                for (PagePairTask task : tasks) {
                    if (task.basePageNumber == basePage && task.comparePageNumber == comparePage) {
                        exists = true;
                        break;
                    }
                }

                if (!exists) {
                    tasks.add(new PagePairTask(
                            baseDocument, compareDocument, basePage, comparePage, key, 10));
                }
            }
        }

        // Sort by priority (lower value = higher priority)
        tasks.sort(Comparator.comparingInt(a -> a.priority));

        log.info("Created {} prioritized page pair tasks instead of {} potential pairs",
                tasks.size(), baseDocument.getPageCount() * compareDocument.getPageCount());

        return tasks;
    }

    /**
     * Process a batch of page pair tasks with specified timeout.
     * Uses optimized parallel processing with strict timeout control.
     */
    private void processPagePairBatch(
            List<PagePairTask> batchTasks,
            Map<String, Double> similarityScores,
            AtomicInteger completedComparisons,
            int totalComparisons,
            String comparisonId,
            String batchName,
            int timeoutSeconds) {

        if (batchTasks.isEmpty()) return;

        String logPrefix = "[" + batchTasks.get(0).baseDocument.getFileId() + "_" +
                batchTasks.get(0).compareDocument.getFileId() + "] ";

        log.info(logPrefix + "Processing {} with {} tasks, timeout: {}s",
                batchName, batchTasks.size(), timeoutSeconds);

        // Use a semaphore to limit concurrent comparisons
        Semaphore semaphore = new Semaphore(maxConcurrentComparisons);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (PagePairTask task : batchTasks) {
            // Submit tasks to calculate similarity scores
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    // Acquire permit from semaphore to limit concurrent processing
                    if (!semaphore.tryAcquire(2, TimeUnit.SECONDS)) {
                        log.debug(logPrefix + "Skipping task {}/{} due to resource constraints",
                                task.basePageNumber, task.comparePageNumber);
                        return;
                    }

                    try {
                        // Calculate similarity with retry and quick timeout
                        double similarity = calculateSimilarityWithRetry(
                                task.baseDocument, task.compareDocument,
                                task.basePageNumber, task.comparePageNumber);

                        // Cache the score
                        similarityCache.put(task.key, similarity);
                        similarityScores.put(task.key, similarity);

                        // Log progress
                        int completed = completedComparisons.incrementAndGet();

                        // Update progress less frequently in turbo mode
                        if (completed % 20 == 0 || completed == totalComparisons) {
                            int progressPercent = (completed * 100) / totalComparisons;
                            log.info(logPrefix + "Comparison progress: {}/{} ({}%)",
                                    completed, totalComparisons, progressPercent);

                            // Update progress in database if comparison ID provided
                            updateComparisonProgress(comparisonId, completed, totalComparisons);
                        }
                    } finally {
                        // Always release the semaphore
                        semaphore.release();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error(logPrefix + "Thread interrupted while processing page pair", e);
                } catch (Exception e) {
                    log.error(logPrefix + "Error calculating similarity for pages {}/{}: {}",
                            task.basePageNumber, task.comparePageNumber, e.getMessage());
                    completedComparisons.incrementAndGet();
                }
            }, executorService);

            futures.add(future);
        }

        // Wait for all tasks to complete with timeout
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(timeoutSeconds, TimeUnit.SECONDS);
            log.info(logPrefix + "{} completed successfully", batchName);
        } catch (Exception e) {
            log.warn(logPrefix + "Timeout or error waiting for {} to complete: {}",
                    batchName, e.getMessage());
            // Continue with partial results
        }

        // In turbo mode, aggressively release memory
        if (turboMode) {
            System.gc();
        }
    }

    /**
     * Update progress in the comparison service.
     */
    private void updateComparisonProgress(String comparisonId, int completed, int total) {
        if (comparisonId == null) return;

        try {
            Object comparisonService = getComparisonService();
            if (comparisonService != null) {
                java.lang.reflect.Method updateMethod = comparisonService.getClass()
                        .getMethod("updateComparisonProgress",
                                String.class, int.class, int.class, String.class);

                updateMethod.invoke(comparisonService,
                        comparisonId, completed, total,
                        "Calculating visual similarity scores");
            }
        } catch (Exception e) {
            log.warn("Failed to update comparison progress: {}", e.getMessage());
        }
    }

    /**
     * Class to represent a page pair comparison task.
     */
    private static class PagePairTask {
        final PdfDocument baseDocument;
        final PdfDocument compareDocument;
        final int basePageNumber;
        final int comparePageNumber;
        final String key;
        final int priority; // Lower value = higher priority

        PagePairTask(PdfDocument baseDocument, PdfDocument compareDocument,
                     int basePageNumber, int comparePageNumber, String key, int priority) {
            this.baseDocument = baseDocument;
            this.compareDocument = compareDocument;
            this.basePageNumber = basePageNumber;
            this.comparePageNumber = comparePageNumber;
            this.key = key;
            this.priority = priority;
        }
    }

    /**
     * Calculate similarity with retry mechanism and turbo mode optimizations.
     */
    private double calculateSimilarityWithRetry(
            PdfDocument baseDocument, PdfDocument compareDocument,
            int basePageNum, int comparePageNum) throws IOException {

        IOException lastException = null;

        for (int attempt = 0; attempt < retryCount; attempt++) {
            try {
                // Use try-with-resources to ensure images are properly disposed after use
                BufferedImage baseImage = null;
                BufferedImage compareImage = null;

                try {
                    // In turbo mode, use a very short timeout
                    int timeout = turboMode ? 5 : 10;

                    // Get the rendered page images with a short timeout
                    baseImage = getPageImage(baseDocument, basePageNum, timeout);
                    compareImage = getPageImage(compareDocument, comparePageNum, timeout);

                    // Check if images were loaded successfully
                    if (baseImage == null) {
                        throw new IOException("Failed to load base image for page " + basePageNum);
                    }
                    if (compareImage == null) {
                        throw new IOException("Failed to load compare image for page " + comparePageNum);
                    }

                    // In turbo mode, use a faster but less accurate comparison
                    if (fastSimilarityMetrics || turboMode) {
                        return calculateFastSimilarity(baseImage, compareImage);
                    } else {
                        // Use the more accurate SSIM calculator
                        return ssimCalculator.calculate(baseImage, compareImage);
                    }
                } finally {
                    // Explicit resource cleanup to help GC
                    cleanupImage(baseImage);
                    cleanupImage(compareImage);
                }
            } catch (IOException e) {
                lastException = e;
                log.debug("Attempt {} failed for pages {} and {}: {}",
                        attempt + 1, basePageNum, comparePageNum, e.getMessage());

                if (attempt < retryCount - 1) {
                    // Sleep before retrying with exponential backoff
                    try {
                        Thread.sleep(retryDelayMs * (1 << attempt)); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Thread interrupted during retry delay", ie);
                    }
                }
            }
        }

        // All retries failed
        if (lastException != null) {
            throw lastException;
        } else {
            throw new IOException("Failed to calculate similarity after " + retryCount + " attempts");
        }
    }

    /**
     * Clean up an image to help garbage collection.
     */
    private void cleanupImage(BufferedImage image) {
        if (image != null && !imageInCache(image)) {
            image.flush();
        }
    }

    /**
     * Check if an image is in the cache.
     */
    private boolean imageInCache(BufferedImage image) {
        for (SoftReference<BufferedImage> ref : imageCache.values()) {
            if (ref != null && ref.get() == image) {
                return true;
            }
        }
        return false;
    }

    /**
     * Calculate a fast similarity metric between two images.
     * This is much faster than SSIM but less accurate.
     */
    private double calculateFastSimilarity(BufferedImage img1, BufferedImage img2) {
        // Very fast histogram-based comparison
        // Sample only a subset of pixels for speed
        int[] hist1 = calculateHistogram(img1, 8);
        int[] hist2 = calculateHistogram(img2, 8);

        // Calculate histogram intersection (faster than SSIM)
        double intersection = 0;
        double sum1 = 0;
        double sum2 = 0;

        for (int i = 0; i < hist1.length; i++) {
            intersection += Math.min(hist1[i], hist2[i]);
            sum1 += hist1[i];
            sum2 += hist2[i];
        }

        // Normalize to [0,1]
        return intersection / Math.max(sum1, sum2);
    }

    /**
     * Calculate a color histogram for an image.
     */
    private int[] calculateHistogram(BufferedImage image, int binCount) {
        int[] hist = new int[binCount * binCount * binCount]; // RGB bins
        int width = image.getWidth();
        int height = image.getHeight();

        // Sample every 4th pixel for speed
        for (int y = 0; y < height; y += 4) {
            for (int x = 0; x < width; x += 4) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                // Map to bins (simplify color space)
                int rBin = r * binCount / 256;
                int gBin = g * binCount / 256;
                int bBin = b * binCount / 256;

                int idx = (rBin * binCount * binCount) + (gBin * binCount) + bBin;
                hist[idx]++;
            }
        }

        return hist;
    }

    /**
     * Get the rendered page image with efficient caching.
     */
    private BufferedImage getPageImage(PdfDocument document, int pageNumber, int timeoutSeconds) throws IOException {
        String cacheKey = document.getFileId() + "_" + pageNumber;

        // Check if the image is already in the cache
        SoftReference<BufferedImage> cachedImageRef = imageCache.get(cacheKey);
        if (cachedImageRef != null) {
            BufferedImage cachedImage = cachedImageRef.get();
            if (cachedImage != null) {
                return cachedImage;
            } else {
                // Reference was cleared by GC
                imageCache.remove(cacheKey);
            }
        }

        // Get the rendered page with timeout
        File pageFile;
        try {
            // Use timeout to avoid blocking too long on rendering
            CompletableFuture<File> renderFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return pdfRenderingService.renderPage(document, pageNumber);
                } catch (IOException e) {
                    throw new CompletionException(e);
                }
            }, executorService);

            pageFile = renderFuture.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IOException("Timed out or error rendering page " + pageNumber + ": " + e.getMessage(), e);
        }

        // Ensure file exists and is readable
        if (!pageFile.exists() || !pageFile.canRead()) {
            throw new IOException("Page file does not exist or is not readable: " + pageFile.getPath());
        }

        // Check file size to avoid empty files
        if (pageFile.length() == 0) {
            throw new IOException("Page file is empty: " + pageFile.getPath());
        }

        // Load the image
        BufferedImage image = ImageIO.read(pageFile);

        // Check if the image was loaded successfully
        if (image == null) {
            throw new IOException("Failed to load image: " + pageFile.getPath());
        }

        // In turbo mode, downsample the image for faster processing
        if (turboMode && image.getWidth() > 500) {
            int newWidth = image.getWidth() / 2;
            int newHeight = image.getHeight() / 2;

            BufferedImage resized = new BufferedImage(newWidth, newHeight, image.getType());
            Graphics2D g = resized.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(image, 0, 0, newWidth, newHeight, null);
            g.dispose();

            image.flush(); // Release the original
            image = resized;
        }

        // Cache the image with soft reference to allow GC when memory is low
        if (imageCache.size() >= MAX_CACHE_SIZE) {
            // Evict an entry if cache is full
            if (!imageCache.isEmpty()) {
                String keyToRemove = imageCache.keySet().iterator().next();
                SoftReference<BufferedImage> removedRef = imageCache.remove(keyToRemove);
                if (removedRef != null) {
                    BufferedImage removedImage = removedRef.get();
                    if (removedImage != null) {
                        removedImage.flush();
                    }
                }
            }
        }

        imageCache.put(cacheKey, new SoftReference<>(image));
        return image;
    }

    /**
     * Clear the image cache to free memory.
     */
    private void clearImageCache() {
        // Explicitly flush images before removing references
        for (SoftReference<BufferedImage> ref : imageCache.values()) {
            if (ref != null) {
                BufferedImage image = ref.get();
                if (image != null) {
                    image.flush();
                }
            }
        }
        imageCache.clear();
        System.gc(); // Request garbage collection
    }

    /**
     * Match pages using the Hungarian algorithm with early stopping for large documents.
     */
    private List<PagePair> matchPagesUsingHungarian(
            PdfDocument baseDocument, PdfDocument compareDocument,
            Map<String, Double> similarityScores) {

        int basePageCount = baseDocument.getPageCount();
        int comparePageCount = compareDocument.getPageCount();

        // Fast path for turbo mode with large documents
        if (turboMode && (basePageCount > 50 || comparePageCount > 50)) {
            log.info("Using simple matching for large documents in turbo mode");
            return createSimpleMatching(baseDocument, compareDocument, similarityScores);
        }

        // Create a cost matrix for the Hungarian algorithm
        double[][] costMatrix = new double[basePageCount][comparePageCount];

        // Fill the cost matrix with the negative similarity scores
        // (Hungarian algorithm minimizes cost, but we want to maximize similarity)
        for (int i = 0; i < basePageCount; i++) {
            for (int j = 0; j < comparePageCount; j++) {
                String key = baseDocument.getFileId() + "_" + (i + 1) + "_" +
                        compareDocument.getFileId() + "_" + (j + 1);

                double similarity = similarityScores.getOrDefault(key, 0.0);

                // If the similarity is below the threshold, set a high cost
                if (similarity < visualSimilarityThreshold) {
                    costMatrix[i][j] = 1.0;
                } else {
                    costMatrix[i][j] = 1.0 - similarity;
                }
            }
        }

        // Run the Hungarian algorithm
        HungarianAlgorithm hungarian = new HungarianAlgorithm(costMatrix);
        int[] assignments = hungarian.execute();

        // Create page pairs based on the assignments
        List<PagePair> pagePairs = new ArrayList<>();

        for (int i = 0; i < basePageCount; i++) {
            int j = assignments[i];

            // Create a page pair
            PagePair.PagePairBuilder builder = PagePair.builder()
                    .baseDocumentId(baseDocument.getFileId())
                    .compareDocumentId(compareDocument.getFileId())
                    .basePageNumber(i + 1);

            // If the page is matched
            if (j != -1 && j < comparePageCount) {
                String key = baseDocument.getFileId() + "_" + (i + 1) + "_" +
                        compareDocument.getFileId() + "_" + (j + 1);

                double similarity = similarityScores.getOrDefault(key, 0.0);

                // If the similarity is above the threshold, mark as matched
                if (similarity >= visualSimilarityThreshold) {
                    builder.comparePageNumber(j + 1)
                            .matched(true)
                            .similarityScore(similarity);
                } else {
                    builder.matched(false);
                }
            } else {
                builder.matched(false);
            }

            pagePairs.add(builder.build());
        }

        // Add unmatched pages from the compare document
        for (int j = 0; j < comparePageCount; j++) {
            boolean matched = false;

            for (int i = 0; i < basePageCount; i++) {
                if (assignments[i] == j) {
                    matched = true;
                    break;
                }
            }

            if (!matched) {
                pagePairs.add(PagePair.builder()
                        .baseDocumentId(baseDocument.getFileId())
                        .compareDocumentId(compareDocument.getFileId())
                        .comparePageNumber(j + 1)
                        .matched(false)
                        .build());
            }
        }

        return pagePairs;
    }

    /**
     * Create a simple matching for large documents in turbo mode.
     * This uses a greedy approach instead of Hungarian algorithm for speed.
     */
    private List<PagePair> createSimpleMatching(
            PdfDocument baseDocument, PdfDocument compareDocument,
            Map<String, Double> similarityScores) {

        List<PagePair> pagePairs = new ArrayList<>();
        boolean[] compareMatched = new boolean[compareDocument.getPageCount() + 1];

        // First pass: try to match pages with the same number
        for (int i = 1; i <= baseDocument.getPageCount(); i++) {
            if (i <= compareDocument.getPageCount()) {
                String key = baseDocument.getFileId() + "_" + i + "_" +
                        compareDocument.getFileId() + "_" + i;

                double similarity = similarityScores.getOrDefault(key, 0.0);

                if (similarity >= visualSimilarityThreshold) {
                    pagePairs.add(PagePair.builder()
                            .baseDocumentId(baseDocument.getFileId())
                            .compareDocumentId(compareDocument.getFileId())
                            .basePageNumber(i)
                            .comparePageNumber(i)
                            .matched(true)
                            .similarityScore(similarity)
                            .build());
                    compareMatched[i] = true;
                    continue;
                }
            }

            // Try to find the best match for this base page
            int bestMatch = -1;
            double bestSimilarity = visualSimilarityThreshold;

            for (int j = 1; j <= compareDocument.getPageCount(); j++) {
                if (compareMatched[j]) continue;

                String key = baseDocument.getFileId() + "_" + i + "_" +
                        compareDocument.getFileId() + "_" + j;

                double similarity = similarityScores.getOrDefault(key, 0.0);

                if (similarity > bestSimilarity) {
                    bestSimilarity = similarity;
                    bestMatch = j;
                }
            }

            if (bestMatch != -1) {
                pagePairs.add(PagePair.builder()
                        .baseDocumentId(baseDocument.getFileId())
                        .compareDocumentId(compareDocument.getFileId())
                        .basePageNumber(i)
                        .comparePageNumber(bestMatch)
                        .matched(true)
                        .similarityScore(bestSimilarity)
                        .build());
                compareMatched[bestMatch] = true;
            } else {
                pagePairs.add(PagePair.builder()
                        .baseDocumentId(baseDocument.getFileId())
                        .compareDocumentId(compareDocument.getFileId())
                        .basePageNumber(i)
                        .matched(false)
                        .build());
            }
        }

        // Add unmatched pages from compare document
        for (int j = 1; j <= compareDocument.getPageCount(); j++) {
            if (!compareMatched[j]) {
                pagePairs.add(PagePair.builder()
                        .baseDocumentId(baseDocument.getFileId())
                        .compareDocumentId(compareDocument.getFileId())
                        .comparePageNumber(j)
                        .matched(false)
                        .build());
            }
        }

        return pagePairs;
    }

    /**
     * Implementation of the Hungarian algorithm for solving the assignment problem.
     * Optimized for performance and early stopping to handle large matrices efficiently.
     */
    private static class HungarianAlgorithm {
        private final double[][] costMatrix;
        private final int rows, cols;
        private final int[] colAssignment;
        private final int[] rowAssignment;
        private final double[] rowCover;
        private final double[] colCover;
        private final int maxIterations;

        /**
         * Constructor.
         *
         * @param costMatrix The cost matrix
         */
        public HungarianAlgorithm(double[][] costMatrix) {
            this.costMatrix = costMatrix;
            this.rows = costMatrix.length;
            this.cols = costMatrix[0].length;
            this.colAssignment = new int[rows];
            this.rowAssignment = new int[cols];
            this.rowCover = new double[rows];
            this.colCover = new double[cols];
            // Set maximum iterations to prevent infinite loops
            this.maxIterations = Math.max(10, rows * 2);
        }

        /**
         * Execute the Hungarian algorithm with early stopping for performance.
         *
         * @return An array of assignments (column indices for each row)
         */
        public int[] execute() {
            // Initialize assignments
            for (int i = 0; i < rows; i++) {
                colAssignment[i] = -1;
            }
            for (int j = 0; j < cols; j++) {
                rowAssignment[j] = -1;
            }

            // Step 1: Reduce rows by subtracting minimum value from each row
            for (int i = 0; i < rows; i++) {
                double minValue = Double.MAX_VALUE;
                for (int j = 0; j < cols; j++) {
                    if (costMatrix[i][j] < minValue) {
                        minValue = costMatrix[i][j];
                    }
                }
                if (minValue != Double.MAX_VALUE) {
                    for (int j = 0; j < cols; j++) {
                        costMatrix[i][j] -= minValue;
                    }
                }
            }

            // Step 2: Find a zero in each row and mark it as an assignment if possible
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < cols; j++) {
                    if (costMatrix[i][j] == 0 && colCover[j] == 0) {
                        rowAssignment[j] = i;
                        colAssignment[i] = j;
                        colCover[j] = 1;
                        break;
                    }
                }
            }

            // Clear covers for augmenting path
            Arrays.fill(colCover, 0);

            // Step 3: Cover all assigned columns
            for (int j = 0; j < cols; j++) {
                if (rowAssignment[j] != -1) {
                    colCover[j] = 1;
                }
            }

            // Main loop for the Hungarian algorithm
            int iteration = 0;
            while (iteration < maxIterations) {
                // If all columns are covered, we're done
                int coveredCols = 0;
                for (int j = 0; j < cols; j++) {
                    if (colCover[j] == 1) {
                        coveredCols++;
                    }
                }

                // If we've covered all columns or enough of them, we're done
                if (coveredCols == Math.min(rows, cols) || coveredCols >= (int)(Math.min(rows, cols) * 0.9)) {
                    break;
                }

                // Find uncovered zero
                int[] zero = findUncoveredZero();
                if (zero[0] != -1) {
                    // Mark the zero
                    rowCover[zero[0]] = 1;
                    colCover[zero[1]] = 0;

                    // Find assigned zero in the row
                    int assignedCol = colAssignment[zero[0]];
                    if (assignedCol != -1) {
                        // Unassign and cover column
                        colCover[assignedCol] = 1;
                    } else {
                        // Augmenting path found, add new assignment
                        colAssignment[zero[0]] = zero[1];
                        rowAssignment[zero[1]] = zero[0];
                        break;
                    }
                } else {
                    // No uncovered zero, adjust matrix
                    double minValue = findMinValue();

                    for (int i = 0; i < rows; i++) {
                        if (rowCover[i] == 1) {
                            for (int j = 0; j < cols; j++) {
                                costMatrix[i][j] += minValue;
                            }
                        }
                    }

                    for (int j = 0; j < cols; j++) {
                        if (colCover[j] == 0) {
                            for (int i = 0; i < rows; i++) {
                                costMatrix[i][j] -= minValue;
                            }
                        }
                    }
                }

                iteration++;
            }

            return colAssignment;
        }

        /**
         * Find an uncovered zero in the cost matrix.
         *
         * @return The coordinates of the uncovered zero, or [-1, -1] if none found
         */
        private int[] findUncoveredZero() {
            for (int i = 0; i < rows; i++) {
                if (rowCover[i] == 0) {
                    for (int j = 0; j < cols; j++) {
                        if (colCover[j] == 0 && costMatrix[i][j] == 0) {
                            return new int[]{i, j};
                        }
                    }
                }
            }
            return new int[]{-1, -1};
        }

        /**
         * Find the minimum uncovered value in the cost matrix.
         *
         * @return The minimum uncovered value
         */
        private double findMinValue() {
            double minValue = Double.MAX_VALUE;

            for (int i = 0; i < rows; i++) {
                if (rowCover[i] == 0) {
                    for (int j = 0; j < cols; j++) {
                        if (colCover[j] == 0 && costMatrix[i][j] < minValue) {
                            minValue = costMatrix[i][j];
                        }
                    }
                }
            }

            return minValue;
        }
    }
}