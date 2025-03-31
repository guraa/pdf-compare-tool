package guraa.pdfcompare.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Document matching facade that provides backward compatibility
 * and uses new matching strategies
 */
public class SmartDocumentMatcher {
    private static final Logger logger = LoggerFactory.getLogger(SmartDocumentMatcher.class);

    // Composition of matching components
    private final DocumentMatchingStrategy matchingStrategy;
    private final DocumentSegmentationStrategy segmentationStrategy;
    private final VisualMatcher visualMatcher;

    // Configuration parameters
    private double similarityThreshold = 0.1;
    private int minDocumentPages = 1;

    // Feature toggle flags
    private boolean useTextFeatures = true;
    private boolean useTitleMatching = true;
    private boolean useLayoutFeatures = true;
    private boolean useImageFeatures = true;

    public SmartDocumentMatcher() {
        this.matchingStrategy = new DocumentMatchingStrategy();
        this.segmentationStrategy = new DocumentSegmentationStrategy();
        this.visualMatcher = new VisualMatcher();
    }

    /**
     * Match documents with visual hash computation
     */
    public List<DocumentPair> matchDocuments(
            PDFDocumentModel baseDocument,
            PDFDocumentModel compareDocument,
            File baseFile,
            File compareFile) throws IOException {

        // Configure matching strategy
        matchingStrategy.setSimilarityThreshold(similarityThreshold);

        // Compute visual hashes
        List<String> baseVisualHashes = VisualMatcher.computeVisualHashes(baseFile);
        List<String> compareVisualHashes = VisualMatcher.computeVisualHashes(compareFile);

        // Perform document matching with converted segments
        List<DocumentPair> convertedPairs = matchDocumentsWithConvertedSegments(
                baseDocument, compareDocument
        );

        // Enhance matching with visual hash comparison
        enhanceMatchingWithVisualHashes(convertedPairs, baseVisualHashes, compareVisualHashes);

        return convertedPairs;
    }

    /**
     * Match documents without visual hash computation
     */
    public List<DocumentPair> matchDocuments(
            PDFDocumentModel baseDocument,
            PDFDocumentModel compareDocument) throws IOException {

        // Perform document matching with converted segments
        return matchDocumentsWithConvertedSegments(baseDocument, compareDocument);
    }

    /**
     * Match documents with converted segments to handle type compatibility
     */
    private List<DocumentPair> matchDocumentsWithConvertedSegments(
            PDFDocumentModel baseDocument,
            PDFDocumentModel compareDocument) throws IOException {

        // Segment both documents
        List<DocumentSegment> baseSegments = segmentationStrategy.segment(baseDocument);
        List<DocumentSegment> compareSegments = segmentationStrategy.segment(compareDocument);

        // Find matches with converted segments
        List<DocumentPair> matchedPairs = findMatchesWithConvertedSegments(
                baseDocument, compareDocument, baseSegments, compareSegments
        );

        return matchedPairs;
    }

    /**
     * Find matches with converted segments to handle type compatibility
     */
    private List<DocumentPair> findMatchesWithConvertedSegments(
            PDFDocumentModel baseDocument,
            PDFDocumentModel compareDocument,
            List<DocumentSegment> baseSegments,
            List<DocumentSegment> compareSegments) {

        List<DocumentPair> matchedPairs = new ArrayList<>();

        for (DocumentSegment baseSegment : baseSegments) {
            DocumentSegment bestMatchSegment = findBestMatchSegment(
                    baseSegment, compareSegments
            );

            if (bestMatchSegment != null) {
                // Create a match pair
                DocumentPair pair = new DocumentPair(
                        baseSegment.getStartPage(),
                        baseSegment.getEndPage(),
                        bestMatchSegment.getStartPage(),
                        bestMatchSegment.getEndPage(),
                        calculateSimilarity(baseSegment, bestMatchSegment)
                );

                matchedPairs.add(pair);
            } else {
                // Add unmatched base segment
                matchedPairs.add(new DocumentPair(
                        baseSegment.getStartPage(),
                        baseSegment.getEndPage(),
                        -1,
                        -1,
                        0.0
                ));
            }
        }

        // Add unmatched compare segments
        for (DocumentSegment compareSegment : compareSegments) {
            boolean matched = matchedPairs.stream()
                    .anyMatch(pair ->
                            pair.getCompareStartPage() == compareSegment.getStartPage() &&
                                    pair.getCompareEndPage() == compareSegment.getEndPage()
                    );

            if (!matched) {
                matchedPairs.add(new DocumentPair(
                        -1,
                        -1,
                        compareSegment.getStartPage(),
                        compareSegment.getEndPage(),
                        0.0
                ));
            }
        }

        return matchedPairs;
    }

