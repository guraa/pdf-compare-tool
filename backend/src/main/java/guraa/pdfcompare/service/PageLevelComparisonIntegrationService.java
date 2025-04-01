package guraa.pdfcompare.service;

import guraa.pdfcompare.PDFComparisonService;
import guraa.pdfcompare.core.PDFDocumentModel;
import guraa.pdfcompare.core.PDFProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service that integrates the page-level comparison capability with the existing
 * smart document comparison framework
 */
@Service
public class PageLevelComparisonIntegrationService {
    private static final Logger logger = LoggerFactory.getLogger(PageLevelComparisonIntegrationService.class);

    private final PDFComparisonService comparisonService;
    private final PageLevelComparisonService pageLevelComparisonService;
    private final Map<String, PageLevelComparisonResult> comparisonResults = new ConcurrentHashMap<>();
    private final Map<String, String> comparisonStatuses = new ConcurrentHashMap<>();
    private final Map<String, String> comparisonErrors = new ConcurrentHashMap<>();

    @Autowired
    public PageLevelComparisonIntegrationService(
            PDFComparisonService comparisonService,
            PageLevelComparisonService pageLevelComparisonService) {
        this.comparisonService = comparisonService;
        this.pageLevelComparisonService = pageLevelComparisonService;
    }

    /**
     * Compare files using page-level comparison
     * @param baseFilePath Base file path
     * @param compareFilePath Compare file path
     * @return Comparison ID
     * @throws IOException If there's an error reading files
     */
    public String compareFiles(String baseFilePath, String compareFilePath) throws IOException {
        String comparisonId = UUID.randomUUID().toString();
        comparisonStatuses.put(comparisonId, "processing");

        // Start asynchronous comparison
        Thread comparisonThread = new Thread(() -> {
            try {
                logger.info("Starting page-level comparison {} between {} and {}",
                        comparisonId, baseFilePath, compareFilePath);

                // Validate files
                File baseFile = new File(baseFilePath);
                File compareFile = new File(compareFilePath);

                if (!baseFile.exists() || !baseFile.canRead()) {
                    throw new IOException("Base file does not exist or cannot be read: " + baseFilePath);
                }

                if (!compareFile.exists() || !compareFile.canRead()) {
                    throw new IOException("Compare file does not exist or cannot be read: " + compareFilePath);
                }

                // Process documents
                PDFProcessor processor = new PDFProcessor();
                PDFDocumentModel baseDocument = processor.processDocument(baseFile);
                PDFDocumentModel compareDocument = processor.processDocument(compareFile);

                // Perform page-level comparison
                PageLevelComparisonResult result =
                        pageLevelComparisonService.compareDocuments(baseDocument, compareDocument);

                // Store result
                comparisonResults.put(comparisonId, result);
                comparisonStatuses.put(comparisonId, "completed");

                logger.info("Page-level comparison {} completed successfully", comparisonId);
            } catch (Exception e) {
                logger.error("Error in page-level comparison {}: {}", comparisonId, e.getMessage(), e);
                comparisonStatuses.put(comparisonId, "failed");
                comparisonErrors.put(comparisonId, e.getMessage());
            }
        });

        comparisonThread.setDaemon(true);
        comparisonThread.start();

        return comparisonId;
    }

    /**
     * Check if a comparison is ready
     * @param comparisonId Comparison ID
     * @return true if ready, false otherwise
     */
    public boolean isComparisonReady(String comparisonId) {
        String status = comparisonStatuses.get(comparisonId);
        return "completed".equals(status) || "failed".equals(status);
    }

    /**
     * Get comparison result
     * @param comparisonId Comparison ID
     * @return Comparison result
     */
    public PageLevelComparisonResult getComparisonResult(String comparisonId) {
        return comparisonResults.get(comparisonId);
    }

    /**
     * Get comparison status
     * @param comparisonId Comparison ID
     * @return Comparison status
     */
    public String getComparisonStatus(String comparisonId) {
        return comparisonStatuses.getOrDefault(comparisonId, "not_found");
    }

    /**
     * Get comparison error
     * @param comparisonId Comparison ID
     * @return Comparison error
     */
    public String getComparisonError(String comparisonId) {
        return comparisonErrors.get(comparisonId);
    }

    /**
     * Get comparison summary
     * @param comparisonId Comparison ID
     * @return Summary map
     */
    public Map<String, Object> getComparisonSummary(String comparisonId) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("comparisonId", comparisonId);

        String status = comparisonStatuses.get(comparisonId);
        if (status == null) {
            summary.put("status", "not_found");
            return summary;
        }

        summary.put("status", status);

        if ("failed".equals(status)) {
            summary.put("error", comparisonErrors.getOrDefault(comparisonId, "Unknown error"));
            return summary;
        }

        if ("processing".equals(status)) {
            summary.put("message", "Comparison is still processing");
            return summary;
        }

