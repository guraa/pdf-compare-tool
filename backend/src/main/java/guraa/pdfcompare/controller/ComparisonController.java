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
            response.put("status", comparison.getStatusAsString().toUpperCase());
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
            if (!comparisonRepository.existsById(comparisonId)) {
                log.debug("Comparison {} not found", comparisonId);
                return ResponseEntity.notFound().build();
            }

            boolean isReady = comparisonService.isComparisonCompleted(comparisonId);
            if (isReady) {
                log.debug("Comparison {} is ready", comparisonId);
                return ResponseEntity.ok().build();
            } else if (comparisonService.isComparisonInProgress(comparisonId)) {
                log.debug("Comparison {} is in progress", comparisonId);
                return ResponseEntity.accepted().build();
            } else {
                log.debug("Comparison {} is in an unexpected state", comparisonId);
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
            String status = comparison.getStatusAsString();

            Map<String, Object> response = new HashMap<>();
            response.put("status", status.toUpperCase());
            response.put("comparisonId", comparisonId);

            if (comparison.getErrorMessage() != null) {
                response.put("errorMessage", comparison.getErrorMessage());
            }

            if ("completed".equalsIgnoreCase(status)) {
                return ResponseEntity.ok(response);
            } else if ("processing".equalsIgnoreCase(status) || "pending".equalsIgnoreCase(status)) {
                return ResponseEntity.accepted().body(response);
            } else if ("failed".equalsIgnoreCase(status)) {
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
     * Get comparison result.
     *
     * @param comparisonId The comparison ID
     * @return The comparison result
     */
    @GetMapping("/comparison/{comparisonId}")
    public ResponseEntity<?> getComparisonResult(@PathVariable String comparisonId) {
        log.info("Getting result for comparison: {}", comparisonId);

        try {
            // Check if comparison exists
            Optional<Comparison> comparisonOpt = comparisonRepository.findById(comparisonId);
            if (comparisonOpt.isEmpty()) {
                log.warn("Comparison not found: {}", comparisonId);
                Map<String, Object> response = new HashMap<>();
                response.put("key1", "value1");
                response.put("key2", "value2");
                return ResponseEntity.ok(response);
            }

            Comparison comparison = comparisonOpt.get();

            // Check if still processing
            if (comparisonService.isComparisonInProgress(comparisonId)) {
                log.info("Comparison {} is still processing", comparisonId);
                Map<String, Object> response = new HashMap<>();
                response.put("status", "PROCESSING");
                response.put("message", "Comparison still processing");
                response.put("comparisonId", comparisonId);
                return ResponseEntity.accepted().body(response);
            }

            // Check if failed
            if ("failed".equalsIgnoreCase(comparison.getStatusAsString())) {
                log.warn("Comparison {} failed: {}", comparisonId, comparison.getErrorMessage());
                Map<String, Object> response = new HashMap<>();
                response.put("status", "FAILED");
                response.put("error", "Comparison failed: " + comparison.getErrorMessage());
                response.put("comparisonId", comparisonId);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }

            // Get the result
            ComparisonResult result = comparisonService.getComparisonResult(comparisonId);

            if (result == null) {
                log.warn("Comparison result not found for {}", comparisonId);
                // Try to forcibly reload from storage
                result = resultStorage.retrieveResult(comparisonId);

                if (result == null) {
                    log.error("Comparison {} is marked as completed but no result found", comparisonId);
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
            if (!comparisonRepository.existsById(comparisonId)) {
                log.warn("Comparison not found: {}", comparisonId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Comparison not found", "comparisonId", comparisonId));
            }

            // Check if still processing
            if (comparisonService.isComparisonInProgress(comparisonId)) {
                log.info("Comparison {} is still processing", comparisonId);
                Map<String, Object> response = new HashMap<>();
                response.put("status", "PROCESSING");
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
            if (!comparisonRepository.existsById(comparisonId)) {
                log.warn("Comparison not found: {}", comparisonId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Comparison not found", "comparisonId", comparisonId));
            }

            // Check if still processing
            if (comparisonService.isComparisonInProgress(comparisonId)) {
                log.info("Comparison {} is still processing", comparisonId);
                Map<String, Object> response = new HashMap<>();
                response.put("status", "PROCESSING");
                response.put("message", "Document matching still processing");
                return ResponseEntity.accepted().body(response);
            }

            // Get the document pairs
            List<DocumentPair> documentPairs = comparisonService.getDocumentPairs(comparisonId);

            if (documentPairs == null || documentPairs.isEmpty()) {
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
            if (!comparisonRepository.existsById(comparisonId)) {
                log.warn("Comparison not found: {}", comparisonId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Comparison not found", "comparisonId", comparisonId));
            }

            // Check if still processing
            if (comparisonService.isComparisonInProgress(comparisonId)) {
                log.info("Comparison {} is still processing", comparisonId);
                Map<String, Object> response = new HashMap<>();
                response.put("status", "PROCESSING");
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
                // Check if the comparison is still processing
                if (comparisonService.isComparisonInProgress(comparisonId)) {
                    log.info("Comparison {} is still processing", comparisonId);
                    Map<String, Object> response = new HashMap<>();
                    response.put("status", "PROCESSING");
                    response.put("message", "Comparison still processing");
                    return ResponseEntity.accepted().body(response);
                }

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
            if (!comparisonRepository.existsById(comparisonId)) {
                log.warn("Comparison not found: {}", comparisonId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Comparison not found", "comparisonId", comparisonId));
            }

            // Check if still processing
            if (comparisonService.isComparisonInProgress(comparisonId)) {
                log.info("Comparison {} is still processing", comparisonId);
                Map<String, Object> response = new HashMap<>();
                response.put("status", "PROCESSING");
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
