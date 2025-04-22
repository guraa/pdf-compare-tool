package guraa.pdfcompare.controller;

import guraa.pdfcompare.perf.EnhancedPerformanceMonitor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for accessing performance metrics.
 */
@Slf4j
@RestController
@RequestMapping("/api/diagnostics")
@RequiredArgsConstructor
public class PerformanceController {

    private final EnhancedPerformanceMonitor performanceMonitor;

    /**
     * Get the current performance metrics.
     *
     * @return Performance metrics
     */
    @GetMapping("/performance")
    public ResponseEntity<?> getPerformanceMetrics() {
        try {
            List<EnhancedPerformanceMonitor.PerformanceReport> reports =
                    performanceMonitor.getPerformanceReport();

            Map<String, Object> response = new HashMap<>();
            response.put("metrics", reports);
            response.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error retrieving performance metrics: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to retrieve performance metrics: " + e.getMessage()));
        }
    }

    /**
     * Reset the performance metrics.
     *
     * @return Success or error response
     */
    @PostMapping("/performance/reset")
    public ResponseEntity<?> resetPerformanceMetrics() {
        try {
            performanceMonitor.resetMetrics();

            return ResponseEntity.ok(Map.of(
                    "message", "Performance metrics reset successfully",
                    "timestamp", System.currentTimeMillis()));
        } catch (Exception e) {
            log.error("Error resetting performance metrics: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to reset performance metrics: " + e.getMessage()));
        }
    }
}