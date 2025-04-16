package guraa.pdfcompare.controller;

import guraa.pdfcompare.model.Comparison;
import guraa.pdfcompare.repository.ComparisonRepository;
import guraa.pdfcompare.service.ComparisonResultStorage;
import guraa.pdfcompare.service.ComparisonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Diagnostic controller for debugging comparison status issues.
 * This controller provides endpoints for checking the state of ongoing comparisons.
 */
@Slf4j
@RestController
@RequestMapping("/api/debug")
@RequiredArgsConstructor
public class ComparisonDiagnosticController {

    private final ComparisonRepository comparisonRepository;
    private final ComparisonService comparisonService;
    private final ComparisonResultStorage resultStorage;

    /**
     * Get a list of recent comparisons and their statuses.
     *
     * @param hours The number of hours to look back (default 24)
     * @return A list of recent comparisons
     */
    @GetMapping("/recent-comparisons")
    public ResponseEntity<?> getRecentComparisons(@RequestParam(defaultValue = "24") int hours) {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusHours(hours);
            List<Comparison> recentComparisons = comparisonRepository.findAll();

            // Filter to recent comparisons
            List<Map<String, Object>> result = new ArrayList<>();
            for (Comparison comparison : recentComparisons) {
                if (comparison.getCreatedAt().isAfter(cutoff)) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", comparison.getId());
                    item.put("status", comparison.getStatus().name());
                    item.put("baseDocumentId", comparison.getBaseDocumentId());
                    item.put("compareDocumentId", comparison.getCompareDocumentId());
                    item.put("createdAt", comparison.getCreatedAt());
                    item.put("updatedAt", comparison.getUpdatedAt());
                    item.put("age", ChronoUnit.MINUTES.between(comparison.getCreatedAt(), LocalDateTime.now()) + " minutes");
                    item.put("resultExists", resultStorage.resultExists(comparison.getId()));

                    if (comparison.getStatus() == Comparison.ComparisonStatus.PROCESSING ||
                            comparison.getStatus() == Comparison.ComparisonStatus.PENDING) {
                        item.put("inProgress", comparisonService.isComparisonInProgress(comparison.getId()));
                    }

                    if (comparison.getErrorMessage() != null) {
                        item.put("errorMessage", comparison.getErrorMessage());
                    }

                    result.add(item);
                }
            }

            // Sort by created date (newest first)
            result.sort((a, b) -> {
                LocalDateTime dateA = (LocalDateTime) a.get("createdAt");
                LocalDateTime dateB = (LocalDateTime) b.get("createdAt");
                return dateB.compareTo(dateA);
            });

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error getting recent comparisons: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to get recent comparisons: " + e.getMessage()));
        }
    }


}