package guraa.pdfcompare.service;

import guraa.pdfcompare.PDFComparisonEngine;
import guraa.pdfcompare.comparison.PDFComparisonResult;
import guraa.pdfcompare.comparison.MetadataDifference;
import guraa.pdfcompare.comparison.PageComparisonResult;
import guraa.pdfcompare.core.PDFDocumentModel;
import guraa.pdfcompare.core.PDFPageModel;
import guraa.pdfcompare.core.TextSimilarityCalculator;
import guraa.pdfcompare.visual.VisualMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for comparing PDFs by matching individual pages based on similarity
 */
@Service
public class PageMatchingComparisonService {
    private static final Logger logger = LoggerFactory.getLogger(PageMatchingComparisonService.class);

    // Threshold for considering pages as matched
    private static final double PAGE_MATCH_THRESHOLD = 0.3;

    // Weight factors for different similarity components
    private static final double TEXT_SIMILARITY_WEIGHT = 0.6;
    private static final double VISUAL_SIMILARITY_WEIGHT = 0.4;

    // Storage for page matching results
    private final Map<String, PageMatchingResult> matchingResults = new ConcurrentHashMap<>();

    @Autowired
    private PDFComparisonEngine comparisonEngine;

    /**
     * Compare two PDFs using page-by-page matching
     *
     * @param baseDocument Base PDF document model
     * @param compareDocument Compare PDF document model
     * @param baseFile Base PDF file for visual hash computation
     * @param compareFile Compare PDF file for visual hash computation
     * @return Comparison result with page matching information
     * @throws IOException If there's an error processing the files
     */
    public PDFComparisonResult compareDocumentsWithPageMatching(
            PDFDocumentModel baseDocument,
            PDFDocumentModel compareDocument,
            File baseFile,
            File compareFile) throws IOException {

        logger.info("Starting page-by-page matching comparison between {} and {}",
                baseDocument.getFileName(), compareDocument.getFileName());

        // Create result object
        PDFComparisonResult result = new PDFComparisonResult();
        result.setBasePageCount(baseDocument.getPageCount());
        result.setComparePageCount(compareDocument.getPageCount());
        result.setPageCountDifferent(baseDocument.getPageCount() != compareDocument.getPageCount());

        // Compare metadata
        Map<String, MetadataDifference> metadataDiffs =
                comparisonEngine.compareMetadata(baseDocument.getMetadata(), compareDocument.getMetadata());
        result.setMetadataDifferences(metadataDiffs);

        // Compute visual hashes for both documents (if files are provided)
        List<String> baseVisualHashes = null;
        List<String> compareVisualHashes = null;

        if (baseFile != null && compareFile != null) {
            try {
                baseVisualHashes = VisualMatcher.computeVisualHashes(baseFile);
                compareVisualHashes = VisualMatcher.computeVisualHashes(compareFile);
                logger.debug("Computed visual hashes: {} for base, {} for compare",
                        baseVisualHashes.size(), compareVisualHashes.size());
            } catch (Exception e) {
                logger.warn("Error computing visual hashes: {}", e.getMessage());
                // Continue without visual hashes
            }
        }

        // Calculate page similarities and find best matches
        PageMatchingResult pageMatches = findBestPageMatches(
                baseDocument, compareDocument, baseVisualHashes, compareVisualHashes);

        // Store the matching result with a unique ID
        String matchingId = UUID.randomUUID().toString();
        matchingResults.put(matchingId, pageMatches);
        result.setMatchingId(matchingId);  // Add this field to PDFComparisonResult

        // Compare matched pages
        List<PageComparisonResult> pageDifferences = compareMatchedPages(
                baseDocument, compareDocument, pageMatches);
        result.setPageDifferences(pageDifferences);

        // Calculate summary statistics
        calculateSummaryStatistics(result);

        logger.info("Page-by-page matching comparison completed. Found {} total differences, " +
                        "{} matched pages, {} unmatched base pages, {} unmatched compare pages",
                result.getTotalDifferences(), pageMatches.getMatchedPages().size(),
                pageMatches.getUnmatchedBasePages().size(), pageMatches.getUnmatchedComparePages().size());

        return result;
    }

