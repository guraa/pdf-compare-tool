package guraa.pdfcompare;

import guraa.pdfcompare.core.SmartDocumentMatcher;
import guraa.pdfcompare.model.ComparisonResult;
import guraa.pdfcompare.model.PdfDocument;
import guraa.pdfcompare.model.difference.Difference;
import guraa.pdfcompare.model.difference.ImageDifference;
import guraa.pdfcompare.model.difference.TextDifference;
import guraa.pdfcompare.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Main engine for PDF comparison with improved stability and resource management.
 */
@Slf4j
@Service
public class PDFComparisonEngine {

    private final SmartDocumentMatcher documentMatcher;
    private final TextElementComparisonService textComparisonService;
    private final ImageComparisonService imageComparisonService;
    private final FontComparisonService fontComparisonService;
    private final ExecutorService executorService;
    private final PerformanceMonitoringService monitoringService;

    // Mark ComparisonService with @Lazy
    @Lazy
    private ComparisonService comparisonService;

    // Reduced maximum concurrent page comparisons to prevent resource exhaustion
    private static final int MAX_CONCURRENT_PAGE_COMPARISONS = 3;

    // Cache for comparison results to avoid redundant comparisons
    private final Map<String, ComparisonResult> comparisonCache = new ConcurrentHashMap<>();

    // Maximum cache size
    private static final int MAX_CACHE_SIZE = 5;

    @Value("${app.comparison.smart-matching-enabled:true}")
    private boolean smartMatchingEnabled;

    @Value("${app.comparison.cache-enabled:true}")
    private boolean cacheEnabled;

    @Value("${app.comparison.batch-size:3}")
    private int batchSize = 3;

    /**
     * Constructor with qualifier to specify which executor service to use.
     */
    public PDFComparisonEngine(
            SmartDocumentMatcher documentMatcher,
            TextElementComparisonService textComparisonService,
            ImageComparisonService imageComparisonService,
            FontComparisonService fontComparisonService,
            @Qualifier("comparisonExecutor") ExecutorService executorService,
            PerformanceMonitoringService monitoringService) {
        this.documentMatcher = documentMatcher;
        this.textComparisonService = textComparisonService;
        this.imageComparisonService = imageComparisonService;
        this.fontComparisonService = fontComparisonService;
        this.executorService = executorService;
        this.monitoringService = monitoringService;
    }

    /**
     * Setter for ComparisonService to break circular dependency.
     */
    @Autowired
    public void setComparisonService(@Lazy ComparisonService comparisonService) {
        this.comparisonService = comparisonService;
    }

    /**
     * Compare two PDF documents.
     */
    public ComparisonResult compareDocuments(PdfDocument baseDocument, PdfDocument compareDocument) throws IOException {
        return compareDocuments(baseDocument, compareDocument, null);
    }

