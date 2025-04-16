package guraa.pdfcompare.service;

import guraa.pdfcompare.PDFComparisonEngine;
import guraa.pdfcompare.model.*;
import guraa.pdfcompare.repository.ComparisonRepository;
import guraa.pdfcompare.repository.PdfRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * Service for managing PDF comparisons.
 */
@Slf4j
@Service
public class ComparisonService {

    private final PdfRepository pdfRepository;
    private final ComparisonRepository comparisonRepository;
    private final PDFComparisonEngine comparisonEngine;
    private final ExecutorService executorService;
    private final ComparisonResultStorage resultStorage;

    /**
     * Constructor with dependencies.
     */
    public ComparisonService(
            PdfRepository pdfRepository,
            ComparisonRepository comparisonRepository,
            PDFComparisonEngine comparisonEngine,
            @Qualifier("comparisonExecutor") ExecutorService executorService,
            ComparisonResultStorage resultStorage) {
        this.pdfRepository = pdfRepository;
        this.comparisonRepository = comparisonRepository;
        this.comparisonEngine = comparisonEngine;
        this.executorService = executorService;
        this.resultStorage = resultStorage;
    }

    // Cache of comparison tasks
    private final ConcurrentHashMap<String, CompletableFuture<ComparisonResult>> comparisonTasks = new ConcurrentHashMap<>();

