package guraa.pdfcompare.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import guraa.pdfcompare.model.Comparison;
import guraa.pdfcompare.model.ComparisonResult;
import guraa.pdfcompare.model.DocumentPair;
import guraa.pdfcompare.model.PageDetails;
import guraa.pdfcompare.repository.ComparisonRepository;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache management for the ComparisonService.
 * Handles caching of comparison results, page details, and document pairs.
 */
@Slf4j
@Component
public class ComparisonServiceCache {
    // Define logger explicitly as fallback if Lombok annotation doesn't work
    private static final Logger logger = LoggerFactory.getLogger(ComparisonServiceCache.class);

    // Cache for comparison results and page details using soft references
    private final Map<String, ComparisonResult> resultCache = new ConcurrentHashMap<>();
    private final Map<String, PageDetails> pageDetailsCache = new ConcurrentHashMap<>(100);
    private final Map<String, List<DocumentPair>> documentPairsCache = new ConcurrentHashMap<>();

    /**
     * Store a comparison result in the cache.
     *
     * @param comparisonId The comparison ID
     * @param result The comparison result to cache
     */
    public void cacheComparisonResult(String comparisonId, ComparisonResult result) {
        resultCache.put(comparisonId, result);
    }

    /**
     * Store document pairs in the cache.
     *
     * @param comparisonId The comparison ID
     * @param documentPairs The document pairs to cache
     */
    public void cacheDocumentPairs(String comparisonId, List<DocumentPair> documentPairs) {
        documentPairsCache.put(comparisonId, documentPairs);
    }

    /**
     * Store page details in the cache.
     *
     * @param cacheKey The cache key for the page details
     * @param details The page details to cache
     */
    public void cachePageDetails(String cacheKey, PageDetails details) {
        // Manage cache size
        if (pageDetailsCache.size() >= 100) {
            // Remove a random entry if cache is full
            String keyToRemove = pageDetailsCache.keySet().iterator().next();
            pageDetailsCache.remove(keyToRemove);
        }

        pageDetailsCache.put(cacheKey, details);
    }

    /**
     * Get a comparison result from the cache.
     *
     * @param comparisonId The comparison ID
     * @param comparisonRepository Repository for comparison data
     * @param objectMapper Object mapper for JSON serialization
     * @return The comparison result, or null if not found
     * @throws IOException If there's an error loading the result
     */
    public ComparisonResult getComparisonResult(String comparisonId,
                                                ComparisonRepository comparisonRepository,
                                                ObjectMapper objectMapper) throws IOException {
        // Check cache first
        ComparisonResult result = resultCache.get(comparisonId);
        if (result != null) {
            return result;
        }

        // Load from database
        Comparison comparison = comparisonRepository.findByComparisonId(comparisonId)
                .orElse(null);

        if (comparison == null) {
            return null;
        }

        // If comparison is still in progress, return null
        if (comparison.getStatus() != Comparison.ComparisonStatus.COMPLETED) {
            return null;
        }

        // Load from file
        Path resultPath = Paths.get(comparison.getResultFilePath());
        if (Files.exists(resultPath)) {
            result = objectMapper.readValue(resultPath.toFile(), ComparisonResult.class);
            resultCache.put(comparisonId, result);
            return result;
        }

        return null;
    }

    /**
     * Get document pairs from the cache.
     *
     * @param comparisonId The comparison ID
     * @param comparisonRepository Repository for comparison data
     * @param objectMapper Object mapper for JSON serialization
     * @param comparisonService The comparison service for getting results
     * @return List of document pairs, or null if not found
     * @throws IOException If there's an error loading the pairs
     */
    public List<DocumentPair> getDocumentPairs(String comparisonId,
                                               ComparisonRepository comparisonRepository,
                                               ObjectMapper objectMapper,
                                               ComparisonService comparisonService) throws IOException {
        // Check cache first
        List<DocumentPair> pairs = documentPairsCache.get(comparisonId);
        if (pairs != null) {
            return pairs;
        }

        // Load from database
        Comparison comparison = comparisonRepository.findByComparisonId(comparisonId)
                .orElse(null);

        if (comparison == null) {
            return null;
        }

        // If comparison is still in progress, return null
        if (comparison.getStatus() != Comparison.ComparisonStatus.COMPLETED) {
            return null;
        }

        // Load from the main result file
        ComparisonResult result = comparisonService.getComparisonResult(comparisonId);
        if (result != null && result.getDocumentPairs() != null) {
            documentPairsCache.put(comparisonId, result.getDocumentPairs());
            return result.getDocumentPairs();
        }

        return null;
    }

