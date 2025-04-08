package guraa.pdfcompare.service;

import guraa.pdfcompare.repository.ComparisonRepository;
import guraa.pdfcompare.repository.PdfRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for monitoring system health and resources.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemMonitorService {

    private final PdfRepository pdfRepository;
    private final ComparisonRepository comparisonRepository;

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Monitor system health every hour.
     */
    @Scheduled(fixedRate = 3600000) // Every hour
    public void monitorSystemHealth() {
        log.info("Running system health check at {}", LocalDateTime.now().format(formatter));

        try {
            // Check database
            long documentsCount = pdfRepository.count();
            long comparisonsCount = comparisonRepository.count();

            log.info("Database status: {} documents, {} comparisons",
                    documentsCount, comparisonsCount);

            // Check storage
            Map<String, Object> storageInfo = checkStorage();
            log.info("Storage status: uploads dir size: {}, documents: {}, comparisons: {}, temp: {}",
                    formatSize((Long) storageInfo.get("uploadsSize")),
                    formatSize((Long) storageInfo.get("documentsSize")),
                    formatSize((Long) storageInfo.get("comparisonsSize")),
                    formatSize((Long) storageInfo.get("tempSize")));

            // Check system resources
            Map<String, Object> resourceInfo = checkSystemResources();
            log.info("System resources: available memory: {}, max memory: {}, processors: {}",
                    formatSize((Long) resourceInfo.get("freeMemory")),
                    formatSize((Long) resourceInfo.get("maxMemory")),
                    resourceInfo.get("processors"));

            // Check for potential issues
            checkForIssues(documentsCount, comparisonsCount, storageInfo, resourceInfo);

        } catch (Exception e) {
            log.error("Error during system health check", e);
        }
    }

    /**
     * Check storage usage.
     *
     * @return Map with storage information
     */
    private Map<String, Object> checkStorage() {
        Map<String, Object> info = new HashMap<>();

        // Check main upload directory
        Path uploadsDir = Paths.get("uploads");
        long uploadsSize = getDirSize(uploadsDir.toFile());
        info.put("uploadsSize", uploadsSize);

        // Check documents directory
        Path documentsDir = Paths.get("uploads", "documents");
        long documentsSize = getDirSize(documentsDir.toFile());
        info.put("documentsSize", documentsSize);

        // Check comparisons directory
        Path comparisonsDir = Paths.get("uploads", "comparisons");
        long comparisonsSize = getDirSize(comparisonsDir.toFile());
        info.put("comparisonsSize", comparisonsSize);

        // Check temp directory
        Path tempDir = Paths.get("uploads", "temp");
        long tempSize = getDirSize(tempDir.toFile());
        info.put("tempSize", tempSize);

        return info;
    }

    /**
     * Check system resources.
     *
     * @return Map with resource information
     */
    private Map<String, Object> checkSystemResources() {
        Map<String, Object> info = new HashMap<>();

        Runtime runtime = Runtime.getRuntime();

        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        int processors = runtime.availableProcessors();

        info.put("maxMemory", maxMemory);
        info.put("totalMemory", totalMemory);
        info.put("freeMemory", freeMemory);
        info.put("usedMemory", usedMemory);
        info.put("processors", processors);

        return info;
    }

    /**
     * Check for potential system issues.
     *
     * @param documentsCount Number of documents
     * @param comparisonsCount Number of comparisons
     * @param storageInfo Storage information
     * @param resourceInfo System resource information
     */
    private void checkForIssues(long documentsCount, long comparisonsCount,
                                Map<String, Object> storageInfo, Map<String, Object> resourceInfo) {
        // Check if disk space is getting low
        long totalUploadsSize = (Long) storageInfo.get("uploadsSize");
        if (totalUploadsSize > 1024 * 1024 * 1024 * 5) { // 5 GB
            log.warn("Uploads directory is very large: {}", formatSize(totalUploadsSize));
        }

        // Check if temp directory is getting large
        long tempSize = (Long) storageInfo.get("tempSize");
        if (tempSize > 1024 * 1024 * 200) { // 200 MB
            log.warn("Temp directory is unusually large: {}", formatSize(tempSize));
        }

        // Check memory usage
        long maxMemory = (Long) resourceInfo.get("maxMemory");
        long usedMemory = (Long) resourceInfo.get("usedMemory");
        double memoryUsage = (double) usedMemory / maxMemory;

        if (memoryUsage > 0.85) { // 85%
            log.warn("High memory usage: {}% of maximum", String.format("%.2f", memoryUsage * 100));
        }

        // Check database growth
        if (documentsCount > 10000) {
            log.warn("Large number of document records: {}", documentsCount);
        }

        if (comparisonsCount > 5000) {
            log.warn("Large number of comparison records: {}", comparisonsCount);
        }
    }

    /**
     * Calculate directory size.
     *
     * @param directory Directory to measure
     * @return Size in bytes
     */
    private long getDirSize(File directory) {
        if (!directory.exists()) {
            return 0;
        }

        long size = 0;
        File[] files = directory.listFiles();

        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    size += file.length();
                } else {
                    size += getDirSize(file);
                }
            }
        }

        return size;
    }

    /**
     * Format file size in human-readable format.
     *
     * @param size Size in bytes
     * @return Formatted size string
     */
    private String formatSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", size / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
        }
    }
}