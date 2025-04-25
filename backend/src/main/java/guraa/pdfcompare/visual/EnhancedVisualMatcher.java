package guraa.pdfcompare.visual;

import guraa.pdfcompare.model.PdfDocument;
import guraa.pdfcompare.service.PagePair;
import guraa.pdfcompare.service.PdfRenderingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Enhanced visual matcher for PDF pages with improved performance, error handling, threading, and caching.
 * This class uses SSIM (Structural Similarity Index) to match pages between two PDF documents based on visual similarity.
 */
@Slf4j
@Component
public class EnhancedVisualMatcher implements VisualMatcher {

    private final SSIMCalculator ssimCalculator;
    private final PdfRenderingService pdfRenderingService;
    private final ExecutorService executorService;

    // Cache of rendered pages to avoid repeated file I/O, using SoftReferences to allow GC when memory is low
    private final ConcurrentHashMap<String, SoftReference<BufferedImage>> imageCache = new ConcurrentHashMap<>();

    // Cache of similarity scores
    private final ConcurrentHashMap<String, Double> similarityCache = new ConcurrentHashMap<>();

    // Map to track cancellation tokens for comparisons
    private final ConcurrentHashMap<String, AtomicBoolean> cancellationTokens = new ConcurrentHashMap<>();

    /**
     * ComparisonTask class for page comparison tasks
     */
    private static class ComparisonTask {
        final PdfDocument baseDocument;
        final PdfDocument compareDocument;
        final int basePageNum;
        final int comparePageNum;
        final String key;

        ComparisonTask(PdfDocument baseDocument, PdfDocument compareDocument,
                       int basePageNum, int comparePageNum, String key) {
            this.baseDocument = baseDocument;
            this.compareDocument = compareDocument;
            this.basePageNum = basePageNum;
            this.comparePageNum = comparePageNum;
            this.key = key;
        }
    }

    /**
     * ComparisonResult class for page comparison results
     */
    private static class ComparisonResult {
        final String key;
        final double similarity;

        ComparisonResult(String key, double similarity) {
            this.key = key;
            this.similarity = similarity;
        }
    }

