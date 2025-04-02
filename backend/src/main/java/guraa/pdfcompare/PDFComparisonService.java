package guraa.pdfcompare;

import guraa.pdfcompare.comparison.PDFComparisonResult;
import guraa.pdfcompare.comparison.PageComparisonResult;
import guraa.pdfcompare.comparison.TextElementDifference;
import guraa.pdfcompare.core.PDFDocumentModel;
import guraa.pdfcompare.core.PDFPageModel;
import guraa.pdfcompare.core.PDFProcessor;
import guraa.pdfcompare.service.ReportGenerationService;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service for PDF comparison operations with optimizations for handling large files
 */
@Service
public class PDFComparisonService {

    private static final Logger logger = LoggerFactory.getLogger(PDFComparisonService.class);

    // Constants for memory optimization
    private static final int MAX_PAGES_PER_BATCH = 20; // Maximum pages to process in a single batch
    private static final int MEMORY_THRESHOLD_MB = 200; // Memory threshold for GC triggering
    private static final long RESULT_CACHE_DURATION_MS = 1800000; // 30 minutes

    // Thread pool for async operations
    private final ExecutorService executorService = Executors.newFixedThreadPool(2);

    // In-memory storage for comparison results
    // In a production environment, this should be replaced with a database
    private Map<String, ComparisonResultData> comparisonResults = new ConcurrentHashMap<>();

    @Autowired
    private ReportGenerationService reportGenerationService;

    /**
     * Inner class to store comparison result with metadata
     */
    private static class ComparisonResultData {
        private final PDFComparisonResult result;
        private final long creationTime;

        public ComparisonResultData(PDFComparisonResult result) {
            this.result = result;
            this.creationTime = System.currentTimeMillis();
        }

        public PDFComparisonResult getResult() {
            return result;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - creationTime > RESULT_CACHE_DURATION_MS;
        }
    }

    /**
     * Compare two PDF files
     *
     * @param baseFilePath    Path to the base PDF file
     * @param compareFilePath Path to the comparison PDF file
     * @return Comparison ID
     * @throws IOException If there's an error reading the files
     */
    public String compareFiles(String baseFilePath, String compareFilePath) throws IOException {
        // Generate comparison ID
        String comparisonId = UUID.randomUUID().toString();

        // Add explicit logging to verify the ID
        logger.info("Generated new comparison ID: {}", comparisonId);

        // Start async comparison process
        startAsyncComparison(comparisonId, baseFilePath, compareFilePath);

        return comparisonId;
    }

    /**
     * Start asynchronous comparison to prevent request timeouts with large files
     */
    private void startAsyncComparison(String comparisonId, String baseFilePath, String compareFilePath) {
        executorService.submit(() -> {
            try {
                logger.info("Starting async comparison {} between {} and {}",
                        comparisonId, baseFilePath, compareFilePath);

                // Create PDF processor with memory optimization
                PDFProcessor processor = new PDFProcessor();

                // Process documents in a memory-efficient way
                PDFDocumentModel baseDocument = processor.processDocument(new File(baseFilePath));

                // Force garbage collection after processing first document
                System.gc();

                PDFDocumentModel compareDocument = processor.processDocument(new File(compareFilePath));

                // Create comparison engine
                PDFComparisonEngine engine = new PDFComparisonEngine();

                // Perform comparison with batching for large documents
                PDFComparisonResult result = compareDocumentsInBatches(engine, baseDocument, compareDocument);

                // Store result with expiration metadata - ensure this line executes correctly
                comparisonResults.put(comparisonId, new ComparisonResultData(result));
                logger.info("Comparison {} completed and stored successfully with {} differences",
                        comparisonId, result.getTotalDifferences());

                // Clean up expired results
                cleanupExpiredResults();
            } catch (Exception e) {
                logger.error("Error in async comparison {}: {}", comparisonId, e.getMessage(), e);
            }
        });
    }


    /**
     * Store a temporary result for report generation
     * @param comparisonId The comparison ID
     * @param result The comparison result to store
     */
    public void storeTemporaryResult(String comparisonId, PDFComparisonResult result) {
        if (comparisonId == null || result == null) {
            logger.warn("Cannot store null comparison ID or result");
            return;
        }

        // Store the result directly in the comparison results map
        ComparisonResultData resultData = new ComparisonResultData(result);
        comparisonResults.put(comparisonId, resultData);
        logger.info("Stored temporary result for comparison ID: {}", comparisonId);
    }

