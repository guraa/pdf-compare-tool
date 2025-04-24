package guraa.pdfcompare;

import guraa.pdfcompare.core.DocumentMatchingStrategy;
import guraa.pdfcompare.core.SmartDocumentMatcher;
import guraa.pdfcompare.model.ComparisonResult;
import guraa.pdfcompare.model.PdfDocument;
import guraa.pdfcompare.model.difference.Difference;
import guraa.pdfcompare.model.difference.ImageDifference;
import guraa.pdfcompare.model.difference.TextDifference;
import guraa.pdfcompare.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Main engine for PDF comparison with improved completion handling.
 */
@Slf4j
@Service
public class PDFComparisonEngine {

    private final SmartDocumentMatcher documentMatcher;
    private final TextElementComparisonService textComparisonService;
    private final ImageComparisonService imageComparisonService;
    private final FontComparisonService fontComparisonService;
    private final ExecutorService executorService;

    // Maximum number of concurrent page comparisons
    private final int MAX_CONCURRENT_PAGE_COMPARISONS;

    // Cache for comparison results to avoid redundant comparisons
    private final Map<String, ComparisonResult> comparisonCache = new ConcurrentHashMap<>();

    // Maximum cache size
    private static final int MAX_CACHE_SIZE = 10;

    /**
     * Constructor with qualifier to specify which executor service to use.
     */
    public PDFComparisonEngine(
            SmartDocumentMatcher documentMatcher,
            TextElementComparisonService textComparisonService,
            ImageComparisonService imageComparisonService,
            FontComparisonService fontComparisonService,
            @Qualifier("comparisonExecutor") ExecutorService executorService) {
        this.documentMatcher = documentMatcher;
        this.textComparisonService = textComparisonService;
        this.imageComparisonService = imageComparisonService;
        this.fontComparisonService = fontComparisonService;
        this.executorService = executorService;

        // Set max concurrent operations based on available processors
        this.MAX_CONCURRENT_PAGE_COMPARISONS = Math.min(4, Runtime.getRuntime().availableProcessors());
    }

    @Value("${app.comparison.smart-matching-enabled:true}")
    private boolean smartMatchingEnabled;

    @Value("${app.comparison.cache-enabled:true}")
    private boolean cacheEnabled;

    @Value("${app.comparison.batch-size:4}")
    private int batchSize;

    @Value("${app.comparison.parallel-page-processing:true}")
    private boolean parallelPageProcessing;

    @Value("${app.comparison.page-timeout-minutes:2}")
    private int pageTimeoutMinutes = 2;

    /**
     * Compare two PDF documents with improved completion handling.
     *
     * @param baseDocument    The base document
     * @param compareDocument The document to compare against the base
     * @return The comparison result
     * @throws IOException If there is an error comparing the documents
     */
    public ComparisonResult compareDocuments(PdfDocument baseDocument, PdfDocument compareDocument) throws IOException {
        String logPrefix = "[" + baseDocument.getFileId() + " vs " + compareDocument.getFileId() + "] ";
        log.info(logPrefix + "Starting comparison between documents: {} and {}",
                baseDocument.getFileId(), compareDocument.getFileId());

        long startTime = System.currentTimeMillis();

        // Generate a cache key
        String cacheKey = baseDocument.getFileId() + "_" + compareDocument.getFileId();
        log.info(logPrefix + "cacheKey: {}", cacheKey);

        // Check if the result is already in the cache
        if (cacheEnabled && comparisonCache.containsKey(cacheKey)) {
            log.info(logPrefix + "Retrieved comparison result from cache");
            return comparisonCache.get(cacheKey);
        }

        try {
            // Step 1: Match pages between documents
            log.info(logPrefix + "Starting document matching phase");
            List<PagePair> pagePairs = matchDocuments(baseDocument, compareDocument);
            log.info(logPrefix + "Document matching phase completed, found {} page pairs", pagePairs.size());

            // Step 2: Create a comparison summary
            log.info(logPrefix + "Creating comparison summary");
            PageLevelComparisonSummary summary = createComparisonSummary(baseDocument, compareDocument, pagePairs);
            log.info(logPrefix + "Comparison summary created with similarity score: {}", summary.getOverallSimilarityScore());

            // Step 3: Compare matched pages
            Map<String, List<Difference>> differencesByPage;
            try {
                log.info(logPrefix + "Starting page comparison phase");
                differencesByPage = comparePages(baseDocument, compareDocument, pagePairs);
                log.info(logPrefix + "Page comparison phase completed, found differences on {} pages",
                        differencesByPage.size());
            } catch (Exception e) {
                log.error(logPrefix + "Error in page comparison phase: {}", e.getMessage(), e);
                // Continue with empty differences rather than failing the entire comparison
                differencesByPage = new HashMap<>();
            }

            // Step 4: Create the comparison result
            String resultId = UUID.randomUUID().toString();
            log.info(logPrefix + "Building final comparison result with ID: {}", resultId);
            ComparisonResult result = ComparisonResult.builder()
                    .id(resultId)
                    .baseDocumentId(baseDocument.getFileId())
                    .compareDocumentId(compareDocument.getFileId())
                    .pagePairs(pagePairs)
                    .summary(summary)
                    .differencesByPage(differencesByPage)
                    .build();

            // Cache the result if caching is enabled
            if (cacheEnabled) {
                // Evict oldest entry if cache is full
                if (comparisonCache.size() >= MAX_CACHE_SIZE) {
                    evictOldestCacheEntry();
                }
                log.info(logPrefix + "Storing comparison result in cache");
                comparisonCache.put(cacheKey, result);
            }

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            log.info(logPrefix + "Completed comparison between documents in {}ms", duration);

            return result;
        } catch (Exception e) {
            log.error(logPrefix + "Error during document comparison: {}", e.getMessage(), e);
            throw new IOException("Document comparison failed", e);
        }
    }

