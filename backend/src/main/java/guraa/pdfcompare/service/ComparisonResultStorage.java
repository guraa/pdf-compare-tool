package guraa.pdfcompare.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import guraa.pdfcompare.model.ComparisonResult;
import guraa.pdfcompare.util.FileUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;

/**
 * Service for storing and retrieving comparison results.
 */
@Slf4j
@Service
public class ComparisonResultStorage {

    private final String storageLocation;
    private final ObjectMapper objectMapper;

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

        // Ensure the storage directory exists
        try {
            FileUtils.createDirectories(new File(getResultsDirectory()));
        } catch (IOException e) {
            log.error("Failed to create comparison results directory: {}", e.getMessage(), e);
        }
    }

    /**
     * Store a comparison result.
     *
     * @param comparisonId The comparison ID
     * @param result The comparison result
     * @throws IOException If there is an error storing the result
     */
    public void storeResult(String comparisonId, ComparisonResult result) throws IOException {
        if (comparisonId == null || result == null) {
            throw new IllegalArgumentException("Comparison ID and result cannot be null");
        }

        File resultFile = getResultFile(comparisonId);

        // Ensure the parent directory exists
        FileUtils.createDirectories(resultFile.getParentFile());

        // Write the result to the file
        objectMapper.writeValue(resultFile, result);

        log.debug("Stored comparison result for ID: {}", comparisonId);
    }

    /**
     * Retrieve a comparison result.
     *
     * @param comparisonId The comparison ID
     * @return The comparison result, or null if not found
     */
    public ComparisonResult retrieveResult(String comparisonId) {
        if (comparisonId == null) {
            return null;
        }

        File resultFile = getResultFile(comparisonId);

        if (!FileUtils.isFileValid(resultFile)) {
            log.debug("Comparison result file not found or invalid for ID: {}", comparisonId);
            return null;
        }

        try {
            return objectMapper.readValue(resultFile, ComparisonResult.class);
        } catch (IOException e) {
            log.error("Failed to read comparison result for ID {}: {}", comparisonId, e.getMessage(), e);
            return null;
        }
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

        File resultFile = getResultFile(comparisonId);
        return FileUtils.deleteFile(resultFile);
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

        File resultFile = getResultFile(comparisonId);
        return FileUtils.isFileValid(resultFile);
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
}