    /**
     * Get page details from the cache.
     *
     * @param cacheKey The cache key for the page details
     * @return The page details, or null if not found
     */
    public PageDetails getPageDetailsFromCache(String cacheKey) {
        return pageDetailsCache.get(cacheKey);
    }

    /**
     * Check if a comparison is completed with cached results.
     *
     * @param comparisonId The comparison ID
     * @param comparisonRepository Repository for comparison data
     * @return True if the comparison is completed
     */
    public boolean isComparisonCompleted(String comparisonId, ComparisonRepository comparisonRepository) {
        // Check cache first to avoid database query
        if (resultCache.containsKey(comparisonId)) {
            return true;
        }

        Comparison comparison = comparisonRepository.findByComparisonId(comparisonId)
                .orElse(null);

        if (comparison == null) {
            return false;
        }

        return comparison.getStatus() == Comparison.ComparisonStatus.COMPLETED;
    }

    /**
     * Check if a comparison has failed.
     *
     * @param comparisonId The comparison ID
     * @param comparisonRepository Repository for comparison data
     * @return True if the comparison has failed
     */
    public boolean isComparisonFailed(String comparisonId, ComparisonRepository comparisonRepository) {
        // We can't easily cache failure status as it's not part of the result object
        Comparison comparison = comparisonRepository.findByComparisonId(comparisonId)
                .orElse(null);

        if (comparison == null) {
            return false;
        }

        return comparison.getStatus() == Comparison.ComparisonStatus.FAILED;
    }

    /**
     * Get the status message for a comparison.
     *
     * @param comparisonId The comparison ID
     * @param comparisonRepository Repository for comparison data
     * @return The status message, or null if not found
     */
    public String getComparisonStatusMessage(String comparisonId, ComparisonRepository comparisonRepository) {
        Comparison comparison = comparisonRepository.findByComparisonId(comparisonId)
                .orElse(null);

        if (comparison == null) {
            return null;
        }

        return comparison.getStatusMessage();
    }

    /**
     * Check if a comparison is still in progress.
     *
     * @param comparisonId The comparison ID
     * @param comparisonRepository Repository for comparison data
     * @return True if the comparison is still in progress
     */
    public boolean isComparisonInProgress(String comparisonId, ComparisonRepository comparisonRepository) {
        // Check cache first to avoid database query if completed
        if (resultCache.containsKey(comparisonId)) {
            return false; // If it's in the result cache, it's not in progress
        }

        Comparison comparison = comparisonRepository.findByComparisonId(comparisonId)
                .orElse(null);

        if (comparison == null) {
            return false;
        }

        return comparison.getStatus() == Comparison.ComparisonStatus.PENDING ||
                comparison.getStatus() == Comparison.ComparisonStatus.PROCESSING ||
                comparison.getStatus() == Comparison.ComparisonStatus.PROCESSING_DOCUMENTS ||
                comparison.getStatus() == Comparison.ComparisonStatus.DOCUMENT_MATCHING ||
                comparison.getStatus() == Comparison.ComparisonStatus.COMPARING;
    }

    /**
     * Cleanup method to free memory when comparison details are no longer needed.
     * Should be called when users navigate away from comparison details.
     *
     * @param comparisonId The comparison ID to clean up
     */
    public void cleanupComparisonMemory(String comparisonId) {
        // Remove from all caches
        resultCache.remove(comparisonId);
        documentPairsCache.remove(comparisonId);

        // Remove all related page details
        List<String> keysToRemove = new ArrayList<>();
        for (String key : pageDetailsCache.keySet()) {
            if (key.startsWith(comparisonId + "_")) {
                keysToRemove.add(key);
            }
        }

        for (String key : keysToRemove) {
            pageDetailsCache.remove(key);
        }
    }

    /**
     * Get cache statistics.
     *
     * @return Map with cache statistics
     */
    public Map<String, Integer> getCacheStats() {
        Map<String, Integer> stats = new HashMap<>();
        stats.put("resultCacheSize", resultCache.size());
        stats.put("pageDetailsCacheSize", pageDetailsCache.size());
        stats.put("documentPairsCacheSize", documentPairsCache.size());
        return stats;
    }
}