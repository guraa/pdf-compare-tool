package guraa.pdfcompare.service;

import guraa.pdfcompare.PDFComparisonEngine;
import guraa.pdfcompare.PDFComparisonService;
import guraa.pdfcompare.comparison.PDFComparisonResult;
import guraa.pdfcompare.core.DocumentFeaturesExtractor;
import guraa.pdfcompare.core.PDFDocumentModel;
import guraa.pdfcompare.core.PDFPageModel;
import guraa.pdfcompare.core.PDFProcessor;
import guraa.pdfcompare.core.SmartDocumentMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Enhanced service for smart document comparison that incorporates page-level comparison
 */
@Service
public class SmartDocumentComparisonService {
    private static final Logger logger = LoggerFactory.getLogger(SmartDocumentComparisonService.class);

    private final PDFComparisonService comparisonService;
    private final PageLevelComparisonService pageLevelComparisonService;
    private final ExecutorService executorService = Executors.newFixedThreadPool(2);

    // Storage for document matching results
    private final Map<String, List<SmartDocumentMatcher.DocumentPair>> matchingResults = new ConcurrentHashMap<>();
    private final Map<String, List<PagePair>> pageLevelResults = new ConcurrentHashMap<>();

    // Storage for comparison results for individual document pairs
    private final Map<String, Map<Integer, PDFComparisonResult>> detailedResults = new ConcurrentHashMap<>();
    private final Map<String, PageLevelComparisonResult> pageLevelDetailedResults = new ConcurrentHashMap<>();

    // Status tracking for comparisons
    private final Map<String, AtomicInteger> completionCounters = new ConcurrentHashMap<>();

    // Store comparison status - processing, completed, failed
    private final Map<String, String> comparisonStatus = new ConcurrentHashMap<>();

    // Store error messages for failed comparisons
    private final Map<String, String> comparisonErrors = new ConcurrentHashMap<>();

    // Store comparison mode
    private final Map<String, String> comparisonMode = new ConcurrentHashMap<>();

    @Autowired
    public SmartDocumentComparisonService(
            PDFComparisonService comparisonService,
            PageLevelComparisonService pageLevelComparisonService) {
        this.comparisonService = comparisonService;
        this.pageLevelComparisonService = pageLevelComparisonService;
    }

    /**
     * Compare two PDFs intelligently, detecting multiple documents and matching
     * corresponding documents for comparison
     *
     * @param baseFilePath Path to the base PDF file
     * @param compareFilePath Path to the comparison PDF file
     * @param smartMatchingEnabled Whether to use smart matching (for multi-document PDFs)
     * @return Comparison ID
     * @throws IOException If there's an error reading the files
     */
    public String compareFiles(String baseFilePath, String compareFilePath, boolean smartMatchingEnabled)
            throws IOException {
        String comparisonId = UUID.randomUUID().toString();

        if (!smartMatchingEnabled) {
            // Fall back to standard comparison if smart matching is disabled
            return comparisonService.compareFiles(baseFilePath, compareFilePath);
        }

        // Initialize status for this comparison
        comparisonStatus.put(comparisonId, "processing");

        // Determine if we should use page-level matching
        // For now, always use page-level matching when smart matching is enabled
        boolean usePageLevelMatching = true;
        comparisonMode.put(comparisonId, usePageLevelMatching ? "page-level" : "document-level");

        // Start the comparison process
        startAsyncSmartComparison(comparisonId, baseFilePath, compareFilePath, usePageLevelMatching);

        return comparisonId;
    }

