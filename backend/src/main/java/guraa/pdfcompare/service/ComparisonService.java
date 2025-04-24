package guraa.pdfcompare.service;

import guraa.pdfcompare.PDFComparisonEngine;
import guraa.pdfcompare.model.*;
import guraa.pdfcompare.model.difference.Difference;
import guraa.pdfcompare.repository.ComparisonRepository;
import guraa.pdfcompare.repository.PdfRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

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
    private final ImageComparisonService imageComparisonService;

    // Map to track active comparison tasks
    private final Map<String, CompletableFuture<Void>> activeComparisonTasks = new ConcurrentHashMap<>();

    // Map to track cancellation tokens for comparisons
    private final Map<String, AtomicBoolean> cancellationTokens = new ConcurrentHashMap<>();

    @Value("${app.comparison.max-processing-minutes:15}")
    private int maxProcessingMinutes = 15;

    /**
     * Constructor with dependencies.
     */
    public ComparisonService(
            PdfRepository pdfRepository,
            ComparisonRepository comparisonRepository,
            PDFComparisonEngine comparisonEngine,
            @Qualifier("comparisonExecutor") ExecutorService executorService,
            ComparisonResultStorage resultStorage,
            ImageComparisonService imageComparisonService) {
        this.pdfRepository = pdfRepository;
        this.comparisonRepository = comparisonRepository;
        this.comparisonEngine = comparisonEngine;
        this.executorService = executorService;
        this.resultStorage = resultStorage;
        this.imageComparisonService = imageComparisonService;
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
                .progress(0)
                .totalOperations(100)
                .completedOperations(0)
                .currentPhase("Initializing")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // Save the comparison
        comparison = comparisonRepository.saveAndFlush(comparison);
        final String comparisonId = comparison.getId();
        log.info("Created comparison with ID: {} in PROCESSING state", comparisonId);

        // Create cancellation token
        AtomicBoolean cancellationToken = new AtomicBoolean(false);
        cancellationTokens.put(comparisonId, cancellationToken);

        // Execute comparison asynchronously with timeout
        CompletableFuture<Void> comparisonTask = CompletableFuture.runAsync(() -> {
            try {
                log.info("Starting asynchronous comparison for ID: {}", comparisonId);
                updateComparisonPhase(comparisonId, "Loading documents", 5);

                // Load documents in a new transaction
                PdfDocument baseDoc;
                PdfDocument compareDoc;
                try {
                    baseDoc = pdfRepository.findById(baseDocumentId)
                            .orElseThrow(() -> new IllegalArgumentException("Base document not found"));
                    updateComparisonProgress(comparisonId, 10);

                    compareDoc = pdfRepository.findById(compareDocumentId)
                            .orElseThrow(() -> new IllegalArgumentException("Compare document not found"));
                    updateComparisonProgress(comparisonId, 15);
                } catch (Exception e) {
                    log.error("Error loading documents for comparison {}: {}", comparisonId, e.getMessage());
                    updateComparisonStatus(comparisonId, Comparison.ComparisonStatus.FAILED,
                            "Error loading documents: " + e.getMessage());
                    return;
                }

                // Check if cancelled
                if (cancellationToken.get()) {
                    log.info("Comparison {} was cancelled before processing", comparisonId);
                    updateComparisonStatus(comparisonId, Comparison.ComparisonStatus.CANCELLED, "Comparison was cancelled");
                    return;
                }

                updateComparisonPhase(comparisonId, "Comparing documents", 20);

                // Perform the actual comparison with timeout and cancellation handling
                ComparisonResult result;
                try {
                    // Perform the comparison with a timeout
                    result = comparisonEngine.compareDocuments(baseDoc, compareDoc);
                    updateComparisonProgress(comparisonId, 85);
                } catch (Exception e) {
                    log.error("Error during comparison process for ID {}: {}", comparisonId, e.getMessage(), e);
                    updateComparisonStatus(comparisonId, Comparison.ComparisonStatus.FAILED,
                            "Error during comparison: " + e.getMessage());
                    return;
                }

                // Check if cancelled
                if (cancellationToken.get()) {
                    log.info("Comparison {} was cancelled after processing", comparisonId);
                    updateComparisonStatus(comparisonId, Comparison.ComparisonStatus.CANCELLED, "Comparison was cancelled");
                    return;
                }

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
                updateComparisonPhase(comparisonId, "Saving results", 90);

                // Store the result
                try {
                    resultStorage.storeResult(comparisonId, result);
                    log.info("Stored comparison result for ID: {}", comparisonId);
                    updateComparisonProgress(comparisonId, 95);
                } catch (Exception e) {
                    log.error("Error storing result for comparison {}: {}", comparisonId, e.getMessage(), e);
                    updateComparisonStatus(comparisonId, Comparison.ComparisonStatus.FAILED,
                            "Error storing result: " + e.getMessage());
                    return;
                }

                // Final update - completed
                updateComparisonPhase(comparisonId, "Completed", 100);

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
            } finally {
                // Always clean up the cancellation token
                cancellationTokens.remove(comparisonId);
                activeComparisonTasks.remove(comparisonId);
            }
        }, executorService);

        // Add timeout to the task
        comparisonTask = comparisonTask.orTimeout(maxProcessingMinutes, TimeUnit.MINUTES)
                .exceptionally(ex -> {
                    if (ex instanceof TimeoutException) {
                        log.error("Comparison {} timed out after {} minutes", comparisonId, maxProcessingMinutes);

                        // Cancel ongoing operations
                        cancellationToken.set(true);
                        imageComparisonService.cancelAllComparisons();

                        // Update status to FAILED
                        try {
                            updateComparisonStatus(comparisonId, Comparison.ComparisonStatus.FAILED,
                                    "Comparison timed out after " + maxProcessingMinutes + " minutes");
                        } catch (Exception e) {
                            log.error("Failed to update status for timed out comparison {}: {}",
                                    comparisonId, e.getMessage());
                        }
                    } else {
                        log.error("Unexpected error in comparison {}: {}", comparisonId, ex.getMessage(), ex);
                    }
                    return null;
                });

        // Store task for tracking
        activeComparisonTasks.put(comparisonId, comparisonTask);

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

            // If completed, ensure progress is 100%
            if (status == Comparison.ComparisonStatus.COMPLETED) {
                comparison.setProgress(100);
                comparison.setCompletedOperations(comparison.getTotalOperations());
                comparison.setCurrentPhase("Completed");
            }

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
     * Update the phase of a comparison.
     *
     * @param comparisonId The comparison ID
     * @param phase The current phase
     * @param progress The progress percentage (0-100)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateComparisonPhase(String comparisonId, String phase, int progress) {
        try {
            Optional<Comparison> comparisonOpt = comparisonRepository.findById(comparisonId);
            if (comparisonOpt.isEmpty()) {
                log.error("Cannot update phase - comparison {} not found", comparisonId);
                return;
            }

            Comparison comparison = comparisonOpt.get();
            comparison.setCurrentPhase(phase);
            comparison.setProgress(progress);
            comparison.setCompletedOperations(progress * comparison.getTotalOperations() / 100);
            comparison.setUpdatedAt(LocalDateTime.now());

            comparisonRepository.saveAndFlush(comparison);
            log.debug("Updated comparison {} to phase: {} ({}%)", comparisonId, phase, progress);
        } catch (Exception e) {
            log.error("Error updating comparison {} phase: {}", comparisonId, e.getMessage(), e);
        }
    }

    /**
     * Update the progress of a comparison.
     *
     * @param comparisonId The comparison ID
     * @param progress The progress percentage (0-100)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateComparisonProgress(String comparisonId, int progress) {
        try {
            Optional<Comparison> comparisonOpt = comparisonRepository.findById(comparisonId);
            if (comparisonOpt.isEmpty()) {
                return;
            }

            Comparison comparison = comparisonOpt.get();
            comparison.setProgress(progress);
            comparison.setCompletedOperations(progress * comparison.getTotalOperations() / 100);
            comparison.setUpdatedAt(LocalDateTime.now());

            comparisonRepository.saveAndFlush(comparison);
            log.debug("Updated comparison {} progress: {}%", comparisonId, progress);
        } catch (Exception e) {
            log.error("Error updating comparison {} progress: {}", comparisonId, e.getMessage(), e);
        }
    }

    /**
     * Check if a comparison is in progress.
     *
     * @param id The comparison ID
     * @return true if the comparison is in progress, false otherwise
     */
    public boolean isComparisonInProgress(String id) {
        // Check if task is still active
        CompletableFuture<Void> task = activeComparisonTasks.get(id);
        if (task != null && !task.isDone()) {
            return true;
        }

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
     * Cancel a comparison.
     *
     * @param comparisonId The comparison ID
     * @return true if cancelled successfully, false otherwise
     */
    public boolean cancelComparison(String comparisonId) {
        // Check if the comparison is active
        if (!isComparisonInProgress(comparisonId)) {
            log.info("Comparison {} is not in progress, cannot cancel", comparisonId);
            return false;
        }

        // Set cancellation token
        AtomicBoolean token = cancellationTokens.get(comparisonId);
        if (token != null) {
            token.set(true);
            log.info("Cancellation requested for comparison {}", comparisonId);

            // Cancel ongoing image comparisons
            imageComparisonService.cancelAllComparisons();

            // Try to cancel the task
            CompletableFuture<Void> task = activeComparisonTasks.get(comparisonId);
            if (task != null && !task.isDone()) {
                task.cancel(true);
            }

            // Update the status to cancelled
            try {
                updateComparisonStatus(comparisonId, Comparison.ComparisonStatus.CANCELLED, "Cancelled by user");
                return true;
            } catch (Exception e) {
                log.error("Failed to update status for cancelled comparison {}: {}", comparisonId, e.getMessage());
                return false;
            }
        }

        return false;
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
            return new ArrayList<>(); // Return empty list instead of null
        }

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
                .similarityScore(result.getOverallSimilarityScore())
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
    public PageDetails getPageDetailsForPair(String comparisonId, int pairIndex, int pageNumber, Map<String, Object> filters) {
        ComparisonResult result = getComparisonResult(comparisonId);
        if (result == null) {
            log.warn("No result found for comparison ID: {}", comparisonId);
            return null;
        }

        // Get document pairs
        List<DocumentPair> pairs = getDocumentPairs(comparisonId);
        if (pairs == null || pairs.isEmpty() || pairIndex >= pairs.size()) {
            log.warn("Invalid pair index {} for comparison ID: {}", pairIndex, comparisonId);
            return null;
        }

        DocumentPair pair = pairs.get(pairIndex);

        // Check if page number is within range
        if (pageNumber < pair.getBaseStartPage() || pageNumber > pair.getBaseEndPage()) {
            log.warn("Page number {} is out of range for pair index {} in comparison: {}",
                    pageNumber, pairIndex, comparisonId);
            return null;
        }

        // Find the corresponding page pair
        Optional<PagePair> pagePairOpt = result.getPagePairs().stream()
                .filter(p -> p.getBasePageNumber() == pageNumber || p.getComparePageNumber() == pageNumber)
                .findFirst();

        if (pagePairOpt.isEmpty()) {
            log.warn("No page pair found for page number {} in comparison: {}", pageNumber, comparisonId);
            return null;
        }

        PagePair pagePair = pagePairOpt.get();

        // Create page details
        PageDetails pageDetails = PageDetails.builder()
                .pageNumber(pageNumber)
                .pageId(pagePair.getId())
                .pageExistsInBase(pagePair.getBasePageNumber() > 0)
                .pageExistsInCompare(pagePair.getComparePageNumber() > 0)
                .build();

        // Add difference counts
        if (pagePair.isMatched() && pagePair.hasDifferences()) {
            pageDetails.setTextDifferenceCount(pagePair.getDifferenceCountByType("text"));
            pageDetails.setImageDifferenceCount(pagePair.getDifferenceCountByType("image"));
            pageDetails.setFontDifferenceCount(pagePair.getDifferenceCountByType("font"));
            pageDetails.setStyleDifferenceCount(pagePair.getDifferenceCountByType("style"));

            // Get differences for this page
            List<Difference> differences = result.getDifferencesByPage().get(pagePair.getId());
            if (differences != null && !differences.isEmpty()) {
                // Process differences
                List<Difference> baseDiffs = differences.stream()
                        .filter(d -> d.getBasePageNumber() == pageNumber)
                        .collect(Collectors.toList());

                List<Difference> compareDiffs = differences.stream()
                        .filter(d -> d.getComparePageNumber() == pageNumber)
                        .collect(Collectors.toList());

                pageDetails.setBaseDifferences(baseDiffs);
                pageDetails.setCompareDifferences(compareDiffs);
            }
        }

        // Set extracted text if available
        try {
            PdfDocument baseDocument = pdfRepository.findById(result.getBaseDocumentId()).orElse(null);
            PdfDocument compareDocument = pdfRepository.findById(result.getCompareDocumentId()).orElse(null);

            if (baseDocument != null && pageDetails.isPageExistsInBase()) {
                String baseExtractedTextPath = baseDocument.getExtractedTextPath(pagePair.getBasePageNumber());
                File baseTextFile = new File(baseExtractedTextPath);
                if (baseTextFile.exists()) {
                    pageDetails.setBaseExtractedText(new String(Files.readAllBytes(baseTextFile.toPath())));
                }
            }

            if (compareDocument != null && pageDetails.isPageExistsInCompare()) {
                String compareExtractedTextPath = compareDocument.getExtractedTextPath(pagePair.getComparePageNumber());
                File compareTextFile = new File(compareExtractedTextPath);
                if (compareTextFile.exists()) {
                    pageDetails.setCompareExtractedText(new String(Files.readAllBytes(compareTextFile.toPath())));
                }
            }

            // Set rendered image paths
            if (baseDocument != null && pageDetails.isPageExistsInBase()) {
                pageDetails.setBaseRenderedImagePath(baseDocument.getRenderedPagePath(pagePair.getBasePageNumber()));
                pageDetails.setBaseWidth(getImageWidth(pageDetails.getBaseRenderedImagePath()));
                pageDetails.setBaseHeight(getImageHeight(pageDetails.getBaseRenderedImagePath()));
            }

            if (compareDocument != null && pageDetails.isPageExistsInCompare()) {
                pageDetails.setCompareRenderedImagePath(compareDocument.getRenderedPagePath(pagePair.getComparePageNumber()));
                pageDetails.setCompareWidth(getImageWidth(pageDetails.getCompareRenderedImagePath()));
                pageDetails.setCompareHeight(getImageHeight(pageDetails.getCompareRenderedImagePath()));
            }
        } catch (Exception e) {
            log.error("Error setting extracted text or rendered images for page {} in comparison {}: {}",
                    pageNumber, comparisonId, e.getMessage(), e);
        }

        // Apply filters if needed
        applyFilters(pageDetails, filters);

        return pageDetails;
    }
    /**
     * Get image width from file path.
     *
     * @param imagePath The image file path
     * @return The image width, or 0 if unable to determine
     */
    private double getImageWidth(String imagePath) {
        if (imagePath == null) {
            return 0;
        }

        try {
            File imageFile = new File(imagePath);
            if (imageFile.exists()) {
                BufferedImage image = ImageIO.read(imageFile);
                return image != null ? image.getWidth() : 0;
            }
        } catch (Exception e) {
            log.warn("Unable to get image width for {}: {}", imagePath, e.getMessage());
        }

        return 0;
    }

    /**
     * Get image height from file path.
     *
     * @param imagePath The image file path
     * @return The image height, or 0 if unable to determine
     */
    private double getImageHeight(String imagePath) {
        if (imagePath == null) {
            return 0;
        }

        try {
            File imageFile = new File(imagePath);
            if (imageFile.exists()) {
                BufferedImage image = ImageIO.read(imageFile);
                return image != null ? image.getHeight() : 0;
            }
        } catch (Exception e) {
            log.warn("Unable to get image height for {}: {}", imagePath, e.getMessage());
        }

        return 0;
    }
}