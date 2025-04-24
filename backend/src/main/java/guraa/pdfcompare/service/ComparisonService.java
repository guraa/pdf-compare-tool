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
import java.util.concurrent.ExecutorService;

/**
 * Service for managing PDF comparisons with robust transaction handling.
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

    /**
     * Create a new comparison between two PDF documents.
     *
     * @param baseDocumentId    The ID of the base document
     * @param compareDocumentId The ID of the document to compare against the base
     * @return The created comparison
     * @throws IOException If there is an error creating the comparison
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Comparison createComparison(String baseDocumentId, String compareDocumentId) throws IOException {
        // Validate inputs
        if (baseDocumentId == null || compareDocumentId == null) {
            throw new IllegalArgumentException("Both base document ID and compare document ID are required");
        }

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

        // Execute comparison asynchronously
        CompletableFuture.runAsync(() -> {
            try {
                log.info("Starting asynchronous comparison for ID: {}", comparisonId);

                // Load documents in a new transaction
                PdfDocument baseDoc = pdfRepository.findById(baseDocumentId)
                        .orElseThrow(() -> new IllegalArgumentException("Base document not found"));
                PdfDocument compareDoc = pdfRepository.findById(compareDocumentId)
                        .orElseThrow(() -> new IllegalArgumentException("Compare document not found"));

                // Perform the actual comparison
                ComparisonResult result = comparisonEngine.compareDocuments(baseDoc, compareDoc);

                // Ensure result ID matches comparison ID
                if (!comparisonId.equals(result.getId())) {
                    log.info("Updating result ID from {} to {}", result.getId(), comparisonId);
                    result = ComparisonResult.builder()
                            .id(comparisonId)
                            .baseDocumentId(result.getBaseDocumentId())
                            .compareDocumentId(result.getCompareDocumentId())
                            .pagePairs(result.getPagePairs())
                            .summary(result.getSummary())
                            .differencesByPage(result.getDifferencesByPage())
                            .build();
                }

                log.info("Comparison completed successfully for ID: {}", comparisonId);

                // Store the result
                resultStorage.storeResult(comparisonId, result);
                log.info("Stored comparison result for ID: {}", comparisonId);

                // Update status to COMPLETED - this must be done in a new transaction
                updateComparisonStatus(comparisonId, Comparison.ComparisonStatus.COMPLETED, null);
                log.info("Updated comparison {} status to COMPLETED", comparisonId);

            } catch (Exception e) {
                log.error("Error during comparison process for ID {}: {}", comparisonId, e.getMessage(), e);

                // Update status to FAILED
                try {
                    updateComparisonStatus(comparisonId, Comparison.ComparisonStatus.FAILED, e.getMessage());
                    log.info("Updated comparison {} status to FAILED: {}", comparisonId, e.getMessage());
                } catch (Exception ex) {
                    log.error("Failed to update status for failed comparison {}: {}",
                            comparisonId, ex.getMessage(), ex);
                }
            }
        }, executorService);

        return comparison;
    }

    /**
     * Update the status of a comparison atomically.
     * This method runs in a new transaction to ensure database updates.
     *
     * @param comparisonId The comparison ID
     * @param status       The new status
     * @param errorMessage The error message (if status is FAILED)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateComparisonStatus(String comparisonId, Comparison.ComparisonStatus status, String errorMessage) {
        try {
            log.info("Updating comparison {} status to {}", comparisonId, status);
            Optional<Comparison> comparisonOpt = comparisonRepository.findById(comparisonId);
            if (comparisonOpt.isEmpty()) {
                log.error("Cannot update status - comparison {} not found", comparisonId);
                return;
            }

            Comparison comparison = comparisonOpt.get();

            // Update status and error message
            comparison.setStatus(status);
            comparison.setErrorMessage(errorMessage);
            comparison.setUpdatedAt(LocalDateTime.now());

            // Save and flush immediately to ensure it's written to the database
            comparisonRepository.saveAndFlush(comparison);
            log.info("Successfully updated comparison {} status from {} to {}",
                    comparisonId, comparison.getStatus(), status);
        } catch (Exception e) {
            log.error("Error updating comparison {} status: {}", comparisonId, e.getMessage(), e);
            throw e; // Re-throw to allow transaction rollback
        }
    }

    /**
     * Check if a comparison is in progress.
     *
     * @param id The comparison ID
     * @return true if the comparison is in progress, false otherwise
     */
    public boolean isComparisonInProgress(String id) {
        // Check if result exists (which indicates the comparison is complete)
        if (resultStorage.resultExists(id)) {
            return false;
        }

        // Check database status
        Optional<Comparison> comparisonOpt = comparisonRepository.findById(id);
        if (comparisonOpt.isEmpty()) {
            return false;
        }

        Comparison comparison = comparisonOpt.get();
        return comparison.getStatus() == Comparison.ComparisonStatus.PROCESSING ||
                comparison.getStatus() == Comparison.ComparisonStatus.PENDING;
    }

    /**
     * Get the result of a comparison with improved result existence checking.
     *
     * @param id The comparison ID
     * @return The comparison result, or null if not found or not completed
     */
    public ComparisonResult getComparisonResult(String id) {
        // First check if result exists in storage
        if (resultStorage.resultExists(id)) {
            // Also check if the status is COMPLETED, if not fix it
            Optional<Comparison> comparisonOpt = comparisonRepository.findById(id);
            if (comparisonOpt.isPresent() &&
                    comparisonOpt.get().getStatus() != Comparison.ComparisonStatus.COMPLETED) {

                log.warn("Result exists for comparison {} but status is {}. Fixing to COMPLETED.",
                        id, comparisonOpt.get().getStatus());
                updateComparisonStatus(id, Comparison.ComparisonStatus.COMPLETED, null);
            }

            // Return the result
            return resultStorage.retrieveResult(id);
        }

        // Check database status
        Optional<Comparison> comparisonOpt = comparisonRepository.findById(id);
        if (comparisonOpt.isEmpty()) {
            log.warn("Comparison not found when retrieving result for ID: {}", id);
            return null;
        }

        Comparison comparison = comparisonOpt.get();

        if (comparison.getStatus() == Comparison.ComparisonStatus.COMPLETED) {
            // Try again to get result from storage
            ComparisonResult result = resultStorage.retrieveResult(id);

            if (result == null) {
                log.error("Comparison {} is marked as COMPLETED but result is missing from storage!", id);
            }

            return result;
        } else {
            log.debug("Comparison {} status is {}, not retrieving result", id, comparison.getStatus());
            return null;
        }
    }

    /**
     * Check if a comparison is completed.
     *
     * @param id The comparison ID
     * @return true if the comparison is completed, false otherwise
     */
    public boolean isComparisonCompleted(String id) {
        // First check if result exists, which is the most reliable indicator
        if (resultStorage.resultExists(id)) {
            // Also ensure database status is consistent
            Optional<Comparison> comparisonOpt = comparisonRepository.findById(id);
            if (comparisonOpt.isPresent() &&
                    comparisonOpt.get().getStatus() != Comparison.ComparisonStatus.COMPLETED) {

                // Fix inconsistent state
                updateComparisonStatus(id, Comparison.ComparisonStatus.COMPLETED, null);
            }
            return true;
        }

        // Fall back to database check
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
        // Check if result exists first, which might indicate COMPLETED status
        if (resultStorage.resultExists(id)) {
            // Ensure database status is consistent
            Optional<Comparison> comparisonOpt = comparisonRepository.findById(id);
            if (comparisonOpt.isPresent() &&
                    comparisonOpt.get().getStatus() != Comparison.ComparisonStatus.COMPLETED) {

                updateComparisonStatus(id, Comparison.ComparisonStatus.COMPLETED, null);
            }

            return "COMPLETED";
        }

        return comparisonRepository.findById(id)
                .map(comparison -> comparison.getStatus().toString())
                .orElse("NOT_FOUND");
    }

    /**
     * Get page details for a specific page in a comparison.
     *
     * @param comparisonId The comparison ID
     * @param pageNumber   The page number
     * @param filters      Filters to apply to the results
     * @return The page details, or null if not found
     */
    public PageDetails getPageDetails(String comparisonId, int pageNumber, Map<String, Object> filters) {
        ComparisonResult result = getComparisonResult(comparisonId);
        if (result == null) {
            return null;
        }

        // Implementation details
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
     * @param filters     The filters to apply
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

        // Implementation details
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
}