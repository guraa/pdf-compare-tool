package guraa.pdfcompare.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import guraa.pdfcompare.model.ComparisonResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Service for storing and retrieving comparison results with improved robustness.
 */
@Slf4j
@Service
public class ComparisonResultStorage {

    private final String storageLocation;
    private final ObjectMapper objectMapper;

    // Memory cache for frequently accessed results
    private final ConcurrentHashMap<String, ComparisonResult> resultCache = new ConcurrentHashMap<>();

    // Locks for file operations to prevent concurrent writes
    private final ConcurrentHashMap<String, ReentrantLock> fileLocks = new ConcurrentHashMap<>();

    // Maximum cache size (adjust based on memory considerations)
    private static final int MAX_CACHE_SIZE = 20;

    // Retry parameters for robust file operations
    private static final int RETRY_COUNT = 3;
    private static final int RETRY_DELAY_MS = 100;

    /**
     * Constructor with storage location and object mapper.
     *
     * @param storageLocation The location to store comparison results
     * @param objectMapper The object mapper for serialization
     */
    public ComparisonResultStorage(
            @Value("${app.storage.location:uploads/results}") String storageLocation,
            ObjectMapper objectMapper) {
        this.storageLocation = storageLocation;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        // Ensure the storage directory exists on startup
        try {
            File resultsDir = new File(getResultsDirectory());
            if (!resultsDir.exists()) {
                if (!resultsDir.mkdirs()) {
                    log.error("Failed to create comparison results directory: {}", getResultsDirectory());
                } else {
                    log.info("Created comparison results directory: {}", getResultsDirectory());
                }
            }
        } catch (Exception e) {
            log.error("Error initializing comparison result storage: {}", e.getMessage(), e);
        }
    }

    /**
     * Store a comparison result with improved error handling and atomicity.
     *
     * @param comparisonId The comparison ID
     * @param result The comparison result
     * @throws IOException If there is an error storing the result
     */
    public void storeResult(String comparisonId, ComparisonResult result) throws IOException {
        if (comparisonId == null || result == null) {
            throw new IllegalArgumentException("Comparison ID and result cannot be null");
        }

        // Add to memory cache first
        resultCache.put(comparisonId, result);

        // Ensure we don't exceed max cache size
        if (resultCache.size() > MAX_CACHE_SIZE) {
            // Simple strategy: remove a random entry
            if (!resultCache.isEmpty()) {
                String keyToRemove = resultCache.keySet().iterator().next();
                resultCache.remove(keyToRemove);
            }
        }

        File resultFile = getResultFile(comparisonId);
        ReentrantLock fileLock = fileLocks.computeIfAbsent(comparisonId, k -> new ReentrantLock());

        // Acquire lock for this file
        fileLock.lock();
        try {
            // Ensure the parent directory exists
            File parentDir = resultFile.getParentFile();
            if (!parentDir.exists() && !parentDir.mkdirs()) {
                throw new IOException("Failed to create directory: " + parentDir.getAbsolutePath());
            }

            // Write to a temporary file first for atomic update
            Path tempFile = Files.createTempFile(parentDir.toPath(), "result_", ".tmp");
            try {
                // Write the result to the temporary file
                objectMapper.writeValue(tempFile.toFile(), result);

                // Move temporary file to final location (atomic operation)
                Files.move(tempFile, resultFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

                log.debug("Successfully stored comparison result for ID: {}", comparisonId);
            } catch (Exception e) {
                // Clean up temp file if something went wrong
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Exception cleanupEx) {
                    log.warn("Failed to clean up temporary file: {}", tempFile);
                }
                throw e;
            }
        } finally {
            fileLock.unlock();
        }
    }

    /**
     * Retrieve a comparison result with caching and retry mechanism.
     *
     * @param comparisonId The comparison ID
     * @return The comparison result, or null if not found
     */
    public ComparisonResult retrieveResult(String comparisonId) {
        if (comparisonId == null) {
            return null;
        }

        // Check memory cache first
        ComparisonResult cachedResult = resultCache.get(comparisonId);
        if (cachedResult != null) {
            log.debug("Retrieved comparison result from memory cache for ID: {}", comparisonId);
            return cachedResult;
        }

        File resultFile = getResultFile(comparisonId);
        if (!resultFile.exists() || !resultFile.canRead() || resultFile.length() == 0) {
            log.debug("Comparison result file not found or invalid for ID: {}", comparisonId);
            return null;
        }

        ReentrantLock fileLock = fileLocks.computeIfAbsent(comparisonId, k -> new ReentrantLock());

        // Try with retry mechanism for file system issues
        for (int attempt = 0; attempt < RETRY_COUNT; attempt++) {
            // Acquire lock for reading to prevent concurrent modifications
            fileLock.lock();
            try {
                ComparisonResult result = objectMapper.readValue(resultFile, ComparisonResult.class);

                // Cache the result for future retrievals
                resultCache.put(comparisonId, result);

                return result;
            } catch (IOException e) {
                log.warn("Attempt {} failed to read comparison result for ID {}: {}",
                        attempt + 1, comparisonId, e.getMessage());

                if (attempt == RETRY_COUNT - 1) {
                    log.error("All attempts to read comparison result failed for ID {}", comparisonId, e);
                } else {
                    // Wait before retry
                    try {
                        Thread.sleep(RETRY_DELAY_MS * (attempt + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.warn("Interrupted while waiting to retry reading result");
                        break;
                    }
                }
            } finally {
                fileLock.unlock();
            }
        }

        return null;
    }

    /**
     * Delete a comparison result.
     *
     * @param comparisonId The comparison ID
     * @return true if the result was deleted, false otherwise
     */
    public boolean deleteResult(String comparisonId) {
        if (comparisonId == null) {
            return false;
        }

        // Remove from memory cache
        resultCache.remove(comparisonId);

        File resultFile = getResultFile(comparisonId);
        if (!resultFile.exists()) {
            return true; // Already deleted
        }

        ReentrantLock fileLock = fileLocks.computeIfAbsent(comparisonId, k -> new ReentrantLock());

        // Acquire lock for deletion
        fileLock.lock();
        try {
            boolean deleted = resultFile.delete();
            if (deleted) {
                // Remove the lock from the map
                fileLocks.remove(comparisonId);
                log.debug("Deleted comparison result for ID: {}", comparisonId);
            } else {
                log.warn("Failed to delete comparison result file for ID: {}", comparisonId);
            }
            return deleted;
        } finally {
            fileLock.unlock();
        }
    }

    /**
     * Check if a comparison result exists.
     *
     * @param comparisonId The comparison ID
     * @return true if the result exists, false otherwise
     */
    public boolean resultExists(String comparisonId) {
        if (comparisonId == null) {
            return false;
        }

        // Check memory cache first
        if (resultCache.containsKey(comparisonId)) {
            return true;
        }

        File resultFile = getResultFile(comparisonId);
        return resultFile.exists() && resultFile.canRead() && resultFile.length() > 0;
    }

    /**
     * Get the file for a comparison result.
     *
     * @param comparisonId The comparison ID
     * @return The file
     */
    private File getResultFile(String comparisonId) {
        String filePath = getResultsDirectory() + File.separator + comparisonId + ".json";
        return new File(filePath);
    }

    /**
     * Get the directory for comparison results.
     *
     * @return The directory path
     */
    private String getResultsDirectory() {
        return storageLocation + File.separator + "comparison-results";
    }

    /**
     * Clear the memory cache.
     */
    public void clearCache() {
        resultCache.clear();
        log.info("Cleared comparison result memory cache");
    }
}