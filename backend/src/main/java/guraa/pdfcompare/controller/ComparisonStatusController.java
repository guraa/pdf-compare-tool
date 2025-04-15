package guraa.pdfcompare.controller;

import guraa.pdfcompare.service.ComparisonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/pdfs")
@RequiredArgsConstructor
public class ComparisonStatusController {

    private final ComparisonService comparisonService;

    /**
     * Check if a comparison is ready.
     * This supports the HEAD method from api.js: checkComparisonStatus.
     *
     * @param comparisonId The comparison ID
     * @return 200 OK if ready, 202 Accepted if processing, 404 Not Found if not found
     */
    @RequestMapping(value = "/comparison/{comparisonId}", method = RequestMethod.HEAD)
    public ResponseEntity<?> checkComparisonStatus(@PathVariable String comparisonId) {
        try {
            boolean isReady = comparisonService.isComparisonCompleted(comparisonId);
            if (isReady) {
                return ResponseEntity.ok().build();
            } else if (comparisonService.isComparisonInProgress(comparisonId)) {
                return ResponseEntity.accepted().build();
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Error checking comparison status: {}", e.getMessage());
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
        try {
            String status = comparisonService.getComparisonStatus(comparisonId);

            Map<String, Object> response = new HashMap<>();

            if ("not_found".equals(status)) {
                response.put("status", "NOT_FOUND");
                response.put("message", "Comparison not found");
                return ResponseEntity.notFound().build();
            }

            response.put("status", status.toUpperCase());
            response.put("comparisonId", comparisonId);

            if ("completed".equalsIgnoreCase(status)) {
                return ResponseEntity.ok(response);
            } else if ("processing".equalsIgnoreCase(status) || "pending".equalsIgnoreCase(status)) {
                return ResponseEntity.accepted().body(response);
            } else if ("failed".equalsIgnoreCase(status)) {
                response.put("message", "Comparison failed");
                return ResponseEntity.internalServerError().body(response);
            } else {
                return ResponseEntity.ok(response);
            }
        } catch (Exception e) {
            log.error("Error getting comparison status: {}", e.getMessage());

            Map<String, Object> response = new HashMap<>();
            response.put("status", "ERROR");
            response.put("message", "Failed to get comparison status: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}