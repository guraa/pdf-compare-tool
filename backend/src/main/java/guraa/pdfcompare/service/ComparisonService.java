package guraa.pdfcompare.service;

import guraa.pdfcompare.PDFComparisonEngine;
import guraa.pdfcompare.model.Comparison;
import guraa.pdfcompare.model.ComparisonResult;
import guraa.pdfcompare.model.PdfDocument;
import guraa.pdfcompare.repository.ComparisonRepository;
import guraa.pdfcompare.repository.PdfRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
@RequiredArgsConstructor
public class ComparisonService {

    private final PdfRepository pdfRepository;
    private final ComparisonRepository comparisonRepository;
    private final PDFComparisonEngine comparisonEngine;
    private final ExecutorService executorService;

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
        comparisonTasks.computeIfAbsent(comparisonId, key -> {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    // Update the status to "processing"
                    Comparison updatedComparison = comparisonRepository.findById(comparisonId)
                            .orElseThrow(() -> new IllegalArgumentException("Comparison not found: " + comparisonId));
                    updatedComparison.status = Comparison.ComparisonStatus.PROCESSING;
                    comparisonRepository.save(updatedComparison);
                    
                    // Perform the comparison
                    ComparisonResult result = comparisonEngine.compareDocuments(baseDocument, compareDocument);
                    
                    // Update the status to "completed"
                    updatedComparison = comparisonRepository.findById(comparisonId)
                            .orElseThrow(() -> new IllegalArgumentException("Comparison not found: " + comparisonId));
                    updatedComparison.status = Comparison.ComparisonStatus.COMPLETED;
                    updatedComparison.setResult(result);
                    comparisonRepository.save(updatedComparison);
                    
                    return result;
                } catch (Exception e) {
                    // Update the status to "failed"
                    try {
                        Comparison updatedComparison = comparisonRepository.findById(comparisonId)
                                .orElseThrow(() -> new IllegalArgumentException("Comparison not found: " + comparisonId));
                        updatedComparison.status = Comparison.ComparisonStatus.FAILED;
                        updatedComparison.setErrorMessage(e.getMessage());
                        comparisonRepository.save(updatedComparison);
                    } catch (Exception ex) {
                        log.error("Error updating comparison status: {}", ex.getMessage(), ex);
                    }
                    
                    log.error("Error comparing documents: {}", e.getMessage(), e);
                    throw new RuntimeException("Error comparing documents", e);
                }
            }, executorService);
        });
        
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
     * @return The comparison result
     */
    public Optional<ComparisonResult> getComparisonResult(String id) {
        return comparisonRepository.findById(id)
                .map(Comparison::getResult);
    }

    /**
     * Delete a comparison.
     *
     * @param id The comparison ID
     */
    public void deleteComparison(String id) {
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
                // Update the status to "cancelled"
                comparisonRepository.findById(id).ifPresent(comparison -> {
                    comparison.status = Comparison.ComparisonStatus.CANCELLED;
                    comparisonRepository.save(comparison);
                });
            }
            
            return cancelled;
        }
        
        return false;
    }
}
