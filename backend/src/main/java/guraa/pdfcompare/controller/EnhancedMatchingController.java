package guraa.pdfcompare.controller;

import guraa.pdfcompare.model.ComparisonRequest;
import guraa.pdfcompare.service.EnhancedMatchingService;
import guraa.pdfcompare.service.FileStorageService;
import guraa.pdfcompare.comparison.PageComparisonResult;
import guraa.pdfcompare.service.PagePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Controller for enhanced PDF comparison operations
 */
@RestController
@RequestMapping("/api/pdfs/enhanced")
public class EnhancedMatchingController {

    private static final Logger logger = LoggerFactory.getLogger(EnhancedMatchingController.class);
    private static final long MAX_WAIT_TIME_MS = 300000; // 5 minutes max wait time

    private final EnhancedMatchingService matchingService;
    private final FileStorageService storageService;

    // Track when comparison requests were started
    private final Map<String, Long> comparisonStartTimes = new ConcurrentHashMap<>();

    @Autowired
    public EnhancedMatchingController(
            EnhancedMatchingService matchingService,
            FileStorageService storageService) {
        this.matchingService = matchingService;
        this.storageService = storageService;
    }

    /**
     * Compare two PDF documents using enhanced matching
     * @param request The comparison request
     * @return Response with comparison ID
     */
    @PostMapping("/compare")
    public ResponseEntity<Map<String, String>> compareDocuments(@RequestBody ComparisonRequest request) {
        try {
            // Validate request
            if (request.getBaseFileId() == null || request.getCompareFileId() == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Both base and compare file IDs are required"));
            }

            // Get file paths
            String baseFilePath = storageService.getFilePath(request.getBaseFileId());
            String compareFilePath = storageService.getFilePath(request.getCompareFileId());

            logger.info("Starting enhanced comparison between files: base={}, compare={}",
                    request.getBaseFileId(), request.getCompareFileId());

            // Start comparison
            String comparisonId = matchingService.compareFiles(baseFilePath, compareFilePath);
            logger.info("Enhanced comparison started with ID: {}", comparisonId);

            // Track start time
            comparisonStartTimes.put(comparisonId, System.currentTimeMillis());

            // Create response
            Map<String, String> response = new HashMap<>();
            response.put("comparisonId", comparisonId);
            response.put("mode", "enhanced");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to compare documents: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to compare documents: " + e.getMessage()));
        }
    }

    /**
     * Get comparison result
     * @param comparisonId The comparison ID
     * @return The comparison result
     */
    @GetMapping("/comparison/{comparisonId}")
    public ResponseEntity<?> getComparisonResult(@PathVariable String comparisonId) {
        logger.info("Received request for enhanced comparison result: {}", comparisonId);

        // Check for timeout
        Long startTime = comparisonStartTimes.get(comparisonId);
        if (startTime != null && System.currentTimeMillis() - startTime > MAX_WAIT_TIME_MS) {
            logger.warn("Comparison {} has exceeded maximum wait time of {} ms", comparisonId, MAX_WAIT_TIME_MS);
            comparisonStartTimes.remove(comparisonId);
            Map<String, Object> timeoutResponse = new HashMap<>();
            timeoutResponse.put("status", "timeout");
            timeoutResponse.put("message", "Comparison is taking too long to complete. Please try again with smaller files.");
            return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(timeoutResponse);
        }

        // Check if comparison is ready
        if (matchingService.isComparisonReady(comparisonId)) {
            logger.info("Returning enhanced comparison result for ID: {}", comparisonId);
            Map<String, Object> summary = matchingService.getComparisonSummary(comparisonId);

            // Check if comparison failed
            if ("failed".equals(summary.get("status"))) {
                logger.error("Comparison {} failed: {}", comparisonId, summary.get("error"));
                comparisonStartTimes.remove(comparisonId); // Clean up tracking
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(summary);
            }

            comparisonStartTimes.remove(comparisonId); // Clean up tracking
            return ResponseEntity.ok(summary);
        }

        // Get a progress update
        Map<String, Object> summary = matchingService.getComparisonSummary(comparisonId);
        logger.info("Comparison {} is still processing", comparisonId);

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .header("X-Comparison-Status", "processing")
                .body(summary);
    }