        // If completed, add summary from result
        PageLevelComparisonResult result = comparisonResults.get(comparisonId);
        if (result != null) {
            PageLevelComparisonSummary resultSummary = result.getSummary();

            summary.put("matchedPageCount", resultSummary.getMatchedPageCount());
            summary.put("unmatchedBasePageCount", resultSummary.getUnmatchedBasePageCount());
            summary.put("unmatchedComparePageCount", resultSummary.getUnmatchedComparePageCount());
            summary.put("identicalPageCount", resultSummary.getIdenticalPageCount());
            summary.put("pagesWithDifferencesCount", resultSummary.getPagesWithDifferencesCount());
            summary.put("totalDifferences", resultSummary.getTotalDifferences());

            // Add high-level statistics
            summary.put("totalBasePages", resultSummary.getMatchedPageCount() + resultSummary.getUnmatchedBasePageCount());
            summary.put("totalComparePages", resultSummary.getMatchedPageCount() + resultSummary.getUnmatchedComparePageCount());
            summary.put("identicalPercentage", calculatePercentage(resultSummary.getIdenticalPageCount(), resultSummary.getMatchedPageCount()));

            // Add page change summary
            List<Map<String, Object>> pageChanges = new ArrayList<>();
            List<PageComparisonResult> pageResults = result.getPageResults();

            for (PageComparisonResult pageResult : pageResults) {
                Map<String, Object> pageChange = new HashMap<>();

                PagePair pagePair = pageResult.getPagePair();
                pageChange.put("changeType", pageResult.getChangeType());

                if (pagePair.getBaseFingerprint() != null) {
                    pageChange.put("basePage", pagePair.getBaseFingerprint().getPageIndex() + 1);
                }

                if (pagePair.getCompareFingerprint() != null) {
                    pageChange.put("comparePage", pagePair.getCompareFingerprint().getPageIndex() + 1);
                }

                if (pagePair.isMatched()) {
                    pageChange.put("similarityScore", String.format("%.2f", pagePair.getSimilarityScore()));
                    pageChange.put("differenceCount", pageResult.getTotalDifferences());
                }

                pageChanges.add(pageChange);
            }

            summary.put("pageChanges", pageChanges);
        }

        return summary;
    }

    /**
     * Calculate percentage
     * @param numerator Numerator
     * @param denominator Denominator
     * @return Percentage string
     */
    private String calculatePercentage(int numerator, int denominator) {
        if (denominator == 0) {
            return "0%";
        }

        double percentage = (double) numerator / denominator * 100;
        return String.format("%.1f%%", percentage);
    }

    /**
     * Get detailed result for a specific page pair
     * @param comparisonId Comparison ID
     * @param pairIndex Pair index
     * @return Detailed result
     */
    public Map<String, Object> getPagePairResult(String comparisonId, int pairIndex) {
        Map<String, Object> result = new HashMap<>();

        PageLevelComparisonResult comparisonResult = comparisonResults.get(comparisonId);
        if (comparisonResult == null) {
            result.put("status", "not_found");
            return result;
        }

        List<PageComparisonResult> pageResults = comparisonResult.getPageResults();
        if (pairIndex < 0 || pairIndex >= pageResults.size()) {
            result.put("status", "not_found");
            return result;
        }

        PageComparisonResult pageResult = pageResults.get(pairIndex);
        result.put("status", "ok");
        result.put("pairIndex", pairIndex);
        result.put("changeType", pageResult.getChangeType());
        result.put("hasDifferences", pageResult.isHasDifferences());
        result.put("totalDifferences", pageResult.getTotalDifferences());

        if (pageResult.getError() != null) {
            result.put("error", pageResult.getError());
        }

        PagePair pagePair = pageResult.getPagePair();
        if (pagePair.getBaseFingerprint() != null) {
            Map<String, Object> baseInfo = new HashMap<>();
            baseInfo.put("pageIndex", pagePair.getBaseFingerprint().getPageIndex());
            baseInfo.put("pageNumber", pagePair.getBaseFingerprint().getPageIndex() + 1);
            result.put("basePageInfo", baseInfo);
        }

        if (pagePair.getCompareFingerprint() != null) {
            Map<String, Object> compareInfo = new HashMap<>();
            compareInfo.put("pageIndex", pagePair.getCompareFingerprint().getPageIndex());
            compareInfo.put("pageNumber", pagePair.getCompareFingerprint().getPageIndex() + 1);
            result.put("comparePageInfo", compareInfo);
        }

        if (pagePair.isMatched()) {
            result.put("similarityScore", pagePair.getSimilarityScore());
        }

        // Add detailed differences if available
        if (pageResult.getPageDifference() != null) {
            result.put("pageDifference", pageResult.getPageDifference());
        }

        return result;
    }
}
