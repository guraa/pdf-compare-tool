package guraa.pdfcompare.service;

import guraa.pdfcompare.PDFComparisonEngine;
import guraa.pdfcompare.model.*;
import guraa.pdfcompare.repository.ComparisonRepository;
import guraa.pdfcompare.repository.PdfRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * Service for managing PDF comparisons with improved status handling.
 */
@Slf4j
@Service
public class ComparisonService {

    private final PdfRepository pdfRepository;
    private final ComparisonRepository comparisonRepository;
    private final PDFComparisonEngine comparisonEngine;
    private final ExecutorService executorService;
    private final ComparisonResultStorage resultStorage;

    // Maximum time to wait for a comparison task to complete (30 minutes)
    private static final long MAX_COMPARISON_TIMEOUT_MINUTES = 30;

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
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
                .status(Comparison.ComparisonStatus.PROCESSING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // Save the comparison
        comparison = comparisonRepository.saveAndFlush(comparison);
        final String comparisonId = comparison.getId();
        log.info("Created comparison with ID: {} in PROCESSING state", comparisonId);


        CompletableFuture.runAsync(() -> {
            try {
                // Load the documents again - this ensures we're in a new transaction
                PdfDocument baseDoc = pdfRepository.findById(baseDocumentId)
                        .orElseThrow(() -> new IllegalArgumentException("Base document not found"));
                PdfDocument compareDoc = pdfRepository.findById(compareDocumentId)
                        .orElseThrow(() -> new IllegalArgumentException("Compare document not found"));

                // Perform the actual comparison
                ComparisonResult result = comparisonEngine.compareDocuments(baseDoc, compareDoc);
                log.info("Comparison completed for ID: {}", comparisonId);

                // Store the result
                resultStorage.storeResult(comparisonId, result);
                log.info("Stored comparison result for ID: {}", comparisonId);

                // Update status to COMPLETED
                updateComparisonStatus(comparisonId, Comparison.ComparisonStatus.COMPLETED, null);
            } catch (Exception e) {
                log.error("Error during comparison process for ID {}: {}", comparisonId, e.getMessage(), e);
                // Update status to FAILED
                try {
                    updateComparisonStatus(comparisonId, Comparison.ComparisonStatus.FAILED, e.getMessage());
                } catch (Exception ex) {
                    log.error("Failed to update status for failed comparison {}", comparisonId, ex);
                }
            }
        }, executorService);

        return comparison;
    }


    /**
     * Ensure the comparison is in PROCESSING state.
     *
     * @param comparisonId The comparison ID
     */
    private void ensureProcessingStatus(String comparisonId) {
        try {
            Optional<Comparison> comparisonOpt = comparisonRepository.findById(comparisonId);
            if (comparisonOpt.isPresent()) {
                Comparison comparison = comparisonOpt.get();
                if (comparison.getStatus() != Comparison.ComparisonStatus.PROCESSING) {
                    log.warn("Comparison {} found in unexpected state: {}. Forcing update to PROCESSING",
                            comparisonId, comparison.getStatus());
                    updateComparisonStatus(comparisonId, Comparison.ComparisonStatus.PROCESSING, null);
                }
            } else {
                log.error("Cannot ensure PROCESSING status - comparison {} not found", comparisonId);
            }
        } catch (Exception e) {
            log.error("Error ensuring PROCESSING status for comparison {}: {}", comparisonId, e.getMessage(), e);
        }
    }

    /**
     * Update the status of a comparison atomically.
     *
     * @param comparisonId The comparison ID
     * @param status The new status
     * @param errorMessage The error message (if status is FAILED)
     * @return The updated comparison
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateComparisonStatus(String comparisonId, Comparison.ComparisonStatus status, String errorMessage) {
        try {
            log.info("Updating comparison {} status to {}", comparisonId, status);
            Optional<Comparison> comparisonOpt = comparisonRepository.findById(comparisonId);
            if (comparisonOpt.isPresent()) {
                Comparison comparison = comparisonOpt.get();
                comparison.setStatus(status);
                comparison.setErrorMessage(errorMessage);
                comparison.setUpdatedAt(LocalDateTime.now());
                comparisonRepository.saveAndFlush(comparison);
                log.info("Successfully updated comparison {} status to {}", comparisonId, status);
            }
        } catch (Exception e) {
            log.error("Error updating comparison status: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Check if a comparison is in progress.
     *
     * @param id The comparison ID
     * @return true if the comparison is in progress, false otherwise
     */
    public boolean isComparisonInProgress(String id) {
        Optional<Comparison> comparisonOpt = comparisonRepository.findById(id);
        if (comparisonOpt.isEmpty()) {
            return false;
        }

        Comparison comparison = comparisonOpt.get();
        boolean inProgress = comparison.getStatus() == Comparison.ComparisonStatus.PROCESSING ||
                comparison.getStatus() == Comparison.ComparisonStatus.PENDING;

        // If the comparison is marked as in progress but the task is done,
        // we should update the status
        if (inProgress) {
            CompletableFuture<ComparisonResult> task = comparisonTasks.get(id);
            if (task != null && task.isDone() && !task.isCompletedExceptionally()) {
                try {
                    log.warn("Found completed task for comparison {} still marked as {}",
                            id, comparison.getStatus());

                    // Try to get result from the task
                    ComparisonResult result = task.get();

                    if (result != null) {
                        // Ensure result is stored
                        if (!resultStorage.resultExists(id)) {
                            resultStorage.storeResult(id, result);
                            log.info("Retroactively stored result for completed comparison {}", id);
                        }

                        // Update status
                        updateComparisonStatus(id, Comparison.ComparisonStatus.COMPLETED, null);
                        log.info("Retroactively updated comparison {} status to COMPLETED", id);

                        return false; // No longer in progress
                    }
                } catch (Exception e) {
                    log.error("Error handling completed task for in-progress comparison {}: {}",
                            id, e.getMessage(), e);
                }
            }
        }

        return inProgress;
    }

