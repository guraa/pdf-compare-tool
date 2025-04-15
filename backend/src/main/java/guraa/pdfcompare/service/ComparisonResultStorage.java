package guraa.pdfcompare.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import guraa.pdfcompare.model.ComparisonResult;
import guraa.pdfcompare.util.FileUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Service for storing and retrieving comparison results.
 * This service handles the persistence of comparison results to the file system.
 */
@Slf4j
@Service
public class ComparisonResultStorage {

    private final String storageLocation;
    private final ObjectMapper objectMapper;

    /**
     * Constructor with storage location.
     *
     * @param storageLocation The location to store comparison results
     * @param objectMapper The object mapper for serialization/deserialization
     */
    public ComparisonResultStorage(
            @Value("${app.storage.location}") String storageLocation,
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
        
        // Write the result to a temporary file first
        File tempFile = FileUtils.createTempFile(resultFile.getParentFile(), "result_", ".tmp");
        
        try {
            // Serialize the result to the temporary file
            objectMapper.writeValue(tempFile, result);
            
            // Move the temporary file to the final location
            Files.move(tempFile.toPath(), resultFile.toPath(), 
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            
            log.debug("Stored comparison result for ID: {}", comparisonId);
        } catch (IOException e) {
            // Clean up the temporary file if there was an error
            FileUtils.deleteFile(tempFile);
            throw e;
        }
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
            // Deserialize the result from the file
            ComparisonResult result = objectMapper.readValue(resultFile, ComparisonResult.class);
            log.debug("Retrieved comparison result for ID: {}", comparisonId);
            return result;
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
        
        if (!resultFile.exists()) {
            return true; // Already deleted
        }
        
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
