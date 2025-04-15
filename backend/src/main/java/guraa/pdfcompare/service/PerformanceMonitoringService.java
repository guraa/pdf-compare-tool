package guraa.pdfcompare.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for monitoring application performance metrics.
 * This service tracks memory usage, processing times, and other performance indicators
 * to help diagnose and resolve performance issues.
 */
@Slf4j
@Service
public class PerformanceMonitoringService {

    // Memory metrics
    private final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
    private final OperatingSystemMXBean osMXBean = ManagementFactory.getOperatingSystemMXBean();

    // Operation counters
    private final AtomicInteger documentProcessedCount = new AtomicInteger(0);
    private final AtomicInteger comparisonCount = new AtomicInteger(0);
    private final AtomicInteger pageRenderCount = new AtomicInteger(0);

    // Operation timing
    private final AtomicLong totalComparisonTimeMs = new AtomicLong(0);
    private final AtomicLong totalRenderTimeMs = new AtomicLong(0);

    // Performance stats
    @Getter
    private final Map<String, OperationStats> operationStats = new ConcurrentHashMap<>();

    // Start time
    private final LocalDateTime startTime = LocalDateTime.now();

    /**
     * Initialize the performance monitoring service.
     */
    @PostConstruct
    public void init() {
        // Initialize operation stats for key operations
        operationStats.put("document-processing", new OperationStats("Document Processing"));
        operationStats.put("page-rendering", new OperationStats("Page Rendering"));
        operationStats.put("document-comparison", new OperationStats("Document Comparison"));
        operationStats.put("text-comparison", new OperationStats("Text Comparison"));
        operationStats.put("image-comparison", new OperationStats("Image Comparison"));
        operationStats.put("font-comparison", new OperationStats("Font Comparison"));

        // Log initial state
        logSystemInfo();
    }

    /**
     * Log detailed system information.
     */
    private void logSystemInfo() {
        Runtime runtime = Runtime.getRuntime();

        log.info("System Information:");
        log.info("  Available processors: {}", runtime.availableProcessors());
        log.info("  Max memory: {} MB", runtime.maxMemory() / (1024 * 1024));
        log.info("  Total memory: {} MB", runtime.totalMemory() / (1024 * 1024));
        log.info("  Free memory: {} MB", runtime.freeMemory() / (1024 * 1024));

        if (osMXBean instanceof com.sun.management.OperatingSystemMXBean) {
            try {
                com.sun.management.OperatingSystemMXBean sunOsMXBean = (com.sun.management.OperatingSystemMXBean) osMXBean;
                log.info("  Total physical memory: {} MB", sunOsMXBean.getTotalPhysicalMemorySize() / (1024 * 1024));
                log.info("  Free physical memory: {} MB", sunOsMXBean.getFreePhysicalMemorySize() / (1024 * 1024));
                log.info("  Process CPU time: {}", formatDuration(sunOsMXBean.getProcessCpuTime() / 1_000_000));
                log.info("  System CPU load: {}", String.format("%.2f%%", sunOsMXBean.getSystemCpuLoad() * 100));
                log.info("  Process CPU load: {}", String.format("%.2f%%", sunOsMXBean.getProcessCpuLoad() * 100));
            } catch (Exception e) {
                log.debug("Unable to access extended OS metrics: {}", e.getMessage());
            }
        }
    }

    /**
     * Format a duration in milliseconds to a readable string.
     *
     * @param milliseconds The duration in milliseconds
     * @return A formatted string (e.g., "2m 30s")
     */
    private String formatDuration(long milliseconds) {
        Duration duration = Duration.ofMillis(milliseconds);
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();

        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }

    /**
     * Record the start of an operation.
     *
     * @param operationType The type of operation
     * @return A timer object to record completion
     */
    public OperationTimer startOperation(String operationType) {
        OperationStats stats = operationStats.computeIfAbsent(operationType, OperationStats::new);
        return new OperationTimer(stats);
    }

    /**
     * Record a document being processed.
     */
    public void documentProcessed() {
        documentProcessedCount.incrementAndGet();
    }

    /**
     * Record a comparison being performed.
     *
     * @param durationMs The duration of the comparison in milliseconds
     */
    public void comparisonPerformed(long durationMs) {
        comparisonCount.incrementAndGet();
        totalComparisonTimeMs.addAndGet(durationMs);
    }

    /**
     * Record a page being rendered.
     *
     * @param durationMs The duration of the rendering in milliseconds
     */
    public void pageRendered(long durationMs) {
        pageRenderCount.incrementAndGet();
        totalRenderTimeMs.addAndGet(durationMs);
    }

