package guraa.pdfcompare.controller;

import guraa.pdfcompare.model.Comparison;
import guraa.pdfcompare.model.ComparisonResult;
import guraa.pdfcompare.model.DocumentPair;
import guraa.pdfcompare.model.PageDetails;
import guraa.pdfcompare.repository.ComparisonRepository;
import guraa.pdfcompare.service.ComparisonResultStorage;
import guraa.pdfcompare.service.ComparisonService;
import guraa.pdfcompare.service.ReportGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Enhanced controller for PDF comparison operations.
 * Provides endpoints for initiating comparisons and retrieving results.
 */
@Slf4j
@RestController
@RequestMapping("/api/pdfs")
@RequiredArgsConstructor
public class ComparisonController {

    private final ComparisonService comparisonService;
    private final ComparisonRepository comparisonRepository;
    private final ReportGenerationService reportService;
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
        Instant startTime = Instant.now();
        log.info("Received comparison request: baseFileId={}, compareFileId={}",
                request.getBaseFileId(), request.getCompareFileId());

        try {
            // Validate input
            if (request.getBaseFileId() == null || request.getCompareFileId() == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Both baseFileId and compareFileId are required"));
            }

            // Create the comparison
            Comparison comparison = comparisonService.createComparison(
                    request.getBaseFileId(), request.getCompareFileId());

            // Log completion time
            Instant endTime = Instant.now();
            Duration processingTime = Duration.between(startTime, endTime);
            log.info("Comparison initiated with ID: {} in {} ms",
                    comparison.getId(), processingTime.toMillis());

            // Return the comparison ID
            Map<String, Object> response = new HashMap<>();
            response.put("comparisonId", comparison.getId());
            response.put("status", comparison.getStatus().name());
            response.put("message", "Comparison initiated successfully");

            return ResponseEntity.accepted().body(response);
        } catch (Exception e) {
            log.error("Failed to initiate comparison: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("error", "Failed to compare PDFs: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Check if a comparison is ready.
     *
     * @param comparisonId The comparison ID
     * @return 200 if ready, 202 if processing, 404 if not found
     */
    @RequestMapping(value = "/comparison/{comparisonId}", method = RequestMethod.HEAD)
    public ResponseEntity<?> checkComparisonStatus(@PathVariable String comparisonId) {
        log.debug("HEAD request for comparison status: {}", comparisonId);

        try {
            // First check if comparison exists
            Optional<Comparison> comparisonOpt = comparisonRepository.findById(comparisonId);
            if (comparisonOpt.isEmpty()) {
                log.debug("Comparison {} not found", comparisonId);
                return ResponseEntity.notFound().build();
            }

            Comparison comparison = comparisonOpt.get();
            Comparison.ComparisonStatus status = comparison.getStatus();

            // Return appropriate status code
            if (status == Comparison.ComparisonStatus.COMPLETED) {
                log.debug("Comparison {} is ready", comparisonId);

                // Double-check result is actually available
                if (resultStorage.resultExists(comparisonId)) {
                    return ResponseEntity.ok().build();
                } else {
                    log.warn("Comparison {} marked as COMPLETED but result not found in storage", comparisonId);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
                }
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
            // Check if comparison exists
            Optional<Comparison> comparisonOpt = comparisonRepository.findById(comparisonId);
            if (comparisonOpt.isEmpty()) {
                log.warn("Comparison not found: {}", comparisonId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("status", "NOT_FOUND", "message", "Comparison not found"));
            }

            Comparison comparison = comparisonOpt.get();
            String status = comparison.getStatus().name();

            Map<String, Object> response = new HashMap<>();
            response.put("status", status);
            response.put("comparisonId", comparisonId);

            if (comparison.getErrorMessage() != null) {
                response.put("errorMessage", comparison.getErrorMessage());
            }

            if (comparison.getStatus() == Comparison.ComparisonStatus.COMPLETED) {
                return ResponseEntity.ok(response);
            } else if (comparison.getStatus() == Comparison.ComparisonStatus.PROCESSING ||
                    comparison.getStatus() == Comparison.ComparisonStatus.PENDING) {
                return ResponseEntity.accepted().body(response);
            } else if (comparison.getStatus() == Comparison.ComparisonStatus.FAILED) {
                response.put("message", "Comparison failed");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            } else {
                return ResponseEntity.ok(response);
            }
        } catch (Exception e) {
            log.error("Error getting comparison status for {}: {}", comparisonId, e.getMessage(), e);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "ERROR");
            response.put("message", "Failed to get comparison status: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Get comparison result with improved status checking.
     *
     * @param comparisonId The comparison ID
     * @return The comparison result
     */
    @GetMapping("/comparison/{comparisonId}")
    public ResponseEntity<?> getComparisonResult(@PathVariable String comparisonId) {
        log.info("Getting result for comparison: {}", comparisonId);

        try {
            // First check directly if the result exists, regardless of status
            if (resultStorage.resultExists(comparisonId)) {
                log.info("Result exists for comparison: {}", comparisonId);

                // Get the comparison to check its status
                Optional<Comparison> comparisonOpt = comparisonRepository.findById(comparisonId);
                if (comparisonOpt.isPresent()) {
                    Comparison comparison = comparisonOpt.get();

                    // If status is not COMPLETED, fix it
                    if (comparison.getStatus() != Comparison.ComparisonStatus.COMPLETED) {
                        log.warn("Result exists but comparison {} status is {}. Fixing to COMPLETED.",
                                comparisonId, comparison.getStatus());
                        comparisonService.updateComparisonStatus(comparisonId,
                                Comparison.ComparisonStatus.COMPLETED, null);
                    }

                    // Retrieve and return the result directly
                    ComparisonResult result = resultStorage.retrieveResult(comparisonId);
                    if (result != null) {
                        return ResponseEntity.ok(result);
                    } else {
                        log.error("Result exists but couldn't be retrieved for comparison: {}", comparisonId);
                    }
                }
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
                Map<String, Object> response = new HashMap<>();
                response.put("status", comparison.getStatus().name());
                response.put("message", "Comparison still processing");
                response.put("comparisonId", comparisonId);
                return ResponseEntity.accepted().body(response);
            } else if (comparison.getStatus() == Comparison.ComparisonStatus.FAILED) {
                log.warn("Comparison {} failed: {}", comparisonId, comparison.getErrorMessage());
                Map<String, Object> response = new HashMap<>();
                response.put("status", "FAILED");
                response.put("error", "Comparison failed: " + comparison.getErrorMessage());
                response.put("comparisonId", comparisonId);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }

            // Get the result for COMPLETED status
            ComparisonResult result = comparisonService.getComparisonResult(comparisonId);

            if (result == null) {
                log.warn("Comparison result not found for {}", comparisonId);
                // Check if the result exists in storage but failed to deserialize
                if (resultStorage.resultExists(comparisonId)) {
                    log.error("Comparison result exists in storage but could not be deserialized for {}", comparisonId);
                    Map<String, Object> response = new HashMap<>();
                    response.put("status", "ERROR");
                    response.put("error", "Comparison result could not be read");
                    response.put("comparisonId", comparisonId);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
                } else {
                    log.error("Comparison {} is marked as completed but no result found in storage", comparisonId);
                    Map<String, Object> response = new HashMap<>();
                    response.put("status", "ERROR");
                    response.put("error", "Comparison result not available");
                    response.put("comparisonId", comparisonId);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
                }
            }

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error getting comparison result for {}: {}", comparisonId, e.getMessage(), e);

            Map<String, Object> response = new HashMap<>();
            response.put("error", "Failed to get comparison result: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Emergency fix endpoint to force update a comparison status.
     * Add this directly to your existing ComparisonController class.
     *
     * @param comparisonId The comparison ID
     * @return Success or error response
     */
    @PostMapping("/comparison/{comparisonId}/force-completion")
    public ResponseEntity<?> forceCompletion(@PathVariable String comparisonId) {
        try {
            Optional<Comparison> compOpt = comparisonRepository.findById(comparisonId);
            if (compOpt.isEmpty()) {
                log.warn("Comparison not found for force completion: {}", comparisonId);
                return ResponseEntity.notFound().build();
            }

            Comparison comparison = compOpt.get();
            log.info("Forcing completion of comparison: {} from status {}",
                    comparisonId, comparison.getStatus());

            // Force update to COMPLETED
            comparison.setStatus(Comparison.ComparisonStatus.COMPLETED);
            comparisonRepository.save(comparison);

            log.info("Successfully forced completion of comparison: {}", comparisonId);

            Map<String, Object> result = new HashMap<>();
            result.put("comparisonId", comparisonId);
            result.put("status", "COMPLETED");
            result.put("message", "Forced completion successful");

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error forcing completion of comparison: {}", comparisonId, e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to force completion: " + e.getMessage()));
        }
    }

    /**
     * Emergency fix endpoint to clear all stalled comparisons.
     * Add this directly to your existing ComparisonController class.
     *
     * @return Success or error response
     */
    @PostMapping("/comparisons/clear-stalled")
    public ResponseEntity<?> clearStalledComparisons() {
        try {
            List<Comparison> comparisons = comparisonRepository.findAll();
            int updated = 0;

            for (Comparison comparison : comparisons) {
                if (comparison.getStatus() == Comparison.ComparisonStatus.PROCESSING ||
                        comparison.getStatus() == Comparison.ComparisonStatus.PENDING) {

                    log.info("Clearing stalled comparison: {} from status {}",
                            comparison.getId(), comparison.getStatus());

                    comparison.setStatus(Comparison.ComparisonStatus.FAILED);
                    comparison.setErrorMessage("Cleared by emergency fix");
                    comparisonRepository.save(comparison);
                    updated++;
                }
            }

            log.info("Cleared {} stalled comparisons", updated);

            return ResponseEntity.ok(Map.of(
                    "clearedCount", updated,
                    "message", "Successfully cleared " + updated + " stalled comparisons"));
        } catch (Exception e) {
            log.error("Error clearing stalled comparisons", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to clear stalled comparisons: " + e.getMessage()));
        }
    }

    /**
     * Get page-specific comparison details.
     *
     * @param comparisonId The comparison ID
     * @param pageNumber The page number
     * @param types Filter by difference types (optional)
     * @param severity Filter by minimum severity (optional)
     * @param search Search term (optional)
     * @return Page details with differences
     */
    @GetMapping("/comparison/{comparisonId}/page/{pageNumber}")
    public ResponseEntity<?> getPageDetails(
            @PathVariable String comparisonId,
            @PathVariable int pageNumber,
            @RequestParam(required = false) String types,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String search) {

        log.debug("Getting page details for comparison: {}, page: {}, filters: types={}, severity={}, search={}",
                comparisonId, pageNumber, types, severity, search);

        try {
            // Check if comparison exists
            Optional<Comparison> comparisonOpt = comparisonRepository.findById(comparisonId);
            if (comparisonOpt.isEmpty()) {
                log.warn("Comparison not found: {}", comparisonId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Comparison not found", "comparisonId", comparisonId));
            }

            Comparison comparison = comparisonOpt.get();

            // Check if still processing
            if (comparison.getStatus() == Comparison.ComparisonStatus.PROCESSING ||
                    comparison.getStatus() == Comparison.ComparisonStatus.PENDING) {
                log.info("Comparison {} is still processing", comparisonId);
                Map<String, Object> response = new HashMap<>();
                response.put("status", comparison.getStatus().name());
                response.put("message", "Comparison still processing");
                return ResponseEntity.accepted().body(response);
            }

            // Create filter map from query parameters
            Map<String, Object> filters = new HashMap<>();
            if (types != null && !types.isEmpty()) {
                filters.put("types", types.split(","));
            }
            if (severity != null && !severity.isEmpty()) {
                filters.put("severity", severity);
            }
            if (search != null && !search.isEmpty()) {
                filters.put("search", search);
            }

            // Get the page details
            PageDetails pageDetails = comparisonService.getPageDetails(comparisonId, pageNumber, filters);

            if (pageDetails == null) {
                log.warn("Page details not found for comparison: {}, page: {}", comparisonId, pageNumber);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Page details not found",
                                "comparisonId", comparisonId, "pageNumber", pageNumber));
            }

            return ResponseEntity.ok(pageDetails);
        } catch (Exception e) {
            log.error("Error getting page details for comparison: {}, page: {}: {}",
                    comparisonId, pageNumber, e.getMessage(), e);

            Map<String, Object> response = new HashMap<>();
            response.put("error", "Failed to get page details: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }



    /**
     * Get document pairs for smart comparison mode.
     *
     * @param comparisonId The comparison ID
     * @return List of document pairs
     */
    @GetMapping("/comparison/{comparisonId}/documents")
    public ResponseEntity<?> getDocumentPairs(@PathVariable String comparisonId) {
        log.debug("Getting document pairs for comparison: {}", comparisonId);

        try {
            // Check if comparison exists
            Optional<Comparison> comparisonOpt = comparisonRepository.findById(comparisonId);
            if (comparisonOpt.isEmpty()) {
                log.warn("Comparison not found: {}", comparisonId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Comparison not found", "comparisonId", comparisonId));
            }

            Comparison comparison = comparisonOpt.get();

            // Check if still processing
            if (comparison.getStatus() == Comparison.ComparisonStatus.PROCESSING ||
                    comparison.getStatus() == Comparison.ComparisonStatus.PENDING) {
                log.info("Comparison {} is still processing", comparisonId);
                Map<String, Object> response = new HashMap<>();
                response.put("status", comparison.getStatus().name());
                response.put("message", "Document matching still processing");
                return ResponseEntity.accepted().body(response);
            }

            // Get the document pairs
            List<DocumentPair> documentPairs = comparisonService.getDocumentPairs(comparisonId);

            if (documentPairs == null) {
                log.warn("No document pairs found for comparison: {}", comparisonId);
                // Send an empty list instead of 404 to prevent frontend issues
                return ResponseEntity.ok(List.of());
            }

            return ResponseEntity.ok(documentPairs);
        } catch (Exception e) {
            log.error("Error getting document pairs for comparison {}: {}", comparisonId, e.getMessage(), e);

            Map<String, Object> response = new HashMap<>();
            response.put("error", "Failed to get document pairs: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Get comparison result for a specific document pair.
     *
     * @param comparisonId The comparison ID
     * @param pairIndex The pair index
     * @return Comparison result for the document pair
     */
    @GetMapping("/comparison/{comparisonId}/documents/{pairIndex}")
    public ResponseEntity<?> getDocumentPairResult(
            @PathVariable String comparisonId,
            @PathVariable int pairIndex) {

        log.debug("Getting document pair result for comparison: {}, pair index: {}", comparisonId, pairIndex);

        try {
            // Check if comparison exists
            Optional<Comparison> comparisonOpt = comparisonRepository.findById(comparisonId);
            if (comparisonOpt.isEmpty()) {
                log.warn("Comparison not found: {}", comparisonId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Comparison not found", "comparisonId", comparisonId));
            }

            Comparison comparison = comparisonOpt.get();

            // Check if still processing
            if (comparison.getStatus() == Comparison.ComparisonStatus.PROCESSING ||
                    comparison.getStatus() == Comparison.ComparisonStatus.PENDING) {
                log.info("Comparison {} is still processing", comparisonId);
                Map<String, Object> response = new HashMap<>();
                response.put("status", comparison.getStatus().name());
                response.put("message", "Comparison still processing");
                return ResponseEntity.accepted().body(response);
            }

            // First check if this document pair exists
            List<DocumentPair> documentPairs = comparisonService.getDocumentPairs(comparisonId);
            if (documentPairs == null || documentPairs.isEmpty() || pairIndex >= documentPairs.size()) {
                log.warn("Document pair {} not found in comparison {}", pairIndex, comparisonId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Document pair not found",
                                "comparisonId", comparisonId, "pairIndex", pairIndex));
            }

            // Get the result for this document pair
            ComparisonResult result = comparisonService.getDocumentPairResult(comparisonId, pairIndex);

            if (result == null) {
                log.warn("Document pair result not found for comparison: {}, pair index: {}",
                        comparisonId, pairIndex);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Document pair result not found",
                                "comparisonId", comparisonId, "pairIndex", pairIndex));
            }

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error getting document pair result for comparison: {}, pair index: {}: {}",
                    comparisonId, pairIndex, e.getMessage(), e);

            Map<String, Object> response = new HashMap<>();
            response.put("error", "Failed to get document pair result: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Get page-specific comparison details for a document pair.
     *
     * @param comparisonId The comparison ID
     * @param pairIndex The pair index
     * @param pageNumber The page number (relative to the document pair)
     * @param types Filter by difference types (optional)
     * @param severity Filter by minimum severity (optional)
     * @param search Search term (optional)
     * @return Page details with differences
     */
    @GetMapping("/comparison/{comparisonId}/documents/{pairIndex}/page/{pageNumber}")
    public ResponseEntity<?> getDocumentPairPageDetails(
            @PathVariable String comparisonId,
            @PathVariable int pairIndex,
            @PathVariable int pageNumber,
            @RequestParam(required = false) String types,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String search) {

        log.debug("Getting page details for comparison: {}, pair index: {}, page: {}, filters: types={}, severity={}, search={}",
                comparisonId, pairIndex, pageNumber, types, severity, search);

        try {
            // Create filter map from query parameters
            Map<String, Object> filters = new HashMap<>();
            if (types != null && !types.isEmpty()) {
                filters.put("types", types.split(","));
                log.info("Filtering by types: {}", types);
            }
            if (severity != null && !severity.isEmpty()) {
                filters.put("severity", severity);
            }
            if (search != null && !search.isEmpty()) {
                filters.put("search", search);
            }

            // Log request details
            log.info("Received request for page {} of document pair {} in comparison {} with filters: {}",
                    pageNumber, pairIndex, comparisonId, filters);

            // Check if comparison exists and status
            Optional<Comparison> comparisonOpt = comparisonRepository.findById(comparisonId);
            if (comparisonOpt.isEmpty()) {
                log.warn("Comparison {} not found", comparisonId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Comparison not found", "comparisonId", comparisonId));
            }

            Comparison comparison = comparisonOpt.get();

            // Check if still processing
            if (comparison.getStatus() == Comparison.ComparisonStatus.PROCESSING ||
                    comparison.getStatus() == Comparison.ComparisonStatus.PENDING) {
                log.info("Comparison {} is still processing ({})", comparisonId, comparison.getStatus());
                Map<String, Object> response = new HashMap<>();
                response.put("status", comparison.getStatus().name());
                response.put("message", "Comparison still processing");
                return ResponseEntity.accepted().body(response);
            } else if (comparison.getStatus() == Comparison.ComparisonStatus.FAILED) {
                log.warn("Comparison {} failed: {}", comparisonId, comparison.getErrorMessage());
                Map<String, Object> response = new HashMap<>();
                response.put("status", "FAILED");
                response.put("error", "Comparison failed: " + comparison.getErrorMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }

            // First check if the document pair exists
            List<DocumentPair> documentPairs = comparisonService.getDocumentPairs(comparisonId);
            if (documentPairs == null || documentPairs.isEmpty() || pairIndex >= documentPairs.size()) {
                log.warn("Document pair {} not found in comparison {}", pairIndex, comparisonId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Document pair not found",
                                "comparisonId", comparisonId, "pairIndex", pairIndex));
            }

            DocumentPair pair = documentPairs.get(pairIndex);
            int basePageCount = pair.getBasePageCount();
            int comparePageCount = pair.getComparePageCount();
            int maxPageCount = Math.max(basePageCount, comparePageCount);

            if (pageNumber < 1 || pageNumber > maxPageCount) {
                log.warn("Page {} not found in document pair {} of comparison {}",
                        pageNumber, pairIndex, comparisonId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Page not found in document pair",
                                "maxPage", maxPageCount,
                                "comparisonId", comparisonId,
                                "pairIndex", pairIndex,
                                "pageNumber", pageNumber));
            }

            // Get the page details
            PageDetails pageDetails = comparisonService.getDocumentPairPageDetails(
                    comparisonId, pairIndex, pageNumber, filters);

            if (pageDetails == null) {
                log.warn("Page details not found for page {} of document pair {} in comparison {}",
                        pageNumber, pairIndex, comparisonId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Page details not found",
                                "comparisonId", comparisonId,
                                "pairIndex", pairIndex,
                                "pageNumber", pageNumber));
            }

            return ResponseEntity.ok(pageDetails);
        } catch (Exception e) {
            log.error("Error getting document pair page details for comparison: {}, pair index: {}, page: {}: {}",
                    comparisonId, pairIndex, pageNumber, e.getMessage(), e);

            Map<String, Object> response = new HashMap<>();
            response.put("error", "Failed to get document pair page details: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Generate a report for the comparison.
     *
     * @param comparisonId The comparison ID
     * @param request The report generation request
     * @return The generated report file
     */
    @PostMapping("/comparison/{comparisonId}/report")
    public ResponseEntity<?> generateReport(
            @PathVariable String comparisonId,
            @RequestBody ReportRequest request) {

        log.info("Generating report for comparison: {} in format: {}", comparisonId, request.getFormat());

        try {
            // Check if comparison exists
            Optional<Comparison> comparisonOpt = comparisonRepository.findById(comparisonId);
            if (comparisonOpt.isEmpty()) {
                log.warn("Comparison not found: {}", comparisonId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Comparison not found", "comparisonId", comparisonId));
            }

            Comparison comparison = comparisonOpt.get();

            // Check if still processing
            if (comparison.getStatus() == Comparison.ComparisonStatus.PROCESSING ||
                    comparison.getStatus() == Comparison.ComparisonStatus.PENDING) {
                log.info("Comparison {} is still processing", comparisonId);
                Map<String, Object> response = new HashMap<>();
                response.put("status", comparison.getStatus().name());
                response.put("message", "Cannot generate report while comparison is still processing");
                return ResponseEntity.accepted().body(response);
            }

            String format = request.getFormat() != null ? request.getFormat() : "pdf";
            Map<String, Object> options = request.getOptions() != null ? request.getOptions() : new HashMap<>();

            Resource reportResource = reportService.generateReport(comparisonId, format, options);

            if (reportResource == null) {
                log.error("Failed to generate report for comparison: {}", comparisonId);
                return ResponseEntity.internalServerError()
                        .body(Map.of("error", "Failed to generate report", "comparisonId", comparisonId));
            }

            // Determine content type based on format
            MediaType mediaType;
            String filename;

            switch (format.toLowerCase()) {
                case "html":
                    mediaType = MediaType.TEXT_HTML;
                    filename = "comparison_report.html";
                    break;
                case "json":
                    mediaType = MediaType.APPLICATION_JSON;
                    filename = "comparison_data.json";
                    break;
                case "pdf":
                default:
                    mediaType = MediaType.APPLICATION_PDF;
                    filename = "comparison_report.pdf";
                    break;
            }

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(reportResource);
        } catch (Exception e) {
            log.error("Error generating report for comparison {}: {}", comparisonId, e.getMessage(), e);

            Map<String, Object> response = new HashMap<>();
            response.put("error", "Failed to generate report: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Request object for report generation.
     */
    public static class ReportRequest {
        private String format;
        private Map<String, Object> options;

        public String getFormat() { return format; }
        public void setFormat(String format) { this.format = format; }

        public Map<String, Object> getOptions() { return options; }
        public void setOptions(Map<String, Object> options) { this.options = options; }
    }
}