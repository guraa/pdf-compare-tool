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
    private static final int MAX_CONCURRENT_PAGE_COMPARISONS = 4;

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
     * Compare two PDF documents.
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
     * Compare pages between two documents with reliable completion.
     */
    private Map<String, List<Difference>> comparePages(
            PdfDocument baseDocument, PdfDocument compareDocument, List<PagePair> pagePairs) throws IOException {

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

        // Determine if we should use parallel or sequential processing based on configuration
        if (parallelPageProcessing && totalPairs > 1) {
            // Parallel processing approach
            List<CompletableFuture<Void>> pageFutures = new ArrayList<>();

            for (PagePair pagePair : matchedPairs) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        processSinglePagePair(baseDocument, compareDocument, pagePair, differencesByPage);
                        int completed = processedPairs.incrementAndGet();
                        log.info(logPrefix + "Completed page pair {}/{} - Base: {}, Compare: {}",
                                completed, totalPairs, pagePair.getBasePageNumber(), pagePair.getComparePageNumber());
                    } catch (Exception e) {
                        log.error(logPrefix + "Error processing page pair: Base={}, Compare={}: {}",
                                pagePair.getBasePageNumber(), pagePair.getComparePageNumber(), e.getMessage());
                        // Ensure we count this as processed even if it fails
                        processedPairs.incrementAndGet();
                    }
                }, executorService);

                pageFutures.add(future);
            }

            // Wait for all page processing to complete with a timeout
            try {
                CompletableFuture<Void> allFutures = CompletableFuture.allOf(pageFutures.toArray(new CompletableFuture[0]));

                // Add a timeout to prevent hanging
                try {
                    allFutures.get(pageTimeoutMinutes, TimeUnit.MINUTES);
                    log.info(logPrefix + "All page futures completed successfully");
                } catch (TimeoutException e) {
                    log.warn(logPrefix + "Timeout waiting for page comparisons to complete. Processed {}/{} pages.",
                            processedPairs.get(), totalPairs);

                    // Cancel any unfinished futures
                    for (CompletableFuture<Void> future : pageFutures) {
                        if (!future.isDone()) {
                            future.cancel(true);
                        }
                    }
                } catch (Exception e) {
                    log.error(logPrefix + "Error waiting for page comparisons: {}", e.getMessage(), e);
                }
            } catch (Exception e) {
                log.error(logPrefix + "Error processing page comparisons: {}", e.getMessage(), e);
            }

            log.info(logPrefix + "Completed {}/{} page comparisons", processedPairs.get(), totalPairs);
        } else {
            // Sequential processing approach - more reliable but potentially slower
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
     * Process a single page pair, finding all differences.
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

        // Find text differences
        try {
            List<TextDifference> textDifferences = textComparisonService.compareText(
                    baseDocument, compareDocument, basePageNum, comparePageNum);

            if (textDifferences != null) {
                allDifferences.addAll(textDifferences);
                log.info(logPrefix + "Found {} text differences for page pair {}/{}",
                        textDifferences.size(), basePageNum, comparePageNum);
            }
        } catch (Exception e) {
            log.error(logPrefix + "Error comparing text for page pair {}/{}: {}",
                    basePageNum, comparePageNum, e.getMessage());
        }

        // Find image differences - add more logging and error handling
        try {
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