    /**
     * Check if a comparison is completed.
     *
     * @param id The comparison ID
     * @return true if the comparison is completed, false otherwise
     */
    public boolean isComparisonCompleted(String id) {
        return comparisonRepository.findById(id)
                .map(comparison -> comparison.getStatus() == Comparison.ComparisonStatus.COMPLETED)
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
                .map(comparison -> comparison.getStatus().toString())
                .orElse("NOT_FOUND");
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
        log.debug("Getting result for comparison {} with status {}", id, comparison.getStatus());

        // If the comparison is still running, check if it's actually done now
        if (comparison.getStatus() == Comparison.ComparisonStatus.PROCESSING ||
                comparison.getStatus() == Comparison.ComparisonStatus.PENDING) {
            CompletableFuture<ComparisonResult> task = comparisonTasks.get(id);
            if (task != null && task.isDone() && !task.isCompletedExceptionally()) {
                log.info("Comparison task is done but status not updated yet. Forcing status update for {}", id);
                try {
                    ComparisonResult result = task.get();
                    if (result != null && !resultStorage.resultExists(id)) {
                        resultStorage.storeResult(id, result);
                    }
                    updateComparisonStatus(id, Comparison.ComparisonStatus.COMPLETED, null);
                    comparison.setStatus(Comparison.ComparisonStatus.COMPLETED);
                    return result;
                } catch (Exception e) {
                    log.error("Failed to update status for completed comparison {}", id, e);
                }
            }
        }

        if (comparison.getStatus() == Comparison.ComparisonStatus.COMPLETED) {
            log.debug("Retrieving comparison result from storage for ID: {}", id);
            ComparisonResult result = resultStorage.retrieveResult(id);

            if (result == null) {
                log.error("Comparison {} is marked as COMPLETED but result is missing from storage!", id);

                // Check if task is still available and done
                CompletableFuture<ComparisonResult> task = comparisonTasks.get(id);
                if (task != null && task.isDone() && !task.isCompletedExceptionally()) {
                    try {
                        result = task.get();
                        if (result != null) {
                            log.info("Retrieved result from task cache for ID: {}", id);
                            // Store it since it was missing
                            resultStorage.storeResult(id, result);
                        }
                    } catch (Exception e) {
                        log.error("Failed to retrieve result from task cache for ID: {}", id, e);
                    }
                }
            }

            return result;
        } else {
            log.debug("Comparison {} status is {}, not retrieving result", id, comparison.getStatus());
            return null;
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
        PageDetails pageDetails = PageDetails.builder()
                .pageNumber(pageNumber)
                .pageId(UUID.randomUUID().toString())
                .pageExistsInBase(true)
                .pageExistsInCompare(true)
                .build();

        // Apply filters if needed
        applyFilters(pageDetails, filters);

        return pageDetails;
    }

    /**
     * Apply filters to page details.
     *
     * @param pageDetails The page details
     * @param filters The filters to apply
     */
    private void applyFilters(PageDetails pageDetails, Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) {
            return;
        }

        // Filter logic would go here in a real implementation
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
                .baseStartPage(1)
                .baseEndPage(result.getPagePairs().size())
                .basePageCount(result.getPagePairs().size())
                .compareStartPage(1)
                .compareEndPage(result.getPagePairs().size())
                .comparePageCount(result.getPagePairs().size())
                .hasBaseDocument(true)
                .hasCompareDocument(true)
                .similarityScore(0.85)
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
        PageDetails pageDetails = PageDetails.builder()
                .pageNumber(pageNumber)
                .pageId(UUID.randomUUID().toString())
                .pageExistsInBase(true)
                .pageExistsInCompare(true)
                .textDifferenceCount(2)
                .imageDifferenceCount(1)
                .fontDifferenceCount(0)
                .styleDifferenceCount(1)
                .build();

        // Apply filters if needed
        applyFilters(pageDetails, filters);

        return pageDetails;
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