    /**
     * Get page pairs found in the comparison
     * @param comparisonId The comparison ID
     * @return List of page pairs
     */
    @GetMapping("/comparison/{comparisonId}/pages")
    public ResponseEntity<?> getPagePairs(@PathVariable String comparisonId) {
        // Check for timeout
        Long startTime = comparisonStartTimes.get(comparisonId);
        if (startTime != null && System.currentTimeMillis() - startTime > MAX_WAIT_TIME_MS) {
            logger.warn("Page pairs request for comparison {} has timed out", comparisonId);
            comparisonStartTimes.remove(comparisonId);
            Map<String, Object> timeoutResponse = new HashMap<>();
            timeoutResponse.put("status", "timeout");
            timeoutResponse.put("message", "Comparison is taking too long to complete. Please try again with smaller files.");
            return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(timeoutResponse);
        }

        List<PagePair> pairs = matchingService.getPagePairs(comparisonId);

        if (pairs.isEmpty()) {
            // Check comparison status
            String status = matchingService.getComparisonStatus(comparisonId);
            if ("failed".equals(status)) {
                String error = matchingService.getComparisonError(comparisonId);
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("status", "failed");
                errorResponse.put("error", error != null ? error : "Unknown error occurred");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
            }

            if ("not_found".equals(status)) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .header("X-Comparison-Status", "processing")
                    .build();
        }

        // Convert page pairs to a format suitable for response
        List<Map<String, Object>> pairDetails = new ArrayList<>();
        for (int i = 0; i < pairs.size(); i++) {
            PagePair pair = pairs.get(i);
            Map<String, Object> details = new HashMap<>();

            details.put("pairIndex", i);
            details.put("matched", pair.isMatched());
            details.put("similarityScore", pair.getSimilarityScore());

            if (pair.getBaseFingerprint() != null) {
                details.put("basePage", pair.getBaseFingerprint().getPageIndex() + 1); // 1-based for API

                // Add base page dimensions if available
                if (pair.getBaseFingerprint().getPage() != null) {
                    Map<String, Float> dimensions = new HashMap<>();
                    dimensions.put("width", pair.getBaseFingerprint().getPage().getWidth());
                    dimensions.put("height", pair.getBaseFingerprint().getPage().getHeight());
                    details.put("baseDimensions", dimensions);
                }
            }

            if (pair.getCompareFingerprint() != null) {
                details.put("comparePage", pair.getCompareFingerprint().getPageIndex() + 1); // 1-based for API

                // Add compare page dimensions if available
                if (pair.getCompareFingerprint().getPage() != null) {
                    Map<String, Float> dimensions = new HashMap<>();
                    dimensions.put("width", pair.getCompareFingerprint().getPage().getWidth());
                    dimensions.put("height", pair.getCompareFingerprint().getPage().getHeight());
                    details.put("compareDimensions", dimensions);
                }
            }

            // Add comparison results if available
            PageComparisonResult result = matchingService.getPageResult(comparisonId, i);
            if (result != null) {
                details.put("changeType", result.getChangeType());
                details.put("hasDifferences", result.isHasDifferences());
                details.put("differenceCount", result.getTotalDifferences());

                if (result.hasError()) {
                    details.put("error", result.getError());
                }
            }

            pairDetails.add(details);
        }

        return ResponseEntity.ok(pairDetails);
    }

