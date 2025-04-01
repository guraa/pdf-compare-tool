package guraa.pdfcompare.controller;

import guraa.pdfcompare.model.ComparisonRequest;
import guraa.pdfcompare.service.FileStorageService;
import guraa.pdfcompare.service.PageLevelComparisonIntegrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Controller for page-level PDF comparison operations
 */
@RestController
@RequestMapping("/api/pdfs/page-level")
public class PageLevelComparisonController {

    private static final Logger logger = LoggerFactory.getLogger(PageLevelComparisonController.class);
    private static final long MAX_WAIT_TIME_MS = 300000; // 5 minutes max wait time for comparison

    private final PageLevelComparisonIntegrationService pageLevelComparisonService;
    private final FileStorageService storageService;

    // Track when comparison requests were started
    private final Map<String, Long> comparisonStartTimes = new ConcurrentHashMap<>();

    @Autowired
    public PageLevelComparisonController(
            PageLevelComparisonIntegrationService pageLevelComparisonService,
            FileStorageService storageService) {
        this.pageLevelComparisonService = pageLevelComparisonService;
        this.storageService = storageService;
    }

    /**
     * Compare two PDF documents using page-level comparison
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

            logger.info("Starting page-level comparison between files: base={}, compare={}",
                    request.getBaseFileId(), request.getCompareFileId());

            try {
                // Start comparison
                String comparisonId = pageLevelComparisonService.compareFiles(baseFilePath, compareFilePath);
                logger.info("Page-level comparison started with ID: {}", comparisonId);

                // Track start time
                comparisonStartTimes.put(comparisonId, System.currentTimeMillis());

                // Create response
                Map<String, String> response = new HashMap<>();
                response.put("comparisonId", comparisonId);
                response.put("mode", "page-level");

                return ResponseEntity.ok(response);
            } catch (Exception e) {
                logger.error("Error starting page-level comparison: {}", e.getMessage(), e);
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "Error starting comparison: " + e.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
            }
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
        logger.info("Received request for page-level comparison result: {}", comparisonId);

        // Check if comparison has timed out
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
        if (pageLevelComparisonService.isComparisonReady(comparisonId)) {
            logger.info("Returning page-level comparison result for ID: {}", comparisonId);
            Map<String, Object> summary = pageLevelComparisonService.getComparisonSummary(comparisonId);

            // Check if comparison failed
            if ("failed".equals(summary.get("status"))) {
                logger.error("Comparison {} failed: {}", comparisonId, summary.get("error"));
                comparisonStartTimes.remove(comparisonId); // Clean up tracking
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(summary);
            }

            comparisonStartTimes.remove(comparisonId); // Clean up tracking
            return ResponseEntity.ok(summary);
        }

        // Comparison is still processing
        logger.info("Page-level comparison result not ready yet: {}", comparisonId);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .header("X-Comparison-Status", "processing")
                .build();
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

        // Check if comparison has timed out
        Long startTime = comparisonStartTimes.get(comparisonId);
        if (startTime != null && System.currentTimeMillis() - startTime > MAX_WAIT_TIME_MS) {
            logger.warn("Page pair request for comparison {} has timed out", comparisonId);
            comparisonStartTimes.remove(comparisonId);
            Map<String, Object> timeoutResponse = new HashMap<>();
            timeoutResponse.put("status", "timeout");
            timeoutResponse.put("message", "Comparison is taking too long to complete. Please try again with smaller files.");
            return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(timeoutResponse);
        }

        // Check if comparison is ready
        if (!pageLevelComparisonService.isComparisonReady(comparisonId)) {
            logger.info("Comparison {} is not ready yet", comparisonId);
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .header("X-Comparison-Status", "processing")
                    .build();
        }

        // Get page pair result
        Map<String, Object> result = pageLevelComparisonService.getPagePairResult(comparisonId, pairIndex);

        if ("not_found".equals(result.get("status"))) {
            logger.warn("Page pair not found: comparison={}, pairIndex={}", comparisonId, pairIndex);
            return ResponseEntity.notFound().build();
        }

        logger.info("Returning page pair result: comparison={}, pairIndex={}", comparisonId, pairIndex);
        return ResponseEntity.ok(result);
    }

    /**
     * Get page-level comparison status
     * @param comparisonId The comparison ID
     * @return Comparison status
     */
    @GetMapping("/comparison/{comparisonId}/status")
    public ResponseEntity<Map<String, Object>> getComparisonStatus(@PathVariable String comparisonId) {
        logger.info("Received request for page-level comparison status: {}", comparisonId);

        String status = pageLevelComparisonService.getComparisonStatus(comparisonId);

        Map<String, Object> response = new HashMap<>();
        response.put("comparisonId", comparisonId);
        response.put("status", status);

        if ("failed".equals(status)) {
            String error = pageLevelComparisonService.getComparisonError(comparisonId);
            response.put("error", error != null ? error : "Unknown error");
        }

        if ("not_found".equals(status)) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(response);
    }
}