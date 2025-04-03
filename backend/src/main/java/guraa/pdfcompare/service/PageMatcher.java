package guraa.pdfcompare.service;

import guraa.pdfcompare.model.DocumentPair;
import guraa.pdfcompare.util.SSIMCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

/**
 * Responsible for matching individual pages between documents
 * and creating page mappings for document pairs.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PageMatcher {

    private final SSIMCalculator ssimCalculator;

    @Value("${app.comparison.text-similarity-threshold:0.5}")
    private double textSimilarityThreshold;

    /**
     * Create page mappings between documents for more detailed comparison.
     *
     * @param pair            Document pair to update with mappings
     * @param baseBoundary    Document boundary in base PDF
     * @param compareBoundary Document boundary in comparison PDF
     * @param baseTexts       List of page texts in base PDF
     * @param compareTexts    List of page texts in comparison PDF
     * @param basePdf         Base PDF document
     * @param comparePdf      Comparison PDF document
     * @param baseRenderer    Renderer for base PDF
     * @param compareRenderer Renderer for comparison PDF
     * @throws IOException If there's an error processing the documents
     */
    public void createPageMappings(
            DocumentPair pair,
            DocumentBoundary baseBoundary,
            DocumentBoundary compareBoundary,
            List<String> baseTexts,
            List<String> compareTexts,
            PDDocument basePdf,
            PDDocument comparePdf,
            PDFRenderer baseRenderer,
            PDFRenderer compareRenderer) throws IOException {

        int basePageCount = baseBoundary.getEndPage() - baseBoundary.getStartPage() + 1;
        int comparePageCount = compareBoundary.getEndPage() - compareBoundary.getStartPage() + 1;

        // Simple case: equal number of pages
        if (basePageCount == comparePageCount) {
            createOneToOnePageMappings(
                    pair, baseBoundary, compareBoundary,
                    baseTexts, compareTexts, basePdf, comparePdf,
                    baseRenderer, compareRenderer);
        } else {
            // Complex case: different number of pages
            // Use dynamic programming to find optimal page mappings
            createOptimalPageMappings(
                    pair, baseBoundary, compareBoundary,
                    baseTexts, compareTexts, basePdf, comparePdf,
                    baseRenderer, compareRenderer);
        }
    }

    /**
     * Create one-to-one mappings when document page counts match.
     */
    private void createOneToOnePageMappings(
            DocumentPair pair,
            DocumentBoundary baseBoundary,
            DocumentBoundary compareBoundary,
            List<String> baseTexts,
            List<String> compareTexts,
            PDDocument basePdf,
            PDDocument comparePdf,
            PDFRenderer baseRenderer,
            PDFRenderer compareRenderer) throws IOException {

        int basePageCount = baseBoundary.getEndPage() - baseBoundary.getStartPage() + 1;

        // Create one-to-one mappings
        for (int i = 0; i < basePageCount; i++) {
            int baseIndex = baseBoundary.getStartPage() + i;
            int compareIndex = compareBoundary.getStartPage() + i;

            DocumentPair.PageMapping mapping = DocumentPair.PageMapping.builder()
                    .basePageNumber(baseIndex + 1) // Convert to 1-based
                    .comparePageNumber(compareIndex + 1) // Convert to 1-based
                    // Calculate page similarity
                    .similarityScore(calculatePageSimilarity(
                            baseIndex, compareIndex, baseTexts, compareTexts,
                            basePdf, comparePdf, baseRenderer, compareRenderer))
                    .build();

            pair.getPageMappings().add(mapping);
        }
    }

    /**
     * Calculate similarity between two pages using a combination of text and visual similarity.
     *
     * @param basePageIndex    Page index in base PDF
     * @param comparePageIndex Page index in comparison PDF
     * @param baseTexts        List of page texts in base PDF
     * @param compareTexts     List of page texts in comparison PDF
     * @param basePdf          Base PDF document
     * @param comparePdf       Comparison PDF document
     * @param baseRenderer     Renderer for base PDF
     * @param compareRenderer  Renderer for comparison PDF
     * @return Similarity score between 0.0 and 1.0
     * @throws IOException If there's an error processing the pages
     */
    public double calculatePageSimilarity(
            int basePageIndex,
            int comparePageIndex,
            List<String> baseTexts,
            List<String> compareTexts,
            PDDocument basePdf,
            PDDocument comparePdf,
            PDFRenderer baseRenderer,
            PDFRenderer compareRenderer) throws IOException {

        // Ensure page indices are valid
        if (basePageIndex >= baseTexts.size() || comparePageIndex >= compareTexts.size()) {
            return 0.0;
        }

        // Calculate text similarity
        double textSimilarity = TextSimilarityUtils.calculateTextSimilarity(
                baseTexts.get(basePageIndex), compareTexts.get(comparePageIndex));

        // If text similarity is very low, no need to check visual similarity
        if (textSimilarity < textSimilarityThreshold / 2) {
            return textSimilarity;
        }

        // Calculate visual similarity
        double visualSimilarity = 0.0;
        try {
            BufferedImage baseImage = baseRenderer.renderImageWithDPI(
                    basePageIndex, 72, ImageType.RGB);

            BufferedImage compareImage = compareRenderer.renderImageWithDPI(
                    comparePageIndex, 72, ImageType.RGB);

            visualSimilarity = ssimCalculator.getSimilarityScore(baseImage, compareImage);
        } catch (Exception e) {
            log.warn("Error calculating visual similarity for pages: {} and {}",
                    basePageIndex, comparePageIndex, e);
        }

        // Combined similarity score (60% text, 40% visual)
        return 0.6 * textSimilarity + 0.4 * visualSimilarity;
    }

    /**
     * Create optimal page mappings between documents with different page counts.
     *
     * @param pair            Document pair to update with mappings
     * @param baseBoundary    Document boundary in base PDF
     * @param compareBoundary Document boundary in comparison PDF
     * @param baseTexts       List of page texts in base PDF
     * @param compareTexts    List of page texts in comparison PDF
     * @param basePdf         Base PDF document
     * @param comparePdf      Comparison PDF document
     * @param baseRenderer    Renderer for base PDF
     * @param compareRenderer Renderer for comparison PDF
     * @throws IOException If there's an error processing the documents
     */
    private void createOptimalPageMappings(
            DocumentPair pair,
            DocumentBoundary baseBoundary,
            DocumentBoundary compareBoundary,
            List<String> baseTexts,
            List<String> compareTexts,
            PDDocument basePdf,
            PDDocument comparePdf,
            PDFRenderer baseRenderer,
            PDFRenderer compareRenderer) throws IOException {

        int basePageCount = baseBoundary.getEndPage() - baseBoundary.getStartPage() + 1;
        int comparePageCount = compareBoundary.getEndPage() - compareBoundary.getStartPage() + 1;

        // Calculate similarity matrix
        double[][] similarityMatrix = new double[basePageCount][comparePageCount];

        for (int i = 0; i < basePageCount; i++) {
            int baseIndex = baseBoundary.getStartPage() + i;

            for (int j = 0; j < comparePageCount; j++) {
                int compareIndex = compareBoundary.getStartPage() + j;

                similarityMatrix[i][j] = calculatePageSimilarity(
                        baseIndex, compareIndex, baseTexts, compareTexts,
                        basePdf, comparePdf, baseRenderer, compareRenderer);
            }
        }

        // Find optimal page mappings using greedy approach
        // For each base page, find the best matching compare page that hasn't been matched yet
        boolean[] matchedComparePages = new boolean[comparePageCount];

        for (int i = 0; i < basePageCount; i++) {
            double maxSimilarity = 0.0;
            int bestMatch = -1;

            // Find best unmatched compare page for this base page
            for (int j = 0; j < comparePageCount; j++) {
                if (!matchedComparePages[j] && similarityMatrix[i][j] > maxSimilarity) {
                    maxSimilarity = similarityMatrix[i][j];
                    bestMatch = j;
                }
            }

            // If we found a good match
            if (bestMatch >= 0 && maxSimilarity > textSimilarityThreshold) {
                // Create mapping
                DocumentPair.PageMapping mapping = DocumentPair.PageMapping.builder()
                        .basePageNumber(baseBoundary.getStartPage() + i + 1) // Convert to 1-based
                        .comparePageNumber(compareBoundary.getStartPage() + bestMatch + 1) // Convert to 1-based
                        .similarityScore(maxSimilarity)
                        .build();

                pair.getPageMappings().add(mapping);

                // Mark this compare page as matched
                matchedComparePages[bestMatch] = true;
            } else {
                // No good match found for this base page
                DocumentPair.PageMapping mapping = DocumentPair.PageMapping.builder()
                        .basePageNumber(baseBoundary.getStartPage() + i + 1) // Convert to 1-based
                        .comparePageNumber(-1) // No match
                        .similarityScore(0.0)
                        .build();

                pair.getPageMappings().add(mapping);
            }
        }

        // Find any unmatched compare pages
        for (int j = 0; j < comparePageCount; j++) {
            if (!matchedComparePages[j]) {
                // Create mapping for unmatched compare page
                DocumentPair.PageMapping mapping = DocumentPair.PageMapping.builder()
                        .basePageNumber(-1) // No match
                        .comparePageNumber(compareBoundary.getStartPage() + j + 1) // Convert to 1-based
                        .similarityScore(0.0)
                        .build();

                pair.getPageMappings().add(mapping);
            }
        }
    }
}