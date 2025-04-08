package guraa.pdfcompare.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Helper component to optimize the PDF difference detection process.
 * Used by DifferenceDetectionService to improve performance and memory usage.
 */
@Slf4j
@Component
public class DifferenceDetectionServiceOptimizer {

    // Configure memory thresholds (MB)
    private static final int HIGH_MEMORY_THRESHOLD = 1500; // 1.5GB
    private static final int VERY_HIGH_MEMORY_THRESHOLD = 2500; // 2.5GB
    private static final int CRITICAL_MEMORY_THRESHOLD = 3500; // 3.5GB

    // Temp file counters for memory management
    private final AtomicInteger tempFileCounter = new AtomicInteger(0);

    /**
     * Monitor and optimize memory usage during processing.
     *
     * @param pairIndex Current document pair index
     * @param action Action to run with memory monitoring
     */
    public void withMemoryOptimization(int pairIndex, Runnable action) {
        logMemoryUsage("START of processing for pair " + pairIndex);

        try {
            action.run();
        } finally {
            System.gc(); // Suggest garbage collection after processing
            logMemoryUsage("END of processing for pair " + pairIndex);
        }
    }

    /**
     * Process a page with memory optimization.
     *
     * @param pageNumber The page number
     * @param pageProcessor The page processing logic
     * @param <T> The type of page result
     * @return The result of page processing
     */
    public <T> T processPageWithMemoryOptimization(int pageNumber, java.util.function.Supplier<T> pageProcessor) {
        logMemoryUsage("Before processing page " + pageNumber);

        // Check if we need to free memory before processing
        checkAndOptimizeMemory();

        T result = pageProcessor.get();

        // If memory usage is high after processing, suggest GC
        if (getUsedMemoryMB() > HIGH_MEMORY_THRESHOLD) {
            System.gc();
        }

        return result;
    }

    /**
     * Use temporary files for large content to reduce memory usage.
     *
     * @param content The content to potentially store in a temp file
     * @param contentProcessor The processor function that handles the content
     * @param <T> The return type
     * @return The result of content processing
     */
    public <T> T useTemporaryStorageIfNeeded(byte[] content, java.util.function.Function<byte[], T> contentProcessor) {
        // If content is large and memory usage is high, use temp file
        if (content.length > 1_000_000 && getUsedMemoryMB() > HIGH_MEMORY_THRESHOLD) {
            return processThroughTempFile(content, contentProcessor);
        } else {
            // Process directly in memory
            return contentProcessor.apply(content);
        }
    }

    /**
     * Process content through a temporary file to reduce memory pressure.
     *
     * @param content The content to process
     * @param contentProcessor The processor function
     * @param <T> The return type
     * @return The result of processing
     */
    private <T> T processThroughTempFile(byte[] content, java.util.function.Function<byte[], T> contentProcessor) {
        Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"), "pdfcompare");
        try {
            Files.createDirectories(tempDir);

            String tempFileName = "temp-content-" + tempFileCounter.incrementAndGet() + ".tmp";
            Path tempFile = tempDir.resolve(tempFileName);

            // Write content to temp file
            Files.write(tempFile, content);

            // Clear the content array to free memory
            content = null;
            System.gc();

            // Read back in chunks if needed
            byte[] reloadedContent = Files.readAllBytes(tempFile);

            // Process the content
            T result = contentProcessor.apply(reloadedContent);

            // Clean up
            Files.deleteIfExists(tempFile);

            return result;
        } catch (IOException e) {
            log.error("Error using temporary storage: {}", e.getMessage(), e);
            // Fall back to direct processing
            return contentProcessor.apply(content);
        }
    }

    /**
     * Process a document in chunks to reduce memory pressure.
     *
     * @param <T> The chunk type
     * @param chunkSupplier Supplies chunks to process
     * @param chunkProcessor Processes each chunk
     * @param resultAggregator Aggregates results
     */
    public <T> void processInChunks(java.util.function.Supplier<T> chunkSupplier,
                                    Consumer<T> chunkProcessor,
                                    Runnable resultAggregator) {
        while (true) {
            T chunk = chunkSupplier.get();
            if (chunk == null) {
                break; // No more chunks
            }

            chunkProcessor.accept(chunk);

            // Check memory after each chunk
            if (getUsedMemoryMB() > HIGH_MEMORY_THRESHOLD) {
                System.gc();
            }
        }

        // After all chunks are processed
        resultAggregator.run();
    }

    /**
     * Check and optimize memory if necessary.
     */
    public void checkAndOptimizeMemory() {
        long usedMemoryMB = getUsedMemoryMB();

        if (usedMemoryMB > CRITICAL_MEMORY_THRESHOLD) {
            log.warn("CRITICAL memory usage detected: {}MB - Forcing garbage collection", usedMemoryMB);
            System.gc();
            cleanupTempFiles();
            System.gc(); // Second GC after temp file cleanup
        } else if (usedMemoryMB > VERY_HIGH_MEMORY_THRESHOLD) {
            log.warn("VERY HIGH memory usage detected: {}MB - Suggesting garbage collection", usedMemoryMB);
            System.gc();
        } else if (usedMemoryMB > HIGH_MEMORY_THRESHOLD) {
            log.info("HIGH memory usage detected: {}MB", usedMemoryMB);
            System.gc();
        }
    }

    /**
     * Clean up any temporary files created during processing.
     */
    public void cleanupTempFiles() {
        Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"), "pdfcompare");
        try {
            if (Files.exists(tempDir)) {
                Files.list(tempDir)
                        .filter(path -> path.getFileName().toString().startsWith("temp-content-"))
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException e) {
                                log.warn("Failed to delete temp file: {}", path, e);
                            }
                        });
            }
        } catch (IOException e) {
            log.error("Error cleaning up temp files: {}", e.getMessage(), e);
        }
    }

    /**
     * Log current memory usage.
     *
     * @param stage Description of the current processing stage
     */
    public void logMemoryUsage(String stage) {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / (1024 * 1024);
        long totalMemory = runtime.totalMemory() / (1024 * 1024);
        long freeMemory = runtime.freeMemory() / (1024 * 1024);
        long usedMemory = totalMemory - freeMemory;

        log.info("Memory at {}: Used={}MB, Free={}MB, Total={}MB, Max={}MB",
                stage, usedMemory, freeMemory, totalMemory, maxMemory);
    }

    /**
     * Get current used memory in MB.
     *
     * @return Used memory in MB
     */
    private long getUsedMemoryMB() {
        Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
    }
}