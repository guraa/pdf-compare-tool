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
 * This service provides methods for creating, retrieving, and managing
 * comparisons between PDF documents.
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
     * Constructor with qualifier to specify which executor service to use.
     * 
     * @param pdfRepository The PDF repository
     * @param comparisonRepository The comparison repository
     * @param comparisonEngine The PDF comparison engine
     * @param executorService The executor service for comparison operations
     * @param resultStorage The comparison result storage
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
                try {
                    // Update the status to "processing"
                    Comparison updatedComparison = comparisonRepository.findById(comparisonId)
                            .orElseThrow(() -> new IllegalArgumentException("Comparison not found: " + comparisonId));
                    updatedComparison.setStatus("processing");
                    comparisonRepository.save(updatedComparison);

                    // Perform the comparison
                    ComparisonResult result = comparisonEngine.compareDocuments(baseDocument, compareDocument);

                    // Update the status to "completed"
                    updatedComparison = comparisonRepository.findById(comparisonId)
                            .orElseThrow(() -> new IllegalArgumentException("Comparison not found: " + comparisonId));
                    updatedComparison.setStatus("completed");
                    
                    // Store the result in the file system
                    try {
                        resultStorage.storeResult(comparisonId, result);
                        log.debug("Stored comparison result for ID: {}", comparisonId);
                    } catch (IOException e) {
                        log.error("Failed to store comparison result for ID {}: {}", comparisonId, e.getMessage(), e);
                        // Continue even if storage fails, as we can still return the result in memory
                    }
                    
                    // Set the result in memory for immediate use
                    updatedComparison.setResult(result);
                    comparisonRepository.save(updatedComparison);

                    return result;
                } catch (Exception e) {
                    // Update the status to "failed"
                    try {
                        Comparison updatedComparison = comparisonRepository.findById(comparisonId)
                                .orElseThrow(() -> new IllegalArgumentException("Comparison not found: " + comparisonId));
                        updatedComparison.setStatus("failed");
                        updatedComparison.setErrorMessage(e.getMessage());
                        comparisonRepository.save(updatedComparison);
                    } catch (Exception ex) {
                        log.error("Error updating comparison status: {}", ex.getMessage(), ex);
                    }

                    log.error("Error comparing documents: {}", e.getMessage(), e);
                    throw new RuntimeException("Error comparing documents", e);
                }
            }, executorService)
        );

        return comparison;
    }

    /**
     * Get a comparison by ID.
     *
     * @param id The comparison ID
     * @return The comparison
     */
    public Optional<Comparison> getComparison(String id) {
        return comparisonRepository.findById(id);
    }

    /**
     * Get all comparisons.
     *
     * @return A list of all comparisons
     */
    public List<Comparison> getAllComparisons() {
        return comparisonRepository.findAll();
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
     * @return The comparison result, or null if not found
     */
    public ComparisonResult getComparisonResult(String id) {
        // First try to get the result from the comparison object in memory
        ComparisonResult resultFromMemory = comparisonRepository.findById(id)
                .map(Comparison::getResult)
                .orElse(null);
        
        // If the result is not in memory, try to get it from the file system
        if (resultFromMemory == null) {
            log.debug("Comparison result not found in memory for ID: {}, trying file storage", id);
            final ComparisonResult resultFromStorage = resultStorage.retrieveResult(id);
            
            // If we found the result in the file system, update the comparison object in memory
            if (resultFromStorage != null) {
                log.debug("Retrieved comparison result from file storage for ID: {}", id);
                comparisonRepository.findById(id).ifPresent(comparison -> {
                    comparison.setResult(resultFromStorage);
                    // No need to save as we're just updating the transient field
                });
                
                return resultFromStorage;
            }
            
            return null;
        }
        
        return resultFromMemory;
    }

    /**
     * Delete a comparison.
     *
     * @param id The comparison ID
     */
    public void deleteComparison(String id) {
        // Delete the comparison result from the file system
        try {
            if (resultStorage.resultExists(id)) {
                boolean deleted = resultStorage.deleteResult(id);
                if (deleted) {
                    log.debug("Deleted comparison result file for ID: {}", id);
                } else {
                    log.warn("Failed to delete comparison result file for ID: {}", id);
                }
            }
        } catch (Exception e) {
            log.error("Error deleting comparison result file for ID {}: {}", id, e.getMessage(), e);
            // Continue with deletion even if file deletion fails
        }
        
        // Delete the comparison from the database
        comparisonRepository.deleteById(id);
    }

    /**
     * Cancel a comparison.
     *
     * @param id The comparison ID
     * @return true if the comparison was cancelled, false otherwise
     */
    public boolean cancelComparison(String id) {
        // Get the comparison task
        CompletableFuture<ComparisonResult> task = comparisonTasks.get(id);

        if (task != null && !task.isDone()) {
            // Cancel the task
            boolean cancelled = task.cancel(true);

            if (cancelled) {
                // Delete any existing result file
                try {
                    if (resultStorage.resultExists(id)) {
                        boolean deleted = resultStorage.deleteResult(id);
                        if (deleted) {
                            log.debug("Deleted comparison result file for cancelled comparison ID: {}", id);
                        } else {
                            log.warn("Failed to delete comparison result file for cancelled comparison ID: {}", id);
                        }
                    }
                } catch (Exception e) {
                    log.error("Error deleting comparison result file for cancelled comparison ID {}: {}", id, e.getMessage(), e);
                    // Continue with cancellation even if file deletion fails
                }
                
                // Update the status to "cancelled"
                comparisonRepository.findById(id).ifPresent(comparison -> {
                    comparison.setStatus("cancelled");
                    comparisonRepository.save(comparison);
                });
            }

            return cancelled;
        }

        return false;
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

        // Apply filters if any
        if (filters != null && !filters.isEmpty()) {
            // Filter the differences based on the provided filters
            // This is just a placeholder for the actual filtering logic
        }

        return pageDetails;
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

    /**
     * Get page details for a specific page in a document pair.
     *
     * @param comparisonId The comparison ID
     * @param pairIndex The pair index
     * @param pageNumber The page number
     * @param filters Filters to apply to the results
     * @return The page details, or null if not found
     */
    public PageDetails getDocumentPairPageDetails(String comparisonId, int pairIndex, int pageNumber, Map<String, Object> filters) {
        ComparisonResult result = getComparisonResult(comparisonId);
        if (result == null) {
            return null;
        }

        List<DocumentPair> pairs = getDocumentPairs(comparisonId);
        if (pairs == null || pairIndex >= pairs.size()) {
            return null;
        }

        DocumentPair pair = pairs.get(pairIndex);

        // Check if the page is within the range for this pair
        if (pageNumber < 1 || pageNumber > Math.max(pair.getBasePageCount(), pair.getComparePageCount())) {
            return null;
        }

        // In a real implementation, this would extract the page details from the result for the specific pair
        // For now, we'll just create a dummy page details object
        PageDetails pageDetails = PageDetails.builder()
                .pageNumber(pageNumber)
                .pageId(UUID.randomUUID().toString())
                .pageExistsInBase(pageNumber <= pair.getBasePageCount())
                .pageExistsInCompare(pageNumber <= pair.getComparePageCount())
                .build();

        // Apply filters if any
        if (filters != null && !filters.isEmpty()) {
            // Filter the differences based on the provided filters
            // This is just a placeholder for the actual filtering logic
        }

        return pageDetails;
    }
}