    /**
     * Log performance metrics at regular intervals.
     */
    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES)
    public void logPerformanceMetrics() {
        // Get memory usage
        MemoryUsage heapMemory = memoryMXBean.getHeapMemoryUsage();
        MemoryUsage nonHeapMemory = memoryMXBean.getNonHeapMemoryUsage();

        // Log memory metrics
        log.info("Performance Metrics - Memory:");
        log.info("  Heap Memory: {} MB used / {} MB committed / {} MB max",
                heapMemory.getUsed() / (1024 * 1024),
                heapMemory.getCommitted() / (1024 * 1024),
                heapMemory.getMax() / (1024 * 1024));

        log.info("  Non-Heap Memory: {} MB used / {} MB committed",
                nonHeapMemory.getUsed() / (1024 * 1024),
                nonHeapMemory.getCommitted() / (1024 * 1024));

        // Log operation metrics
        log.info("Performance Metrics - Operations:");
        log.info("  Documents Processed: {}", documentProcessedCount.get());
        log.info("  Comparisons Performed: {}", comparisonCount.get());
        log.info("  Pages Rendered: {}", pageRenderCount.get());

        // Log average operation times
        long avgComparisonTime = comparisonCount.get() > 0 ?
                totalComparisonTimeMs.get() / comparisonCount.get() : 0;

        long avgRenderTime = pageRenderCount.get() > 0 ?
                totalRenderTimeMs.get() / pageRenderCount.get() : 0;

        log.info("  Avg Comparison Time: {} ms", avgComparisonTime);
        log.info("  Avg Page Render Time: {} ms", avgRenderTime);

        // Log uptime
        Duration uptime = Duration.between(startTime, LocalDateTime.now());
        log.info("  Application Uptime: {}", formatDuration(uptime.toMillis()));

        // Log detailed operation stats
        log.info("Performance Metrics - Detailed Operation Stats:");
        for (OperationStats stats : operationStats.values()) {
            if (stats.getCount() > 0) {
                log.info("  {}: {} operations, avg {} ms, min {} ms, max {} ms",
                        stats.getName(),
                        stats.getCount(),
                        stats.getAverageTimeMs(),
                        stats.getMinTimeMs(),
                        stats.getMaxTimeMs());
            }
        }
    }

    /**
     * Timer class for tracking operation duration.
     */
    public static class OperationTimer implements AutoCloseable {
        private final OperationStats stats;
        private final long startTime;

        /**
         * Create a new operation timer.
         *
         * @param stats The operation stats to update
         */
        public OperationTimer(OperationStats stats) {
            this.stats = stats;
            this.startTime = System.currentTimeMillis();
        }

        /**
         * Stop the timer and record the operation.
         */
        @Override
        public void close() {
            long duration = System.currentTimeMillis() - startTime;
            stats.recordOperation(duration);
        }
    }

    /**
     * Statistics for a type of operation.
     */
    public static class OperationStats {
        private final String name;
        private final AtomicInteger count = new AtomicInteger(0);
        private final AtomicLong totalTimeMs = new AtomicLong(0);
        private final AtomicLong minTimeMs = new AtomicLong(Long.MAX_VALUE);
        private final AtomicLong maxTimeMs = new AtomicLong(0);

        /**
         * Create new operation stats.
         *
         * @param name The name of the operation type
         */
        public OperationStats(String name) {
            this.name = name;
        }

        /**
         * Record an operation.
         *
         * @param durationMs The duration of the operation in milliseconds
         */
        public void recordOperation(long durationMs) {
            count.incrementAndGet();
            totalTimeMs.addAndGet(durationMs);

            // Update min and max using atomic operations
            updateMin(durationMs);
            updateMax(durationMs);
        }

        /**
         * Update the minimum time using atomic operations.
         *
         * @param durationMs The duration to compare
         */
        private void updateMin(long durationMs) {
            long currentMin = minTimeMs.get();
            while (durationMs < currentMin) {
                if (minTimeMs.compareAndSet(currentMin, durationMs)) {
                    break;
                }
                currentMin = minTimeMs.get();
            }
        }

        /**
         * Update the maximum time using atomic operations.
         *
         * @param durationMs The duration to compare
         */
        private void updateMax(long durationMs) {
            long currentMax = maxTimeMs.get();
            while (durationMs > currentMax) {
                if (maxTimeMs.compareAndSet(currentMax, durationMs)) {
                    break;
                }
                currentMax = maxTimeMs.get();
            }
        }

        /**
         * Get the name of the operation type.
         *
         * @return The name
         */
        public String getName() {
            return name;
        }

        /**
         * Get the number of operations recorded.
         *
         * @return The count
         */
        public int getCount() {
            return count.get();
        }

        /**
         * Get the total time spent on this operation type.
         *
         * @return The total time in milliseconds
         */
        public long getTotalTimeMs() {
            return totalTimeMs.get();
        }

        /**
         * Get the minimum operation time.
         *
         * @return The minimum time in milliseconds
         */
        public long getMinTimeMs() {
            long min = minTimeMs.get();
            return min == Long.MAX_VALUE ? 0 : min;
        }

        /**
         * Get the maximum operation time.
         *
         * @return The maximum time in milliseconds
         */
        public long getMaxTimeMs() {
            return maxTimeMs.get();
        }

        /**
         * Get the average operation time.
         *
         * @return The average time in milliseconds
         */
        public long getAverageTimeMs() {
            int currentCount = count.get();
            return currentCount > 0 ? totalTimeMs.get() / currentCount : 0;
        }
    }
}