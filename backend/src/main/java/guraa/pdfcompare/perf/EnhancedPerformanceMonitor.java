package guraa.pdfcompare.perf;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Enhanced performance monitoring for PDF comparison operations.
 * Captures detailed metrics at different levels of granularity.
 */
@Slf4j
@Component
public class EnhancedPerformanceMonitor {

    // Store timings by operation type and document size
    private final Map<String, Map<String, OperationMetrics>> metrics = new ConcurrentHashMap<>();

    // Track currently running operations for nested timing
    private final ThreadLocal<Stack<TimingContext>> activeOperations =
            ThreadLocal.withInitial(Stack::new);

    /**
     * Start timing an operation.
     *
     * @param operationType The type of operation
     * @param metadata Additional context (e.g., document size)
     * @return An AutoCloseable timer that will record the duration when closed
     */
    public AutoCloseable startOperation(String operationType, Map<String, Object> metadata) {
        String sizeCategory = getSizeCategory(metadata);
        TimingContext context = new TimingContext(operationType, sizeCategory, System.nanoTime());
        activeOperations.get().push(context);

        // Return an AutoCloseable to ensure timing ends properly
        return () -> {
            if (!activeOperations.get().isEmpty()) {
                TimingContext completed = activeOperations.get().pop();
                if (completed.equals(context)) {
                    long duration = System.nanoTime() - completed.startTime;
                    recordTiming(completed.operationType, completed.sizeCategory, duration);
                } else {
                    log.warn("Operation timing stack corrupted! Expected {} but found {}",
                            context.operationType, completed.operationType);
                }
            }
        };
    }

    /**
     * Record a timing measurement.
     */
    private void recordTiming(String operationType, String sizeCategory, long durationNanos) {
        metrics.computeIfAbsent(operationType, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(sizeCategory, k -> new OperationMetrics())
                .recordOperation(durationNanos);
    }

    /**
     * Determine size category from metadata.
     */
    private String getSizeCategory(Map<String, Object> metadata) {
        if (metadata == null) return "unknown";

        Integer pageCount = (Integer) metadata.getOrDefault("pageCount", 0);

        if (pageCount > 1000) return "very_large";
        if (pageCount > 500) return "large";
        if (pageCount > 100) return "medium";
        if (pageCount > 20) return "small";
        return "tiny";
    }

    /**
     * Get a performance report.
     */
    public List<PerformanceReport> getPerformanceReport() {
        List<PerformanceReport> reports = new ArrayList<>();

        for (Map.Entry<String, Map<String, OperationMetrics>> entry : metrics.entrySet()) {
            String operationType = entry.getKey();
            Map<String, OperationMetrics> sizeMetrics = entry.getValue();

            for (Map.Entry<String, OperationMetrics> sizeEntry : sizeMetrics.entrySet()) {
                String sizeCategory = sizeEntry.getKey();
                OperationMetrics opMetrics = sizeEntry.getValue();

                reports.add(new PerformanceReport(
                        operationType,
                        sizeCategory,
                        opMetrics.getCount(),
                        opMetrics.getMinTimeMs(),
                        opMetrics.getMaxTimeMs(),
                        opMetrics.getAverageTimeMs(),
                        opMetrics.getTotalTimeMs()
                ));
            }
        }

        // Sort by total time (descending)
        reports.sort(Comparator.comparing(PerformanceReport::getTotalTimeMs).reversed());

        return reports;
    }

    /**
     * Reset all metrics.
     */
    public void resetMetrics() {
        metrics.clear();
    }

    /**
     * Context for a timing operation.
     */
    private static class TimingContext {
        final String operationType;
        final String sizeCategory;
        final long startTime;

        TimingContext(String operationType, String sizeCategory, long startTime) {
            this.operationType = operationType;
            this.sizeCategory = sizeCategory;
            this.startTime = startTime;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TimingContext that = (TimingContext) o;
            return startTime == that.startTime &&
                    Objects.equals(operationType, that.operationType) &&
                    Objects.equals(sizeCategory, that.sizeCategory);
        }

        @Override
        public int hashCode() {
            return Objects.hash(operationType, sizeCategory, startTime);
        }
    }

    /**
     * Metrics for a specific operation type and size category.
     */
    private static class OperationMetrics {
        private final AtomicLong count = new AtomicLong(0);
        private final AtomicLong totalTimeNanos = new AtomicLong(0);
        private final AtomicLong minTimeNanos = new AtomicLong(Long.MAX_VALUE);
        private final AtomicLong maxTimeNanos = new AtomicLong(0);

        void recordOperation(long durationNanos) {
            count.incrementAndGet();
            totalTimeNanos.addAndGet(durationNanos);

            // Update min time
            while (true) {
                long currentMin = minTimeNanos.get();
                if (durationNanos >= currentMin) break;
                if (minTimeNanos.compareAndSet(currentMin, durationNanos)) break;
            }

            // Update max time
            while (true) {
                long currentMax = maxTimeNanos.get();
                if (durationNanos <= currentMax) break;
                if (maxTimeNanos.compareAndSet(currentMax, durationNanos)) break;
            }
        }

        long getCount() {
            return count.get();
        }

        double getMinTimeMs() {
            long nanos = minTimeNanos.get();
            return nanos == Long.MAX_VALUE ? 0 : nanos / 1_000_000.0;
        }

        double getMaxTimeMs() {
            return maxTimeNanos.get() / 1_000_000.0;
        }

        double getAverageTimeMs() {
            long operations = count.get();
            return operations > 0 ? (totalTimeNanos.get() / 1_000_000.0) / operations : 0;
        }

        double getTotalTimeMs() {
            return totalTimeNanos.get() / 1_000_000.0;
        }
    }

    /**
     * Report of performance metrics for a specific operation type and size category.
     */
    @Getter
    public static class PerformanceReport {
        private final String operationType;
        private final String sizeCategory;
        private final long count;
        private final double minTimeMs;
        private final double maxTimeMs;
        private final double averageTimeMs;
        private final double totalTimeMs;

        public PerformanceReport(String operationType, String sizeCategory,
                                 long count, double minTimeMs, double maxTimeMs,
                                 double averageTimeMs, double totalTimeMs) {
            this.operationType = operationType;
            this.sizeCategory = sizeCategory;
            this.count = count;
            this.minTimeMs = minTimeMs;
            this.maxTimeMs = maxTimeMs;
            this.averageTimeMs = averageTimeMs;
            this.totalTimeMs = totalTimeMs;
        }
    }
}