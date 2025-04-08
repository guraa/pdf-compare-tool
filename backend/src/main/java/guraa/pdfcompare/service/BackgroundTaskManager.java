package guraa.pdfcompare.service;

import guraa.pdfcompare.model.Comparison;
import guraa.pdfcompare.repository.ComparisonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * Service for managing background tasks and processing queues.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackgroundTaskManager {

    private final ComparisonRepository comparisonRepository;
    private final ComparisonService comparisonService;
    private final ExecutorService comparisonExecutor;

    @Value("${app.background-tasks.max-concurrent-comparisons:2}")
    private int maxConcurrentComparisons;

    // Set to track comparisons that are currently processing
    private final Set<String> activeComparisons = new HashSet<>();

    /**
     * Process pending comparisons from the queue.
     * Runs every 30 seconds.
     */
    @Scheduled(fixedRate = 30000)
    @Transactional
    public void processPendingComparisons() {
        // Skip if we're already at max capacity
        synchronized (activeComparisons) {
            if (activeComparisons.size() >= maxConcurrentComparisons) {
                log.debug("Already processing {} comparisons, skipping check", activeComparisons.size());
                return;
            }
        }

        try {
            // Find pending comparisons
            List<Comparison> pendingComparisons = comparisonRepository.findByStatus(Comparison.ComparisonStatus.PENDING);

            if (pendingComparisons.isEmpty()) {
                return;
            }

            log.info("Found {} pending comparisons", pendingComparisons.size());

            // Process each pending comparison up to our concurrent limit
            for (Comparison comparison : pendingComparisons) {
                synchronized (activeComparisons) {
                    if (activeComparisons.size() >= maxConcurrentComparisons) {
                        log.debug("Reached max concurrent comparisons ({}), will process remaining later",
                                maxConcurrentComparisons);
                        break;
                    }

                    String comparisonId = comparison.getComparisonId();
// Skip if already being processed
                    if (activeComparisons.contains(comparisonId)) {
                        continue;
                    }

                    // Add to active set
                    activeComparisons.add(comparisonId);

                    // Submit for processing
                    comparisonExecutor.submit(() -> {
                        try {
                            log.info("Starting processing of comparison: {}", comparisonId);
                            comparisonService.processComparison(comparisonId);
                            log.info("Completed processing of comparison: {}", comparisonId);
                        } catch (Exception e) {
                            log.error("Error processing comparison {}: {}", comparisonId, e.getMessage(), e);
                        } finally {
                            // Remove from active set
                            synchronized (activeComparisons) {
                                activeComparisons.remove(comparisonId);
                            }
                        }
                    });

                    log.info("Submitted comparison {} for processing", comparisonId);
                }
            }

        } catch (Exception e) {
            log.error("Error checking for pending comparisons", e);
        }
    }

    /**
     * Check for stalled comparisons and reset them if necessary.
     * Runs every 15 minutes.
     */
    @Scheduled(fixedRate = 900000)
    @Transactional
    public void checkStalledComparisons() {
        try {
            // Find processing comparisons
            List<Comparison> processingComparisons = comparisonRepository.findByStatus(Comparison.ComparisonStatus.PROCESSING);
            processingComparisons.addAll(comparisonRepository.findByStatus(Comparison.ComparisonStatus.DOCUMENT_MATCHING));
            processingComparisons.addAll(comparisonRepository.findByStatus(Comparison.ComparisonStatus.COMPARING));

            if (processingComparisons.isEmpty()) {
                return;
            }

            // Current time minus 30 minutes
            LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(30);

            int resetCount = 0;

            // Check each processing comparison
            for (Comparison comparison : processingComparisons) {
                // If started more than 30 minutes ago, it's likely stalled
                if (comparison.getStartTime().isBefore(cutoffTime)) {
                    log.warn("Detected stalled comparison: {}, started at {}",
                            comparison.getComparisonId(), comparison.getStartTime());

                    // If it's not in our active set, reset it to pending
                    synchronized (activeComparisons) {
                        if (!activeComparisons.contains(comparison.getComparisonId())) {
                            comparison.setStatus(Comparison.ComparisonStatus.PENDING);
                            comparison.setStatusMessage("Comparison was reset after being stalled");
                            comparisonRepository.save(comparison);
                            resetCount++;

                            log.info("Reset stalled comparison: {}", comparison.getComparisonId());
                        }
                    }
                }
            }

            if (resetCount > 0) {
                log.info("Reset {} stalled comparisons", resetCount);
            }
        } catch (Exception e) {
            log.error("Error checking for stalled comparisons", e);
        }
    }


}