    /**
     * Find best matching segment
     */
    private DocumentSegment findBestMatchSegment(
            DocumentSegment baseSegment,
            List<DocumentSegment> compareSegments) {

        return compareSegments.stream()
                .max(Comparator.comparingDouble(compareSegment ->
                        calculateSimilarity(baseSegment, compareSegment)
                ))
                .filter(compareSegment ->
                        calculateSimilarity(baseSegment, compareSegment) > similarityThreshold
                )
                .orElse(null);
    }

    /**
     * Calculate similarity between segments
     */
    private double calculateSimilarity(
            DocumentSegment baseSegment,
            DocumentSegment compareSegment) {

        // Extract text features
        String baseText = extractFullText(baseSegment, baseDocument);
        String compareText = extractFullText(compareSegment, compareDocument);

        // Basic similarity calculation (can be enhanced)
        return calculateTextSimilarity(baseText, compareText);
    }

    /**
     * Extract full text from a segment
     */
    private String extractFullText(
            DocumentSegment segment,
            PDFDocumentModel document) {

        StringBuilder text = new StringBuilder();
        for (int i = segment.getStartPage(); i <= segment.getEndPage(); i++) {
            PDFPageModel page = document.getPages().get(i);
            text.append(page.getText()).append("\n");
        }
        return text.toString();
    }

    /**
     * Calculate text similarity
     */
    private double calculateTextSimilarity(String text1, String text2) {
        if (text1 == null || text2 == null) return 0.0;

        // Simple similarity calculation
        return 1.0 - (double) levenshteinDistance(text1, text2) /
                Math.max(text1.length(), text2.length());
    }

    /**
     * Levenshtein distance calculation
     */
    private int levenshteinDistance(String s1, String s2) {
        int[] costs = new int[s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) {
            int lastValue = i;
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0) {
                    costs[j] = j;
                } else if (j > 0) {
                    int newValue = costs[j - 1];
                    if (s1.charAt(i - 1) != s2.charAt(j - 1)) {
                        newValue = Math.min(Math.min(newValue, lastValue), costs[j]) + 1;
                    }
                    costs[j - 1] = lastValue;
                    lastValue = newValue;
                }
            }
            if (i > 0) {
                costs[s2.length()] = lastValue;
            }
        }
        return costs[s2.length()];
    }

    /**
     * Enhance matching results with visual hash comparison
     */
    private void enhanceMatchingWithVisualHashes(
            List<DocumentPair> matchedPairs,
            List<String> baseVisualHashes,
            List<String> compareVisualHashes) {

        for (DocumentPair pair : matchedPairs) {
            if (pair.isMatched()) {
                // Slice visual hashes for the matched segments
                List<String> baseSegmentHashes = VisualMatcher.sliceHashes(
                        baseVisualHashes,
                        pair.getBaseStartPage(),
                        pair.getBaseEndPage()
                );

                List<String> compareSegmentHashes = VisualMatcher.sliceHashes(
                        compareVisualHashes,
                        pair.getCompareStartPage(),
                        pair.getCompareEndPage()
                );

                // Compare visual hashes
                double visualSimilarity = VisualMatcher.compareHashLists(
                        baseSegmentHashes,
                        compareSegmentHashes
                );

                // Log visual matching details
                logger.debug("Visual hash similarity for pair: {}", visualSimilarity);
            }
        }
    }

    // Remaining configuration methods stay the same...

    // Inner classes (DocumentSegment and DocumentPair) remain the same
}