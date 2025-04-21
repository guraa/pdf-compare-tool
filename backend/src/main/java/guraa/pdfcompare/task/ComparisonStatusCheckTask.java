package guraa.pdfcompare.task;

import guraa.pdfcompare.model.Comparison;
import guraa.pdfcompare.repository.ComparisonRepository;
import guraa.pdfcompare.service.ComparisonResultStorage;
import guraa.pdfcompare.service.ComparisonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Scheduled task to check for and fix stuck comparisons.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComparisonStatusCheckTask {

    private final ComparisonRepository comparisonRepository;
    private final ComparisonResultStorage resultStorage;
    private final ComparisonService comparisonService;

    // Maximum age of a comparison in PROCESSING state before it's considered stalled (15 minutes)
    private static final long MAX_PROCESSING_AGE_MINUTES = 15;

    /**
     * Check every 10 seconds for comparisons that need status fixing.
     */
    @Scheduled(fixedRate = 10000)
    @Transactional
    public void checkForStuckComparisons() {
        try {
            // Find all comparisons in PROCESSING or PENDING state
            List<Comparison> inProgressComparisons = comparisonRepository.findAll().stream()
                    .filter(c -> c != null && c.getStatus() != null && 
                           (c.getStatus() == Comparison.ComparisonStatus.PROCESSING ||
                            c.getStatus() == Comparison.ComparisonStatus.PENDING))
                    .collect(Collectors.toList());

            if (inProgressComparisons.isEmpty()) {
                return; // Nothing to check
            }

            log.debug("Checking {} comparisons in PROCESSING/PENDING state", inProgressComparisons.size());

            for (Comparison comparison : inProgressComparisons) {
                // Check if result exists
                if (resultStorage.resultExists(comparison.getId())) {
                    // Result exists but status is not COMPLETED - fix it
                    log.warn("Found comparison {} in {} state but result exists - fixing to COMPLETED",
                            comparison.getId(), comparison.getStatus());
                    comparisonService.updateComparisonStatus(comparison.getId(),
                            Comparison.ComparisonStatus.COMPLETED, null);
                } else {
                    // No result, check if it's been stuck too long
                    LocalDateTime cutoff = LocalDateTime.now().minusMinutes(MAX_PROCESSING_AGE_MINUTES);
                    if (comparison.getUpdatedAt().isBefore(cutoff)) {
                        log.warn("Comparison {} has been stuck in {} state for over {} minutes - marking as FAILED",
                                comparison.getId(), comparison.getStatus(), MAX_PROCESSING_AGE_MINUTES);
                        comparisonService.updateComparisonStatus(comparison.getId(),
                                Comparison.ComparisonStatus.FAILED,
                                "Comparison timed out after " + MAX_PROCESSING_AGE_MINUTES + " minutes");
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error in comparison status check task: {}", e.getMessage(), e);
        }
    }

    /**
     * Every hour, check for old, completed comparisons to clean up.
     */
    @Scheduled(fixedRate = 3600000)
    public void cleanupOldCompletedComparisons() {
        try {
            // Find comparisons completed more than 24 hours ago
            LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
            List<Comparison> oldCompletedComparisons = comparisonRepository.findAll().stream()
                    .filter(c -> c != null && c.getStatus() != null && 
                           (c.getStatus() == Comparison.ComparisonStatus.COMPLETED ||
                            c.getStatus() == Comparison.ComparisonStatus.FAILED))
                    .filter(c -> c.getUpdatedAt() != null && c.getUpdatedAt().isBefore(cutoff))
                    .collect(Collectors.toList());

            if (oldCompletedComparisons.isEmpty()) {
                return; // Nothing to clean up
            }

            log.info("Cleaning up {} old completed/failed comparisons", oldCompletedComparisons.size());

            for (Comparison comparison : oldCompletedComparisons) {
                try {
                    // Delete the result from storage
                    resultStorage.deleteResult(comparison.getId());
                    log.debug("Deleted result for old comparison: {}", comparison.getId());
                } catch (Exception e) {
                    log.warn("Error deleting result for old comparison {}: {}",
                            comparison.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Error in comparison cleanup task: {}", e.getMessage(), e);
        }
    }
}