    /**
     * Start asynchronous smart comparison process
     */
    private void startAsyncSmartComparison(
            String comparisonId,
            String baseFilePath,
            String compareFilePath,
            boolean usePageLevelMatching) {
        executorService.submit(() -> {
            try {
                logger.info("Starting smart comparison {} between {} and {} (mode: {})",
                        comparisonId, baseFilePath, compareFilePath,
                        usePageLevelMatching ? "page-level" : "document-level");

                // Validate input files
                File baseFile = new File(baseFilePath);
                File compareFile = new File(compareFilePath);

                if (!baseFile.exists() || !baseFile.canRead()) {
                    String errorMsg = "Base file does not exist or cannot be read: " + baseFilePath;
                    handleComparisonError(comparisonId, errorMsg);
                    return;
                }

                if (!compareFile.exists() || !compareFile.canRead()) {
                    String errorMsg = "Compare file does not exist or cannot be read: " + compareFilePath;
                    handleComparisonError(comparisonId, errorMsg);
                    return;
                }

                logger.info("Processing base document: {} ({}KB)", baseFile.getName(), baseFile.length()/1024);
                PDFProcessor processor = new PDFProcessor();
                PDFDocumentModel baseDocument = processor.processDocument(baseFile);
                logger.info("Base document processed: {} pages", baseDocument.getPageCount());

                logger.info("Processing compare document: {} ({}KB)", compareFile.getName(), compareFile.length()/1024);
                PDFDocumentModel compareDocument = processor.processDocument(compareFile);
                logger.info("Compare document processed: {} pages", compareDocument.getPageCount());

                if (usePageLevelMatching) {
                    // Perform page-level matching
                    performPageLevelMatching(comparisonId, baseDocument, compareDocument, baseFile, compareFile);
                } else {
                    // Perform document-level matching
                    performDocumentLevelMatching(comparisonId, baseDocument, compareDocument, baseFile, compareFile);
                }

            } catch (Exception e) {
                logger.error("Error in smart comparison {}: {}", comparisonId, e.getMessage(), e);
                handleComparisonError(comparisonId, e.getMessage());
            }
        });
    }

    /**
     * Perform page-level matching and comparison
     */
    private void performPageLevelMatching(
            String comparisonId,
            PDFDocumentModel baseDocument,
            PDFDocumentModel compareDocument,
            File baseFile,
            File compareFile) {
        try {
            logger.info("Performing page-level matching for comparison {}", comparisonId);

            // Use the PageLevelComparisonService to perform page-level matching
            PageLevelComparisonResult result = pageLevelComparisonService.compareDocuments(
                    baseDocument, compareDocument);

            // Store the result
            pageLevelDetailedResults.put(comparisonId, result);
            pageLevelResults.put(comparisonId, result.getPagePairs());

            // Create document pairs from page pairs for backward compatibility
            List<SmartDocumentMatcher.DocumentPair> documentPairs = convertToDocumentPairs(result.getPagePairs());
            matchingResults.put(comparisonId, documentPairs);

            // Create comparison results for document pairs
            Map<Integer, PDFComparisonResult> pairResults = new ConcurrentHashMap<>();
            for (int i = 0; i < documentPairs.size(); i++) {
                if (i < result.getPageResults().size()) {
                    SmartDocumentMatcher.DocumentPair pair = documentPairs.get(i);

                    if (pair.isMatched()) {
                        PDFComparisonResult pairResult = convertToComparisonResult(
                                result.getPageResults().get(i), baseDocument, compareDocument);
                        pairResults.put(i, pairResult);
                    } else {
                        pairResults.put(i, createEmptyComparisonResult(pair, baseDocument, compareDocument));
                    }
                }
            }

            detailedResults.put(comparisonId, pairResults);

            // Mark as completed
            comparisonStatus.put(comparisonId, "completed");
            logger.info("Page-level matching for comparison {} completed successfully", comparisonId);

        } catch (Exception e) {
            logger.error("Error in page-level matching for comparison {}: {}", comparisonId, e.getMessage(), e);
            handleComparisonError(comparisonId, "Error in page-level matching: " + e.getMessage());
        }
    }