    /**
     * Find the best matching pages between two documents
     */
    private PageMatchingResult findBestPageMatches(
            PDFDocumentModel baseDocument,
            PDFDocumentModel compareDocument,
            List<String> baseVisualHashes,
            List<String> compareVisualHashes) {

        logger.info("Finding best page matches between documents");

        // Calculate similarity matrix - each cell [i][j] contains similarity between
        // base page i and compare page j
        double[][] similarityMatrix = calculatePageSimilarityMatrix(
                baseDocument, compareDocument, baseVisualHashes, compareVisualHashes);

        // Find best matches using a greedy algorithm
        Map<Integer, Integer> baseToCompareMatches = new HashMap<>();  // Maps base page to compare page
        Set<Integer> matchedComparePages = new HashSet<>();

        // For each base page, find the best matching compare page
        for (int baseIdx = 0; baseIdx < baseDocument.getPageCount(); baseIdx++) {
            int bestMatchIdx = -1;
            double bestSimilarity = PAGE_MATCH_THRESHOLD;  // Must be above threshold

            for (int compareIdx = 0; compareIdx < compareDocument.getPageCount(); compareIdx++) {
                // Skip already matched compare pages
                if (matchedComparePages.contains(compareIdx)) continue;

                double similarity = similarityMatrix[baseIdx][compareIdx];
                if (similarity > bestSimilarity) {
                    bestSimilarity = similarity;
                    bestMatchIdx = compareIdx;
                }
            }

            // If we found a match above threshold
            if (bestMatchIdx >= 0) {
                baseToCompareMatches.put(baseIdx, bestMatchIdx);
                matchedComparePages.add(bestMatchIdx);
                logger.debug("Matched base page {} to compare page {} with similarity {}",
                        baseIdx + 1, bestMatchIdx + 1, bestSimilarity);
            }
        }

        // Collect unmatched pages
        List<Integer> unmatchedBasePages = new ArrayList<>();
        for (int i = 0; i < baseDocument.getPageCount(); i++) {
            if (!baseToCompareMatches.containsKey(i)) {
                unmatchedBasePages.add(i);
                logger.debug("Base page {} has no match", i + 1);
            }
        }

        List<Integer> unmatchedComparePages = new ArrayList<>();
        for (int i = 0; i < compareDocument.getPageCount(); i++) {
            if (!matchedComparePages.contains(i)) {
                unmatchedComparePages.add(i);
                logger.debug("Compare page {} has no match", i + 1);
            }
        }

        // Create matching result
        PageMatchingResult result = new PageMatchingResult();
        result.setMatchedPages(baseToCompareMatches);
        result.setUnmatchedBasePages(unmatchedBasePages);
        result.setUnmatchedComparePages(unmatchedComparePages);
        result.setSimilarityMatrix(similarityMatrix);

        return result;
    }

    /**
     * Calculate similarity matrix between all pages of two documents
     */
    private double[][] calculatePageSimilarityMatrix(
            PDFDocumentModel baseDocument,
            PDFDocumentModel compareDocument,
            List<String> baseVisualHashes,
            List<String> compareVisualHashes) {

        int basePageCount = baseDocument.getPageCount();
        int comparePageCount = compareDocument.getPageCount();

        double[][] similarityMatrix = new double[basePageCount][comparePageCount];

        // Calculate similarity for each page pair
        for (int baseIdx = 0; baseIdx < basePageCount; baseIdx++) {
            PDFPageModel basePage = baseDocument.getPages().get(baseIdx);

            for (int compareIdx = 0; compareIdx < comparePageCount; compareIdx++) {
                PDFPageModel comparePage = compareDocument.getPages().get(compareIdx);

                // Calculate text similarity
                double textSimilarity = calculateTextSimilarity(basePage, comparePage);

                // Calculate visual similarity if hashes are available
                double visualSimilarity = 0.0;
                if (baseVisualHashes != null && compareVisualHashes != null &&
                        baseIdx < baseVisualHashes.size() && compareIdx < compareVisualHashes.size()) {
                    String baseHash = baseVisualHashes.get(baseIdx);
                    String compareHash = compareVisualHashes.get(compareIdx);
                    visualSimilarity = VisualMatcher.compareHashes(baseHash, compareHash);
                }

                // Combine similarities with weights
                double combinedSimilarity = (textSimilarity * TEXT_SIMILARITY_WEIGHT) +
                        (visualSimilarity * VISUAL_SIMILARITY_WEIGHT);

                similarityMatrix[baseIdx][compareIdx] = combinedSimilarity;

                logger.debug("Similarity between base page {} and compare page {}: {} (text: {}, visual: {})",
                        baseIdx + 1, compareIdx + 1,
                        String.format("%.2f", combinedSimilarity),
                        String.format("%.2f", textSimilarity),
                        String.format("%.2f", visualSimilarity));
            }
        }

        return similarityMatrix;
    }

