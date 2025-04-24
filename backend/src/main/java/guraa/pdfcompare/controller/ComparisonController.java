package guraa.pdfcompare.controller;

import guraa.pdfcompare.model.Comparison;
import guraa.pdfcompare.model.ComparisonResult;
import guraa.pdfcompare.repository.ComparisonRepository;
import guraa.pdfcompare.service.ComparisonResultStorage;
import guraa.pdfcompare.service.ComparisonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Controller for PDF comparison operations with improved status handling.
 */
@Slf4j
@RestController
@RequestMapping("/api/pdfs")
@RequiredArgsConstructor
public class ComparisonController {

    private final ComparisonService comparisonService;
    private final ComparisonRepository comparisonRepository;
    private final ComparisonResultStorage resultStorage;

    @PostConstruct
    public void init() {
        log.info("ComparisonController initialized with base paths: /api/pdfs and /api/pdfs/comparison");
    }

    /**
     * Initiate a comparison between two PDF documents.
     *
     * @param request The comparison request
     * @return Response with comparison ID
     */
    @PostMapping("/compare")
    public ResponseEntity<?> comparePdfs(@RequestBody CompareRequest request) {
        try {
            log.info("Received comparison request: baseFileId={}, compareFileId={}",
                    request.getBaseFileId(), request.getCompareFileId());

            // Validate input
            if (request.getBaseFileId() == null || request.getCompareFileId() == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Both baseFileId and compareFileId are required"));
            }

            // Create the comparison
            Comparison comparison = comparisonService.createComparison(
                    request.getBaseFileId(), request.getCompareFileId());

            // Return the comparison ID
            Map<String, Object> response = new HashMap<>();
            response.put("comparisonId", comparison.getId());
            response.put("status", comparison.getStatus().name());
            response.put("message", "Comparison initiated successfully");

            log.info("Comparison initiated with ID: {}", comparison.getId());
            return ResponseEntity.accepted().body(response);
        } catch (Exception e) {
            log.error("Failed to initiate comparison: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("error", "Failed to compare PDFs: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Check if a comparison is ready using a HEAD request.
     *
     * @param comparisonId The comparison ID
     * @return Appropriate status code based on comparison state
     */
    @RequestMapping(value = "/comparison/{comparisonId}", method = RequestMethod.HEAD)
    public ResponseEntity<?> checkComparisonStatus(@PathVariable String comparisonId) {
        log.debug("HEAD request for comparison status: {}", comparisonId);

        try {
            // First check if result exists, which is the most reliable indicator
            if (resultStorage.resultExists(comparisonId)) {
                log.debug("Result exists for comparison {}", comparisonId);

                // Ensure database status is consistent
                Optional<Comparison> comparisonOpt = comparisonRepository.findById(comparisonId);
                if (comparisonOpt.isPresent() &&
                        comparisonOpt.get().getStatus() != Comparison.ComparisonStatus.COMPLETED) {

                    log.info("Fixing inconsistent status for comparison {}", comparisonId);
                    comparisonService.updateComparisonStatus(comparisonId,
                            Comparison.ComparisonStatus.COMPLETED, null);
                }

                return ResponseEntity.ok().build();
            }

            // Check database status
            Optional<Comparison> comparisonOpt = comparisonRepository.findById(comparisonId);
            if (comparisonOpt.isEmpty()) {
                log.debug("Comparison {} not found", comparisonId);
                return ResponseEntity.notFound().build();
            }

            Comparison comparison = comparisonOpt.get();
            Comparison.ComparisonStatus status = comparison.getStatus();

            // Return appropriate status code
            if (status == Comparison.ComparisonStatus.COMPLETED) {
                return ResponseEntity.ok().build();
            } else if (status == Comparison.ComparisonStatus.PROCESSING ||
                    status == Comparison.ComparisonStatus.PENDING) {
                log.debug("Comparison {} is in progress ({})", comparisonId, status);
                return ResponseEntity.accepted().build();
            } else if (status == Comparison.ComparisonStatus.FAILED) {
                log.debug("Comparison {} failed", comparisonId);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            } else {
                log.debug("Comparison {} is in an unexpected state: {}", comparisonId, status);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (Exception e) {
            log.error("Error checking comparison status for {}: {}", comparisonId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get the status of a comparison.
     *
     * @param comparisonId The comparison ID
     * @return The comparison status
     */
    @GetMapping("/comparison/{comparisonId}/status")
    public ResponseEntity<?> getComparisonStatus(@PathVariable String comparisonId) {
        log.debug("Getting status for comparison: {}", comparisonId);

        try {
            // Check if result exists first (most reliable indicator)
            boolean resultExists = resultStorage.resultExists(comparisonId);

            // Check if comparison exists in database
            Optional<Comparison> comparisonOpt = comparisonRepository.findById(comparisonId);
            if (comparisonOpt.isEmpty()) {
                log.warn("Comparison not found: {}", comparisonId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("status", "NOT_FOUND", "message", "Comparison not found"));
            }

            Comparison comparison = comparisonOpt.get();

            // If result exists but status isn't COMPLETED, fix it
            if (resultExists && comparison.getStatus() != Comparison.ComparisonStatus.COMPLETED) {
                log.info("Result exists but comparison {} status is {}. Fixing to COMPLETED.",
                        comparisonId, comparison.getStatus());
                comparisonService.updateComparisonStatus(comparisonId,
                        Comparison.ComparisonStatus.COMPLETED, null);
                comparison.setStatus(Comparison.ComparisonStatus.COMPLETED);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("status", comparison.getStatus().name());
            response.put("comparisonId", comparisonId);
            response.put("resultReady", resultExists || comparison.getStatus() == Comparison.ComparisonStatus.COMPLETED);

            if (comparison.getErrorMessage() != null) {
                response.put("errorMessage", comparison.getErrorMessage());
            }

            // Add timing information for PROCESSING status
            if (comparison.getStatus() == Comparison.ComparisonStatus.PROCESSING ||
                    comparison.getStatus() == Comparison.ComparisonStatus.PENDING) {

                LocalDateTime now = LocalDateTime.now();
                Duration duration = Duration.between(comparison.getCreatedAt(), now);
                response.put("elapsedTimeSeconds", duration.getSeconds());
                response.put("startedAt", comparison.getCreatedAt());

                // Flag as potentially stuck if running for too long
                boolean potentiallyStuck = duration.toMinutes() > 5; // 5 minutes
                response.put("potentiallyStuck", potentiallyStuck);
            }

            HttpStatus httpStatus;
            if (comparison.getStatus() == Comparison.ComparisonStatus.COMPLETED) {
                httpStatus = HttpStatus.OK;
            } else if (comparison.getStatus() == Comparison.ComparisonStatus.PROCESSING ||
                    comparison.getStatus() == Comparison.ComparisonStatus.PENDING) {
                httpStatus = HttpStatus.ACCEPTED;
            } else if (comparison.getStatus() == Comparison.ComparisonStatus.FAILED) {
                httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
            } else {
                httpStatus = HttpStatus.OK;
            }

            return ResponseEntity.status(httpStatus).body(response);
        } catch (Exception e) {
            log.error("Error getting comparison status for {}: {}", comparisonId, e.getMessage(), e);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "ERROR");
            response.put("message", "Failed to get comparison status: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Get comparison result with improved consistency checking.
     *
     * @param comparisonId The comparison ID
     * @return The comparison result
     */
    @GetMapping("/comparison/{comparisonId}")
    public ResponseEntity<?> getComparisonResult(@PathVariable String comparisonId) {
        log.info("Getting result for comparison: {}", comparisonId);

        try {
            // First check if result exists, which is the most reliable indicator
            if (resultStorage.resultExists(comparisonId)) {
                log.info("Result exists for comparison: {}", comparisonId);

                // Ensure database status is consistent
                Optional<Comparison> comparisonOpt = comparisonRepository.findById(comparisonId);
                if (comparisonOpt.isPresent() &&
                        comparisonOpt.get().getStatus() != Comparison.ComparisonStatus.COMPLETED) {

                    log.info("Fixing inconsistent status for comparison {}", comparisonId);
                    comparisonService.updateComparisonStatus(comparisonId,
                            Comparison.ComparisonStatus.COMPLETED, null);
                }

                // Get the result - should never be null at this point
                ComparisonResult result = resultStorage.retrieveResult(comparisonId);

                if (result == null) {
                    // This should never happen if resultExists returned true
                    log.error("Critical error: Result exists but could not be retrieved for {}", comparisonId);
                    return ResponseEntity.internalServerError().body(Map.of(
                            "error", "Result exists but could not be retrieved"));
                }

                return ResponseEntity.ok(result);
            }

            // Check if comparison exists
            Optional<Comparison> comparisonOpt = comparisonRepository.findById(comparisonId);
            if (comparisonOpt.isEmpty()) {
                log.warn("Comparison not found: {}", comparisonId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Comparison not found", "comparisonId", comparisonId));
            }

            Comparison comparison = comparisonOpt.get();

            // Check status and return appropriate response
            if (comparison.getStatus() == Comparison.ComparisonStatus.PROCESSING ||
                    comparison.getStatus() == Comparison.ComparisonStatus.PENDING) {
                log.info("Comparison {} is still processing ({})", comparisonId, comparison.getStatus());

                LocalDateTime now = LocalDateTime.now();
                Duration duration = Duration.between(comparison.getCreatedAt(), now);

                Map<String, Object> response = new HashMap<>();
                response.put("status", comparison.getStatus().name());
                response.put("message", "Comparison still processing");
                response.put("comparisonId", comparisonId);
                response.put("elapsedTimeSeconds", duration.getSeconds());
                response.put("startedAt", comparison.getCreatedAt());

                // Flag as potentially stuck if running for too long
                boolean potentiallyStuck = duration.toMinutes() > 5; // 5 minutes
                response.put("potentiallyStuck", potentiallyStuck);

                return ResponseEntity.accepted().body(response);

            } else if (comparison.getStatus() == Comparison.ComparisonStatus.FAILED) {
                log.warn("Comparison {} failed: {}", comparisonId, comparison.getErrorMessage());
                Map<String, Object> response = new HashMap<>();
                response.put("status", "FAILED");
                response.put("error", "Comparison failed: " + comparison.getErrorMessage());
                response.put("comparisonId", comparisonId);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }

            // Should be COMPLETED at this point, try to get the result
            ComparisonResult result = resultStorage.retrieveResult(comparisonId);

            if (result == null) {
                log.error("Comparison {} is marked as COMPLETED but result not found", comparisonId);
                Map<String, Object> response = new HashMap<>();
                response.put("status", "ERROR");
                response.put("error", "Comparison result not available");
                response.put("comparisonId", comparisonId);

                // Mark as failed since result is missing
                comparisonService.updateComparisonStatus(comparisonId,
                        Comparison.ComparisonStatus.FAILED, "Result is missing");

                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error getting comparison result for {}: {}", comparisonId, e.getMessage(), e);

            Map<String, Object> response = new HashMap<>();
            response.put("error", "Failed to get comparison result: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}