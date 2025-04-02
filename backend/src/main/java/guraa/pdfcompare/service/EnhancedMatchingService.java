package guraa.pdfcompare.service;

import guraa.pdfcompare.PDFComparisonEngine;
import guraa.pdfcompare.core.PDFDocumentModel;
import guraa.pdfcompare.core.PDFPageModel;
import guraa.pdfcompare.comparison.PageComparisonResult;
import guraa.pdfcompare.core.PDFProcessor;
import guraa.pdfcompare.util.PDFComparisonLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Enhanced service for document matching and comparison with improved algorithms
 */
@Service
public class EnhancedMatchingService {
    private static final Logger logger = LoggerFactory.getLogger(EnhancedMatchingService.class);

    // Configured components
    private final PDFComparisonEngine comparisonEngine;
    private final PDFProcessor pdfProcessor;
    private final EnhancedPageMatcher pageMatcher;

    // Thread pool for async operations
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);

    // Results storage
    private final Map<String, List<PagePair>> matchingResults = new ConcurrentHashMap<>();
    private final Map<String, Map<Integer, guraa.pdfcompare.comparison.PageComparisonResult>> pageResults = new ConcurrentHashMap<>();
    private final Map<String, String> comparisonStatus = new ConcurrentHashMap<>();
    private final Map<String, String> comparisonErrors = new ConcurrentHashMap<>();

    // Strategy options
    private enum MatchingStrategy {
        PAGE_LEVEL,       // Match individual pages
        DOCUMENT_LEVEL,   // Match logical document segments
        HYBRID            // Use both strategies
    }

    @Autowired
    public EnhancedMatchingService(PDFComparisonEngine comparisonEngine) {
        this.comparisonEngine = comparisonEngine;
        this.pdfProcessor = new PDFProcessor();
        this.pageMatcher = new EnhancedPageMatcher();
    }

    /**
     * Compare two PDF files with enhanced matching
     * @param baseFilePath Base file path
     * @param compareFilePath Compare file path
     * @return Comparison ID for retrieving results
     * @throws IOException If there's an error reading the files
     */
    public String compareFiles(String baseFilePath, String compareFilePath) throws IOException {
        // Generate a unique ID for this comparison
        String comparisonId = UUID.randomUUID().toString();
        comparisonStatus.put(comparisonId, "processing");

        // Start asynchronous comparison
        startAsyncComparison(comparisonId, baseFilePath, compareFilePath, MatchingStrategy.PAGE_LEVEL); // Changed to PAGE_LEVEL only for simplicity

        return comparisonId;
    }

    /**
     * Start asynchronous comparison with selected strategy
     */
    private void startAsyncComparison(String comparisonId, String baseFilePath, String compareFilePath,
                                      MatchingStrategy strategy) {
        executorService.submit(() -> {
            try {
                // Log the start of comparison
                logger.info("Starting enhanced comparison {} between {} and {} using strategy {}",
                        comparisonId, baseFilePath, compareFilePath, strategy);

                // Validate input files
                File baseFile = new File(baseFilePath);
                File compareFile = new File(compareFilePath);

                validateFiles(baseFile, compareFile);

                // Process the PDF documents
                PDFDocumentModel baseDocument = pdfProcessor.processDocument(baseFile);
                PDFDocumentModel compareDocument = pdfProcessor.processDocument(compareFile);

                // Log document info
                logger.info("Base document: {} pages, Compare document: {} pages",
                        baseDocument.getPageCount(), compareDocument.getPageCount());

                // Use page-level matching for all cases for now
                performPageLevelMatching(comparisonId, baseDocument, compareDocument);

                // Update status to completed
                comparisonStatus.put(comparisonId, "completed");
                logger.info("Enhanced comparison {} completed successfully", comparisonId);

            } catch (Exception e) {
                logger.error("Error in enhanced comparison {}: {}", comparisonId, e.getMessage(), e);
                comparisonStatus.put(comparisonId, "failed");
                comparisonErrors.put(comparisonId, e.getMessage());
            }
        });
    }

    /**
     * Validate input files
     */
    private void validateFiles(File baseFile, File compareFile) throws IOException {
        if (!baseFile.exists() || !baseFile.canRead()) {
            throw new IOException("Base file does not exist or cannot be read: " + baseFile.getAbsolutePath());
        }
        if (!compareFile.exists() || !compareFile.canRead()) {
            throw new IOException("Compare file does not exist or cannot be read: " + compareFile.getAbsolutePath());
        }

        // Log file information
        PDFComparisonLogger.logPdfFileInfo(baseFile);
        PDFComparisonLogger.logPdfFileInfo(compareFile);
    }

    /**
     * Perform page-level matching using the enhanced matcher
     */
    private void performPageLevelMatching(String comparisonId, PDFDocumentModel baseDocument,
                                          PDFDocumentModel compareDocument) {
        logger.info("Using page-level matching strategy for comparison {}", comparisonId);

        // Match pages using the enhanced matcher
        List<PagePair> pagePairs = pageMatcher.matchPages(baseDocument, compareDocument);

        // Store the matching results
        matchingResults.put(comparisonId, pagePairs);

        // Compare matched pages
        compareMatchedPages(comparisonId, pagePairs, baseDocument, compareDocument);
    }

    /**
     * Compare matched pages to find differences
     */
    private void compareMatchedPages(String comparisonId, List<PagePair> pagePairs,
                                     PDFDocumentModel baseDocument, PDFDocumentModel compareDocument) {
        // Initialize results storage
        Map<Integer, guraa.pdfcompare.comparison.PageComparisonResult> results = new HashMap<>();
        pageResults.put(comparisonId, results);

        // Compare each page pair
        for (int i = 0; i < pagePairs.size(); i++) {
            final int pairIndex = i;
            final PagePair pair = pagePairs.get(i);

            executorService.submit(() -> {
                try {
                    guraa.pdfcompare.comparison.PageComparisonResult result = comparePagePair(pair, baseDocument, compareDocument);
                    results.put(pairIndex, result);

                    logger.debug("Completed comparison for page pair {}", pairIndex);
                } catch (Exception e) {
                    logger.error("Error comparing page pair {}: {}", pairIndex, e.getMessage(), e);

                    // Create an error result
                    guraa.pdfcompare.comparison.PageComparisonResult errorResult = new guraa.pdfcompare.comparison.PageComparisonResult();
                    errorResult.setPagePair(pair);
                    errorResult.setError("Error comparing pages: " + e.getMessage());
                    results.put(pairIndex, errorResult);
                }
            });
        }
    }

    /**
     * Compare a single page pair
     */
    private guraa.pdfcompare.comparison.PageComparisonResult comparePagePair(PagePair pair, PDFDocumentModel baseDocument,
                                                                          PDFDocumentModel compareDocument) {
        guraa.pdfcompare.comparison.PageComparisonResult result = new guraa.pdfcompare.comparison.PageComparisonResult();
        result.setPagePair(pair);

        if (!pair.isMatched()) {
            // Handle unmatched pages
            if (pair.getBaseFingerprint() != null) {
                // Page only exists in base document
                result.setChangeType("DELETION");
                result.setHasDifferences(true);
                result.setTotalDifferences(1);
            } else if (pair.getCompareFingerprint() != null) {
                // Page only exists in compare document
                result.setChangeType("ADDITION");
                result.setHasDifferences(true);
                result.setTotalDifferences(1);
            }
        } else {
            // For matched pages, perform detailed comparison
            PDFPageModel basePage = pair.getBaseFingerprint().getPage();
            PDFPageModel comparePage = pair.getCompareFingerprint().getPage();

            // Use the comparison engine to compare pages
            guraa.pdfcompare.comparison.PageComparisonResult engineResult =
                    comparisonEngine.comparePage(basePage, comparePage);

            // Convert to service result
            result = PageComparisonResultAdapter.toServiceResult(engineResult);

            // Set the page pair
            result.setPagePair(pair);

            // Calculate similarity metric for visual presentation
            double similarity = pair.getSimilarityScore();

            // Determine if pages should be considered "identical" based on threshold
            if (similarity > 0.98 && result.getTotalDifferences() == 0) {
                result.setChangeType("IDENTICAL");
                result.setHasDifferences(false);
            } else {
                result.setChangeType("MODIFIED");
                result.setHasDifferences(true);
            }
        }

        return result;
    }

    /**
     * Check if a comparison is ready
     * @param comparisonId Comparison ID
     * @return true if the comparison is completed or failed
     */
    public boolean isComparisonReady(String comparisonId) {
        String status = comparisonStatus.get(comparisonId);
        return "completed".equals(status) || "failed".equals(status);
    }

    /**
     * Get comparison status
     * @param comparisonId Comparison ID
     * @return Status string
     */
    public String getComparisonStatus(String comparisonId) {
        return comparisonStatus.getOrDefault(comparisonId, "not_found");
    }

    /**
     * Get error message for a failed comparison
     * @param comparisonId Comparison ID
     * @return Error message or null if no error
     */
    public String getComparisonError(String comparisonId) {
        return comparisonErrors.get(comparisonId);
    }

    /**
     * Get page matching results
     * @param comparisonId Comparison ID
     * @return List of page pairs
     */
    public List<PagePair> getPagePairs(String comparisonId) {
        return matchingResults.getOrDefault(comparisonId, Collections.emptyList());
    }

    /**
     * Get page comparison result
     * @param comparisonId Comparison ID
     * @param pairIndex Pair index
     * @return Page comparison result
     */
    public guraa.pdfcompare.comparison.PageComparisonResult getPageResult(String comparisonId, int pairIndex) {
        Map<Integer, guraa.pdfcompare.comparison.PageComparisonResult> results = pageResults.get(comparisonId);
        if (results == null) {
            return null;
        }
        return results.get(pairIndex);
    }

    /**
     * Get summary statistics for a comparison
     * @param comparisonId Comparison ID
     * @return Map with summary information
     */
    public Map<String, Object> getComparisonSummary(String comparisonId) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("comparisonId", comparisonId);

        // Check for errors
        String status = comparisonStatus.get(comparisonId);
        if ("failed".equals(status)) {
            String error = comparisonErrors.get(comparisonId);
            summary.put("status", "failed");
            summary.put("error", error != null ? error : "Unknown error occurred");
            return summary;
        }

        List<PagePair> pairs = getPagePairs(comparisonId);
        if (pairs.isEmpty()) {
            summary.put("status", "processing");
            summary.put("message", "Page pairs not yet identified");
            return summary;
        }

        boolean isReady = isComparisonReady(comparisonId);
        summary.put("isReady", isReady);
        summary.put("status", isReady ? "completed" : "processing");

        // Count matched and unmatched pages
        int matchedPages = (int) pairs.stream().filter(PagePair::isMatched).count();
        int baseOnlyPages = (int) pairs.stream()
                .filter(p -> p.getBaseFingerprint() != null && p.getCompareFingerprint() == null)
                .count();
        int compareOnlyPages = (int) pairs.stream()
                .filter(p -> p.getBaseFingerprint() == null && p.getCompareFingerprint() != null)
                .count();

        summary.put("matchedPageCount", matchedPages);
        summary.put("unmatchedBasePageCount", baseOnlyPages);
        summary.put("unmatchedComparePageCount", compareOnlyPages);
        summary.put("totalBasePages", matchedPages + baseOnlyPages);
        summary.put("totalComparePages", matchedPages + compareOnlyPages);

        // Calculate difference statistics if ready
        if (isReady) {
            Map<Integer, guraa.pdfcompare.comparison.PageComparisonResult> results = pageResults.get(comparisonId);
            if (results != null && !results.isEmpty()) {
                int totalDifferences = 0;
                int totalTextDifferences = 0;
                int totalImageDifferences = 0;
                int totalFontDifferences = 0;
                int totalStyleDifferences = 0;
                int identicalPages = 0;
                int modifiedPages = 0;

                for (guraa.pdfcompare.comparison.PageComparisonResult result : results.values()) {
                    if (result.isIdentical()) {
                        identicalPages++;
                    } else if (result.isModification()) {
                        modifiedPages++;
                        totalDifferences += result.getTotalDifferences();
                    } else {
                        // Additions and deletions count as one difference each
                        totalDifferences++;
                    }

                    // Also count specific types of differences if we have them
                    if (result.getTextDifferences() != null &&
                            result.getTextDifferences().getDifferences() != null) {
                        totalTextDifferences += result.getTextDifferences().getDifferences().size();
                    }

                    if (result.getImageDifferences() != null) {
                        totalImageDifferences += result.getImageDifferences().size();
                    }

                    if (result.getFontDifferences() != null) {
                        totalFontDifferences += result.getFontDifferences().size();
                    }
                }

                summary.put("totalDifferences", totalDifferences);
                summary.put("totalTextDifferences", totalTextDifferences);
                summary.put("totalImageDifferences", totalImageDifferences);
                summary.put("totalFontDifferences", totalFontDifferences);
                summary.put("totalStyleDifferences", totalStyleDifferences);
                summary.put("identicalPageCount", identicalPages);
                summary.put("modifiedPageCount", modifiedPages);

                // Calculate overall similarity
                double averageSimilarity = pairs.stream()
                        .filter(PagePair::isMatched)
                        .mapToDouble(PagePair::getSimilarityScore)
                        .average()
                        .orElse(0.0);

                summary.put("overallSimilarity", averageSimilarity);
            }
        }

        return summary;
    }
}