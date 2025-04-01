package guraa.pdfcompare.core;

import guraa.pdfcompare.visual.EnhancedVisualMatcher;
import guraa.pdfcompare.visual.EnhancedVisualMatcher.DocumentSegmentMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Advanced document matching strategy with visual-first approach
 */
public class DocumentMatchingStrategy {
    private static final Logger logger = LoggerFactory.getLogger(DocumentMatchingStrategy.class);

    // Configuration parameters
    private double visualSimilarityWeight = 0.6;  // Visual similarity gets 60% weight
    private double contentSimilarityWeight = 0.4; // Content similarity gets 40% weight
    private double similarityThreshold = 0.5;

    // Dependency components
    private final EnhancedVisualMatcher visualMatcher;
    private final DocumentSimilarityScorer contentSimilarityScorer;
    private final DocumentSegmentationStrategy segmentationStrategy;

    public DocumentMatchingStrategy() {
        this.visualMatcher = new EnhancedVisualMatcher();
        this.contentSimilarityScorer = new DocumentSimilarityScorer();
        this.segmentationStrategy = new DocumentSegmentationStrategy();
    }

    /**
     * Match documents using visual and content-based techniques
     * @param baseDocument Base PDF document
     * @param compareDocument Comparison PDF document
     * @param baseFile Base PDF file
     * @param compareFile Comparison PDF file
     * @return List of matched document pairs
     * @throws IOException If there's an error processing files
     */
    public List<DocumentPair> matchDocuments(
            PDFDocumentModel baseDocument,
            PDFDocumentModel compareDocument,
            File baseFile,
            File compareFile) throws IOException {

        // Step 1: Compute visual hashes
        List<String> baseVisualHashes = computeVisualHashes(baseFile);
        List<String> compareVisualHashes = computeVisualHashes(compareFile);

        // Step 2: Segment documents
        List<DocumentSegment> baseSegments = segmentationStrategy.segment(baseDocument);
        List<DocumentSegment> compareSegments = segmentationStrategy.segment(compareDocument);

        // Step 3: Perform visual matching first
        List<DocumentSegmentMatch> visualMatches = findVisualMatches(
                baseVisualHashes,
                compareVisualHashes
        );

        // Step 4: Refine matches with content similarity
        List<DocumentPair> matchedPairs = refineMatchesWithContentSimilarity(
                visualMatches,
                baseSegments,
                compareSegments,
                baseDocument,
                compareDocument
        );

        // Step 5: Add unmatched segments
        addUnmatchedSegments(matchedPairs, baseSegments, compareSegments);

        return matchedPairs;
    }

    /**
     * Compute visual hashes for a document
     */
    private List<String> computeVisualHashes(File pdfFile) throws IOException {
        return new EnhancedVisualMatcher().computePerceptualHashes(pdfFile);
    }

    /**
     * Find visually matched segments
     */
    private List<DocumentSegmentMatch> findVisualMatches(
            List<String> baseVisualHashes,
            List<String> compareVisualHashes) {

        return visualMatcher.findVisuallyMatchedSegments(baseVisualHashes, compareVisualHashes);
    }

