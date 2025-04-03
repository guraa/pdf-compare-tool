package guraa.pdfcompare.controller;

import guraa.pdfcompare.model.ComparisonResult;
import guraa.pdfcompare.model.DocumentPair;
import guraa.pdfcompare.model.PageDetails;
import guraa.pdfcompare.service.ComparisonService;
import guraa.pdfcompare.service.ReportGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/pdfs/comparison")
@RequiredArgsConstructor
public class ComparisonController {

    private final ComparisonService comparisonService;
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
                    return ResponseEntity.accepted().body(Map.of(
                            "status", "PROCESSING",
                            "message", "Comparison still processing"
                    ));
                }

                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to get comparison result", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to get comparison result: " + e.getMessage()
            ));
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
            Map<String, Object> filters = new java.util.HashMap<>();
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
                    return ResponseEntity.accepted().body(Map.of(
                            "status", "PROCESSING",
                            "message", "Comparison still processing"
                    ));
                }

                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(pageDetails);
        } catch (Exception e) {
            log.error("Failed to get page details", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to get page details: " + e.getMessage()
            ));
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
                    return ResponseEntity.accepted().body(Map.of(
                            "status", "PROCESSING",
                            "message", "Document matching still processing"
                    ));
                }

                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(documentPairs);
        } catch (Exception e) {
            log.error("Failed to get document pairs", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to get document pairs: " + e.getMessage()
            ));
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
                    return ResponseEntity.accepted().body(Map.of(
                            "status", "PROCESSING",
                            "message", "Comparison still processing"
                    ));
                }

                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to get document pair result", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to get document pair result: " + e.getMessage()
            ));
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
            Map<String, Object> filters = new java.util.HashMap<>();
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
                    return ResponseEntity.accepted().body(Map.of(
                            "status", "PROCESSING",
                            "message", "Comparison still processing"
                    ));
                }

                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(pageDetails);
        } catch (Exception e) {
            log.error("Failed to get document pair page details", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to get document pair page details: " + e.getMessage()
            ));
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
            Map<String, Object> options = request.getOptions() != null ? request.getOptions() : new java.util.HashMap<>();

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
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to generate report: " + e.getMessage()
            ));
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

    /**
     * Java Map interface for responses.
     */
    private static class Map<K, V> extends java.util.HashMap<K, V> {
        public static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2) {
            Map<K, V> map = new Map<>();
            map.put(k1, v1);
            map.put(k2, v2);
            return map;
        }

        public static <K, V> Map<K, V> of(K k1, V v1) {
            Map<K, V> map = new Map<>();
            map.put(k1, v1);
            return map;
        }
    }
}