    /**
     * Convert page pairs to document pairs for backward compatibility
     */
    private List<SmartDocumentMatcher.DocumentPair> convertToDocumentPairs(List<PagePair> pagePairs) {
        List<SmartDocumentMatcher.DocumentPair> documentPairs = new ArrayList<>();

        for (PagePair pagePair : pagePairs) {
            int baseStartPage = -1;
            int baseEndPage = -1;
            int compareStartPage = -1;
            int compareEndPage = -1;
            double similarityScore = 0.0;

            if (pagePair.getBaseFingerprint() != null) {
                baseStartPage = pagePair.getBaseFingerprint().getPageIndex();
                baseEndPage = baseStartPage;
            }

            if (pagePair.getCompareFingerprint() != null) {
                compareStartPage = pagePair.getCompareFingerprint().getPageIndex();
                compareEndPage = compareStartPage;
            }

            if (pagePair.isMatched()) {
                similarityScore = pagePair.getSimilarityScore();
            }

            SmartDocumentMatcher.DocumentPair documentPair = new SmartDocumentMatcher.DocumentPair(
                    baseStartPage, baseEndPage, compareStartPage, compareEndPage, similarityScore);

            documentPairs.add(documentPair);
        }

        return documentPairs;
    }

    /**
     * Convert a page comparison result to a full comparison result
     */
    private PDFComparisonResult convertToComparisonResult(
            PageComparisonResult pageResult,
            PDFDocumentModel baseDocument,
            PDFDocumentModel compareDocument) {

        if (pageResult.getComparisonResult() != null) {
            return pageResult.getComparisonResult();
        }

        // Create a new comparison result
        PDFComparisonResult result = new PDFComparisonResult();

        // Set basic properties
        if (pageResult.getPagePair().getBaseFingerprint() != null) {
            result.setBasePageCount(1);
        } else {
            result.setBasePageCount(0);
        }

        if (pageResult.getPagePair().getCompareFingerprint() != null) {
            result.setComparePageCount(1);
        } else {
            result.setComparePageCount(0);
        }

        result.setPageCountDifferent(result.getBasePageCount() != result.getComparePageCount());

        // Set page differences
        List<PDFComparisonResult.PageDifference> pageDifferences = new ArrayList<>();
        if (pageResult.getPageDifference() != null) {
            pageDifferences.add(pageResult.getPageDifference());
        } else if (pageResult.getCustomPageDifference() != null) {
            // Convert the custom page difference
            PDFComparisonResult.PageDifference pageDifference = new PDFComparisonResult.PageDifference();
            // Set additional properties as needed

            // Add to page differences
            pageDifferences.add(pageDifference);
        }

        result.setPageDifferences(pageDifferences);
        result.setTotalDifferences(pageResult.getTotalDifferences());

        return result;
    }

