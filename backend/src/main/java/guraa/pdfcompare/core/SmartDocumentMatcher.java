package guraa.pdfcompare.core;

import guraa.pdfcompare.model.PdfDocument;
import guraa.pdfcompare.service.PagePair;
import guraa.pdfcompare.visual.EnhancedVisualMatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Smart document matcher that combines multiple matching strategies with intelligent page limitation.
 * This class uses content-based and visual matching to efficiently match pages between PDF documents.
 */
@Slf4j
@Component
public class SmartDocumentMatcher implements DocumentMatchingStrategy {

    private final EnhancedVisualMatcher visualMatcher;
    private final ExecutorService executorService;

    @Value("${app.matching.visual-weight:0.7}")
    private double visualWeight;

    @Value("${app.matching.content-weight:0.3}")
    private double contentWeight;

    @Value("${app.matching.similarity-threshold:0.7}")
    private double similarityThreshold;

    @Value("${app.matching.max-page-gap:5}")
    private int maxPageGap = 5;

    @Value("${app.matching.match-timeout-seconds:180}")
    private int matchTimeoutSeconds = 180;

    private double confidenceLevel;

    /**
     * Constructor with qualifier to specify which executor service to use.
     */
    public SmartDocumentMatcher(
            EnhancedVisualMatcher visualMatcher,
            @Qualifier("comparisonExecutor") ExecutorService executorService) {
        this.visualMatcher = visualMatcher;
        this.executorService = executorService;
    }

    @Override
    public List<PagePair> matchDocuments(PdfDocument baseDocument, PdfDocument compareDocument, Map<String, Object> options) throws IOException {
        String logPrefix = "[" + baseDocument.getFileId() + " vs " + compareDocument.getFileId() + "] ";
        log.info(logPrefix + "Starting smart document matching between documents with {} and {} pages",
                baseDocument.getPageCount(), compareDocument.getPageCount());

        // Check if parallel processing is enabled
        boolean parallelProcessing = options != null && Boolean.TRUE.equals(options.get("parallelProcessing"));

        // Get the comparison ID if provided
        String comparisonId = options != null ? (String) options.get("comparisonId") : null;

        // Add additional options to limit page matching
        Map<String, Object> matchOptions = new HashMap<>();
        if (options != null) {
            matchOptions.putAll(options);
        }

        // Set maximum page gap - this drastically reduces unnecessary comparisons
        matchOptions.put("maxPageGap", maxPageGap);

        // Calculate approximate total potential matches
        int directMatches = Math.min(baseDocument.getPageCount(), compareDocument.getPageCount());
        int nearbyMatches = directMatches * maxPageGap * 2;
        int totalEstimatedMatches = Math.min(directMatches + nearbyMatches,
                baseDocument.getPageCount() * compareDocument.getPageCount());

        log.info(logPrefix + "Estimated page pairs to compare: {} (direct: {}, nearby: {})",
                totalEstimatedMatches, directMatches, nearbyMatches);

        // Update progress in the database if we have a comparison ID
        if (comparisonId != null) {
            updateComparisonProgress(comparisonId, 0, 100, "Matching document pages");
        }

        // Match pages using visual matching with timeout protection
        List<PagePair> visualMatches;
        try {
            // Set timeout for the entire matching operation
            CompletableFuture<List<PagePair>> matchFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return visualMatcher.matchPages(baseDocument, compareDocument, comparisonId);
                } catch (IOException e) {
                    throw new RuntimeException("Visual matching failed", e);
                }
            }, executorService);

            // Wait with timeout
            visualMatches = matchFuture.get(matchTimeoutSeconds, TimeUnit.SECONDS);

            // Calculate confidence level
            calculateConfidenceLevel(visualMatches);

            log.info(logPrefix + "Visual matching completed with {} page pairs and confidence {}",
                    visualMatches.size(), confidenceLevel);
        } catch (Exception e) {
            log.error(logPrefix + "Error during visual matching: {}", e.getMessage(), e);

            // Create a fallback matching when visual matching fails
            visualMatches = createFallbackMatching(baseDocument, compareDocument);

            log.info(logPrefix + "Using fallback matching with {} page pairs", visualMatches.size());
        }

        // Update progress in the database if we have a comparison ID
        if (comparisonId != null) {
            updateComparisonProgress(comparisonId, 100, 100, "Page matching completed");
        }

        log.info(logPrefix + "Completed smart document matching with {} page pairs", visualMatches.size());

        return visualMatches;
    }

    /**
     * Create a fallback matching when visual matching fails.
     * This creates a simple 1:1 matching based on page numbers.
     */
    private List<PagePair> createFallbackMatching(PdfDocument baseDocument, PdfDocument compareDocument) {
        List<PagePair> pagePairs = new ArrayList<>();

        // Match pages with the same page number
        int minPages = Math.min(baseDocument.getPageCount(), compareDocument.getPageCount());
        for (int i = 1; i <= minPages; i++) {
            pagePairs.add(PagePair.builder()
                    .baseDocumentId(baseDocument.getFileId())
                    .compareDocumentId(compareDocument.getFileId())
                    .basePageNumber(i)
                    .comparePageNumber(i)
                    .matched(true)
                    .similarityScore(0.8) // Arbitrary reasonable score
                    .build());
        }

        // Add unmatched pages from base document
        for (int i = minPages + 1; i <= baseDocument.getPageCount(); i++) {
            pagePairs.add(PagePair.builder()
                    .baseDocumentId(baseDocument.getFileId())
                    .compareDocumentId(compareDocument.getFileId())
                    .basePageNumber(i)
                    .matched(false)
                    .build());
        }

        // Add unmatched pages from compare document
        for (int i = minPages + 1; i <= compareDocument.getPageCount(); i++) {
            pagePairs.add(PagePair.builder()
                    .baseDocumentId(baseDocument.getFileId())
                    .compareDocumentId(compareDocument.getFileId())
                    .comparePageNumber(i)
                    .matched(false)
                    .build());
        }

        confidenceLevel = 0.7; // Moderate confidence in fallback matching
        return pagePairs;
    }

    /**
     * Update progress in the comparison service using reflection to avoid circular dependencies.
     */
    private void updateComparisonProgress(String comparisonId, int completed, int total, String phase) {
        try {
            // Use reflection to get the ComparisonService
            Object comparisonService = getComparisonService();
            if (comparisonService != null) {
                java.lang.reflect.Method updateMethod = comparisonService.getClass()
                        .getMethod("updateComparisonProgress",
                                String.class, int.class, int.class, String.class);

                updateMethod.invoke(comparisonService,
                        comparisonId, completed, total, phase);
            }
        } catch (Exception e) {
            log.warn("Failed to update comparison progress: {}", e.getMessage());
        }
    }

    /**
     * Get the ComparisonService bean using reflection to avoid circular dependencies.
     */
    private Object getComparisonService() {
        try {
            // Get application context through Spring utilities
            return org.springframework.web.context.ContextLoader.getCurrentWebApplicationContext()
                    .getBean("comparisonService");
        } catch (Exception e) {
            log.warn("Failed to get ComparisonService bean: {}", e.getMessage());
            return null;
        }
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
     * Calculate the confidence level of the matching.
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
        int totalPages = Math.max(
                pagePairs.stream().mapToInt(PagePair::getBasePageNumber).max().orElse(0),
                pagePairs.stream().mapToInt(PagePair::getComparePageNumber).max().orElse(0)
        );

        if (totalPages == 0) {
            confidenceLevel = 0.0;
            return;
        }

        double matchRatio = (double) matchedPages / totalPages;
        confidenceLevel = matchRatio * averageSimilarity;
    }
}