    /**
     * Compare two PDF documents with a specific comparison ID for progress tracking.
     */
    public ComparisonResult compareDocuments(PdfDocument baseDocument, PdfDocument compareDocument, String comparisonId) throws IOException {
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
            // Match pages between documents
            log.info(logPrefix + "Starting document matching phase");
            List<PagePair> pagePairs = matchDocuments(baseDocument, compareDocument, comparisonId);
            log.info(logPrefix + "Document matching phase completed, found {} page pairs", pagePairs.size());

            // Create a comparison summary
            log.info(logPrefix + "Creating comparison summary");
            PageLevelComparisonSummary summary = createComparisonSummary(baseDocument, compareDocument, pagePairs);
            log.info(logPrefix + "Comparison summary created with similarity score: {}", summary.getOverallSimilarityScore());

            // Compare matched pages with optimized resource usage
            log.info(logPrefix + "Starting page comparison phase");
            Map<String, List<Difference>> differencesByPage = comparePages(baseDocument, compareDocument, pagePairs, comparisonId);
            log.info(logPrefix + "Page comparison phase completed, found differences on {} pages", differencesByPage.size());

            // Create the comparison result
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

            if (monitoringService != null) {
                monitoringService.comparisonPerformed(duration);
            }

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

        // Simply remove the first entry for now
        Iterator<Map.Entry<String, ComparisonResult>> iterator = comparisonCache.entrySet().iterator();
        if (iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    /**
     * Match pages between two documents.
     */
    private List<PagePair> matchDocuments(PdfDocument baseDocument, PdfDocument compareDocument, String comparisonId) throws IOException {
        // Use the document matcher to match pages
        Map<String, Object> options = new HashMap<>();
        options.put("parallelProcessing", false); // Disable parallel processing to reduce memory usage
        options.put("batchSize", batchSize);

        // Add the comparison ID for progress tracking if provided
        if (comparisonId != null) {
            options.put("comparisonId", comparisonId);

            // Update progress in the database if we have a comparison ID
            if (comparisonService != null) {
                comparisonService.updateComparisonProgress(
                        comparisonId, 0, 100, "Matching document pages");
            }
        }

        return documentMatcher.matchDocuments(baseDocument, compareDocument, options);
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
     * Compare pages between two documents using optimized batching and resource management.
     */
    private Map<String, List<Difference>> comparePages(
            PdfDocument baseDocument, PdfDocument compareDocument, List<PagePair> pagePairs, String comparisonId) throws IOException {

        String logPrefix = "[" + baseDocument.getFileId() + " vs " + compareDocument.getFileId() + "] ";
        Map<String, List<Difference>> differencesByPage = new ConcurrentHashMap<>();

        // Filter out only matched pages
        List<PagePair> matchedPairs = pagePairs.stream()
                .filter(PagePair::isMatched)
                .collect(Collectors.toList());

        if (matchedPairs.isEmpty()) {
            log.warn(logPrefix + "No matched pages found between documents");
            return differencesByPage;
        }

        // Sort page pairs by similarity score (descending) to prioritize most similar pages
        matchedPairs.sort(Comparator.comparing(PagePair::getSimilarityScore).reversed());

        // Use a semaphore to limit concurrent comparisons
        Semaphore semaphore = new Semaphore(MAX_CONCURRENT_PAGE_COMPARISONS);

        // Create smaller batches for better load balancing
        List<List<PagePair>> batches = createBatches(matchedPairs, batchSize);
        log.info(logPrefix + "Created {} batches for page comparison", batches.size());

        // Track progress
        AtomicInteger completedPages = new AtomicInteger(0);
        int totalPages = matchedPairs.size();

        // Process each batch sequentially to reduce memory pressure
        for (int batchIndex = 0; batchIndex < batches.size(); batchIndex++) {
            List<PagePair> batch = batches.get(batchIndex);
            List<CompletableFuture<Void>> pageFutures = new ArrayList<>();

            for (PagePair pagePair : batch) {
                CompletableFuture<Void> pageFuture = CompletableFuture.runAsync(() -> {
                    try {
                        log.debug(logPrefix + "Limit concurrent processing");
                        semaphore.acquire(); // Limit concurrent processing

                        try {
                            log.debug(logPrefix + "Processing page pair: base page {} vs compare page {}",
                                    pagePair.getBasePageNumber(), pagePair.getComparePageNumber());

                            // Focus mainly on text comparison for better performance
                            List<TextDifference> textDiffs = textComparisonService.compareText(
                                    baseDocument, compareDocument,
                                    pagePair.getBasePageNumber(), pagePair.getComparePageNumber());

                            // Only add image and font comparison for the first 10 pages
                            // to reduce processing load
                            List<Difference> allDifferences = new ArrayList<>(textDiffs);

                            if (pagePair.getBasePageNumber() <= 10) {
                                try {
                                    // Optional image comparison only for initial pages
                                    List<ImageDifference> imageDiffs = imageComparisonService.compareImages(
                                            baseDocument, compareDocument,
                                            pagePair.getBasePageNumber(), pagePair.getComparePageNumber());
                                    allDifferences.addAll(imageDiffs);
                                } catch (Exception e) {
                                    log.warn("Error comparing images: {}", e.getMessage());
                                }
                            }

                            // Store the differences
                            differencesByPage.put(pagePair.getId(), allDifferences);

                            // Update the page pair with the differences
                            for (Difference difference : allDifferences) {
                                pagePair.addDifference(createPageDifference(difference));
                            }

                            // Log progress
                            int completed = completedPages.incrementAndGet();
                            if (completed % 5 == 0 || completed == totalPages) {
                                int progressPercent = (completed * 100) / totalPages;
                                log.info(logPrefix + "Page comparison progress: {}/{} pages ({}%)",
                                        completed, totalPages, progressPercent);

                                // Update progress in the database if we have a comparison ID
                                if (comparisonId != null && comparisonService != null) {
                                    comparisonService.updateComparisonProgress(
                                            comparisonId, completed, totalPages, "Comparing page contents");
                                }
                            }
                        } finally {
                            // Always release the semaphore
                            semaphore.release();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.error(logPrefix + "Thread interrupted while waiting for semaphore", e);
                    } catch (Exception e) {
                        log.error(logPrefix + "Error comparing page pair: {}", e.getMessage(), e);
                    }
                }, executorService);

                pageFutures.add(pageFuture);
            }

            // Wait for all pages in this batch to complete with timeout
            try {
                CompletableFuture.allOf(pageFutures.toArray(new CompletableFuture[0]))
                        .get(2, TimeUnit.MINUTES);
                log.info(logPrefix + "Completed batch {}/{}", batchIndex + 1, batches.size());
            } catch (Exception e) {
                log.error(logPrefix + "Error or timeout waiting for batch {}/{}: {}",
                        batchIndex + 1, batches.size(), e.getMessage());
                // Continue with next batch rather than failing completely
            }

            // Force garbage collection between batches to free memory
            if (batchIndex % 3 == 2) {
                System.gc();
            }
        }

        return differencesByPage;
    }

    /**
     * Create batches of page pairs for parallel processing.
     */
    private List<List<PagePair>> createBatches(List<PagePair> pagePairs, int batchSize) {
        List<List<PagePair>> batches = new ArrayList<>();

        for (int i = 0; i < pagePairs.size(); i += batchSize) {
            int end = Math.min(i + batchSize, pagePairs.size());
            batches.add(new ArrayList<>(pagePairs.subList(i, end)));
        }

        return batches;
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