    /**
     * Perform traditional document-level matching and comparison
     */
    private void performDocumentLevelMatching(
            String comparisonId,
            PDFDocumentModel baseDocument,
            PDFDocumentModel compareDocument,
            File baseFile,
            File compareFile) {
        try {
            // Step 2: Perform document matching
            logger.info("Matching documents...");
            SmartDocumentMatcher matcher = new SmartDocumentMatcher();
            List<SmartDocumentMatcher.DocumentPair> matchedPairs;

            try {
                matchedPairs = matcher.matchDocuments(baseDocument, compareDocument, baseFile, compareFile);
                logger.info("Document matching completed: {} document pairs identified", matchedPairs.size());
            } catch (Exception e) {
                logger.error("Error during document matching: {}", e.getMessage(), e);

                // Create a fallback match of the entire documents
                matchedPairs = new ArrayList<>();
                SmartDocumentMatcher.DocumentPair wholePair = new SmartDocumentMatcher.DocumentPair(
                        0, baseDocument.getPageCount() - 1,
                        0, compareDocument.getPageCount() - 1,
                        0.5  // Default similarity
                );
                matchedPairs.add(wholePair);
                logger.info("Created fallback document pair for entire document");
            }

            // Store matching results
            matchingResults.put(comparisonId, matchedPairs);

            // Initialize completion counter
            completionCounters.put(comparisonId, new AtomicInteger(matchedPairs.size()));

            // Step 3: Compare each matched document pair
            Map<Integer, PDFComparisonResult> pairResults = new ConcurrentHashMap<>();
            detailedResults.put(comparisonId, pairResults);

            PDFComparisonEngine engine = new PDFComparisonEngine();

            for (int i = 0; i < matchedPairs.size(); i++) {
                final int pairIndex = i;
                SmartDocumentMatcher.DocumentPair pair = matchedPairs.get(i);

                // Submit comparison task for each pair
                executorService.submit(() -> {
                    try {
                        logger.info("Starting comparison for document pair {} (pages {}-{} vs {}-{})",
                                pairIndex,
                                pair.getBaseStartPage(), pair.getBaseEndPage(),
                                pair.getCompareStartPage(), pair.getCompareEndPage());

                        PDFComparisonResult pairResult;

                        if (pair.isMatched()) {
                            // Extract document models for just this pair
                            PDFDocumentModel basePart = extractDocumentPart(
                                    baseDocument, pair.getBaseStartPage(), pair.getBaseEndPage());
                            PDFDocumentModel comparePart = extractDocumentPart(
                                    compareDocument, pair.getCompareStartPage(), pair.getCompareEndPage());

                            logger.info("Extracted document parts for comparison: base={} pages, compare={} pages",
                                    basePart.getPageCount(), comparePart.getPageCount());

                            // Compare this document pair
                            pairResult = engine.compareDocuments(basePart, comparePart);

                            logger.info("Completed document pair {} comparison with {} differences",
                                    pairIndex, pairResult.getTotalDifferences());
                        } else {
                            // Create empty result for non-matched pairs
                            pairResult = createEmptyComparisonResult(pair, baseDocument, compareDocument);

                            if (pair.hasBaseDocument()) {
                                logger.info("Document pair {} (pages {}-{}) only exists in base PDF",
                                        pairIndex, pair.getBaseStartPage(), pair.getBaseEndPage());
                            } else if (pair.hasCompareDocument()) {
                                logger.info("Document pair {} (pages {}-{}) only exists in compare PDF",
                                        pairIndex, pair.getCompareStartPage(), pair.getCompareEndPage());
                            }
                        }

                        // Store the result
                        pairResults.put(pairIndex, pairResult);
                        logger.info("Stored result for document pair {}", pairIndex);

                        // Decrease counter
                        int remaining = completionCounters.get(comparisonId).decrementAndGet();
                        logger.info("Document pair {} completed. Remaining pairs: {}", pairIndex, remaining);

                        // If all done, mark as completed
                        if (remaining == 0) {
                            comparisonStatus.put(comparisonId, "completed");
                            logger.info("Comparison {} completed successfully", comparisonId);
                        }
                    } catch (Exception e) {
                        logger.error("Error comparing document pair {}: {}", pairIndex, e.getMessage(), e);
                        // Mark as failed but store the error
                        handleComparisonError(comparisonId, "Error processing document pair " + pairIndex + ": " + e.getMessage());
                    }
                });
            }

            logger.info("Smart comparison {} started with {} document pairs",
                    comparisonId, matchedPairs.size());
        } catch (Exception e) {
            logger.error("Error in document-level matching for comparison {}: {}", comparisonId, e.getMessage(), e);
            handleComparisonError(comparisonId, "Error in document-level matching: " + e.getMessage());
        }
    }

    /**
     * Helper method to handle comparison errors
     */
    private void handleComparisonError(String comparisonId, String errorMessage) {
        logger.error("Comparison {} failed: {}", comparisonId, errorMessage);
        comparisonStatus.put(comparisonId, "failed");
        comparisonErrors.put(comparisonId, errorMessage);

        // Make sure counter is zero so it's considered completed
        completionCounters.computeIfPresent(comparisonId, (id, counter) -> new AtomicInteger(0));
        completionCounters.putIfAbsent(comparisonId, new AtomicInteger(0));
    }

