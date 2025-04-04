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
import java.util.ArrayList;
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

            double similarity = calculatePageSimilarity(
                    baseIndex, compareIndex, baseTexts, compareTexts,
                    basePdf, comparePdf, baseRenderer, compareRenderer);

            DocumentPair.PageMapping mapping = DocumentPair.PageMapping.builder()
                    .basePageNumber(baseIndex + 1) // Convert to 1-based
                    .comparePageNumber(compareIndex + 1) // Convert to 1-based
                    .similarityScore(similarity)
                    .differenceCount(0) // Will be updated later during comparison
                    .build();

            pair.getPageMappings().add(mapping);
        }
    }

    /**
     * Create optimal page mappings for documents with different page counts.
     * Uses a similarity matrix and the Hungarian algorithm to find optimal matches.
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

        // Apply the Hungarian algorithm to find optimal matches
        // We'll use a greedy approach for simplicity; in a full implementation
        // you would use a proper Hungarian algorithm implementation

        // Create a copy of the similarity matrix to work with
        double[][] workMatrix = new double[basePageCount][comparePageCount];
        for (int i = 0; i < basePageCount; i++) {
            System.arraycopy(similarityMatrix[i], 0, workMatrix[i], 0, comparePageCount);
        }

        // Initialize mappings list
        List<DocumentPair.PageMapping> mappings = new ArrayList<>();

        // Find the best matches, until all base pages are mapped or no more good matches exist
        boolean[] mappedBasePages = new boolean[basePageCount];
        boolean[] mappedComparePages = new boolean[comparePageCount];

        int mappedPages = 0;
        while (mappedPages < Math.min(basePageCount, comparePageCount)) {
            // Find maximum similarity value in the matrix
            double maxSim = -1;
            int maxBaseIdx = -1;
            int maxCompareIdx = -1;

            for (int i = 0; i < basePageCount; i++) {
                if (mappedBasePages[i]) continue;

                for (int j = 0; j < comparePageCount; j++) {
                    if (mappedComparePages[j]) continue;

                    if (workMatrix[i][j] > maxSim) {
                        maxSim = workMatrix[i][j];
                        maxBaseIdx = i;
                        maxCompareIdx = j;
                    }
                }
            }

            // No more good matches or all matches below threshold
            if (maxSim < textSimilarityThreshold || maxBaseIdx == -1) {
                break;
            }

            // Create a page mapping for the match
            DocumentPair.PageMapping mapping = DocumentPair.PageMapping.builder()
                    .basePageNumber(baseBoundary.getStartPage() + maxBaseIdx + 1) // Convert to 1-based
                    .comparePageNumber(compareBoundary.getStartPage() + maxCompareIdx + 1) // Convert to 1-based
                    .similarityScore(maxSim)
                    .differenceCount(0) // Will be updated later during comparison
                    .build();

            mappings.add(mapping);

            // Mark these pages as mapped
            mappedBasePages[maxBaseIdx] = true;
            mappedComparePages[maxCompareIdx] = true;
            mappedPages++;

            // Set the similarity to -1 to exclude this cell
            workMatrix[maxBaseIdx][maxCompareIdx] = -1;
        }

        // Add unmapped base pages
        for (int i = 0; i < basePageCount; i++) {
            if (!mappedBasePages[i]) {
                DocumentPair.PageMapping mapping = DocumentPair.PageMapping.builder()
                        .basePageNumber(baseBoundary.getStartPage() + i + 1) // Convert to 1-based
                        .comparePageNumber(-1) // No match in compare document
                        .similarityScore(0.0)
                        .differenceCount(0)
                        .build();

                mappings.add(mapping);
            }
        }

        // Add unmapped compare pages
        for (int j = 0; j < comparePageCount; j++) {
            if (!mappedComparePages[j]) {
                DocumentPair.PageMapping mapping = DocumentPair.PageMapping.builder()
                        .basePageNumber(-1) // No match in base document
                        .comparePageNumber(compareBoundary.getStartPage() + j + 1) // Convert to 1-based
                        .similarityScore(0.0)
                        .differenceCount(0)
                        .build();

                mappings.add(mapping);
            }
        }

        // Set the mappings on the document pair
        pair.setPageMappings(mappings);
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
}