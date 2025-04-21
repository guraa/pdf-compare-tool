package guraa.pdfcompare;

import guraa.pdfcompare.core.DocumentMatchingStrategy;
import guraa.pdfcompare.core.SmartDocumentMatcher;
import guraa.pdfcompare.model.ComparisonResult;
import guraa.pdfcompare.model.PdfDocument;
import guraa.pdfcompare.model.difference.Difference;
import guraa.pdfcompare.model.difference.FontDifference;
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
 * This class coordinates the comparison process, delegating to specialized
 * services for different types of comparisons.
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
    private ComparisonService comparisonService;

    // Maximum number of concurrent page comparisons
    private static final int MAX_CONCURRENT_PAGE_COMPARISONS = 8;

    // Cache for comparison results to avoid redundant comparisons
    private final Map<String, ComparisonResult> comparisonCache = new ConcurrentHashMap<>();

    // Maximum cache size
    private static final int MAX_CACHE_SIZE = 10;

    /**
     * Constructor with qualifier to specify which executor service to use.
     *
     * @param documentMatcher        The document matcher
     * @param textComparisonService  The text comparison service
     * @param imageComparisonService The image comparison service
     * @param fontComparisonService  The font comparison service
     * @param executorService        The executor service for comparison operations
     * @param monitoringService      The performance monitoring service
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
     * 
     * @param comparisonService The comparison service
     */
    @org.springframework.beans.factory.annotation.Autowired
    public void setComparisonService(ComparisonService comparisonService) {
        this.comparisonService = comparisonService;
    }

    @Value("${app.comparison.smart-matching-enabled:true}")
    private boolean smartMatchingEnabled;

    @Value("${app.comparison.cache-enabled:true}")
    private boolean cacheEnabled;

    @Value("${app.comparison.batch-size:4}")
    private int batchSize;

    /**
     * Compare two PDF documents.
     *
     * @param baseDocument    The base document
     * @param compareDocument The document to compare against the base
     * @return The comparison result
     * @throws IOException If there is an error comparing the documents
     */
    public ComparisonResult compareDocuments(PdfDocument baseDocument, PdfDocument compareDocument) throws IOException {
        return compareDocuments(baseDocument, compareDocument, null);
    }
    
    /**
     * Compare two PDF documents with a specific comparison ID for progress tracking.
     *
     * @param baseDocument    The base document
     * @param compareDocument The document to compare against the base
     * @param comparisonId    The comparison ID for progress tracking (optional)
     * @return The comparison result
     * @throws IOException If there is an error comparing the documents
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

            // Compare matched pages
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
        // In a real implementation, you might want to track access times
        Iterator<Map.Entry<String, ComparisonResult>> iterator = comparisonCache.entrySet().iterator();
        if (iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    /**
     * Match pages between two documents.
     *
     * @param baseDocument    The base document
     * @param compareDocument The document to compare against the base
     * @return A list of page pairs
     * @throws IOException If there is an error matching the documents
     */
    private List<PagePair> matchDocuments(PdfDocument baseDocument, PdfDocument compareDocument) throws IOException {
        return matchDocuments(baseDocument, compareDocument, null);
    }
    
    /**
     * Match pages between two documents with progress tracking.
     *
     * @param baseDocument    The base document
     * @param compareDocument The document to compare against the base
     * @param comparisonId    The comparison ID for progress tracking (optional)
     * @return A list of page pairs
     * @throws IOException If there is an error matching the documents
     */
    private List<PagePair> matchDocuments(PdfDocument baseDocument, PdfDocument compareDocument, String comparisonId) throws IOException {
        // Use the document matcher to match pages
        DocumentMatchingStrategy matcher = documentMatcher;
        Map<String, Object> options = new HashMap<>();
        options.put("parallelProcessing", true);
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

        return matcher.matchDocuments(baseDocument, compareDocument, options);
    }

    /**
     * Create a comparison summary.
     *
     * @param baseDocument    The base document
     * @param compareDocument The document to compare against the base
     * @param pagePairs       The page pairs
     * @return The comparison summary
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
     * Compare pages between two documents using optimized batching and prioritization.
     *
     * @param baseDocument    The base document
     * @param compareDocument The document to compare against the base
     * @param pagePairs       The page pairs
     * @return A map of page pair IDs to differences
     * @throws IOException If there is an error comparing the pages
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

        // Create batches for better load balancing
        List<List<PagePair>> batches = createBatches(matchedPairs, batchSize);
        log.info(logPrefix + "Created {} batches for page comparison", batches.size());

        // Track progress
        AtomicInteger completedPages = new AtomicInteger(0);
        int totalPages = matchedPairs.size();

        // Process each batch in parallel
        List<CompletableFuture<Void>> batchFutures = new ArrayList<>();

        log.info(logPrefix + "Starting comparison of {} matched pages in {} batches", totalPages, batches.size());
        for (List<PagePair> batch : batches) {
            CompletableFuture<Void> batchFuture = CompletableFuture.runAsync(() -> {
                for (PagePair pagePair : batch) {
                    try {
                        log.info(logPrefix + "Limit concurrent processing");
                        semaphore.acquire(); // Limit concurrent processing

                        try {
                            log.info(logPrefix + "Create tasks for different comparison types");
                            // Create tasks for different comparison types
                            CompletableFuture<List<TextDifference>> textFuture = CompletableFuture.supplyAsync(() -> {
                                try {
                                    log.info(logPrefix + "CompletableFuture TextDifference");
                                    return textComparisonService.compareText(
                                            baseDocument, compareDocument,
                                            pagePair.getBasePageNumber(), pagePair.getComparePageNumber());
                                } catch (IOException e) {
                                    log.error(logPrefix + "Error comparing text: {}", e.getMessage(), e);
                                    return new ArrayList<>();
                                }
                            }, executorService);

                            CompletableFuture<List<ImageDifference>> imageFuture = CompletableFuture.supplyAsync(() -> {
                                try {
                                    log.info(logPrefix + "CompletableFuture ImageDifference");
                                    return imageComparisonService.compareImages(
                                            baseDocument, compareDocument,
                                            pagePair.getBasePageNumber(), pagePair.getComparePageNumber());
                                } catch (IOException e) {
                                    log.error(logPrefix + "Error comparing images: {}", e.getMessage(), e);
                                    return new ArrayList<>();
                                }
                            }, executorService);
                            log.info(logPrefix + "CompletableFuture ImageDifference - Done");
                          //  CompletableFuture<List<FontDifference>> fontFuture = CompletableFuture.supplyAsync(() -> {
                         //       try {
                          //          log.info(logPrefix + "CompletableFuture FontDifference");
                          //          return fontComparisonService.compareFonts(
                          //                  baseDocument, compareDocument,
                          //                  pagePair.getBasePageNumber(), pagePair.getComparePageNumber());
                          //      } catch (IOException e) {
                          //          log.error(logPrefix + "Error comparing fonts: {}", e.getMessage(), e);
                           //         return new ArrayList<>();
                          //      }
                          //  }, executorService);
                            // Initialize fontFuture with a completed future containing an empty list
                            CompletableFuture<List<FontDifference>> fontFuture = CompletableFuture.completedFuture(new ArrayList<>());
                            // Wait for all comparisons to complete
                            log.info(logPrefix + "Wait for all comparisons to complete");
                            // Pass the non-null (but completed) fontFuture to allOf
                            CompletableFuture.allOf(textFuture, imageFuture, fontFuture).join(); 

                            // Combine all differences
                            log.info(logPrefix + "Combine all differences");
                            List<Difference> allDifferences = new ArrayList<>();
                            allDifferences.addAll(textFuture.get()); // .get() is safe after .join()
                            allDifferences.addAll(imageFuture.get());
                            allDifferences.addAll(fontFuture.get()); // Will get the empty list

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
                            semaphore.release(); // Always release the semaphore
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.error(logPrefix + "Thread interrupted while waiting for semaphore", e);
                    } catch (Exception e) {
                        log.error(logPrefix + "Error comparing page pair: {}", e.getMessage(), e);
                    }
                }
            }, executorService);

            batchFutures.add(batchFuture);
        }

        // Wait for all batches to complete with timeout handling
        try {
            CompletableFuture.allOf(batchFutures.toArray(new CompletableFuture[0]))
                    .get(5, TimeUnit.MINUTES);
            log.info(logPrefix + "All page comparison batches completed successfully");
        } catch (Exception e) {
            log.error(logPrefix + "Error or timeout waiting for page comparisons to complete: {}", e.getMessage(), e);
            // Continue with partial results rather than failing completely
        }

        return differencesByPage;
    }

    /**
     * Create batches of page pairs for parallel processing.
     *
     * @param pagePairs The page pairs to batch
     * @param batchSize The batch size
     * @return A list of batches
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
     *
     * @param difference The difference
     * @return The page difference
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