    /**
     * Extract a subset of pages from a PDF document model to create a new document model
     *
     * @param document The source document model
     * @param startPage The start page index (0-based)
     * @param endPage The end page index (0-based)
     * @return A new document model containing only the specified pages
     */
    private PDFDocumentModel extractDocumentPart(PDFDocumentModel document, int startPage, int endPage) {
        if (document == null || document.getPages() == null) {
            logger.warn("Cannot extract document part from null document or pages");
            return new PDFDocumentModel();
        }

        PDFDocumentModel part = new PDFDocumentModel();
        part.setFileName(document.getFileName());

        // Copy metadata if available
        if (document.getMetadata() != null) {
            part.setMetadata(new HashMap<>(document.getMetadata()));
        } else {
            part.setMetadata(new HashMap<>());
        }

        // Extract only the pages in the range
        List<PDFPageModel> pages = new ArrayList<>();
        for (int i = startPage; i <= endPage && i < document.getPages().size(); i++) {
            PDFPageModel page = document.getPages().get(i);
            if (page != null) {
                pages.add(page);
            } else {
                logger.warn("Page {} is null in document {}", i, document.getFileName());
            }
        }

        part.setPages(pages);
        part.setPageCount(pages.size());

        // Use the DocumentFeaturesExtractor to enrich the document part with features
        try {
            Map<String, Object> features = DocumentFeaturesExtractor.extractBasicFeatures(document, startPage, endPage);
            logger.debug("Extracted {} features for document part", features.size());
        } catch (Exception e) {
            logger.warn("Could not extract features for document part: {}", e.getMessage());
        }

        return part;
    }

    /**
     * Create an empty comparison result for non-matched document pairs
     */
    private PDFComparisonResult createEmptyComparisonResult(SmartDocumentMatcher.DocumentPair pair,
                                                            PDFDocumentModel baseDocument, PDFDocumentModel compareDocument) {
        PDFComparisonResult result = new PDFComparisonResult();

        if (pair.hasBaseDocument()) {
            result.setBasePageCount(pair.getBaseEndPage() - pair.getBaseStartPage() + 1);
            result.setComparePageCount(0);
        } else if (pair.hasCompareDocument()) {
            result.setBasePageCount(0);
            result.setComparePageCount(pair.getCompareEndPage() - pair.getCompareStartPage() + 1);
        }

        result.setPageCountDifferent(true);
        result.setPageDifferences(new ArrayList<>());
        result.setTotalDifferences(result.getBasePageCount() + result.getComparePageCount());

        return result;
    }

    /**
     * Get comparison result from the standard service
     *
     * @param comparisonId The comparison ID
     * @return Comparison result from the standard service
     */
    public PDFComparisonResult getComparisonResult(String comparisonId) {
        return comparisonService.getComparisonResult(comparisonId);
    }

    /**
     * Get document pairs found in the comparison
     *
     * @param comparisonId The comparison ID
     * @return List of document pairs
     */
    public List<SmartDocumentMatcher.DocumentPair> getDocumentPairs(String comparisonId) {
        return matchingResults.getOrDefault(comparisonId, new ArrayList<>());
    }

    /**
     * Get comparison result for a specific document pair
     *
     * @param comparisonId The comparison ID
     * @param pairIndex The document pair index
     * @return Comparison result for the document pair
     */
    public PDFComparisonResult getDocumentPairResult(String comparisonId, int pairIndex) {
        Map<Integer, PDFComparisonResult> results = detailedResults.get(comparisonId);
        if (results == null) {
            return null;
        }
        return results.get(pairIndex);
    }

    /**
     * Check if a comparison is ready
     *
     * @param comparisonId The comparison ID
     * @return true if the comparison is complete
     */
    public boolean isComparisonReady(String comparisonId) {
        // Check if status is completed or failed
        String status = comparisonStatus.get(comparisonId);
        if ("completed".equals(status) || "failed".equals(status)) {
            return true;
        }

        // Alternative check with completion counters
        AtomicInteger counter = completionCounters.get(comparisonId);
        return counter != null && counter.get() == 0;
    }

