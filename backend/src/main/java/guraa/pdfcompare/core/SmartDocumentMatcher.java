package guraa.pdfcompare.core;

import guraa.pdfcompare.comparison.PDFComparisonResult;
import guraa.pdfcompare.VisualMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Document matching facade that provides backward compatibility
 * and uses new matching strategies
 */
public class SmartDocumentMatcher {
    private static final Logger logger = LoggerFactory.getLogger(SmartDocumentMatcher.class);

    // Composition of matching components
    private final DocumentMatchingStrategy matchingStrategy;
    private final DocumentSegmentationStrategy segmentationStrategy;

    // Storage for document matching results
    private Map<String, List<DocumentPair>> documentPairsById = new HashMap<>();
    private Map<String, Map<Integer, PDFComparisonResult>> pairResultsById = new HashMap<>();

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

        // Perform document matching with SmartDocumentMatcher.DocumentSegment
        List<DocumentPair> convertedPairs = matchDocumentsWithInternalSegments(
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

        // Perform document matching with internal segments
        return matchDocumentsWithInternalSegments(baseDocument, compareDocument);
    }

    /**
     * Match documents with internal DocumentSegment class
     */
    private List<DocumentPair> matchDocumentsWithInternalSegments(
            PDFDocumentModel baseDocument,
            PDFDocumentModel compareDocument) throws IOException {

        // Segment both documents using our internal DocumentSegment class
        List<DocumentSegment> baseSegments = convertToInternalSegments(
                segmentationStrategy.segment(baseDocument)
        );

        List<DocumentSegment> compareSegments = convertToInternalSegments(
                segmentationStrategy.segment(compareDocument)
        );

        // Find matches
        List<DocumentPair> matchedPairs = findMatches(
                baseDocument, compareDocument, baseSegments, compareSegments
        );

        return matchedPairs;
    }

    /**
     * Convert external DocumentSegment to internal DocumentSegment
     */
    private List<DocumentSegment> convertToInternalSegments(List<guraa.pdfcompare.core.DocumentSegment> externalSegments) {
        List<DocumentSegment> internalSegments = new ArrayList<>();

        for (guraa.pdfcompare.core.DocumentSegment segment : externalSegments) {
            DocumentSegment internalSegment = new DocumentSegment(
                    segment.getStartPage(),
                    segment.getEndPage(),
                    segment.getTitle()
            );

            // Check for null features map before copying
            Map<String, Object> features = segment.getFeatures();
            if (features != null) {
                internalSegment.setFeatures(new HashMap<>(features));
            } else {
                // Initialize with empty map if features is null
                internalSegment.setFeatures(new HashMap<>());
            }

            internalSegments.add(internalSegment);
        }

        return internalSegments;
    }

    /**
     * Find matches between base and compare segments
     */
    private List<DocumentPair> findMatches(
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

        DocumentSegment bestMatch = null;
        double bestSimilarity = similarityThreshold;

        for (DocumentSegment compareSegment : compareSegments) {
            double similarity = calculateSimilarity(baseSegment, compareSegment);
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity;
                bestMatch = compareSegment;
            }
        }