    /**
     * Create a new comparison between two PDF documents.
     *
     * @param baseDocumentId The ID of the base document
     * @param compareDocumentId The ID of the document to compare against the base
     * @return The created comparison
     * @throws IOException If there is an error creating the comparison
     */
    public Comparison createComparison(String baseDocumentId, String compareDocumentId) throws IOException {
        // Get the documents
        PdfDocument baseDocument = pdfRepository.findById(baseDocumentId)
                .orElseThrow(() -> new IllegalArgumentException("Base document not found: " + baseDocumentId));

        PdfDocument compareDocument = pdfRepository.findById(compareDocumentId)
                .orElseThrow(() -> new IllegalArgumentException("Compare document not found: " + compareDocumentId));

        // Create a new comparison
        Comparison comparison = Comparison.builder()
                .id(UUID.randomUUID().toString())
                .baseDocumentId(baseDocumentId)
                .compareDocumentId(compareDocumentId)
                .status(Comparison.ComparisonStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // Save the comparison
        comparison = comparisonRepository.save(comparison);

        // Start the comparison in the background
        final String comparisonId = comparison.getId();
        comparisonTasks.computeIfAbsent(comparisonId, key ->
            CompletableFuture.supplyAsync(() -> {
                Comparison comparisonToUpdate = null;
                try {
                    // Fetch the comparison entity once
                    comparisonToUpdate = comparisonRepository.findById(comparisonId)
                            .orElseThrow(() -> new IllegalStateException("Comparison disappeared: " + comparisonId));

                    // Set status to PROCESSING and save
                    comparisonToUpdate.setStatus("processing"); // Use String setter
                    comparisonToUpdate.preUpdate(); // Manually trigger update timestamp
                    comparisonRepository.save(comparisonToUpdate);
                    log.info("Comparison {} status set to PROCESSING", comparisonId);

                    // Perform the actual comparison
                    ComparisonResult result = comparisonEngine.compareDocuments(baseDocument, compareDocument);

                    // Store the result FIRST. If this fails, we go to the catch block.
                    resultStorage.storeResult(comparisonId, result);
                    log.info("Stored comparison result for ID: {}", comparisonId);

                    // If storage succeeded, update status to COMPLETED and save
                    comparisonToUpdate.setStatus("completed"); // Use String setter
                    comparisonToUpdate.setErrorMessage(null); // Clear any previous error
                    comparisonToUpdate.preUpdate(); // Manually trigger update timestamp
                    comparisonRepository.save(comparisonToUpdate);
                    log.info("Comparison {} status set to COMPLETED", comparisonId);

                    return result; // Return the result object (though it's not directly used by caller)

                } catch (Exception e) {
                    log.error("Error during comparison process for ID {}: {}", comparisonId, e.getMessage(), e);

                    // Attempt to update status to FAILED
                    if (comparisonToUpdate != null) {
                        try {
                            // Re-fetch to ensure we have the latest version before updating failure status
                            Comparison finalComparisonState = comparisonRepository.findById(comparisonId)
                                    .orElse(comparisonToUpdate); // Fallback to potentially stale object if fetch fails

                            finalComparisonState.setStatus("failed"); // Use String setter
                            finalComparisonState.setErrorMessage(e.getMessage());
                            finalComparisonState.preUpdate(); // Manually trigger update timestamp
                            comparisonRepository.save(finalComparisonState);
                            log.info("Comparison {} status set to FAILED", comparisonId);
                        } catch (Exception updateEx) {
                            log.error("CRITICAL: Failed to update comparison {} status to FAILED: {}", comparisonId, updateEx.getMessage(), updateEx);
                        }
                    } else {
                         log.error("CRITICAL: comparisonToUpdate was null when trying to set FAILED status for ID {}", comparisonId);
                    }
                    // Propagate exception to mark CompletableFuture as failed
                    throw new RuntimeException("Comparison failed for ID " + comparisonId, e);
                }
            }, executorService)
        );

        // Return the initially saved comparison object (status PENDING)
        return comparison; // Note: The caller gets the PENDING object, status updates happen async
    }

    /**
     * Check if a comparison is in progress.
     *
     * @param id The comparison ID
     * @return true if the comparison is in progress, false otherwise
     */
    public boolean isComparisonInProgress(String id) {
        return comparisonRepository.findById(id)
                .map(comparison -> comparison.getStatusAsString().equals("processing"))
                .orElse(false);
    }

    /**
     * Check if a comparison is completed.
     *
     * @param id The comparison ID
     * @return true if the comparison is completed, false otherwise
     */
    public boolean isComparisonCompleted(String id) {
        return comparisonRepository.findById(id)
                .map(comparison -> comparison.getStatusAsString().equals("completed"))
                .orElse(false);
    }

    /**
     * Get the status of a comparison.
     *
     * @param id The comparison ID
     * @return The comparison status
     */
    public String getComparisonStatus(String id) {
        return comparisonRepository.findById(id)
                .map(Comparison::getStatusAsString)
                .orElse("not_found");
    }

    /**
     * Get the result of a comparison.
     *
     * @param id The comparison ID
     * @return The comparison result, or null if not found or not completed/failed
     */
    public ComparisonResult getComparisonResult(String id) {
        Optional<Comparison> comparisonOpt = comparisonRepository.findById(id);

        if (comparisonOpt.isEmpty()) {
            log.warn("Comparison not found when retrieving result for ID: {}", id);
            return null;
        }

        Comparison comparison = comparisonOpt.get();

        // Only retrieve from storage if the status is COMPLETED
        if (comparison.getStatus() == Comparison.ComparisonStatus.COMPLETED) {
             log.debug("Comparison {} is COMPLETED, retrieving result from storage.", id);
             ComparisonResult result = resultStorage.retrieveResult(id);
             if (result == null) {
                 log.error("Comparison {} is COMPLETED but result is missing from storage!", id);
                 // Optionally, update status back to FAILED here if result is unexpectedly missing
             }
             return result;
        } else {
            log.debug("Comparison {} status is {}, not retrieving result from storage.", id, comparison.getStatus());
            return null; // Don't return result if not completed
        }
    }

    /**
     * Get page details for a specific page in a comparison.
     *
     * @param comparisonId The comparison ID
     * @param pageNumber The page number
     * @param filters Filters to apply to the results
     * @return The page details, or null if not found
     */
    public PageDetails getPageDetails(String comparisonId, int pageNumber, Map<String, Object> filters) {
        ComparisonResult result = getComparisonResult(comparisonId);
        if (result == null) {
            return null;
        }

        // In a real implementation, this would extract the page details from the result
        // For now, we'll just create a dummy page details object
        return PageDetails.builder()
                .pageNumber(pageNumber)
                .pageId(UUID.randomUUID().toString())
                .pageExistsInBase(true)
                .pageExistsInCompare(true)
                .build();
    }

    /**
     * Get document pairs for a comparison.
     *
     * @param comparisonId The comparison ID
     * @return A list of document pairs, or null if not found
     */
    public List<DocumentPair> getDocumentPairs(String comparisonId) {
        ComparisonResult result = getComparisonResult(comparisonId);
        if (result == null) {
            return null;
        }

        // In a real implementation, this would extract the document pairs from the result
        // For now, we'll just create a dummy list
        List<DocumentPair> pairs = new ArrayList<>();
        DocumentPair pair = DocumentPair.builder()
                .pairIndex(0)
                .matched(true)
                .build();

        pairs.add(pair);
        return pairs;
    }

    /**
     * Get page details for a specific page in a document pair.
     *
     * @param comparisonId The comparison ID
     * @param pairIndex The pair index
     * @param pageNumber The page number
     * @param filters Filters to apply to the results
     * @return The page details, or null if not found
     */
    public PageDetails getDocumentPairPageDetails(
            String comparisonId, int pairIndex, int pageNumber, Map<String, Object> filters) {
        ComparisonResult result = getComparisonResult(comparisonId);
        if (result == null) {
            return null;
        }

        // In a real implementation, this would extract the page details from the result
        // For now, we'll just create a dummy page details object
        return PageDetails.builder()
                .pageNumber(pageNumber)
                .pageId(UUID.randomUUID().toString())
                .pageExistsInBase(true)
                .pageExistsInCompare(true)
                .build();
    }
    /**
     * Get the comparison result for a specific document pair.
     *
     * @param comparisonId The comparison ID
     * @param pairIndex The pair index
     * @return The comparison result, or null if not found
     */
    public ComparisonResult getDocumentPairResult(String comparisonId, int pairIndex) {
        ComparisonResult result = getComparisonResult(comparisonId);
        if (result == null) {
            return null;
        }

        List<DocumentPair> pairs = getDocumentPairs(comparisonId);
        if (pairs == null || pairIndex >= pairs.size()) {
            return null;
        }

        // In a real implementation, this would extract the result for the specific pair
        // For now, we'll just return the overall result
        return result;
    }
}
