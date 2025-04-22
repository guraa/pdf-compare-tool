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

    @Value("${app.matching.max-concurrent-comparisons:32}")
    private int maxConcurrentComparisons;

    @Value("${app.matching.retry-count:3}")
    private int retryCount;

    @Value("${app.matching.retry-delay-ms:25}")
    private int retryDelayMs;
    
    @Value("${app.matching.parallel-batch-processing:true}")
    private boolean parallelBatchProcessing = true;
    
    @Value("${app.matching.max-parallel-batches:2}")
    private int maxParallelBatches = 2;

    @Value("${app.matching.max-page-gap:3}")
    private int maxPageGap = 3;

    @Value("${app.matching.batch-timeout-seconds:60}")
    private int batchTimeoutSeconds = 60;
    
    @Value("${app.matching.wait-for-previous-batches:true}")
    private boolean waitForPreviousBatches = true;

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
        
        // Use parallel batch processing if enabled
        if (parallelBatchProcessing && totalComparisons > 20) {
            log.info(logPrefix + "Using parallel batch processing with up to {} concurrent batches", 
                    maxParallelBatches);
        }

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
                int batchSize = turboMode ? 4 : 6;
                int numBatches = (otherPairs.size() + batchSize - 1) / batchSize;

                log.info(logPrefix + "Processing remaining {} pairs in {} batches",
                        otherPairs.size(), numBatches);
                
                if (parallelBatchProcessing && numBatches > 1) {
                    // Process batches in parallel
                    int concurrentBatches = Math.min(maxParallelBatches, numBatches);
                    log.info(logPrefix + "Using parallel batch processing with {} concurrent batches", concurrentBatches);
                    
                    // Create a list to hold all batch futures
                    List<CompletableFuture<Void>> batchFutures = new ArrayList<>();
                    
                    // Process batches in groups to limit concurrency
                    for (int i = 0; i < numBatches; i += concurrentBatches) {
                        // Create a list for the current group of batches
                        List<CompletableFuture<Void>> currentBatchGroup = new ArrayList<>();
                        
                        // Submit batches for this group
                        for (int j = 0; j < concurrentBatches && (i + j) < numBatches; j++) {
                            final int batchIndex = i + j;
                            int start = batchIndex * batchSize;
                            int end = Math.min(start + batchSize, otherPairs.size());
                            if (start >= otherPairs.size()) break;
                            
                            List<PagePairTask> batch = new ArrayList<>(otherPairs.subList(start, end));
                            int remainingTimeout = turboMode ? 10 : 20;
                            
                            CompletableFuture<Void> batchFuture = CompletableFuture.runAsync(() -> {
                                processPagePairBatch(batch, similarityScores, completedComparisons,
                                        totalComparisons, comparisonId, "Batch " + (batchIndex+1) + "/" + numBatches, 
                                        remainingTimeout);
                            }, executorService);
                            
                            currentBatchGroup.add(batchFuture);
                            batchFutures.add(batchFuture);
                        }
                        
                        // If waitForPreviousBatches is enabled, wait for the current group to complete
                        // before processing the next group
                        if (waitForPreviousBatches && !currentBatchGroup.isEmpty()) {
                            try {
                                CompletableFuture.allOf(currentBatchGroup.toArray(new CompletableFuture[0]))
                                        .get(batchTimeoutSeconds * currentBatchGroup.size(), TimeUnit.SECONDS);
                                log.info(logPrefix + "Completed batch group {}-{}/{}", 
                                        i+1, Math.min(i+concurrentBatches, numBatches), numBatches);
                            } catch (Exception e) {
                                log.warn(logPrefix + "Timeout or error waiting for batch group {}-{}/{} to complete: {}", 
                                        i+1, Math.min(i+concurrentBatches, numBatches), numBatches, e.getMessage());
                                // Continue with next group even if this one had issues
                            }
                            
                            // Check for early stopping after each batch group
                            if (earlyStoppingEnabled) {
                                int goodMatches = countGoodMatches(similarityScores, visualSimilarityThreshold);
                                int minDocSize = Math.min(baseDocument.getPageCount(), compareDocument.getPageCount());
                                if (goodMatches >= minDocSize * earlyStoppingThreshold) {
                                    log.info(logPrefix + "Early stopping after batch group {}-{}/{}: found {} good matches",
                                            i+1, Math.min(i+concurrentBatches, numBatches), numBatches, goodMatches);
                                    break;
                                }
                            }
                        }
                    }
                    
                    // Wait for all batches to complete
                    try {
                        CompletableFuture.allOf(batchFutures.toArray(new CompletableFuture[0]))
                                .get(numBatches * batchTimeoutSeconds, TimeUnit.SECONDS);
                        log.info(logPrefix + "All parallel batches completed successfully");
                    } catch (Exception e) {
                        log.warn(logPrefix + "Timeout or error waiting for parallel batches to complete: {}", 
                                e.getMessage());
                        // Continue with partial results
                    }
                    
                    // Check for early stopping after all batches
                    if (earlyStoppingEnabled) {
                        int goodMatches = countGoodMatches(similarityScores, visualSimilarityThreshold);
                        int minDocSize = Math.min(baseDocument.getPageCount(), compareDocument.getPageCount());
                        if (goodMatches >= minDocSize * earlyStoppingThreshold) {
                            log.info(logPrefix + "Early stopping after parallel batches: found {} good matches",
                                    goodMatches);
                        }
                    }
                } else {
                    // Process batches sequentially
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
            }
        } else {
            // No exact matches available, process all pairs in batches
            int batchSize = turboMode ? 4 : 6;
            int numBatches = (pagePairTasks.size() + batchSize - 1) / batchSize;

            log.info(logPrefix + "No exact page matches, processing {} pairs in {} batches",
                    pagePairTasks.size(), numBatches);
                    
            if (parallelBatchProcessing && numBatches > 1) {
                // Process batches in parallel
                int concurrentBatches = Math.min(maxParallelBatches, numBatches);
                log.info(logPrefix + "Using parallel batch processing with {} concurrent batches", concurrentBatches);
                
                // Create a list to hold all batch futures
                List<CompletableFuture<Void>> batchFutures = new ArrayList<>();
                
                // Process batches in groups to limit concurrency
                for (int i = 0; i < numBatches; i += concurrentBatches) {
                    // Create a list for the current group of batches
                    List<CompletableFuture<Void>> currentBatchGroup = new ArrayList<>();
                    
                    // Submit batches for this group
                    for (int j = 0; j < concurrentBatches && (i + j) < numBatches; j++) {
                        final int batchIndex = i + j;
                        int start = batchIndex * batchSize;
                        int end = Math.min(start + batchSize, pagePairTasks.size());
                        if (start >= pagePairTasks.size()) break;
                        
                        List<PagePairTask> batch = new ArrayList<>(pagePairTasks.subList(start, end));
                        int timeout = turboMode ? 15 : 30;
                        
                        CompletableFuture<Void> batchFuture = CompletableFuture.runAsync(() -> {
                            processPagePairBatch(batch, similarityScores, completedComparisons,
                                    totalComparisons, comparisonId, "Batch " + (batchIndex+1) + "/" + numBatches, 
                                    timeout);
                        }, executorService);
                        
                        currentBatchGroup.add(batchFuture);
                        batchFutures.add(batchFuture);
                    }
                    
                    // If waitForPreviousBatches is enabled, wait for the current group to complete
                    // before processing the next group
                    if (waitForPreviousBatches && !currentBatchGroup.isEmpty()) {
                        try {
                            CompletableFuture.allOf(currentBatchGroup.toArray(new CompletableFuture[0]))
                                    .get(batchTimeoutSeconds * currentBatchGroup.size(), TimeUnit.SECONDS);
                            log.info(logPrefix + "Completed batch group {}-{}/{}", 
                                    i+1, Math.min(i+concurrentBatches, numBatches), numBatches);
                        } catch (Exception e) {
                            log.warn(logPrefix + "Timeout or error waiting for batch group {}-{}/{} to complete: {}", 
                                    i+1, Math.min(i+concurrentBatches, numBatches), numBatches, e.getMessage());
                            // Continue with next group even if this one had issues
                        }
                        
                        // Check for early stopping after each batch group
                        if (earlyStoppingEnabled) {
                            int goodMatches = countGoodMatches(similarityScores, visualSimilarityThreshold);
                            int minDocSize = Math.min(baseDocument.getPageCount(), compareDocument.getPageCount());
                            if (goodMatches >= minDocSize * earlyStoppingThreshold) {
                                log.info(logPrefix + "Early stopping after batch group {}-{}/{}: found {} good matches",
                                        i+1, Math.min(i+concurrentBatches, numBatches), numBatches, goodMatches);
                                break;
                            }
                        }
                    }
                }
                
                // Wait for all batches to complete
                try {
                    CompletableFuture.allOf(batchFutures.toArray(new CompletableFuture[0]))
                            .get(numBatches * batchTimeoutSeconds, TimeUnit.SECONDS);
                    log.info(logPrefix + "All parallel batches completed successfully");
                } catch (Exception e) {
                    log.warn(logPrefix + "Timeout or error waiting for parallel batches to complete: {}", 
                            e.getMessage());
                    // Continue with partial results
                }
                
                // Check for early stopping after all batches
                if (earlyStoppingEnabled) {
                    int goodMatches = countGoodMatches(similarityScores, visualSimilarityThreshold);
                    int minDocSize = Math.min(baseDocument.getPageCount(), compareDocument.getPageCount());
                    if (goodMatches >= minDocSize * earlyStoppingThreshold) {
                        log.info(logPrefix + "Early stopping after parallel batches: found {} good matches",
                                goodMatches);
                    }
                }
            } else {
                // Process batches sequentially
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
     * Enhanced with better fallback handling for rendering failures.
     */
    private double calculateSimilarityWithRetry(
            PdfDocument baseDocument, PdfDocument compareDocument,
            int basePageNum, int comparePageNum) {

        IOException lastException = null;

        for (int attempt = 0; attempt < retryCount; attempt++) {
            BufferedImage baseImage = null;
            BufferedImage compareImage = null;
            
            try {
                // In turbo mode, use a very short timeout
                int timeout = turboMode ? 5 : 10;

                // Get the rendered page images with a short timeout
                try {
                    baseImage = getPageImage(baseDocument, basePageNum, timeout);
                } catch (Exception e) {
                    log.warn("Failed to load base image for page {}: {}", basePageNum, e.getMessage());
                    // Continue with fallback handling below
                }
                
                try {
                    compareImage = getPageImage(compareDocument, comparePageNum, timeout);
                } catch (Exception e) {
                    log.warn("Failed to load compare image for page {}: {}", comparePageNum, e.getMessage());
                    // Continue with fallback handling below
                }

                // If either image failed to load, use a fallback approach
                if (baseImage == null || compareImage == null) {
                    // For exact page number matches, assume moderate similarity
                    if (basePageNum == comparePageNum) {
                        log.info("Using fallback similarity for exact page number match {}/{}", 
                                basePageNum, comparePageNum);
                        return 0.7; // Moderate similarity for same page numbers
                    } else {
                        // For non-exact matches, use a lower similarity
                        double fallbackSimilarity = 0.5 - (Math.abs(basePageNum - comparePageNum) * 0.05);
                        fallbackSimilarity = Math.max(0.2, fallbackSimilarity); // Don't go below 0.2
                        
                        log.info("Using fallback similarity for pages {}/{}: {}", 
                                basePageNum, comparePageNum, fallbackSimilarity);
                        return fallbackSimilarity;
                    }
                }

                // Both images loaded successfully, calculate similarity
                // In turbo mode, use a faster but less accurate comparison
                if (fastSimilarityMetrics || turboMode) {
                    return calculateFastSimilarity(baseImage, compareImage);
                } else {
                    // Use the more accurate SSIM calculator
                    return ssimCalculator.calculate(baseImage, compareImage);
                }
            } catch (Exception e) {
                lastException = e instanceof IOException ? (IOException)e : new IOException(e);
                log.debug("Attempt {} failed for pages {} and {}: {}",
                        attempt + 1, basePageNum, comparePageNum, e.getMessage());

                if (attempt < retryCount - 1) {
                    // Sleep before retrying with exponential backoff
                    try {
                        Thread.sleep(retryDelayMs * (1 << attempt)); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break; // Stop retrying if interrupted
                    }
                }
            } finally {
                // Explicit resource cleanup to help GC
                if (baseImage != null) {
                    cleanupImage(baseImage);
                }
                if (compareImage != null) {
                    cleanupImage(compareImage);
                }
            }
        }

        // All retries failed - use a very basic fallback
        log.warn("All similarity calculation attempts failed for pages {}/{}. Using fallback similarity.",
                basePageNum, comparePageNum);
                
        // For exact page number matches, assume moderate similarity
        if (basePageNum == comparePageNum) {
            return 0.65; // Slightly lower than normal fallback for exact matches
        } else {
            // For non-exact matches, use a lower similarity based on page distance
            double fallbackSimilarity = 0.4 - (Math.abs(basePageNum - comparePageNum) * 0.05);
            return Math.max(0.1, fallbackSimilarity); // Don't go below 0.1
        }
    }
    
    /**
     * Get a rendered page image with timeout.
     */
    private BufferedImage getPageImage(PdfDocument document, int pageNumber, int timeoutSeconds) throws IOException {
        // Try to get from cache first
        String cacheKey = document.getFileId() + "_page_" + pageNumber;
        SoftReference<BufferedImage> cachedImageRef = imageCache.get(cacheKey);
        if (cachedImageRef != null) {
            BufferedImage cachedImage = cachedImageRef.get();
            if (cachedImage != null) {
                return cachedImage;
            }
            // Reference was cleared by GC, remove from cache
            imageCache.remove(cacheKey);
        }

        // Render the page with timeout
        Future<BufferedImage> renderFuture = executorService.submit(() -> {
            try {
                File renderedPage = pdfRenderingService.renderPage(document, pageNumber);
                return ImageIO.read(renderedPage);
            } catch (Exception e) {
                throw new IOException("Error rendering page " + pageNumber + ": " + e.getMessage(), e);
            }
        });

        try {
            BufferedImage image = renderFuture.get(timeoutSeconds, TimeUnit.SECONDS);
            
            // Cache the image if there's room
            if (imageCache.size() < MAX_CACHE_SIZE) {
                imageCache.put(cacheKey, new SoftReference<>(image));
            }
            
            return image;
        } catch (Exception e) {
            renderFuture.cancel(true);
            throw new IOException("Timed out or error rendering page " + pageNumber + ": " + e.getMessage());
        }
    }
    
    /**
     * Calculate similarity using a faster but less accurate algorithm.
     */
    private double calculateFastSimilarity(BufferedImage img1, BufferedImage img2) {
        // Resize images to a smaller size for faster comparison
        int targetWidth = 100;
        int targetHeight = 140;
        
        BufferedImage scaledImg1 = scaleImage(img1, targetWidth, targetHeight);
        BufferedImage scaledImg2 = scaleImage(img2, targetWidth, targetHeight);
        
        // Convert to grayscale and calculate pixel-based similarity
        int[] pixels1 = convertToGrayscale(scaledImg1);
        int[] pixels2 = convertToGrayscale(scaledImg2);
        
        return calculatePixelSimilarity(pixels1, pixels2);
    }
    
    /**
     * Scale an image to the target dimensions.
     */
    private BufferedImage scaleImage(BufferedImage original, int targetWidth, int targetHeight) {
        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(original, 0, 0, targetWidth, targetHeight, null);
        g.dispose();
        return resized;
    }
    
    /**
     * Convert an image to grayscale pixel array.
     */
    private int[] convertToGrayscale(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = new int[width * height];
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                int gray = (r + g + b) / 3;
                pixels[y * width + x] = gray;
            }
        }
        
        return pixels;
    }
    
    /**
     * Calculate pixel-based similarity between two grayscale pixel arrays.
     */
    private double calculatePixelSimilarity(int[] pixels1, int[] pixels2) {
        if (pixels1.length != pixels2.length) {
            return 0.0;
        }
        
        double sum = 0;
        double maxDiff = 255.0 * pixels1.length;
        
        for (int i = 0; i < pixels1.length; i++) {
            sum += Math.abs(pixels1[i] - pixels2[i]);
        }
        
        return 1.0 - (sum / maxDiff);
    }
    
    /**
     * Clean up an image to help with garbage collection.
     */
    private void cleanupImage(BufferedImage image) {
        if (image != null) {
            image.flush();
        }
    }
    
    /**
     * Clear the image cache to free memory.
     */
    private void clearImageCache() {
        imageCache.clear();
        System.gc();
    }
    
    /**
     * Match pages using the Hungarian algorithm.
     */
    private List<PagePair> matchPagesUsingHungarian(
            PdfDocument baseDocument, PdfDocument compareDocument,
            Map<String, Double> similarityScores) {
        
        int basePageCount = baseDocument.getPageCount();
        int comparePageCount = compareDocument.getPageCount();
        
        // Create a matrix of similarity scores
        double[][] matrix = new double[basePageCount][comparePageCount];
        
        // Fill the matrix with similarity scores
        for (int i = 0; i < basePageCount; i++) {
            for (int j = 0; j < comparePageCount; j++) {
                String key = baseDocument.getFileId() + "_" + (i + 1) + "_" +
                        compareDocument.getFileId() + "_" + (j + 1);
                
                double similarity = similarityScores.getOrDefault(key, 0.0);
                
                // Convert similarity to cost (Hungarian algorithm minimizes cost)
                matrix[i][j] = 1.0 - similarity;
            }
        }
        
        // Run the Hungarian algorithm
        int[] assignment = hungarianAlgorithm(matrix);
        
        // Create page pairs from the assignment
        List<PagePair> pagePairs = new ArrayList<>();
        
        for (int i = 0; i < basePageCount; i++) {
            int j = assignment[i];
            
            // Only include valid assignments
            if (j >= 0 && j < comparePageCount) {
                String key = baseDocument.getFileId() + "_" + (i + 1) + "_" +
                        compareDocument.getFileId() + "_" + (j + 1);
                
                double similarity = similarityScores.getOrDefault(key, 0.0);
                
                // Only include pairs with similarity above threshold
                if (similarity >= visualSimilarityThreshold) {
                    pagePairs.add(PagePair.builder()
                        .baseDocumentId(baseDocument.getFileId())
                        .compareDocumentId(compareDocument.getFileId())
                        .basePageNumber(i + 1)
                        .comparePageNumber(j + 1)
                        .similarityScore(similarity)
                        .matched(true)
                        .build());
                }
            }
        }
        
        // Sort by base page number
        pagePairs.sort(Comparator.comparingInt(PagePair::getBasePageNumber));
        
        return pagePairs;
    }
    
    /**
     * Implementation of the Hungarian algorithm for assignment problems.
     */
    private int[] hungarianAlgorithm(double[][] costMatrix) {
        int n = costMatrix.length;
        int m = costMatrix[0].length;
        
        // Create a copy of the cost matrix
        double[][] costs = new double[n][m];
        for (int i = 0; i < n; i++) {
            System.arraycopy(costMatrix[i], 0, costs[i], 0, m);
        }
        
        // Step 1: Subtract row minima
        for (int i = 0; i < n; i++) {
            double rowMin = Double.MAX_VALUE;
            for (int j = 0; j < m; j++) {
                rowMin = Math.min(rowMin, costs[i][j]);
            }
            for (int j = 0; j < m; j++) {
                costs[i][j] -= rowMin;
            }
        }
        
        // Step 2: Subtract column minima
        for (int j = 0; j < m; j++) {
            double colMin = Double.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                colMin = Math.min(colMin, costs[i][j]);
            }
            for (int i = 0; i < n; i++) {
                costs[i][j] -= colMin;
            }
        }
        
        // Step 3: Cover all zeros with a minimum number of lines
        int[] rowCover = new int[n];
        int[] colCover = new int[m];
        int[] assignment = new int[n];
        Arrays.fill(assignment, -1);
        
        // Find an initial assignment
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                if (costs[i][j] == 0 && rowCover[i] == 0 && colCover[j] == 0) {
                    rowCover[i] = 1;
                    colCover[j] = 1;
                    assignment[i] = j;
                    break;
                }
            }
        }
        
        // Clear covers
        Arrays.fill(rowCover, 0);
        Arrays.fill(colCover, 0);
        
        // Main loop
        while (true) {
            // Mark all rows having no assignment
            for (int i = 0; i < n; i++) {
                if (assignment[i] == -1) {
                    rowCover[i] = 1;
                }
            }
            
            // Mark all columns having zeros in marked rows
            boolean[] newColCover = new boolean[m];
            for (int i = 0; i < n; i++) {
                if (rowCover[i] == 1) {
                    for (int j = 0; j < m; j++) {
                        if (costs[i][j] == 0) {
                            colCover[j] = 1;
                        }
                    }
                }
            }
            
            // Mark all rows having assignments in marked columns
            for (int i = 0; i < n; i++) {
                if (assignment[i] != -1 && colCover[assignment[i]] == 1) {
                    rowCover[i] = 1;
                }
            }
            
            // Count covered columns
            int count = 0;
            for (int j = 0; j < m; j++) {
                if (colCover[j] == 1) {
                    count++;
                }
            }
            
            // If all columns are covered, we're done
            if (count == Math.min(n, m)) {
                break;
            }
            
            // Find the minimum uncovered value
            double minUncovered = Double.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                if (rowCover[i] == 0) {
                    for (int j = 0; j < m; j++) {
                        if (colCover[j] == 0) {
                            minUncovered = Math.min(minUncovered, costs[i][j]);
                        }
                    }
                }
            }
            
            // Subtract from uncovered, add to covered
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < m; j++) {
                    if (rowCover[i] == 0 && colCover[j] == 0) {
                        costs[i][j] -= minUncovered;
                    } else if (rowCover[i] == 1 && colCover[j] == 1) {
                        costs[i][j] += minUncovered;
                    }
                }
            }
        }
        
        return assignment;
    }
}
