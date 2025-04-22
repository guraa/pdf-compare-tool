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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Smart document matcher with improved reliability.
 * Optimized for stability by using simpler matching when needed.
 */
@Slf4j
@Component
public class SmartDocumentMatcher implements DocumentMatchingStrategy {

    private final EnhancedVisualMatcher visualMatcher;
    private final ExecutorService executorService;

    @Value("${app.matching.visual-similarity-threshold:0.7}")
    private double visualSimilarityThreshold;

    @Value("${app.matching.max-page-gap:2}")
    private int maxPageGap = 2;

    @Value("${app.matching.match-timeout-seconds:300}")
    private int matchTimeoutSeconds = 300;

    private double confidenceLevel = 0.8;

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

        // Get the comparison ID if provided
        String comparisonId = options != null ? (String) options.get("comparisonId") : null;

        // Check if both documents have more than 100 total pages - use simple matching for large documents
        int totalPages = baseDocument.getPageCount() + compareDocument.getPageCount();
        if (totalPages > 100) {
            log.info(logPrefix + "Using simple matching strategy for large documents with {} total pages", totalPages);
            return createSimpleMatching(baseDocument, compareDocument);
        }

        try {
            // Try to do visual matching with a timeout
            try {
                CompletableFuture<List<PagePair>> matchFuture = CompletableFuture.supplyAsync(() -> {
                    try {
                        return visualMatcher.matchPages(baseDocument, compareDocument, comparisonId);
                    } catch (IOException e) {
                        throw new CompletionException(e);
                    }
                }, executorService);

                // Wait with timeout
                List<PagePair> pagePairs = matchFuture.get(matchTimeoutSeconds, TimeUnit.SECONDS);

                // Update confidence based on number of matched pages
                int matchedCount = (int) pagePairs.stream().filter(PagePair::isMatched).count();
                int maxPages = Math.max(baseDocument.getPageCount(), compareDocument.getPageCount());
                double matchRatio = (double) matchedCount / maxPages;
                confidenceLevel = 0.5 + (0.5 * matchRatio); // Range from 0.5 to 1.0

                log.info(logPrefix + "Visual matching completed with {} matched pages, confidence {}",
                        matchedCount, confidenceLevel);

                return pagePairs;
            } catch (Exception e) {
                log.warn(logPrefix + "Visual matching failed or timed out: {}. Using simple matching strategy.",
                        e.getMessage());
                return createSimpleMatching(baseDocument, compareDocument);
            }
        } catch (Exception e) {
            log.error(logPrefix + "Error during document matching: {}", e.getMessage(), e);
            throw new IOException("Document matching failed", e);
        }
    }

    /**
     * Create a simple 1:1 matching based on page numbers.
     * This is a fallback for when visual matching fails or is too costly.
     */
    private List<PagePair> createSimpleMatching(PdfDocument baseDocument, PdfDocument compareDocument) {
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
                    .similarityScore(0.8) // Reasonable assumed score
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

        // Set a moderate confidence level for simple matching
        confidenceLevel = 0.7;
        return pagePairs;
    }

    @Override
    public String getStrategyName() {
        return "SmartDocumentMatcher";
    }

    @Override
    public double getConfidenceLevel() {
        return confidenceLevel;
    }
}