    /**
     * Compare documents in batches to prevent memory issues with large files
     */
    private PDFComparisonResult compareDocumentsInBatches(PDFComparisonEngine engine,
                                                          PDFDocumentModel baseDocument,
                                                          PDFDocumentModel compareDocument) {
        PDFComparisonResult result = new PDFComparisonResult();
        result.setBasePageCount(baseDocument.getPageCount());
        result.setComparePageCount(compareDocument.getPageCount());
        result.setPageCountDifferent(baseDocument.getPageCount() != compareDocument.getPageCount());

        // Steg 1: Extrahera text från sidorna
        Map<Integer, String> baseText = extractTextByPage(baseDocument.getPages());
        Map<Integer, String> compareText = extractTextByPage(compareDocument.getPages());

        // Steg 2: Matcha sidor innehållsmässigt
        Map<Integer, Integer> pageMatches = matchPagesSmartly(baseText, compareText);
        result.setPageMapping(pageMatches);

        // Steg 3: Jämför de matchade sidorna
        List<PageComparisonResult> allPageDifferences = new ArrayList<>();

        for (Map.Entry<Integer, Integer> entry : pageMatches.entrySet()) {
            int baseIndex = entry.getKey();
            int compareIndex = entry.getValue();

            if (baseIndex < baseDocument.getPageCount() && compareIndex < compareDocument.getPageCount()) {
                logger.debug("Comparing matched pages: base {} ↔ compare {}", baseIndex + 1, compareIndex + 1);

                PageComparisonResult pageResult = engine.comparePage(
                        baseDocument.getPages().get(baseIndex),
                        compareDocument.getPages().get(compareIndex)
                );

                pageResult.setPageNumber(baseIndex + 1); // för visning
                pageResult.setBasePageIndex(baseIndex);
                pageResult.setComparePageIndex(compareIndex);

                allPageDifferences.add(pageResult);
            }
        }

        // Steg 4: Lägg till sidor som bara finns i base
        for (int i = 0; i < baseDocument.getPageCount(); i++) {
            if (!pageMatches.containsKey(i)) {
                PageComparisonResult onlyInBase = new PageComparisonResult();
                onlyInBase.setPageNumber(i + 1);
                onlyInBase.setOnlyInBase(true);
                onlyInBase.setBasePageIndex(i);
                onlyInBase.setComparePageIndex(-1);
                allPageDifferences.add(onlyInBase);
            }
        }

        // Steg 5: Lägg till sidor som bara finns i compare
        for (int j = 0; j < compareDocument.getPageCount(); j++) {
            if (!pageMatches.containsValue(j)) {
                PageComparisonResult onlyInCompare = new PageComparisonResult();
                onlyInCompare.setPageNumber(j + 1);
                onlyInCompare.setOnlyInCompare(true);
                onlyInCompare.setBasePageIndex(-1);
                onlyInCompare.setComparePageIndex(j);
                allPageDifferences.add(onlyInCompare);
            }
        }

        result.setPageDifferences(allPageDifferences);

        // Steg 6: Summera
        calculateSummaryStatistics(result);

        return result;
    }



    /**
     * Compare a batch of pages
     */
    private List<PageComparisonResult> comparePageBatch(PDFComparisonEngine engine,
                                                        PDFDocumentModel baseDocument,
                                                        PDFDocumentModel compareDocument,
                                                        int startIndex,
                                                        int endIndex) {
        List<PageComparisonResult> batchResults = new ArrayList<>();

        for (int i = startIndex; i < endIndex; i++) {
            PageComparisonResult pageResult;

            if (i < baseDocument.getPageCount() && i < compareDocument.getPageCount()) {
                // Compare existing pages
                logger.debug("Comparing page {}", i + 1);
                pageResult = engine.comparePage(baseDocument.getPages().get(i), compareDocument.getPages().get(i));
            } else if (i < baseDocument.getPageCount()) {
                // Page exists only in base document
                logger.debug("Page {} exists only in base document", i + 1);
                pageResult = new PageComparisonResult();
                pageResult.setPageNumber(i + 1);
                pageResult.setOnlyInBase(true);
            } else {
                // Page exists only in compare document
                logger.debug("Page {} exists only in compare document", i + 1);
                pageResult = new PageComparisonResult();
                pageResult.setPageNumber(i + 1);
                pageResult.setOnlyInCompare(true);
            }

            batchResults.add(pageResult);
        }

        return batchResults;
    }