    /**
     * Constructor with qualifiers to specify which beans to use.
     *
     * @param ssimCalculator The optimized SSIM calculator for image comparison
     * @param pdfRenderingService The PDF rendering service
     * @param executorService The executor service for comparison operations
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

    @Value("${app.matching.max-comparison-distance:10}")
    private int maxComparisonDistance = 10;

    @Value("${app.matching.batch-size:20}")
    private int batchSize = 20;

    @Value("${app.matching.timeout-minutes:10}")
    private int timeoutMinutes = 10;

    @Value("${app.matching.use-progressive-matching:true}")
    private boolean useProgressiveMatching = true;

    @Value("${app.matching.max-concurrent-comparisons:8}")
    private int maxConcurrentComparisons;

    @Value("${app.matching.retry-count:3}")
    private int retryCount;

    @Value("${app.matching.retry-delay-ms:50}")
    private int retryDelayMs;

    @Value("${app.matching.image-scale-factor:0.5}")
    private float imageScaleFactor = 0.5f;

    @Override
    public List<PagePair> matchPages(PdfDocument baseDocument, PdfDocument compareDocument) throws IOException {
        log.info("Starting visual matching between documents: {} and {}",
                baseDocument.getFileId(), compareDocument.getFileId());

        long startTime = System.currentTimeMillis();

        try {
            // Pre-render some pages in parallel for both documents
            CompletableFuture<Void> preRenderingFuture = preRenderSomePages(baseDocument, compareDocument);

            // Calculate similarity scores for page pairs
            Map<String, Double> similarityScores;

            if (useProgressiveMatching) {
                similarityScores = calculateSimilarityScoresProgressively(baseDocument, compareDocument);
            } else {
                similarityScores = calculateSimilarityScores(baseDocument, compareDocument);
            }

            // Wait for pre-rendering to complete if it hasn't already (with a timeout)
            try {
                preRenderingFuture.get(30, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("Timeout waiting for page pre-rendering. Continuing with partial results.");
            }

            // Match pages using the Hungarian algorithm
            List<PagePair> pagePairs = matchPagesUsingHungarian(baseDocument, compareDocument, similarityScores);

            long endTime = System.currentTimeMillis();
            log.info("Completed visual matching between documents: {} and {} in {}ms",
                    baseDocument.getFileId(), compareDocument.getFileId(), (endTime - startTime));

            // Clear the image cache after matching to free memory
            clearImageCache();

            return pagePairs;
        } catch (Exception e) {
            log.error("Error during visual matching: {}", e.getMessage(), e);
            throw new IOException("Visual matching failed", e);
        }
    }

    /**
     * Match pages using the Hungarian algorithm.
     *
     * @param baseDocument The base document
     * @param compareDocument The compare document
     * @param similarityScores The similarity scores for all page pairs
     * @return A list of page pairs
     */
    private List<PagePair> matchPagesUsingHungarian(
            PdfDocument baseDocument, PdfDocument compareDocument,
            Map<String, Double> similarityScores) {

        int basePageCount = baseDocument.getPageCount();
        int comparePageCount = compareDocument.getPageCount();

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

    private static class HungarianAlgorithm {
        private final double[][] costMatrix;
        private final int rows, cols;
        private final int[] colAssignment;
        private final int[] rowAssignment;
        private final double[] rowCover;
        private final double[] colCover;

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
        }

        /**
         * Execute the Hungarian algorithm.
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

            // Step 1: Subtract the smallest value in each row from all values in that row
            for (int i = 0; i < rows; i++) {
                double minValue = Double.MAX_VALUE;
                for (int j = 0; j < cols; j++) {
                    minValue = Math.min(minValue, costMatrix[i][j]);
                }
                for (int j = 0; j < cols; j++) {
                    costMatrix[i][j] -= minValue;
                }
            }

            // Step 2: Subtract the smallest value in each column from all values in that column
            for (int j = 0; j < cols; j++) {
                double minValue = Double.MAX_VALUE;
                for (int i = 0; i < rows; i++) {
                    minValue = Math.min(minValue, costMatrix[i][j]);
                }
                for (int i = 0; i < rows; i++) {
                    costMatrix[i][j] -= minValue;
                }
            }

            // Step 3: Cover all zeros with a minimum number of lines
            int[] rowsCovered = new int[rows];
            int[] colsCovered = new int[cols];
            int numLines = 0;

            // Find a maximal matching
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < cols; j++) {
                    if (costMatrix[i][j] == 0 && rowsCovered[i] == 0 && colsCovered[j] == 0) {
                        rowsCovered[i] = 1;
                        colsCovered[j] = 1;
                        colAssignment[i] = j;
                        rowAssignment[j] = i;
                        numLines++;
                        break;
                    }
                }
            }

            // If the number of lines equals the number of rows, we're done
            if (numLines == Math.min(rows, cols)) {
                return colAssignment;
            }

            // Step 4: Create additional zeros
            // Find the minimum uncovered value
            double minUncovered = Double.MAX_VALUE;
            for (int i = 0; i < rows; i++) {
                if (rowsCovered[i] == 0) {
                    for (int j = 0; j < cols; j++) {
                        if (colsCovered[j] == 0) {
                            minUncovered = Math.min(minUncovered, costMatrix[i][j]);
                        }
                    }
                }
            }

            // Subtract the minimum uncovered value from all uncovered elements
            // and add it to all elements covered by two lines
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < cols; j++) {
                    if (rowsCovered[i] == 0 && colsCovered[j] == 0) {
                        costMatrix[i][j] -= minUncovered;
                    } else if (rowsCovered[i] == 1 && colsCovered[j] == 1) {
                        costMatrix[i][j] += minUncovered;
                    }
                }
            }

            // Recursively call the algorithm
            return execute();
        }
    }

    /**
     * Pre-render some pages of both documents (first few pages and some samples).
     *
     * @param baseDocument The base document
     * @param compareDocument The compare document
     * @return A CompletableFuture representing the pre-rendering task
     */
    private CompletableFuture<Void> preRenderSomePages(PdfDocument baseDocument, PdfDocument compareDocument) {
        // Submit tasks to pre-render strategically important pages
        CompletableFuture<Void> baseRenderingFuture = CompletableFuture.runAsync(() -> {
            try {
                // Render first few pages and some samples throughout the document
                preRenderKeyPages(baseDocument);
            } catch (IOException e) {
                log.error("Error pre-rendering base document: {}", e.getMessage(), e);
            }
        }, executorService);

        CompletableFuture<Void> compareRenderingFuture = CompletableFuture.runAsync(() -> {
            try {
                // Render first few pages and some samples throughout the document
                preRenderKeyPages(compareDocument);
            } catch (IOException e) {
                log.error("Error pre-rendering compare document: {}", e.getMessage(), e);
            }
        }, executorService);

        // Combine both futures
        return CompletableFuture.allOf(baseRenderingFuture, compareRenderingFuture);
    }

    /**
     * Pre-render key pages of a document (first few pages and some samples).
     *
     * @param document The document
     * @throws IOException If there is an error rendering the pages
     */
    private void preRenderKeyPages(PdfDocument document) throws IOException {
        int pageCount = document.getPageCount();
        Set<Integer> pagesToRender = new HashSet<>();

        // Always render first 3 pages
        for (int i = 1; i <= Math.min(3, pageCount); i++) {
            pagesToRender.add(i);
        }

        // Add a few sample pages throughout the document
        if (pageCount > 10) {
            pagesToRender.add(pageCount / 4);
            pagesToRender.add(pageCount / 2);
            pagesToRender.add(3 * pageCount / 4);
            pagesToRender.add(pageCount); // Last page
        }

        // Render the selected pages
        List<CompletableFuture<Void>> renderTasks = new ArrayList<>();
        for (int pageNumber : pagesToRender) {
            CompletableFuture<Void> task = CompletableFuture.runAsync(() -> {
                try {
                    pdfRenderingService.renderPage(document, pageNumber);
                } catch (Exception e) {
                    log.warn("Error pre-rendering page {} of document {}: {}",
                            pageNumber, document.getFileId(), e.getMessage());
                }
            }, executorService);
            renderTasks.add(task);
        }

        // Wait for all tasks to complete
        try {
            CompletableFuture.allOf(renderTasks.toArray(new CompletableFuture[0]))
                    .get(1, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("Timeout or error waiting for page pre-rendering: {}", e.getMessage());
        }
    }

    /**
     * Calculate similarity scores for all page pairs using a progressive approach
     * that first tries matching pages with same numbers, then expands to nearby pages.
     *
     * @param baseDocument The base document
     * @param compareDocument The compare document
     * @return A map of page pair keys to similarity scores
     */
    private Map<String, Double> calculateSimilarityScoresProgressively(
            PdfDocument baseDocument, PdfDocument compareDocument) {

        Map<String, Double> similarityScores = new ConcurrentHashMap<>();

        // First, try matching pages with the same page numbers
        Map<String, Double> samePageMatches = calculateSamePageSimilarities(baseDocument, compareDocument);
        similarityScores.putAll(samePageMatches);

        // Identify pages that need further matching (similarity below threshold)
        Set<Integer> unmatchedBasePages = new HashSet<>();
        Set<Integer> unmatchedComparePages = new HashSet<>();

        for (int i = 1; i <= baseDocument.getPageCount(); i++) {
            String key = createKey(baseDocument.getFileId(), i, compareDocument.getFileId(), i);
            if (!similarityScores.containsKey(key) ||
                    similarityScores.get(key) < visualSimilarityThreshold) {
                unmatchedBasePages.add(i);
            }
        }

        for (int i = 1; i <= compareDocument.getPageCount(); i++) {
            String key = createKey(baseDocument.getFileId(), i, compareDocument.getFileId(), i);
            if (!similarityScores.containsKey(key) ||
                    similarityScores.get(key) < visualSimilarityThreshold) {
                unmatchedComparePages.add(i);
            }
        }

        log.info("After same-page matching: {} unmatched base pages, {} unmatched compare pages",
                unmatchedBasePages.size(), unmatchedComparePages.size());

        // For unmatched pages, try nearby pages first
        if (!unmatchedBasePages.isEmpty() && !unmatchedComparePages.isEmpty()) {
            Map<String, Double> nearbyMatches = calculateNearbyPageSimilarities(
                    baseDocument, compareDocument, unmatchedBasePages, unmatchedComparePages);
            similarityScores.putAll(nearbyMatches);
        }

        // If we still have a low match rate, try some distant matches as a fallback
        if (unmatchedBasePages.size() > baseDocument.getPageCount() * 0.3 ||
                unmatchedComparePages.size() > compareDocument.getPageCount() * 0.3) {

            log.info("Using fallback distant matching for remaining unmatched pages");
            Map<String, Double> distantMatches = calculateDistantPageSimilarities(
                    baseDocument, compareDocument, unmatchedBasePages, unmatchedComparePages);
            similarityScores.putAll(distantMatches);
        }

        return similarityScores;
    }

    /**
     * Calculate similarity scores for pages with the same page number.
     *
     * @param baseDocument The base document
     * @param compareDocument The compare document
     * @return A map of page pair keys to similarity scores
     */
    private Map<String, Double> calculateSamePageSimilarities(
            PdfDocument baseDocument, PdfDocument compareDocument) {

        Map<String, Double> matches = new ConcurrentHashMap<>();

        int minPages = Math.min(baseDocument.getPageCount(), compareDocument.getPageCount());

        // Use a semaphore to limit concurrent operations
        Semaphore semaphore = new Semaphore(maxConcurrentComparisons);

        // Track progress
        AtomicInteger completedComparisons = new AtomicInteger(0);

        List<CompletableFuture<Void>> tasks = new ArrayList<>();
        for (int pageNum = 1; pageNum <= minPages; pageNum++) {
            final int pageNumber = pageNum;

            tasks.add(CompletableFuture.runAsync(() -> {
                try {
                    // Acquire permit
                    semaphore.acquire();

                    try {
                        String key = createKey(baseDocument.getFileId(), pageNumber,
                                compareDocument.getFileId(), pageNumber);

                        // Check cache first
                        if (similarityCache.containsKey(key)) {
                            matches.put(key, similarityCache.get(key));
                        } else {
                            double similarity = calculateSimilarityWithRetry(
                                    baseDocument, compareDocument, pageNumber, pageNumber);

                            matches.put(key, similarity);
                            similarityCache.put(key, similarity);
                        }

                        // Log progress
                        int completed = completedComparisons.incrementAndGet();
                        if (completed % 10 == 0 || completed == minPages) {
                            log.info("Same-page comparison progress: {}/{}", completed, minPages);
                        }
                    } finally {
                        // Always release the permit
                        semaphore.release();
                    }
                } catch (Exception e) {
                    log.error("Error in same-page matching for page {}: {}", pageNumber, e.getMessage());
                }
            }, executorService));
        }

        // Wait for all tasks to complete
        try {
            CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).get(
                    timeoutMinutes, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("Timeout or error in same-page matching: {}", e.getMessage());
        }

        return matches;
    }

    /**
     * Calculate similarity scores for nearby pages.
     *
     * @param baseDocument The base document
     * @param compareDocument The compare document
     * @param unmatchedBasePages Set of unmatched base page numbers
     * @param unmatchedComparePages Set of unmatched compare page numbers
     * @return A map of page pair keys to similarity scores
     */
    private Map<String, Double> calculateNearbyPageSimilarities(
            PdfDocument baseDocument, PdfDocument compareDocument,
            Set<Integer> unmatchedBasePages, Set<Integer> unmatchedComparePages) {

        Map<String, Double> matches = new ConcurrentHashMap<>();

        // Use a semaphore to limit concurrent operations
        Semaphore semaphore = new Semaphore(maxConcurrentComparisons);

        // Priority queue for comparison tasks, prioritizing smaller page distance
        PriorityQueue<ComparisonTask> comparisonQueue = new PriorityQueue<>(
                (t1, t2) -> Integer.compare(
                        Math.abs(t1.basePageNum - t1.comparePageNum),
                        Math.abs(t2.basePageNum - t2.comparePageNum)
                ));

        // Add tasks for nearby pages
        for (int basePageNum : unmatchedBasePages) {
            for (int comparePageNum : unmatchedComparePages) {
                // Skip if pages are too far apart
                if (Math.abs(basePageNum - comparePageNum) > maxComparisonDistance) {
                    continue;
                }

                String key = createKey(baseDocument.getFileId(), basePageNum,
                        compareDocument.getFileId(), comparePageNum);

                // Skip if already in cache with high similarity
                if (similarityCache.containsKey(key) &&
                        similarityCache.get(key) >= visualSimilarityThreshold) {
                    matches.put(key, similarityCache.get(key));
                    continue;
                }

                comparisonQueue.add(new ComparisonTask(
                        baseDocument, compareDocument, basePageNum, comparePageNum, key));
            }
        }

        // Track progress
        int totalTasks = comparisonQueue.size();
        if (totalTasks == 0) {
            return matches;
        }

        AtomicInteger completedTasks = new AtomicInteger(0);

        log.info("Starting nearby page matching with {} comparison tasks", totalTasks);

        // Process tasks in priority order with controlled concurrency
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        ExecutorCompletionService<ComparisonResult> completionService =
                new ExecutorCompletionService<>(executorService);

        // Submit initial batch of tasks
        int initialBatchSize = Math.min(maxConcurrentComparisons, comparisonQueue.size());
        for (int i = 0; i < initialBatchSize; i++) {
            ComparisonTask task = comparisonQueue.poll();
            if (task == null) break;

            completionService.submit(() -> {
                try {
                    semaphore.acquire();
                    try {
                        double similarity = calculateSimilarityWithRetry(
                                task.baseDocument, task.compareDocument,
                                task.basePageNum, task.comparePageNum);
                        return new ComparisonResult(task.key, similarity);
                    } finally {
                        semaphore.release();
                    }
                } catch (Exception e) {
                    log.error("Error calculating similarity: {}", e.getMessage());
                    return new ComparisonResult(task.key, 0.0);
                }
            });
        }

        // Process results and submit new tasks
        long startTime = System.currentTimeMillis();
        long timeout = TimeUnit.MINUTES.toMillis(timeoutMinutes);

        for (int i = 0; i < Math.min(totalTasks, initialBatchSize * 4); i++) {
            try {
                // Check timeout
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed > timeout) {
                    log.warn("Timeout reached in nearby page matching after {} tasks",
                            completedTasks.get());
                    break;
                }

                // Get next result with timeout
                Future<ComparisonResult> resultFuture = completionService.poll(
                        10, TimeUnit.SECONDS);

                if (resultFuture == null) {
                    log.warn("No result available within timeout, continuing");
                    continue;
                }

                ComparisonResult result = resultFuture.get();

                // Process result
                matches.put(result.key, result.similarity);
                similarityCache.put(result.key, result.similarity);

                int completed = completedTasks.incrementAndGet();

                // Log progress periodically
                if (completed % 10 == 0 || completed == totalTasks) {
                    log.info("Nearby page comparison progress: {}/{}", completed, totalTasks);
                }

                // Submit next task if available
                ComparisonTask nextTask = comparisonQueue.poll();
                if (nextTask != null) {
                    completionService.submit(() -> {
                        try {
                            semaphore.acquire();
                            try {
                                double similarity = calculateSimilarityWithRetry(
                                        nextTask.baseDocument, nextTask.compareDocument,
                                        nextTask.basePageNum, nextTask.comparePageNum);
                                return new ComparisonResult(nextTask.key, similarity);
                            } finally {
                                semaphore.release();
                            }
                        } catch (Exception e) {
                            log.error("Error calculating similarity: {}", e.getMessage());
                            return new ComparisonResult(nextTask.key, 0.0);
                        }
                    });
                }
            } catch (Exception e) {
                log.error("Error processing comparison result: {}", e.getMessage());
            }
        }

        log.info("Completed nearby page matching with {} comparisons", completedTasks.get());

        return matches;
    }

    /**
     * Calculate similarity scores for a sampling of distant pages as a fallback.
     *
     * @param baseDocument The base document
     * @param compareDocument The compare document
     * @param unmatchedBasePages Set of unmatched base page numbers
     * @param unmatchedComparePages Set of unmatched compare page numbers
     * @return A map of page pair keys to similarity scores
     */
    private Map<String, Double> calculateDistantPageSimilarities(
            PdfDocument baseDocument, PdfDocument compareDocument,
            Set<Integer> unmatchedBasePages, Set<Integer> unmatchedComparePages) {

        Map<String, Double> matches = new ConcurrentHashMap<>();

        // If there are too many unmatched pages, sample a subset to reduce processing time
        List<Integer> basePages = new ArrayList<>(unmatchedBasePages);
        List<Integer> comparePages = new ArrayList<>(unmatchedComparePages);

        // Limit the number of comparisons to avoid excessive processing
        int maxComparisons = 100;

        // Use shuffle and sampling to get a random subset of comparisons
        Collections.shuffle(basePages);
        Collections.shuffle(comparePages);

        List<CompletableFuture<Void>> tasks = new ArrayList<>();
        int comparisonsAdded = 0;

        // Semaphore for controlled concurrency
        Semaphore semaphore = new Semaphore(maxConcurrentComparisons);

        // Generate tasks for a sampling of distant page comparisons
        for (int i = 0; i < Math.min(basePages.size(), 10); i++) {
            for (int j = 0; j < Math.min(comparePages.size(), 10); j++) {
                int basePageNum = basePages.get(i);
                int comparePageNum = comparePages.get(j);

                // Skip if pages are too close (already checked in nearby matching)
                if (Math.abs(basePageNum - comparePageNum) <= maxComparisonDistance) {
                    continue;
                }

                String key = createKey(baseDocument.getFileId(), basePageNum,
                        compareDocument.getFileId(), comparePageNum);

                // Skip if already in cache
                if (similarityCache.containsKey(key)) {
                    matches.put(key, similarityCache.get(key));
                    continue;
                }

                final int finalBasePageNum = basePageNum;
                final int finalComparePageNum = comparePageNum;
                final String finalKey = key;

                tasks.add(CompletableFuture.runAsync(() -> {
                    try {
                        semaphore.acquire();
                        try {
                            double similarity = calculateSimilarityWithRetry(
                                    baseDocument, compareDocument, finalBasePageNum, finalComparePageNum);

                            matches.put(finalKey, similarity);
                            similarityCache.put(finalKey, similarity);
                        } finally {
                            semaphore.release();
                        }
                    } catch (Exception e) {
                        log.error("Error in distant page matching: {}", e.getMessage());
                    }
                }, executorService));

                comparisonsAdded++;
                if (comparisonsAdded >= maxComparisons) {
                    break;
                }
            }

            if (comparisonsAdded >= maxComparisons) {
                break;
            }
        }

        log.info("Added {} distant page comparison tasks", tasks.size());

        // Wait for all tasks with a timeout
        try {
            CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0]))
                    .get(timeoutMinutes / 2, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("Timeout or error in distant page matching: {}", e.getMessage());
        }

