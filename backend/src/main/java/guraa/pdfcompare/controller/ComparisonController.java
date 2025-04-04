package guraa.pdfcompare.controller;

import guraa.pdfcompare.model.Comparison;
import guraa.pdfcompare.model.ComparisonResult;
import guraa.pdfcompare.model.DocumentPair;
import guraa.pdfcompare.model.PageDetails;
import guraa.pdfcompare.repository.ComparisonRepository;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/pdfs/comparison")
@RequiredArgsConstructor
public class ComparisonController {

    private final ComparisonService comparisonService;
    private final ComparisonRepository comparisonRepository;
    private final ReportGenerationService reportService;

    /**
     * Get comparison result.
     *
     * @param comparisonId The comparison ID
     * @return The comparison result
     */
    @GetMapping("/{comparisonId}")
    public ResponseEntity<?> getComparisonResult(@PathVariable String comparisonId) {
        try {
            ComparisonResult result = comparisonService.getComparisonResult(comparisonId);

            if (result == null) {
                // Check if the comparison is still processing
                if (comparisonService.isComparisonInProgress(comparisonId)) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("status", "PROCESSING");
                    response.put("message", "Comparison still processing");
                    return ResponseEntity.accepted().body(response);
                }

                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to get comparison result", e);
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
    @GetMapping("/{comparisonId}/page/{pageNumber}")
    public ResponseEntity<?> getPageDetails(
            @PathVariable String comparisonId,
            @PathVariable int pageNumber,
            @RequestParam(required = false) String types,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String search) {

        try {
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

            PageDetails pageDetails = comparisonService.getPageDetails(comparisonId, pageNumber, filters);

            if (pageDetails == null) {
                // Check if the comparison is still processing
                if (comparisonService.isComparisonInProgress(comparisonId)) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("status", "PROCESSING");
                    response.put("message", "Comparison still processing");
                    return ResponseEntity.accepted().body(response);
                }

                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(pageDetails);
        } catch (Exception e) {
            log.error("Failed to get page details", e);
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
    @GetMapping("/{comparisonId}/documents")
    public ResponseEntity<?> getDocumentPairs(@PathVariable String comparisonId) {
        try {
            List<DocumentPair> documentPairs = comparisonService.getDocumentPairs(comparisonId);

            if (documentPairs == null || documentPairs.isEmpty()) {
                // Check if the comparison is still processing
                if (comparisonService.isComparisonInProgress(comparisonId)) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("status", "PROCESSING");
                    response.put("message", "Document matching still processing");
                    return ResponseEntity.accepted().body(response);
                }

                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(documentPairs);
        } catch (Exception e) {
            log.error("Failed to get document pairs", e);
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
    @GetMapping("/{comparisonId}/documents/{pairIndex}")
    public ResponseEntity<?> getDocumentPairResult(
            @PathVariable String comparisonId,
            @PathVariable int pairIndex) {

        try {
            ComparisonResult result = comparisonService.getDocumentPairResult(comparisonId, pairIndex);

            if (result == null) {
                // Check if the comparison is still processing
                if (comparisonService.isComparisonInProgress(comparisonId)) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("status", "PROCESSING");
                    response.put("message", "Comparison still processing");
                    return ResponseEntity.accepted().body(response);
                }

                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to get document pair result", e);
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
    @GetMapping("/{comparisonId}/documents/{pairIndex}/page/{pageNumber}")
    public ResponseEntity<?> getDocumentPairPageDetails(
            @PathVariable String comparisonId,
            @PathVariable int pairIndex,
            @PathVariable int pageNumber,
            @RequestParam(required = false) String types,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String search) {

        try {
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

            PageDetails pageDetails = comparisonService.getDocumentPairPageDetails(
                    comparisonId, pairIndex, pageNumber, filters);

            if (pageDetails == null) {
                // Check if the comparison is still processing
                if (comparisonService.isComparisonInProgress(comparisonId)) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("status", "PROCESSING");
                    response.put("message", "Comparison still processing");
                    return ResponseEntity.accepted().body(response);
                }

                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(pageDetails);
        } catch (Exception e) {
            log.error("Failed to get document pair page details", e);
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
    @PostMapping("/{comparisonId}/report")
    public ResponseEntity<?> generateReport(
            @PathVariable String comparisonId,
            @RequestBody ReportRequest request) {

        try {
            String format = request.getFormat() != null ? request.getFormat() : "pdf";
            Map<String, Object> options = request.getOptions() != null ? request.getOptions() : new HashMap<>();

            Resource reportResource = reportService.generateReport(comparisonId, format, options);

            if (reportResource == null) {
                return ResponseEntity.notFound().build();
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
            log.error("Failed to generate report", e);
            Map<String, Object> response = new HashMap<>();
            response.put("error", "Failed to generate report: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Check if a comparison is ready (HEAD request).
     * This endpoint is used for polling without fetching the full result.
     *
     * @param comparisonId The comparison ID
     * @return 200 if completed, 202 if processing, 404 if not found
     */
    @RequestMapping(value = "/{comparisonId}", method = RequestMethod.HEAD)
    public ResponseEntity<Void> checkComparisonStatus(@PathVariable String comparisonId) {
        try {
            // Check if the comparison exists
            Comparison comparison = comparisonRepository.findByComparisonId(comparisonId)
                    .orElse(null);

            if (comparison == null) {
                return ResponseEntity.notFound().build();
            }

            // If comparison is still in progress, return 202 Accepted
            if (comparison.getStatus() != Comparison.ComparisonStatus.COMPLETED &&
                    comparison.getStatus() != Comparison.ComparisonStatus.FAILED) {
                return ResponseEntity.accepted().build();
            }

            // If failed, return 500 error
            if (comparison.getStatus() == Comparison.ComparisonStatus.FAILED) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }

            // If completed, return 200 OK
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error checking comparison status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Request object for report generation.
     */
    public static class ReportRequest {
        private String format;
        private Map<String, Object> options;

        // Getters and setters
        public String getFormat() { return format; }
        public void setFormat(String format) { this.format = format; }

        public Map<String, Object> getOptions() { return options; }
        public void setOptions(Map<String, Object> options) { this.options = options; }
    }
}