    /**
     * Get a summary of all document pairs and their comparison results
     *
     * @param comparisonId The comparison ID
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

        List<SmartDocumentMatcher.DocumentPair> pairs = getDocumentPairs(comparisonId);
        if (pairs.isEmpty()) {
            summary.put("status", "processing");
            summary.put("message", "Document pairs not yet identified");
            return summary;
        }

        // Basic information
        summary.put("documentPairCount", pairs.size());

        boolean isReady = isComparisonReady(comparisonId);
        summary.put("isReady", isReady);

        // Add comparison mode
        String mode = comparisonMode.getOrDefault(comparisonId, "document-level");
        summary.put("comparisonMode", mode);

        if (!isReady) {
            AtomicInteger counter = completionCounters.get(comparisonId);
            int remaining = counter != null ? counter.get() : pairs.size();
            summary.put("status", "processing");
            summary.put("processedPairs", pairs.size() - remaining);
            summary.put("totalPairs", pairs.size());
            summary.put("percentComplete",
                    pairs.size() > 0 ? ((pairs.size() - remaining) * 100 / pairs.size()) : 0);
            return summary;
        }

        summary.put("status", "completed");

        // Create list of pair summaries
        List<Map<String, Object>> pairSummaries = new ArrayList<>();
        Map<Integer, PDFComparisonResult> results = detailedResults.getOrDefault(comparisonId, new HashMap<>());

        for (int i = 0; i < pairs.size(); i++) {
            SmartDocumentMatcher.DocumentPair pair = pairs.get(i);
            Map<String, Object> pairSummary = new HashMap<>();

            pairSummary.put("pairIndex", i);
            pairSummary.put("matched", pair.isMatched());
            pairSummary.put("similarityScore", pair.getSimilarityScore());

            if (pair.hasBaseDocument()) {
                pairSummary.put("baseStartPage", pair.getBaseStartPage());
                pairSummary.put("baseEndPage", pair.getBaseEndPage());
                pairSummary.put("basePageCount", pair.getBaseEndPage() - pair.getBaseStartPage() + 1);
            }

            if (pair.hasCompareDocument()) {
                pairSummary.put("compareStartPage", pair.getCompareStartPage());
                pairSummary.put("compareEndPage", pair.getCompareEndPage());
                pairSummary.put("comparePageCount", pair.getCompareEndPage() - pair.getCompareStartPage() + 1);
            }

            // Add comparison statistics if available
            PDFComparisonResult result = results.get(i);
            if (result != null) {
                pairSummary.put("totalDifferences", result.getTotalDifferences());
                pairSummary.put("textDifferences", result.getTotalTextDifferences());
                pairSummary.put("imageDifferences", result.getTotalImageDifferences());
                pairSummary.put("fontDifferences", result.getTotalFontDifferences());
                pairSummary.put("styleDifferences", result.getTotalStyleDifferences());
            }

            pairSummaries.add(pairSummary);
        }

        summary.put("documentPairs", pairSummaries);

        // Calculate total differences across all pairs
        int totalDifferences = 0;
        for (PDFComparisonResult result : results.values()) {
            if (result != null) {
                totalDifferences += result.getTotalDifferences();
            }
        }

        summary.put("totalDifferences", totalDifferences);
        summary.put("processedPairs", results.size());
        summary.put("totalPairs", pairs.size());

        return summary;
    }

    /**
     * Clean up expired comparison results
     */
    public void cleanupExpiredResults() {
        // This would use the same expiration logic as in PDFComparisonService
        // For simplicity, just delegating to that service's cleanup method
        comparisonService.cleanupExpiredResults();
    }

    /**
     * Get the comparison mode
     * @param comparisonId Comparison ID
     * @return Comparison mode (page-level or document-level)
     */
    public String getComparisonMode(String comparisonId) {
        return comparisonMode.getOrDefault(comparisonId, "document-level");
    }
}