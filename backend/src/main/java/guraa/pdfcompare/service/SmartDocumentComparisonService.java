package guraa.pdfcompare.service;

import guraa.pdfcompare.PDFComparisonEngine;
import guraa.pdfcompare.PDFComparisonService;
import guraa.pdfcompare.comparison.PDFComparisonResult;
import guraa.pdfcompare.core.PDFDocumentModel;
import guraa.pdfcompare.core.PDFPageModel;
import guraa.pdfcompare.core.PDFProcessor;
import guraa.pdfcompare.core.SmartDocumentMatcher;
import guraa.pdfcompare.core.SmartDocumentMatcher.DocumentPair;
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

/**
 * Service for smart document comparison that handles multi-document PDFs
 */
@Service
public class SmartDocumentComparisonService {
    private static final Logger logger = LoggerFactory.getLogger(SmartDocumentComparisonService.class);

    private final PDFComparisonService comparisonService;
    private final ExecutorService executorService = Executors.newFixedThreadPool(2);

    // Storage for document matching results
    private Map<String, List<DocumentPair>> matchingResults = new ConcurrentHashMap<>();

    // Storage for comparison results for individual document pairs
    private Map<String, Map<String, PDFComparisonResult>> detailedResults = new ConcurrentHashMap<>();

    @Autowired
    public SmartDocumentComparisonService(PDFComparisonService comparisonService) {
        this.comparisonService = comparisonService;
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

        startAsyncSmartComparison(comparisonId, baseFilePath, compareFilePath);

        return comparisonId;
    }

    /**
     * Start asynchronous smart comparison process
     */
    private void startAsyncSmartComparison(String comparisonId, String baseFilePath, String compareFilePath) {
        executorService.submit(() -> {
            try {
                logger.info("Starting smart comparison {} between {} and {}",
                        comparisonId, baseFilePath, compareFilePath);

                // Step 1: Process both documents
                File baseFile = new File(baseFilePath);
                File compareFile = new File(compareFilePath);

                PDFProcessor processor = new PDFProcessor();
                PDFDocumentModel baseDocument = processor.processDocument(baseFile);
                PDFDocumentModel compareDocument = processor.processDocument(compareFile);

                // Step 2: Perform document matching
                SmartDocumentMatcher matcher = new SmartDocumentMatcher();
                List<DocumentPair> matchedPairs = matcher.matchDocuments(baseDocument, compareDocument, baseFile, compareFile);

                // Store matching results
                matchingResults.put(comparisonId, matchedPairs);

                // Step 3: Compare each matched document pair
                Map<String, PDFComparisonResult> pairResults = new HashMap<>();
                PDFComparisonEngine engine = new PDFComparisonEngine();

                for (int i = 0; i < matchedPairs.size(); i++) {
                    DocumentPair pair = matchedPairs.get(i);
                    String pairId = comparisonId + "_" + i;

                    if (pair.isMatched()) {
                        // Extract document models for just this pair
                        PDFDocumentModel basePart = extractDocumentPart(
                                baseDocument, pair.getBaseStartPage(), pair.getBaseEndPage());
                        PDFDocumentModel comparePart = extractDocumentPart(
                                compareDocument, pair.getCompareStartPage(), pair.getCompareEndPage());

                        // Compare this document pair
                        PDFComparisonResult pairResult = engine.compareDocuments(basePart, comparePart);
                        pairResults.put(pairId, pairResult);

                        logger.info("Compared document pair {} (pages {}-{} vs {}-{}) with {} differences",
                                i, pair.getBaseStartPage(), pair.getBaseEndPage(),
                                pair.getCompareStartPage(), pair.getCompareEndPage(),
                                pairResult.getTotalDifferences());
                    } else if (pair.hasBaseDocument()) {
                        // Document only exists in base PDF
                        logger.info("Document pair {} (pages {}-{}) only exists in base PDF",
                                i, pair.getBaseStartPage(), pair.getBaseEndPage());
                    } else if (pair.hasCompareDocument()) {
                        // Document only exists in compare PDF
                        logger.info("Document pair {} (pages {}-{}) only exists in compare PDF",
                                i, pair.getCompareStartPage(), pair.getCompareEndPage());
                    }
                }

                // Store all pair results
                detailedResults.put(comparisonId, pairResults);

                logger.info("Smart comparison {} completed successfully with {} document pairs",
                        comparisonId, matchedPairs.size());

            } catch (Exception e) {
                logger.error("Error in smart comparison {}: {}", comparisonId, e.getMessage(), e);
            }
        });
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
        PDFDocumentModel part = new PDFDocumentModel();
        part.setFileName(document.getFileName());
        part.setMetadata(document.getMetadata());

        // Extract only the pages in the range
        List<PDFPageModel> pages = new ArrayList<>();
        for (int i = startPage; i <= endPage && i < document.getPages().size(); i++) {
            pages.add(document.getPages().get(i));
        }

        part.setPages(pages);
        part.setPageCount(pages.size());

        return part;
    }

    /**
     * Get comparison result for a specific document pair
     *
     * @param comparisonId The master comparison ID
     * @param pairIndex The index of the document pair
     * @return Comparison result for the document pair
     */
    public PDFComparisonResult getDocumentPairResult(String comparisonId, int pairIndex) {
        if (!detailedResults.containsKey(comparisonId)) {
            return null;
        }

        String pairId = comparisonId + "_" + pairIndex;
        return detailedResults.get(comparisonId).get(pairId);
    }

    /**
     * Get all document pairs found in the comparison
     *
     * @param comparisonId The comparison ID
     * @return List of document pairs
     */
    public List<DocumentPair> getDocumentPairs(String comparisonId) {
        return matchingResults.getOrDefault(comparisonId, new ArrayList<>());
    }

    /**
     * Get a summary of all document pairs and their comparison results
     *
     * @param comparisonId The comparison ID
     * @return Map with summary information
     */
    public Map<String, Object> getComparisonSummary(String comparisonId) {
        Map<String, Object> summary = new HashMap<>();

        List<DocumentPair> pairs = getDocumentPairs(comparisonId);
        if (pairs.isEmpty()) {
            return summary;
        }

        // Basic information
        summary.put("comparisonId", comparisonId);
        summary.put("documentPairCount", pairs.size());

        // Create list of pair summaries
        List<Map<String, Object>> pairSummaries = new ArrayList<>();
        Map<String, PDFComparisonResult> results = detailedResults.getOrDefault(comparisonId, new HashMap<>());

        for (int i = 0; i < pairs.size(); i++) {
            DocumentPair pair = pairs.get(i);
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
            String pairId = comparisonId + "_" + i;
            PDFComparisonResult result = results.get(pairId);
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

        return summary;
    }

    /**
     * Check if a comparison is ready
     *
     * @param comparisonId The comparison ID
     * @return true if the comparison is complete
     */
    public boolean isComparisonReady(String comparisonId) {
        return matchingResults.containsKey(comparisonId) && detailedResults.containsKey(comparisonId);
    }

    /**
     * Clean up expired comparison results
     */
    public void cleanupExpiredResults() {
        // This would use the same expiration logic as in PDFComparisonService
        // For simplicity, just delegating to that service's cleanup method
        comparisonService.cleanupExpiredResults();
    }
}