        return matches;
    }

    /**
     * Calculate similarity scores for all possible page pairs (original approach).
     * This can be very slow for large documents.
     *
     * @param baseDocument The base document
     * @param compareDocument The compare document
     * @return A map of page pair keys to similarity scores
     */
    private Map<String, Double> calculateSimilarityScores(PdfDocument baseDocument, PdfDocument compareDocument) {
        Map<String, Double> similarityScores = new ConcurrentHashMap<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // Use a semaphore to limit concurrent comparisons
        Semaphore semaphore = new Semaphore(maxConcurrentComparisons);

        // Track progress for logging
        AtomicInteger completedComparisons = new AtomicInteger(0);
        int totalComparisons = baseDocument.getPageCount() * compareDocument.getPageCount();

        // For each page in the base document
        for (int basePageNumber = 1; basePageNumber <= baseDocument.getPageCount(); basePageNumber++) {
            final int basePageNum = basePageNumber;

            // For each page in the compare document
            for (int comparePageNumber = 1; comparePageNumber <= compareDocument.getPageCount(); comparePageNumber++) {
                final int comparePageNum = comparePageNumber;

                // Skip pages that are too far apart for large documents (optimization)
                if (baseDocument.getPageCount() > 30 && compareDocument.getPageCount() > 30) {
                    if (Math.abs(basePageNum - comparePageNum) > maxComparisonDistance) {
                        completedComparisons.incrementAndGet();
                        continue;
                    }
                }

                // Create a key for this page pair
                String key = createKey(baseDocument.getFileId(), basePageNum,
                        compareDocument.getFileId(), comparePageNum);

                // Check if we already have a similarity score for this page pair
                if (similarityCache.containsKey(key)) {
                    similarityScores.put(key, similarityCache.get(key));
                    completedComparisons.incrementAndGet();
                    continue;
                }

                // Submit a task to calculate the similarity score
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        // Acquire permit from semaphore to limit concurrent operations
                        semaphore.acquire();

                        try {
                            double similarity = calculateSimilarityWithRetry(baseDocument, compareDocument, basePageNum, comparePageNum);

                            // Cache the score
                            similarityCache.put(key, similarity);
                            similarityScores.put(key, similarity);

                            // Log progress periodically
                            int completed = completedComparisons.incrementAndGet();
                            if (completed % 10 == 0 || completed == totalComparisons) {
                                log.info("Comparison progress: {}/{} ({} %)",
                                        completed, totalComparisons, (completed * 100) / totalComparisons);
                            }
                        } finally {
                            // Always release the permit
                            semaphore.release();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.error("Thread interrupted while waiting for semaphore", e);
                    } catch (Exception e) {
                        log.error("Error calculating similarity for pages {} and {}: {}",
                                basePageNum, comparePageNum, e.getMessage());
                        // Use a low similarity score as fallback
                        similarityScores.put(key, 0.0);
                        completedComparisons.incrementAndGet();
                    }
                }, executorService);

                futures.add(future);
            }
        }

        // Wait for all tasks to complete or timeout
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(timeoutMinutes, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("Timeout or error waiting for similarity calculations: {}", e.getMessage());
            // Continue with partial results rather than failing completely
        }

        return similarityScores;
    }

    /**
     * Calculate similarity with retry mechanism and performance optimizations.
     *
     * @param baseDocument The base document
     * @param compareDocument The compare document
     * @param basePageNum The base page number
     * @param comparePageNum The compare page number
     * @return The similarity score
     * @throws IOException If all retry attempts fail
     */
    private double calculateSimilarityWithRetry(
            PdfDocument baseDocument, PdfDocument compareDocument,
            int basePageNum, int comparePageNum) throws IOException {

        IOException lastException = null;

        for (int attempt = 0; attempt < retryCount; attempt++) {
            try {
                // Get the rendered page images
                BufferedImage baseImage = getPageImage(baseDocument, basePageNum);
                BufferedImage compareImage = getPageImage(compareDocument, comparePageNum);

                // Check if images were loaded successfully
                if (baseImage == null) {
                    throw new IOException("Failed to load base image for page " + basePageNum);
                }

                if (compareImage == null) {
                    throw new IOException("Failed to load compare image for page " + comparePageNum);
                }

                // Calculate the similarity score with optimized SSIM calculator
                return ssimCalculator.calculate(baseImage, compareImage);
            } catch (IOException e) {
                lastException = e;
                log.warn("Attempt {} failed for pages {} and {}: {}",
                        attempt + 1, basePageNum, comparePageNum, e.getMessage());

                if (attempt < retryCount - 1) {
                    // Sleep before retrying with exponential backoff
                    try {
                        Thread.sleep(retryDelayMs * (1 << attempt));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Thread interrupted during retry delay", ie);
                    }
                }
            }
        }

        // All retries failed
        throw new IOException("Failed to calculate similarity after " + retryCount + " attempts", lastException);
    }

    /**
     * Get the rendered page image with caching and optimized memory usage.
     *
     * @param document   The document
     * @param pageNumber The page number
     * @return The rendered page image
     * @throws IOException If there is an error loading the image
     */
    private BufferedImage getPageImage(PdfDocument document, int pageNumber) throws IOException {
        String cacheKey = document.getFileId() + "_" + pageNumber;

        // Check if the image is already in the cache and not garbage collected
        SoftReference<BufferedImage> cachedRef = imageCache.get(cacheKey);
        if (cachedRef != null) {
            BufferedImage cachedImage = cachedRef.get();
            if (cachedImage != null) {
                return cachedImage;
            } else {
                // Reference was cleared by GC, remove from cache
                imageCache.remove(cacheKey);
            }
        }

        // Get the rendered page
        File pageFile = pdfRenderingService.renderPage(document, pageNumber);

        // Ensure file exists and is readable
        if (!pageFile.exists() || !pageFile.canRead()) {
            throw new IOException("Page file does not exist or is not readable: " + pageFile.getPath());
        }

        // Check file size to avoid empty files
        if (pageFile.length() == 0) {
            throw new IOException("Page file is empty: " + pageFile.getPath());
        }

        // Load and scale the image for faster processing
        BufferedImage fullImage = ImageIO.read(pageFile);

        // Check if the image was loaded successfully
        if (fullImage == null) {
            throw new IOException("Failed to load image: " + pageFile.getPath());
        }

        // Scale down the image for faster comparison if needed
        BufferedImage scaledImage;
        if (imageScaleFactor < 0.99f) {
            scaledImage = scaleImage(fullImage, imageScaleFactor);
        } else {
            scaledImage = fullImage;
        }

        // Cache the scaled image with a SoftReference to allow GC when memory is low
        imageCache.put(cacheKey, new SoftReference<>(scaledImage));

        return scaledImage;
    }

    /**
     * Scale an image to reduce processing time and memory usage.
     *
     * @param image       The original image
     * @param scaleFactor The scale factor (0.0-1.0)
     * @return The scaled image
     */
    private BufferedImage scaleImage(BufferedImage image, float scaleFactor) {
        int scaledWidth = Math.max(1, (int) (image.getWidth() * scaleFactor));
        int scaledHeight = Math.max(1, (int) (image.getHeight() * scaleFactor));

        BufferedImage scaledImage = new BufferedImage(scaledWidth, scaledHeight, image.getType());
        Graphics2D g = scaledImage.createGraphics();

        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g.drawImage(image, 0, 0, scaledWidth, scaledHeight, null);
            return scaledImage;
        } finally {
            g.dispose();
        }
    }

    /**
     * Clear the image cache to free memory.
     */
    private void clearImageCache() {
        imageCache.clear();
        log.info("Cleared image cache to free memory");
    }

    /**
     * Helper method to create a key for similarity cache.
     */
    private String createKey(String baseDocId, int basePageNum, String compareDocId, int comparePageNum) {
        return baseDocId + "_" + basePageNum + "_" + compareDocId + "_" + comparePageNum;
    }
}