        return bestMatch;
    }

    /**
     * Calculate similarity between segments
     */
    private double calculateSimilarity(
            DocumentSegment baseSegment,
            DocumentSegment compareSegment) {

        // Extract text features from the segments' features map (with null check)
        Map<String, Object> baseFeatures = baseSegment.getFeatures() != null ?
                baseSegment.getFeatures() : new HashMap<>();
        Map<String, Object> compareFeatures = compareSegment.getFeatures() != null ?
                compareSegment.getFeatures() : new HashMap<>();

        // Use text features if available, otherwise extract from document directly
        String baseText = (String) baseFeatures.getOrDefault("fullText", "");
        String compareText = (String) compareFeatures.getOrDefault("fullText", "");

        if (baseText.isEmpty() || compareText.isEmpty()) {
            // Need to extract text (implementation depends on your specific setup)
            // This is a simplified version - just use the title if available
            baseText = baseSegment.getTitle() != null ? baseSegment.getTitle() : "";
            compareText = compareSegment.getTitle() != null ? compareSegment.getTitle() : "";
        }

        // Calculate text similarity (with null/empty checks)
        return calculateTextSimilarity(baseText, compareText);
    }

    /**
     * Calculate text similarity
     */
    private double calculateTextSimilarity(String text1, String text2) {
        // Handle null or empty cases
        if (text1 == null || text2 == null) return 0.0;
        if (text1.isEmpty() || text2.isEmpty()) return 0.0;

        // Simple Jaccard similarity
        Set<String> set1 = new HashSet<>(Arrays.asList(text1.toLowerCase().split("\\W+")));
        Set<String> set2 = new HashSet<>(Arrays.asList(text2.toLowerCase().split("\\W+")));

        // Remove empty strings that might result from splitting
        set1.remove("");
        set2.remove("");

        // If either set is empty after processing, similarity is 0
        if (set1.isEmpty() || set2.isEmpty()) return 0.0;

        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);

        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);

        return (double) intersection.size() / union.size();
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

    /**
     * Store document pairs for a comparison ID
     */
    public void storeDocumentPairs(String comparisonId, List<DocumentPair> pairs) {
        documentPairsById.put(comparisonId, pairs);
    }

    /**
     * Store pair result for a comparison ID and pair index
     */
    public void storePairResult(String comparisonId, int pairIndex, PDFComparisonResult result) {
        pairResultsById.computeIfAbsent(comparisonId, k -> new HashMap<>())
                .put(pairIndex, result);
    }

    /**
     * Get document pairs for a comparison ID
     */
    public List<DocumentPair> getDocumentPairs(String comparisonId) {
        return documentPairsById.getOrDefault(comparisonId, new ArrayList<>());
    }

    /**
     * Get pair result for a comparison ID and pair index
     */
    public PDFComparisonResult getPairResult(String comparisonId, int pairIndex) {
        return pairResultsById.getOrDefault(comparisonId, Collections.emptyMap())
                .get(pairIndex);
    }

    /**
     * Inner class for document segments
     */
    public static class DocumentSegment {
        private final int startPage;
        private final int endPage;
        private final String title;
        private Map<String, Object> features = new HashMap<>();

        public DocumentSegment(int startPage, int endPage, String title) {
            this.startPage = startPage;
            this.endPage = endPage;
            this.title = title;
        }

        public int getStartPage() {
            return startPage;
        }

        public int getEndPage() {
            return endPage;
        }

        public String getTitle() {
            return title;
        }

        public Map<String, Object> getFeatures() {
            return features;
        }

        public void setFeatures(Map<String, Object> features) {
            this.features = features != null ? features : new HashMap<>();
        }
    }

    /**
     * Inner class for document pairs
     */
    public static class DocumentPair {
        private final int baseStartPage;
        private final int baseEndPage;
        private final int compareStartPage;
        private final int compareEndPage;
        private final double similarityScore;

        public DocumentPair(
                int baseStartPage,
                int baseEndPage,
                int compareStartPage,
                int compareEndPage,
                double similarityScore) {
            this.baseStartPage = baseStartPage;
            this.baseEndPage = baseEndPage;
            this.compareStartPage = compareStartPage;
            this.compareEndPage = compareEndPage;
            this.similarityScore = similarityScore;
        }

        public boolean isMatched() {
            return hasBaseDocument() && hasCompareDocument();
        }

        public boolean hasBaseDocument() {
            return baseStartPage >= 0 && baseEndPage >= 0;
        }

        public boolean hasCompareDocument() {
            return compareStartPage >= 0 && compareEndPage >= 0;
        }

        public int getBaseStartPage() {
            return baseStartPage;
        }

        public int getBaseEndPage() {
            return baseEndPage;
        }

        public int getCompareStartPage() {
            return compareStartPage;
        }

        public int getCompareEndPage() {
            return compareEndPage;
        }

        public double getSimilarityScore() {
            return similarityScore;
        }
    }
}