    /**
     * Evict the oldest entry from the comparison cache.
     */
    private void evictOldestCacheEntry() {
        if (comparisonCache.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<String, ComparisonResult>> iterator = comparisonCache.entrySet().iterator();
        if (iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    /**
     * Match pages between two documents.
     */
    private List<PagePair> matchDocuments(PdfDocument baseDocument, PdfDocument compareDocument) throws IOException {
        DocumentMatchingStrategy matcher = documentMatcher;
        Map<String, Object> options = new HashMap<>();
        options.put("parallelProcessing", true);
        options.put("batchSize", batchSize);

        return matcher.matchDocuments(baseDocument, compareDocument, options);
    }

    /**
     * Create a comparison summary.
     */
    private PageLevelComparisonSummary createComparisonSummary(
            PdfDocument baseDocument, PdfDocument compareDocument, List<PagePair> pagePairs) {

        PageLevelComparisonSummary summary = PageLevelComparisonSummary.builder()
                .id(UUID.randomUUID().toString())
                .baseDocumentId(baseDocument.getFileId())
                .compareDocumentId(compareDocument.getFileId())
                .baseTotalPages(baseDocument.getPageCount())
                .compareTotalPages(compareDocument.getPageCount())
                .matchingStrategy(documentMatcher.getStrategyName())
                .confidenceLevel(documentMatcher.getConfidenceLevel())
                .build();

        // Add page pairs to the summary
        for (PagePair pagePair : pagePairs) {
            summary.addPagePair(pagePair);
        }

        // Calculate the overall similarity score
        summary.calculateOverallSimilarityScore();

        return summary;
    }

    /**
     * Compare pages between two documents with guaranteed completion.
     * This method uses a Semaphore to limit concurrent comparisons and ensures
     * the process always completes within a reasonable time.
     */
    private Map<String, List<Difference>> comparePages(
            PdfDocument baseDocument, PdfDocument compareDocument, List<PagePair> pagePairs) {

        String logPrefix = "[" + baseDocument.getFileId() + " vs " + compareDocument.getFileId() + "] ";
        Map<String, List<Difference>> differencesByPage = new ConcurrentHashMap<>();

        // Filter matched pages
        List<PagePair> matchedPairs = pagePairs.stream()
                .filter(PagePair::isMatched)
                .collect(Collectors.toList());

        if (matchedPairs.isEmpty()) {
            log.warn(logPrefix + "No matched pages found between documents");
            return differencesByPage;
        }

        int totalPairs = matchedPairs.size();
        AtomicInteger processedPairs = new AtomicInteger(0);
        log.info(logPrefix + "Processing {} matched page pairs", totalPairs);

        // Use a Semaphore to limit concurrent comparisons
        Semaphore semaphore = new Semaphore(MAX_CONCURRENT_PAGE_COMPARISONS);

        if (parallelPageProcessing && totalPairs > 1) {
            // Parallel processing with controlled concurrency
            CountDownLatch completionLatch = new CountDownLatch(totalPairs);

            for (PagePair pagePair : matchedPairs) {
                try {
                    // Acquire a permit before submitting the task
                    semaphore.acquire();

                    CompletableFuture.runAsync(() -> {
                        try {
                            processSinglePagePair(baseDocument, compareDocument, pagePair, differencesByPage);
                            int completed = processedPairs.incrementAndGet();
                            log.info(logPrefix + "Completed page pair {}/{} - Base: {}, Compare: {}",
                                    completed, totalPairs, pagePair.getBasePageNumber(), pagePair.getComparePageNumber());
                        } catch (Exception e) {
                            log.error(logPrefix + "Error processing page pair: Base={}, Compare={}: {}",
                                    pagePair.getBasePageNumber(), pagePair.getComparePageNumber(), e.getMessage());
                        } finally {
                            // Always release the permit and count down the latch
                            semaphore.release();
                            completionLatch.countDown();
                        }
                    }, executorService);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error(logPrefix + "Thread interrupted while waiting for semaphore: {}", e.getMessage());
                    completionLatch.countDown();
                }
            }

            // Wait for all comparisons to complete or timeout
            try {
                boolean completed = completionLatch.await(pageTimeoutMinutes, TimeUnit.MINUTES);
                if (!completed) {
                    log.warn(logPrefix + "Page comparison timed out after {} minutes. Processed {}/{} pages.",
                            pageTimeoutMinutes, processedPairs.get(), totalPairs);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error(logPrefix + "Interrupted while waiting for page comparisons to complete: {}", e.getMessage());
            }

            log.info(logPrefix + "Completed {}/{} page comparisons", processedPairs.get(), totalPairs);
        } else {
            // Sequential processing - more reliable but potentially slower
// Sequential processing - more reliable but potentially slower
            for (PagePair pagePair : matchedPairs) {
                try {
                    processSinglePagePair(baseDocument, compareDocument, pagePair, differencesByPage);
                    int completed = processedPairs.incrementAndGet();
                    log.info(logPrefix + "Completed page pair {}/{} - Base: {}, Compare: {}",
                            completed, totalPairs, pagePair.getBasePageNumber(), pagePair.getComparePageNumber());
                } catch (Exception e) {
                    log.error(logPrefix + "Error processing page pair: Base={}, Compare={}: {}",
                            pagePair.getBasePageNumber(), pagePair.getComparePageNumber(), e.getMessage());
                    processedPairs.incrementAndGet();
                }
            }
        }

        log.info(logPrefix + "Completed all page comparisons, found differences for {} pages", differencesByPage.size());
        return differencesByPage;
    }

    /**
     * Process a single page pair, finding all differences with a timeout.
     */
    private void processSinglePagePair(
            PdfDocument baseDocument,
            PdfDocument compareDocument,
            PagePair pagePair,
            Map<String, List<Difference>> differencesByPage) {

        String logPrefix = "[" + baseDocument.getFileId() + " vs " + compareDocument.getFileId() + "] ";
        int basePageNum = pagePair.getBasePageNumber();
        int comparePageNum = pagePair.getComparePageNumber();

        log.info(logPrefix + "Processing page pair: Base={}, Compare={}", basePageNum, comparePageNum);

        List<Difference> allDifferences = new ArrayList<>();

        // Measure the start time to limit overall processing time for this page pair
        long startTime = System.currentTimeMillis();
        long maxTimeMs = pageTimeoutMinutes * 60 * 1000 / 2; // Half the overall timeout

        // Find text differences with timeout control
        try {
            if (System.currentTimeMillis() - startTime < maxTimeMs) {
                log.info(logPrefix + "Finding text differences for page pair {}/{}", basePageNum, comparePageNum);
                List<TextDifference> textDifferences = textComparisonService.compareText(
                        baseDocument, compareDocument, basePageNum, comparePageNum);

                if (textDifferences != null) {
                    allDifferences.addAll(textDifferences);
                    log.info(logPrefix + "Found {} text differences for page pair {}/{}",
                            textDifferences.size(), basePageNum, comparePageNum);
                }
            } else {
                log.warn(logPrefix + "Skipping text comparison due to timeout for page pair {}/{}", basePageNum, comparePageNum);
            }
        } catch (Exception e) {
            log.error(logPrefix + "Error comparing text for page pair {}/{}: {}",
                    basePageNum, comparePageNum, e.getMessage());
        }

        // Find image differences with timeout control
        try {
            if (System.currentTimeMillis() - startTime < maxTimeMs) {
                log.info(logPrefix + "Starting image comparison for page pair {}/{}", basePageNum, comparePageNum);
                List<ImageDifference> imageDifferences = imageComparisonService.compareImages(
                        baseDocument, compareDocument, basePageNum, comparePageNum);

                if (imageDifferences != null) {
                    allDifferences.addAll(imageDifferences);
                    log.info(logPrefix + "Found {} image differences for page pair {}/{}",
                            imageDifferences.size(), basePageNum, comparePageNum);
                } else {
                    log.warn(logPrefix + "Image differences returned null for page pair {}/{}",
                            basePageNum, comparePageNum);
                }
            } else {
                log.warn(logPrefix + "Skipping image comparison due to timeout for page pair {}/{}", basePageNum, comparePageNum);
            }
        } catch (Exception e) {
            log.error(logPrefix + "Error comparing images for page pair {}/{}: {}",
                    basePageNum, comparePageNum, e.getMessage(), e);
            // Continue with processing - don't let image comparison failures block completion
        }

        // Font differences are currently disabled as per original code

        // Store differences if any found
        if (!allDifferences.isEmpty()) {
            differencesByPage.put(pagePair.getId(), allDifferences);
        }

        // Add to page pair
        for (Difference difference : allDifferences) {
            pagePair.addDifference(createPageDifference(difference));
        }

        log.info(logPrefix + "Completed processing page pair {}/{} with {} differences",
                basePageNum, comparePageNum, allDifferences.size());
    }

    /**
     * Create a page difference from a difference.
     */
    private PageDifference createPageDifference(Difference difference) {
        return PageDifference.builder()
                .id(UUID.randomUUID().toString())
                .type(difference.getType())
                .severity(difference.getSeverity())
                .description(difference.getDescription())
                .basePageNumber(difference.getBasePageNumber())
                .comparePageNumber(difference.getComparePageNumber())
                .build();
    }

    /**
     * Clear the comparison cache.
     */
    public void clearCache() {
        comparisonCache.clear();
        log.info("Comparison cache cleared");
    }
}