    /**
     * Get used memory in MB
     */
    private long getUsedMemoryMB() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        return usedMemory / (1024 * 1024);
    }

    /**
     * Clean up expired results
     */
    public void cleanupExpiredResults() {
        List<String> expiredKeys = comparisonResults.entrySet().stream()
                .filter(entry -> entry.getValue().isExpired())
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toList());

        for (String key : expiredKeys) {
            comparisonResults.remove(key);
        }

        if (!expiredKeys.isEmpty()) {
            logger.info("Cleaned up {} expired comparison results", expiredKeys.size());
        }
    }

    /**
     * Generate report for comparison results
     * @param comparisonId The comparison ID
     * @param format The report format (pdf, html, json)
     * @return Resource containing the report
     * @throws IOException If there's an error generating the report
     */
    public Resource generateReport(String comparisonId, String format) throws IOException {
        logger.info("Generating {} report for comparison {}", format, comparisonId);

        PDFComparisonResult result = getComparisonResult(comparisonId);

        if (result == null) {
            logger.error("Comparison result not found: {}", comparisonId);
            throw new RuntimeException("Comparison result not found: " + comparisonId);
        }

        switch (format.toLowerCase()) {
            case "pdf":
                return reportGenerationService.generatePdfReport(result);
            case "html":
                return reportGenerationService.generateHtmlReport(result);
            case "json":
                return reportGenerationService.generateJsonReport(result);
            default:
                logger.error("Unsupported report format: {}", format);
                throw new IllegalArgumentException("Unsupported format: " + format);
        }
    }

    /**
     * Get comparison result by ID
     * @param comparisonId The comparison ID
     * @return Comparison result, or null if not found
     */
    public PDFComparisonResult getComparisonResult(String comparisonId) {
        if (comparisonId == null || comparisonId.trim().isEmpty()) {
            logger.warn("Attempted to retrieve comparison result with null or empty ID");
            return null;
        }

        // Add additional logging to debug
        logger.info("Attempting to retrieve comparison result for ID: {}", comparisonId);
        logger.debug("Available comparison IDs: {}", comparisonResults.keySet());

        ComparisonResultData resultData = comparisonResults.get(comparisonId);
        if (resultData != null) {
            // Check if result is expired
            if (resultData.isExpired()) {
                comparisonResults.remove(comparisonId);
                logger.info("Removed expired comparison result: {}", comparisonId);
                return null;
            }

            PDFComparisonResult result = resultData.getResult();

            // Additional validation
            if (result != null) {
                // Validate the page differences array exists
                if (result.getPageDifferences() == null) {
                    logger.warn("Retrieved comparison result has null pageDifferences array! Initializing empty array.");
                    result.setPageDifferences(new ArrayList<>());
                }

                // Log details about the result
                logger.info("Found valid comparison result for ID: {} with {} total differences",
                        comparisonId, result.getTotalDifferences());
                logger.debug("Result contains {} page differences entries",
                        result.getPageDifferences().size());
            }

            return result;
        }

        logger.warn("Comparison result not found for ID: {}", comparisonId);
        return null;
    }

    /**
     * Calculate summary statistics for comparison result
     * @param result The comparison result to update with statistics
     */
    private void calculateSummaryStatistics(PDFComparisonResult result) {
        int totalDifferences = 0;
        int textDifferences = 0;
        int imageDifferences = 0;
        int fontDifferences = 0;
        int styleDifferences = 0;

        // Count metadata differences
        if (result.getMetadataDifferences() != null) {
            totalDifferences += result.getMetadataDifferences().size();
        }

        // Count page structure differences
        if (result.isPageCountDifferent()) {
            totalDifferences++;
        }

        // Count differences for each page
        for (PageComparisonResult page : result.getPageDifferences()) {
            // Count page structure differences
            if (page.isOnlyInBase() || page.isOnlyInCompare()) {
                totalDifferences++;
            } else if (page.isDimensionsDifferent()) {
                totalDifferences++;
            }

            // Count text differences
            if (page.getTextDifferences() != null && page.getTextDifferences().getDifferences() != null) {
                int pageDiffs = page.getTextDifferences().getDifferences().size();
                textDifferences += pageDiffs;
                totalDifferences += pageDiffs;
            }

            // Count text element differences
            if (page.getTextElementDifferences() != null) {
                for (TextElementDifference diff : page.getTextElementDifferences()) {
                    if (diff.isStyleDifferent()) {
                        styleDifferences++;
                    } else {
                        textDifferences++;
                    }
                    totalDifferences++;
                }
            }

            // Count image differences
            if (page.getImageDifferences() != null) {
                int pageDiffs = page.getImageDifferences().size();
                imageDifferences += pageDiffs;
                totalDifferences += pageDiffs;
            }

            // Count font differences
            if (page.getFontDifferences() != null) {
                int pageDiffs = page.getFontDifferences().size();
                fontDifferences += pageDiffs;
                totalDifferences += pageDiffs;
            }
        }

        // Set statistics
        result.setTotalDifferences(totalDifferences);
        result.setTotalTextDifferences(textDifferences);
        result.setTotalImageDifferences(imageDifferences);
        result.setTotalFontDifferences(fontDifferences);
        result.setTotalStyleDifferences(styleDifferences);

        logger.info("Comparison statistics: total={}, text={}, image={}, font={}, style={}",
                totalDifferences, textDifferences, imageDifferences, fontDifferences, styleDifferences);
    }

    private Map<Integer, String> extractTextByPage(List<PDFPageModel> pages) {
        Map<Integer, String> result = new HashMap<>();
        for (int i = 0; i < pages.size(); i++) {
            result.put(i, pages.get(i).getTextContent().toLowerCase());
        }
        return result;
    }


    private Map<Integer, Integer> matchPagesSmartly(Map<Integer, String> basePages, Map<Integer, String> comparePages) {
        Map<Integer, Integer> matches = new HashMap<>();
        Set<Integer> usedCompare = new HashSet<>();
        LevenshteinDistance levenshtein = new LevenshteinDistance();

        double threshold = 0.6; // justera vid behov
        int minLength = 50;     // ignorera väldigt korta sidor

        for (Map.Entry<Integer, String> baseEntry : basePages.entrySet()) {
            int baseIndex = baseEntry.getKey();
            String baseText = baseEntry.getValue();

            if (baseText == null || baseText.length() < minLength) continue;

            double bestScore = 0.0;
            int bestCompareIndex = -1;

            for (Map.Entry<Integer, String> compareEntry : comparePages.entrySet()) {
                int compareIndex = compareEntry.getKey();
                if (usedCompare.contains(compareIndex)) continue;

                String compareText = compareEntry.getValue();
                if (compareText == null || compareText.length() < minLength) continue;

                double jaccard = computeJaccardSimilarity(baseText, compareText);
                double levRatio = computeLevenshteinRatio(levenshtein, baseText, compareText);

                double score = (0.5 * jaccard) + (0.5 * levRatio);

                if (score > bestScore) {
                    bestScore = score;
                    bestCompareIndex = compareIndex;
                }
            }

            if (bestCompareIndex != -1 && bestScore > threshold) {
                matches.put(baseIndex, bestCompareIndex);
                usedCompare.add(bestCompareIndex);
                logger.info("✅ Matched base page {} to compare page {} (score: {})", baseIndex, bestCompareIndex, bestScore);
            } else {
                logger.info("❌ No match for base page {} (best score: {})", baseIndex, bestScore);
            }
        }

        return matches;
    }
    private double computeJaccardSimilarity(String a, String b) {
        Set<String> setA = new HashSet<>(Arrays.asList(a.split("\\s+")));
        Set<String> setB = new HashSet<>(Arrays.asList(b.split("\\s+")));

        Set<String> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);

        Set<String> union = new HashSet<>(setA);
        union.addAll(setB);

        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    private double computeLevenshteinRatio(LevenshteinDistance levenshtein, String s1, String s2) {
        int distance = levenshtein.apply(s1, s2);
        int maxLen = Math.max(s1.length(), s2.length());
        return maxLen == 0 ? 1.0 : 1.0 - ((double) distance / maxLen);
    }


}