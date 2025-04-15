package guraa.pdfcompare;

import guraa.pdfcompare.model.difference.FontDifference;
import guraa.pdfcompare.model.difference.ImageDifference;
import guraa.pdfcompare.core.DocumentMatchingStrategy;
import guraa.pdfcompare.core.SmartDocumentMatcher;
import guraa.pdfcompare.model.ComparisonResult;
import guraa.pdfcompare.model.PdfDocument;
import guraa.pdfcompare.model.difference.Difference;
import guraa.pdfcompare.model.difference.TextDifference;
import guraa.pdfcompare.service.FontComparisonService;
import guraa.pdfcompare.service.ImageComparisonService;
import guraa.pdfcompare.service.PageLevelComparisonSummary;
import guraa.pdfcompare.service.PagePair;
import guraa.pdfcompare.service.TextElementComparisonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/**
 * Main engine for PDF comparison.
 * This class coordinates the comparison process, delegating to specialized
 * services for different types of comparisons.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PDFComparisonEngine {

    private final SmartDocumentMatcher documentMatcher;
    private final TextElementComparisonService textComparisonService;
    private final ImageComparisonService imageComparisonService;
    private final FontComparisonService fontComparisonService;
    private final ExecutorService executorService;

    @Value("${app.comparison.smart-matching-enabled:true}")
    private boolean smartMatchingEnabled;

    /**
     * Compare two PDF documents.
     *
     * @param baseDocument The base document
     * @param compareDocument The document to compare against the base
     * @return The comparison result
     * @throws IOException If there is an error comparing the documents
     */
    public ComparisonResult compareDocuments(PdfDocument baseDocument, PdfDocument compareDocument) throws IOException {
        log.info("Starting comparison between documents: {} and {}", 
                baseDocument.getFileId(), compareDocument.getFileId());

        // Match pages between documents
        List<PagePair> pagePairs = matchDocuments(baseDocument, compareDocument);

        // Create a comparison summary
        PageLevelComparisonSummary summary = createComparisonSummary(baseDocument, compareDocument, pagePairs);

        // Compare matched pages
        Map<String, List<Difference>> differencesByPage = comparePages(baseDocument, compareDocument, pagePairs);

        // Create the comparison result
        ComparisonResult result = ComparisonResult.builder()
                .id(UUID.randomUUID().toString())
                .baseDocumentId(baseDocument.getFileId())
                .compareDocumentId(compareDocument.getFileId())
                .pagePairs(pagePairs)
                .summary(summary)
                .differencesByPage(differencesByPage)
                .build();

        log.info("Completed comparison between documents: {} and {}", 
                baseDocument.getFileId(), compareDocument.getFileId());

        return result;
    }

    /**
     * Match pages between two documents.
     *
     * @param baseDocument The base document
     * @param compareDocument The document to compare against the base
     * @return A list of page pairs
     * @throws IOException If there is an error matching the documents
     */
    private List<PagePair> matchDocuments(PdfDocument baseDocument, PdfDocument compareDocument) throws IOException {
        // Use the document matcher to match pages
        DocumentMatchingStrategy matcher = documentMatcher;
        Map<String, Object> options = new HashMap<>();
        options.put("parallelProcessing", true);

        return matcher.matchDocuments(baseDocument, compareDocument, options);
    }

    /**
     * Create a comparison summary.
     *
     * @param baseDocument The base document
     * @param compareDocument The document to compare against the base
     * @param pagePairs The page pairs
     * @return The comparison summary
     */
    private PageLevelComparisonSummary createComparisonSummary(
            PdfDocument baseDocument, PdfDocument compareDocument, List<PagePair> pagePairs) {
        
        PageLevelComparisonSummary summary = PageLevelComparisonSummary.builder()
                .id(UUID.randomUUID().toString())
                .baseDocumentId(baseDocument.getFileId())
                .compareDocumentId(compareDocument.getFileId())
                .baseTotalPages(baseDocument.getPageCount())
                .compareTotalPages(compareDocument.getPageCount())
                .matchingStrategy(documentMatcher.getStrategyName())
                .confidenceLevel(documentMatcher.getConfidenceLevel())
                .build();

        // Add page pairs to the summary
        for (PagePair pagePair : pagePairs) {
            summary.addPagePair(pagePair);
        }

        // Calculate the overall similarity score
        summary.calculateOverallSimilarityScore();

        return summary;
    }

    /**
     * Compare pages between two documents.
     *
     * @param baseDocument The base document
     * @param compareDocument The document to compare against the base
     * @param pagePairs The page pairs
     * @return A map of page pair IDs to differences
     * @throws IOException If there is an error comparing the pages
     */
    private Map<String, List<Difference>> comparePages(
            PdfDocument baseDocument, PdfDocument compareDocument, List<PagePair> pagePairs) throws IOException {
        
        Map<String, List<Difference>> differencesByPage = new HashMap<>();

        // Process each matched page pair
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (PagePair pagePair : pagePairs) {
            if (pagePair.isMatched()) {
                // Submit a task to compare this page pair
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        // Compare text
                        List<TextDifference> textDifferences = textComparisonService.compareText(
                                baseDocument, compareDocument,
                                pagePair.getBasePageNumber(), pagePair.getComparePageNumber());

                        // Compare images
                        List<ImageDifference> imageDifferences = imageComparisonService.compareImages(
                                baseDocument, compareDocument,
                                pagePair.getBasePageNumber(), pagePair.getComparePageNumber());

                        // Compare fonts
                        List<FontDifference> fontDifferences = fontComparisonService.compareFonts(
                                baseDocument, compareDocument,
                                pagePair.getBasePageNumber(), pagePair.getComparePageNumber());

                        // Combine all differences
                        List<Difference> allDifferences = new ArrayList<>();
                        allDifferences.addAll(textDifferences);
                        allDifferences.addAll(imageDifferences);
                        allDifferences.addAll(fontDifferences);

                        // Store the differences
                        differencesByPage.put(pagePair.getId(), allDifferences);

                        // Update the page pair with the differences
                        for (Difference difference : allDifferences) {
                            pagePair.addDifference(createPageDifference(difference));
                        }
                    } catch (IOException e) {
                        log.error("Error comparing pages: {}", e.getMessage(), e);
                    }
                }, executorService);

                futures.add(future);
            }
        }

        // Wait for all comparisons to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        return differencesByPage;
    }

    /**
     * Create a page difference from a difference.
     *
     * @param difference The difference
     * @return The page difference
     */
    private guraa.pdfcompare.service.PageDifference createPageDifference(Difference difference) {
        return guraa.pdfcompare.service.PageDifference.builder()
                .id(UUID.randomUUID().toString())
                .type(difference.getType())
                .severity(difference.getSeverity())
                .description(difference.getDescription())
                .basePageNumber(difference.getBasePageNumber())
                .comparePageNumber(difference.getComparePageNumber())
                .build();
    }
}