    /**
     * Calculate text similarity between two pages
     */
    private double calculateTextSimilarity(PDFPageModel basePage, PDFPageModel comparePage) {
        String baseText = basePage.getText();
        String compareText = comparePage.getText();

        if (baseText == null || compareText == null ||
                baseText.trim().isEmpty() || compareText.trim().isEmpty()) {
            return 0.0;
        }

        // Use the text similarity calculator
        TextSimilarityCalculator calculator = new TextSimilarityCalculator();
        return calculator.calculateTextSimilarity(baseText, compareText);
    }

    /**
     * Compare matched pages and generate page comparison results
     */
    private List<PageComparisonResult> compareMatchedPages(
            PDFDocumentModel baseDocument,
            PDFDocumentModel compareDocument,
            PageMatchingResult pageMatches) {

        List<PageComparisonResult> results = new ArrayList<>();

        // Compare matched pages
        for (Map.Entry<Integer, Integer> match : pageMatches.getMatchedPages().entrySet()) {
            int baseIdx = match.getKey();
            int compareIdx = match.getValue();

            PDFPageModel basePage = baseDocument.getPages().get(baseIdx);
            PDFPageModel comparePage = compareDocument.getPages().get(compareIdx);

            PageComparisonResult comparison =
                    comparisonEngine.comparePage(basePage, comparePage);

            // Add page matching information
            comparison.setOriginalBasePageNumber(baseIdx + 1);
            comparison.setOriginalComparePageNumber(compareIdx + 1);

            results.add(comparison);
        }

        // Add unmatched base pages
        for (Integer baseIdx : pageMatches.getUnmatchedBasePages()) {
            PageComparisonResult result =
                    new PageComparisonResult();
            result.setPageNumber(baseIdx + 1);
            result.setOriginalBasePageNumber(baseIdx + 1);
            result.setOnlyInBase(true);
            results.add(result);
        }

        // Add unmatched compare pages
        for (Integer compareIdx : pageMatches.getUnmatchedComparePages()) {
            PageComparisonResult result =
                    new PageComparisonResult();
            result.setPageNumber(compareIdx + 1);
            result.setOriginalComparePageNumber(compareIdx + 1);
            result.setOnlyInCompare(true);
            results.add(result);
        }

        // Sort results by base page number for consistent presentation
        results.sort(Comparator.comparing(page -> {
            Integer basePage = page.getOriginalBasePageNumber();
            return basePage != null ? basePage : Integer.MAX_VALUE;
        }));

        return results;
    }

    /**
     * Calculate summary statistics for comparison result
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
                for (int i = 0; i < page.getTextElementDifferences().size(); i++) {
                    styleDifferences++;
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
    }

    /**
     * Get page matching result by ID
     */
    public PageMatchingResult getPageMatchingResult(String matchingId) {
        return matchingResults.get(matchingId);
    }

    /**
     * Class to store page matching results
     */
    public static class PageMatchingResult {
        private Map<Integer, Integer> matchedPages; // Maps base page index to compare page index
        private List<Integer> unmatchedBasePages;
        private List<Integer> unmatchedComparePages;
        private double[][] similarityMatrix;

        public Map<Integer, Integer> getMatchedPages() {
            return matchedPages;
        }

        public void setMatchedPages(Map<Integer, Integer> matchedPages) {
            this.matchedPages = matchedPages;
        }

        public List<Integer> getUnmatchedBasePages() {
            return unmatchedBasePages;
        }

        public void setUnmatchedBasePages(List<Integer> unmatchedBasePages) {
            this.unmatchedBasePages = unmatchedBasePages;
        }

        public List<Integer> getUnmatchedComparePages() {
            return unmatchedComparePages;
        }

        public void setUnmatchedComparePages(List<Integer> unmatchedComparePages) {
            this.unmatchedComparePages = unmatchedComparePages;
        }

        public double[][] getSimilarityMatrix() {
            return similarityMatrix;
        }

        public void setSimilarityMatrix(double[][] similarityMatrix) {
            this.similarityMatrix = similarityMatrix;
        }
    }
}