    /**
     * Refine visual matches with content similarity
     */
    private List<DocumentPair> refineMatchesWithContentSimilarity(
            List<DocumentSegmentMatch> visualMatches,
            List<DocumentSegment> baseSegments,
            List<DocumentSegment> compareSegments,
            PDFDocumentModel baseDocument,
            PDFDocumentModel compareDocument) {

        List<DocumentPair> matchedPairs = new ArrayList<>();
        Set<Integer> matchedBaseIndices = new HashSet<>();
        Set<Integer> matchedCompareIndices = new HashSet<>();

        // Process visual matches first
        for (DocumentSegmentMatch visualMatch : visualMatches) {
            DocumentSegment baseSegment = findSegmentByPageRange(
                    baseSegments,
                    visualMatch.getBaseStartPage(),
                    visualMatch.getBaseEndPage()
            );

            DocumentSegment compareSegment = findSegmentByPageRange(
                    compareSegments,
                    visualMatch.getCompareStartPage(),
                    visualMatch.getCompareEndPage()
            );

            if (baseSegment != null && compareSegment != null) {
                // Compute combined similarity score
                double visualSimilarity = visualMatch.getSimilarity();
                double contentSimilarity = contentSimilarityScorer.calculateSimilarity(
                        baseSegment, compareSegment
                );

                double combinedSimilarity = (visualSimilarity * visualSimilarityWeight) +
                        (contentSimilarity * contentSimilarityWeight);

                // Create document pair if similarity is above threshold
                if (combinedSimilarity >= similarityThreshold) {
                    DocumentPair pair = new DocumentPair(
                            baseSegment.getStartPage(),
                            baseSegment.getEndPage(),
                            compareSegment.getStartPage(),
                            compareSegment.getEndPage(),
                            combinedSimilarity
                    );

                    matchedPairs.add(pair);
                    matchedBaseIndices.add(baseSegments.indexOf(baseSegment));
                    matchedCompareIndices.add(compareSegments.indexOf(compareSegment));
                }
            }
        }

        // Find additional matches for unmatched segments using content-based matching
        for (int i = 0; i < baseSegments.size(); i++) {
            if (matchedBaseIndices.contains(i)) continue;

            DocumentSegment baseSegment = baseSegments.get(i);
            DocumentSegment bestMatch = null;
            double bestSimilarity = 0;

            for (int j = 0; j < compareSegments.size(); j++) {
                if (matchedCompareIndices.contains(j)) continue;

                DocumentSegment compareSegment = compareSegments.get(j);

                // Compute content similarity
                double contentSimilarity = contentSimilarityScorer.calculateSimilarity(
                        baseSegment, compareSegment
                );

                if (contentSimilarity > bestSimilarity && contentSimilarity >= similarityThreshold) {
                    bestSimilarity = contentSimilarity;
                    bestMatch = compareSegment;
                }
            }

            // Add match if found
            if (bestMatch != null) {
                DocumentPair pair = new DocumentPair(
                        baseSegment.getStartPage(),
                        baseSegment.getEndPage(),
                        bestMatch.getStartPage(),
                        bestMatch.getEndPage(),
                        bestSimilarity
                );

                matchedPairs.add(pair);
            }
        }

        return matchedPairs;
    }

    /**
     * Find a segment by its page range
     */
    private DocumentSegment findSegmentByPageRange(
            List<DocumentSegment> segments,
            int startPage,
            int endPage) {

        return segments.stream()
                .filter(seg ->
                        seg.getStartPage() == startPage &&
                                seg.getEndPage() == endPage)
                .findFirst()
                .orElse(null);
    }

    /**
     * Add unmatched segments to the result
     */
    private void addUnmatchedSegments(
            List<DocumentPair> matchedPairs,
            List<DocumentSegment> baseSegments,
            List<DocumentSegment> compareSegments) {

        // Add unmatched base segments
        for (DocumentSegment segment : baseSegments) {
            boolean matched = matchedPairs.stream()
                    .anyMatch(pair ->
                            pair.getBaseStartPage() == segment.getStartPage() &&
                                    pair.getBaseEndPage() == segment.getEndPage()
                    );

            if (!matched) {
                matchedPairs.add(new DocumentPair(
                        segment.getStartPage(),
                        segment.getEndPage(),
                        -1,
                        -1,
                        0.0
                ));
            }
        }

        // Add unmatched compare segments
        for (DocumentSegment segment : compareSegments) {
            boolean matched = matchedPairs.stream()
                    .anyMatch(pair ->
                            pair.getCompareStartPage() == segment.getStartPage() &&
                                    pair.getCompareEndPage() == segment.getEndPage()
                    );

            if (!matched) {
                matchedPairs.add(new DocumentPair(
                        -1,
                        -1,
                        segment.getStartPage(),
                        segment.getEndPage(),
                        0.0
                ));
            }
        }
    }

    // Configuration methods
    public void setVisualSimilarityWeight(double weight) {
        this.visualSimilarityWeight = weight;
    }

    public void setContentSimilarityWeight(double weight) {
        this.contentSimilarityWeight = weight;
    }

    public void setSimilarityThreshold(double threshold) {
        this.similarityThreshold = threshold;
    }

    public double getVisualSimilarityWeight() {
        return visualSimilarityWeight;
    }

    public double getContentSimilarityWeight() {
        return contentSimilarityWeight;
    }

    public double getSimilarityThreshold() {
        return similarityThreshold;
    }
}