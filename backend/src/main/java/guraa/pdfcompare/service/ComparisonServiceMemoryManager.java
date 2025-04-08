package guraa.pdfcompare.service;

import guraa.pdfcompare.model.DocumentPair;
import guraa.pdfcompare.model.PdfDocument;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Memory management utility for the ComparisonService.
 * Handles memory optimization, garbage collection suggestions, and batch processing.
 */
@Slf4j
@Component
public class ComparisonServiceMemoryManager {
    // Define logger explicitly as fallback if Lombok annotation doesn't work
    private static final Logger logger = LoggerFactory.getLogger(ComparisonServiceMemoryManager.class);

    // Memory threshold for GC suggestion (80% of max memory)
    private static final double MEMORY_THRESHOLD = 0.8;

    /**
     * Calculate optimal batch size based on document size and available memory.
     *
     * @param baseDocument The base document
     * @param compareDocument The comparison document
     * @return Optimal batch size
     */
    public int calculateOptimalBatchSize(PdfDocument baseDocument, PdfDocument compareDocument) {
        // Calculate average page size in bytes (approximate)
        long totalSize = baseDocument.getFileSize() + compareDocument.getFileSize();
        int totalPages = baseDocument.getPageCount() + compareDocument.getPageCount();
        double avgPageSizeBytes = totalSize / (double) totalPages;

        // Calculate available memory
        long maxMemory = Runtime.getRuntime().maxMemory();
        long usedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long availableMemory = maxMemory - usedMemory;

        // Reserve 30% of available memory for other operations
        long effectiveMemory = (long)(availableMemory * 0.7);

        // Estimate how many document pairs we can process in parallel
        // Assuming each page needs ~5x its size in RAM for processing
        int estimatedPairs = (int) (effectiveMemory / (avgPageSizeBytes * 5));

        // Ensure at least 1 pair, at most 8 pairs per batch
        return Math.max(1, Math.min(8, estimatedPairs));
    }

    /**
     * Partition a list into smaller lists of specified size.
     *
     * @param list The list to partition
     * @param size The size of each partition
     * @return List of partitioned lists
     */
    public <T> List<List<T>> partitionList(List<T> list, int size) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }

        List<List<T>> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            result.add(list.subList(i, Math.min(i + size, list.size())));
        }

        return result;
    }

    /**
     * Update difference counts for a document pair.
     *
     * @param pair The document pair to update
     * @param totalDifferences Total number of differences
     */
    public void updatePairDifferenceCounts(DocumentPair pair, int totalDifferences) {
        // Use a heuristic to distribute differences by type
        // This could be improved with actual counting if needed
        int textDiffs = (int)(totalDifferences * 0.6); // 60% text differences
        int imageDiffs = (int)(totalDifferences * 0.2); // 20% image differences
        int fontDiffs = (int)(totalDifferences * 0.1); // 10% font differences
        int styleDiffs = totalDifferences - textDiffs - imageDiffs - fontDiffs; // Remaining for style

        pair.setTotalDifferences(totalDifferences);
        pair.setTextDifferences(textDiffs);
        pair.setImageDifferences(imageDiffs);
        pair.setFontDifferences(fontDiffs);
        pair.setStyleDifferences(styleDiffs);
    }

    /**
     * Suggest garbage collection when memory usage is high.
     */
    public void suggestGarbageCollection() {
        long maxMemory = Runtime.getRuntime().maxMemory();
        long usedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        double memoryUsageRatio = (double) usedMemory / maxMemory;

        if (memoryUsageRatio > MEMORY_THRESHOLD) {
            logger.info("High memory usage detected ({}%), suggesting garbage collection",
                    (int)(memoryUsageRatio * 100));
            System.gc();
        }
    }

    /**
     * Get the current memory usage status.
     * Useful for diagnostics and monitoring.
     *
     * @return Map containing memory usage details
     */
    public Map<String, Object> getMemoryUsageStats(ComparisonServiceCache cacheService) {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;

        Map<String, Object> stats = new HashMap<>();
        stats.put("maxMemoryMB", maxMemory / (1024 * 1024));
        stats.put("usedMemoryMB", usedMemory / (1024 * 1024));
        stats.put("freeMemoryMB", freeMemory / (1024 * 1024));
        stats.put("totalMemoryMB", totalMemory / (1024 * 1024));
        stats.put("memoryUsagePercent", (double) usedMemory / maxMemory * 100);

        // Add cache statistics
        stats.putAll(cacheService.getCacheStats());

        return stats;
    }
}