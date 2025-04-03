package guraa.pdfcompare.service;

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
import java.util.*;

/**
 * Responsible for matching documents between base and comparison PDFs.
 * Uses both text and visual features to find the best matches.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentMatcher {

    private final SSIMCalculator ssimCalculator;

    @Value("${app.comparison.text-similarity-threshold:0.5}")
    private double textSimilarityThreshold;

    @Value("${app.comparison.visual-similarity-threshold:0.6}")
    private double visualSimilarityThreshold;

    @Value("${app.comparison.combined-similarity-threshold:0.55}")
    private double combinedSimilarityThreshold;

    @Value("${app.comparison.max-sample-pages:3}")
    private int maxSamplePages;

    /**
     * Match document boundaries between base and comparison PDFs.
     *
     * @param baseDocuments    List of document boundaries in base PDF
     * @param compareDocuments List of document boundaries in comparison PDF
     * @param baseTexts        List of page texts in base PDF
     * @param compareTexts     List of page texts in comparison PDF
     * @param basePdf          Base PDF document
     * @param comparePdf       Comparison PDF document
     * @param baseRenderer     Renderer for base PDF
     * @param compareRenderer  Renderer for comparison PDF
     * @return List of document matches
     * @throws IOException If there's an error processing the documents
     */
    public List<DocumentMatch> matchDocuments(
            List<DocumentBoundary> baseDocuments,
            List<DocumentBoundary> compareDocuments,
            List<String> baseTexts,
            List<String> compareTexts,
            PDDocument basePdf,
            PDDocument comparePdf,
            PDFRenderer baseRenderer,
            PDFRenderer compareRenderer) throws IOException {

        List<DocumentMatch> matches = new ArrayList<>();

        // Calculate similarity scores for all possible document pairs
        List<PotentialMatch> potentialMatches = new ArrayList<>();

        for (int i = 0; i < baseDocuments.size(); i++) {
            DocumentBoundary baseBoundary = baseDocuments.get(i);

            for (int j = 0; j < compareDocuments.size(); j++) {
                DocumentBoundary compareBoundary = compareDocuments.get(j);

                // Calculate similarity using both text and visual features
                double similarity = calculateDocumentSimilarity(
                        baseBoundary, compareBoundary,
                        baseTexts, compareTexts,
                        basePdf, comparePdf,
                        baseRenderer, compareRenderer);

                // If similarity exceeds threshold, consider it as a potential match
                if (similarity > combinedSimilarityThreshold) {
                    potentialMatches.add(new PotentialMatch(i, j, similarity));
                    log.debug("Potential match: Base doc {} and Compare doc {}, similarity: {}",
                            i, j, String.format("%.4f", similarity));
                }
            }
        }

        // Sort potential matches by similarity (highest first)
        potentialMatches.sort((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()));

        // Create matches, ensuring each document is matched at most once
        Set<Integer> matchedBaseIndices = new HashSet<>();
        Set<Integer> matchedCompareIndices = new HashSet<>();

        for (PotentialMatch potentialMatch : potentialMatches) {
            int baseIndex = potentialMatch.getBaseIndex();
            int compareIndex = potentialMatch.getCompareIndex();

            // Only create match if neither document has been matched yet
            if (!matchedBaseIndices.contains(baseIndex) &&
                    !matchedCompareIndices.contains(compareIndex)) {

                matches.add(new DocumentMatch(baseIndex, compareIndex, potentialMatch.getSimilarity()));
                log.info("Matched: Base doc {} and Compare doc {}, similarity: {}",
                        baseIndex, compareIndex, String.format("%.4f", potentialMatch.getSimilarity()));

                // Mark these documents as matched
                matchedBaseIndices.add(baseIndex);
                matchedCompareIndices.add(compareIndex);
            }
        }

        return matches;
    }

    /**
     * Calculate similarity between two documents using both text and visual features.
     *
     * @param baseBoundary    Document boundary in base PDF
     * @param compareBoundary Document boundary in comparison PDF
     * @param baseTexts       List of page texts in base PDF
     * @param compareTexts    List of page texts in comparison PDF
     * @param basePdf         Base PDF document
     * @param comparePdf      Comparison PDF document
     * @param baseRenderer    Renderer for base PDF
     * @param compareRenderer Renderer for comparison PDF
     * @return Similarity score between 0.0 and 1.0
     * @throws IOException If there's an error processing the documents
     */
    private double calculateDocumentSimilarity(
            DocumentBoundary baseBoundary,
            DocumentBoundary compareBoundary,
            List<String> baseTexts,
            List<String> compareTexts,
            PDDocument basePdf,
            PDDocument comparePdf,
            PDFRenderer baseRenderer,
            PDFRenderer compareRenderer) throws IOException {

        // Calculate text similarity
        double textSimilarity = calculateTextSimilarityForDocuments(
                baseBoundary, compareBoundary, baseTexts, compareTexts);

        // Log text similarity for debugging
        log.debug("Text similarity between documents: {}", String.format("%.4f", textSimilarity));

        // If text similarity is very low, no need to check visual similarity
        if (textSimilarity < textSimilarityThreshold / 2) {
            return textSimilarity;
        }

        // Calculate visual similarity for a sample of pages
        double visualSimilarity = calculateVisualSimilarityForDocuments(
                baseBoundary, compareBoundary, basePdf, comparePdf, baseRenderer, compareRenderer);

        // Log visual similarity for debugging
        log.debug("Visual similarity between documents: {}", String.format("%.4f", visualSimilarity));

        // Combined similarity score (70% text, 30% visual)
        double combined = 0.7 * textSimilarity + 0.3 * visualSimilarity;
        log.debug("Combined similarity: {}", String.format("%.4f", combined));

        return combined;
    }

    /**
     * Calculate text similarity between two documents.
     *
     * @param baseBoundary    Document boundary in base PDF
     * @param compareBoundary Document boundary in comparison PDF
     * @param baseTexts       List of page texts in base PDF
     * @param compareTexts    List of page texts in comparison PDF
     * @return Text similarity score between 0.0 and 1.0
     */
    private double calculateTextSimilarityForDocuments(
            DocumentBoundary baseBoundary,
            DocumentBoundary compareBoundary,
            List<String> baseTexts,
            List<String> compareTexts) {

        // Extract a sample of text from each document
        StringBuilder baseText = new StringBuilder();
        StringBuilder compareText = new StringBuilder();

        // Sample the first page, middle page, and last page of each document
        int[] baseSampleIndices = getSamplePageIndices(baseBoundary);
        int[] compareSampleIndices = getSamplePageIndices(compareBoundary);

        for (int idx : baseSampleIndices) {
            if (idx >= 0 && idx < baseTexts.size()) {
                baseText.append(baseTexts.get(idx)).append(" ");
            }
        }

        for (int idx : compareSampleIndices) {
            if (idx >= 0 && idx < compareTexts.size()) {
                compareText.append(compareTexts.get(idx)).append(" ");
            }
        }

        // Calculate similarity between the texts
        return TextSimilarityUtils.calculateTextSimilarity(baseText.toString(), compareText.toString());
    }

    /**
     * Get indices for representative sample pages from a document.
     *
     * @param boundary Document boundary
     * @return Array of page indices to sample
     */
    private int[] getSamplePageIndices(DocumentBoundary boundary) {
        int pageCount = boundary.getEndPage() - boundary.getStartPage() + 1;

        // For small documents, sample all pages
        if (pageCount <= maxSamplePages) {
            int[] indices = new int[pageCount];
            for (int i = 0; i < pageCount; i++) {
                indices[i] = boundary.getStartPage() + i;
            }
            return indices;
        }

        // For larger documents, sample first, middle, and last page
        int[] indices = new int[3];
        indices[0] = boundary.getStartPage();  // First page
        indices[1] = boundary.getStartPage() + pageCount / 2;  // Middle page
        indices[2] = boundary.getEndPage();  // Last page
        return indices;
    }

    /**
     * Calculate visual similarity between two documents using SSIM.
     *
     * @param baseBoundary    Document boundary in base PDF
     * @param compareBoundary Document boundary in comparison PDF
     * @param basePdf         Base PDF document
     * @param comparePdf      Comparison PDF document
     * @param baseRenderer    Renderer for base PDF
     * @param compareRenderer Renderer for comparison PDF
     * @return Visual similarity score between 0.0 and 1.0
     * @throws IOException If there's an error processing the documents
     */
    private double calculateVisualSimilarityForDocuments(
            DocumentBoundary baseBoundary,
            DocumentBoundary compareBoundary,
            PDDocument basePdf,
            PDDocument comparePdf,
            PDFRenderer baseRenderer,
            PDFRenderer compareRenderer) throws IOException {

        int basePageCount = baseBoundary.getEndPage() - baseBoundary.getStartPage() + 1;
        int comparePageCount = compareBoundary.getEndPage() - compareBoundary.getStartPage() + 1;

        // Determine how many pages to sample
        int samplePageCount = Math.min(
                Math.min(basePageCount, comparePageCount),
                maxSamplePages);

        // No pages to compare
        if (samplePageCount <= 0) {
            return 0.0;
        }

        double totalSimilarity = 0.0;

        // Sample evenly distributed pages
        for (int i = 0; i < samplePageCount; i++) {
            // Calculate page indices for even distribution
            int basePageIndex = baseBoundary.getStartPage() +
                    (i * basePageCount / samplePageCount);

            int comparePageIndex = compareBoundary.getStartPage() +
                    (i * comparePageCount / samplePageCount);

            // Ensure we're within bounds
            if (basePageIndex < basePdf.getNumberOfPages() &&
                    comparePageIndex < comparePdf.getNumberOfPages()) {

                // Render pages to images
                BufferedImage baseImage = baseRenderer.renderImageWithDPI(
                        basePageIndex, 72, ImageType.RGB);

                BufferedImage compareImage = compareRenderer.renderImageWithDPI(
                        comparePageIndex, 72, ImageType.RGB);

                // Calculate SSIM
                double pageSimilarity = ssimCalculator.getSimilarityScore(baseImage, compareImage);

                totalSimilarity += pageSimilarity;
            }
        }

        // Return average similarity
        return totalSimilarity / samplePageCount;
    }
}