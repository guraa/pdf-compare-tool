package guraa.pdfcompare.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller for accessing performance metrics.
 * Note: Performance monitoring has been disabled.
 */
@Slf4j
@RestController
@RequestMapping("/api/diagnostics")
public class PerformanceController {

    /**
     * Get the current performance metrics.
     * Note: This is a placeholder as performance monitoring has been disabled.
     *
     * @return Performance metrics
     */
    @GetMapping("/performance")
    public ResponseEntity<?> getPerformanceMetrics() {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("metrics", new ArrayList<>());
            response.put("timestamp", System.currentTimeMillis());
            response.put("message", "Performance monitoring has been disabled");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error retrieving performance metrics: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to retrieve performance metrics: " + e.getMessage()));
        }
    }

    /**
     * Reset the performance metrics.
     * Note: This is a placeholder as performance monitoring has been disabled.
     *
     * @return Success or error response
     */
    @PostMapping("/performance/reset")
    public ResponseEntity<?> resetPerformanceMetrics() {
        try {
            return ResponseEntity.ok(Map.of(
                    "message", "Performance monitoring has been disabled",
                    "timestamp", System.currentTimeMillis()));
        } catch (Exception e) {
            log.error("Error resetting performance metrics: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to reset performance metrics: " + e.getMessage()));
        }
    }
}
