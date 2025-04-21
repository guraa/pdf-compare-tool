package guraa.pdfcompare.core;

import guraa.pdfcompare.model.PdfDocument;
import guraa.pdfcompare.service.PagePair;
import guraa.pdfcompare.visual.EnhancedVisualMatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Smart document matcher that combines multiple matching strategies.
 * This class uses a combination of content-based and visual matching
 * to match pages between two PDF documents.
 */
@Slf4j
@Component
public class SmartDocumentMatcher implements DocumentMatchingStrategy {

    private final EnhancedVisualMatcher visualMatcher;
    private final ExecutorService executorService;
    
    /**
     * Constructor with qualifier to specify which executor service to use.
     * 
     * @param visualMatcher The visual matcher for comparing documents
     * @param executorService The executor service for comparison operations
     */
    public SmartDocumentMatcher(
            EnhancedVisualMatcher visualMatcher,
            @Qualifier("comparisonExecutor") ExecutorService executorService) {
        this.visualMatcher = visualMatcher;
        this.executorService = executorService;
    }

    @Value("${app.matching.visual-weight:0.7}")
    private double visualWeight;

    @Value("${app.matching.content-weight:0.3}")
    private double contentWeight;

    @Value("${app.matching.similarity-threshold:0.7}")
    private double similarityThreshold;

    private double confidenceLevel;

    @Override
    public List<PagePair> matchDocuments(PdfDocument baseDocument, PdfDocument compareDocument, Map<String, Object> options) throws IOException {
        log.info("Starting smart document matching between documents: {} and {}", 
                baseDocument.getFileId(), compareDocument.getFileId());

        // Check if parallel processing is enabled
        boolean parallelProcessing = options != null && Boolean.TRUE.equals(options.get("parallelProcessing"));
        
        // Get the comparison ID if provided
        String comparisonId = options != null ? (String) options.get("comparisonId") : null;

        // Match pages using visual matching
        List<PagePair> visualMatches = matchPagesVisually(baseDocument, compareDocument, parallelProcessing, comparisonId);

        // Calculate confidence level
        calculateConfidenceLevel(visualMatches);

        log.info("Completed smart document matching between documents: {} and {}", 
                baseDocument.getFileId(), compareDocument.getFileId());

        return visualMatches;
    }

    @Override
    public String getStrategyName() {
        return "SmartDocumentMatcher";
    }

    @Override
    public double getConfidenceLevel() {
        return confidenceLevel;
    }

    /**
     * Match pages using visual matching.
     *
     * @param baseDocument The base document
     * @param compareDocument The document to compare against the base
     * @param parallelProcessing Whether to use parallel processing
     * @param comparisonId The comparison ID for progress tracking (optional)
     * @return A list of page pairs
     * @throws IOException If there is an error matching the pages
     */
    private List<PagePair> matchPagesVisually(PdfDocument baseDocument, PdfDocument compareDocument, 
                                             boolean parallelProcessing, String comparisonId) throws IOException {
        // Use the visual matcher to match pages with progress tracking
        return visualMatcher.matchPages(baseDocument, compareDocument, comparisonId);
    }

    /**
     * Calculate the confidence level of the matching.
     *
     * @param pagePairs The page pairs
     */
    private void calculateConfidenceLevel(List<PagePair> pagePairs) {
        // Count the number of matched pages
        int matchedPages = (int) pagePairs.stream()
                .filter(PagePair::isMatched)
                .count();
        
        // Calculate the average similarity score for matched pages
        double averageSimilarity = pagePairs.stream()
                .filter(PagePair::isMatched)
                .mapToDouble(PagePair::getSimilarityScore)
                .average()
                .orElse(0.0);
        
        // Calculate the confidence level
        // This takes into account both the number of matched pages and their similarity
        int totalPages = pagePairs.size();
        double matchRatio = (double) matchedPages / totalPages;
        
        confidenceLevel = matchRatio * averageSimilarity;
    }
}