    /**
     * Get detailed result for a specific page pair
     * @param comparisonId The comparison ID
     * @param pairIndex The page pair index
     * @return Detailed result for the page pair
     */
    @GetMapping("/comparison/{comparisonId}/pages/{pairIndex}")
    public ResponseEntity<?> getPagePairResult(
            @PathVariable String comparisonId,
            @PathVariable int pairIndex) {

        logger.info("Received request for page pair result: comparison={}, pairIndex={}",
                comparisonId, pairIndex);

        // Check if comparison is ready
        if (!matchingService.isComparisonReady(comparisonId)) {
            logger.info("Comparison {} is not ready yet", comparisonId);
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .header("X-Comparison-Status", "processing")
                    .build();
        }

        // Get page pairs
        List<PagePair> pairs = matchingService.getPagePairs(comparisonId);
        if (pairs.isEmpty() || pairIndex < 0 || pairIndex >= pairs.size()) {
            logger.warn("Page pair not found: comparison={}, pairIndex={}", comparisonId, pairIndex);
            return ResponseEntity.notFound().build();
        }

        // Get page pair result
        PageComparisonResult result = matchingService.getPageResult(comparisonId, pairIndex);
        if (result == null) {
            logger.warn("Page pair result not available: comparison={}, pairIndex={}", comparisonId, pairIndex);
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .header("X-Comparison-Status", "processing")
                    .build();
        }

        // Convert result to response format
        Map<String, Object> response = new HashMap<>();
        PagePair pair = pairs.get(pairIndex);

        // Add pair info
        response.put("pairIndex", pairIndex);
        response.put("matched", pair.isMatched());
        response.put("similarityScore", pair.getSimilarityScore());
        response.put("changeType", result.getChangeType());
        response.put("hasDifferences", result.isHasDifferences());
        response.put("totalDifferences", result.getTotalDifferences());

        if (result.hasError()) {
            response.put("error", result.getError());
        }

        // Add base page info
        if (pair.getBaseFingerprint() != null) {
            Map<String, Object> baseInfo = new HashMap<>();
            baseInfo.put("pageIndex", pair.getBaseFingerprint().getPageIndex());
            baseInfo.put("pageNumber", pair.getBaseFingerprint().getPageIndex() + 1);

            if (pair.getBaseFingerprint().getPage() != null) {
                baseInfo.put("width", pair.getBaseFingerprint().getPage().getWidth());
                baseInfo.put("height", pair.getBaseFingerprint().getPage().getHeight());
                baseInfo.put("text", pair.getBaseFingerprint().getPage().getText());
            }

            response.put("basePageInfo", baseInfo);
        }

        // Add compare page info
        if (pair.getCompareFingerprint() != null) {
            Map<String, Object> compareInfo = new HashMap<>();
            compareInfo.put("pageIndex", pair.getCompareFingerprint().getPageIndex());
            compareInfo.put("pageNumber", pair.getCompareFingerprint().getPageIndex() + 1);

            if (pair.getCompareFingerprint().getPage() != null) {
                compareInfo.put("width", pair.getCompareFingerprint().getPage().getWidth());
                compareInfo.put("height", pair.getCompareFingerprint().getPage().getHeight());
                compareInfo.put("text", pair.getCompareFingerprint().getPage().getText());
            }

            response.put("comparePageInfo", compareInfo);
        }

        // Add difference details
        if (result.isHasDifferences() && pair.isMatched()) {
            List<Map<String, Object>> differences = new ArrayList<>();

            // Add text differences
            if (result.getTextDifferences() != null &&
                    result.getTextDifferences().getDifferences() != null) {
                for (guraa.pdfcompare.comparison.TextDifferenceItem textDiff :
                        result.getTextDifferences().getDifferences()) {
                    Map<String, Object> diff = new HashMap<>();
                    diff.put("type", "text");
                    diff.put("lineNumber", textDiff.getLineNumber());
                    diff.put("baseText", textDiff.getBaseText());
                    diff.put("compareText", textDiff.getCompareText());
                    diff.put("differenceType", textDiff.getDifferenceType());

                    differences.add(diff);
                }
            }

            // Add image differences
            if (result.getImageDifferences() != null) {
                for (guraa.pdfcompare.comparison.ImageDifference imageDiff :
                        result.getImageDifferences()) {
                    Map<String, Object> diff = new HashMap<>();
                    diff.put("type", "image");
                    diff.put("onlyInBase", imageDiff.isOnlyInBase());
                    diff.put("onlyInCompare", imageDiff.isOnlyInCompare());
                    diff.put("dimensionsDifferent", imageDiff.isDimensionsDifferent());
                    diff.put("positionDifferent", imageDiff.isPositionDifferent());
                    diff.put("formatDifferent", imageDiff.isFormatDifferent());

                    differences.add(diff);
                }
            }

            // Add font differences
            if (result.getFontDifferences() != null) {
                for (guraa.pdfcompare.comparison.FontDifference fontDiff :
                        result.getFontDifferences()) {
                    Map<String, Object> diff = new HashMap<>();
                    diff.put("type", "font");
                    diff.put("onlyInBase", fontDiff.isOnlyInBase());
                    diff.put("onlyInCompare", fontDiff.isOnlyInCompare());
                    diff.put("embeddingDifferent", fontDiff.isEmbeddingDifferent());
                    diff.put("subsetDifferent", fontDiff.isSubsetDifferent());

                    differences.add(diff);
                }
            }

            response.put("differences", differences);
        }

        logger.info("Returning details for page pair {} in comparison {}", pairIndex, comparisonId);
        return ResponseEntity.ok(response);
    }
}