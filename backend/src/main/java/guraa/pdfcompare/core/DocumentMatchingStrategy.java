package guraa.pdfcompare.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Strategy for matching document segments across two PDFs
 */
public class DocumentMatchingStrategy {
    private static final Logger logger = LoggerFactory.getLogger(DocumentMatchingStrategy.class);

    // Matching configuration
    private double similarityThreshold = 0.5;
    private boolean strictMatching = false;

    // Dependency components
    private final DocumentSimilarityScorer similarityScorer;
    private final DocumentSegmentationStrategy segmentationStrategy;

    public DocumentMatchingStrategy() {
        this.similarityScorer = new DocumentSimilarityScorer();
        this.segmentationStrategy = new DocumentSegmentationStrategy();
    }

    /**
     * Match document segments between two PDFs
     * @param baseDocument Base PDF document
     * @param compareDocument Comparison PDF document
     * @return List of matched document pairs
     */
    public List<DocumentPair> matchDocuments(
            PDFDocumentModel baseDocument,
            PDFDocumentModel compareDocument) {

        // Segment both documents
        List<DocumentSegment> baseSegments = segmentationStrategy.segment(baseDocument);
        List<DocumentSegment> compareSegments = segmentationStrategy.segment(compareDocument);

        // Prepare segments
        prepareSegments(baseSegments, baseDocument);
        prepareSegments(compareSegments, compareDocument);

        // Find matches
        List<DocumentPair> matchedPairs = findSegmentMatches(baseSegments, compareSegments);

        // Add unmatched segments
        addUnmatchedSegments(matchedPairs, baseSegments, compareSegments);

        return matchedPairs;
    }

    /**
     * Prepare segments by extracting features
     * @param segments List of document segments
     * @param document Source document
     */
    private void prepareSegments(
            List<DocumentSegment> segments,
            PDFDocumentModel document) {

        for (DocumentSegment segment : segments) {
            // Extract text features
            String fullText = extractFullText(segment, document);
            List<String> keywords = extractKeywords(segment, document);

            // Prepare features map
            Map<String, Object> features = new HashMap<>();
            features.put("fullText", fullText);
            features.put("keywords", keywords);
            features.put("pageCount", segment.getPageCount());
            features.put("title", segment.getTitle());

            // Add page dimensions
            features.put("pageDimensions",
                    extractPageDimensions(segment, document));

            // Add image count
            features.put("imageCount",
                    countImages(segment, document));

            segment.setFeatures(features);
        }
    }

    /**
     * Find matches between base and compare segments
     * @param baseSegments Base document segments
     * @param compareSegments Compare document segments
     * @return List of matched document pairs
     */
    private List<DocumentPair> findSegmentMatches(
            List<DocumentSegment> baseSegments,
            List<DocumentSegment> compareSegments) {

        List<DocumentPair> matchedPairs = new ArrayList<>();
        Set<Integer> matchedBaseIndices = new HashSet<>();
        Set<Integer> matchedCompareIndices = new HashSet<>();

        // Find best matches
        for (int i = 0; i < baseSegments.size(); i++) {
            DocumentSegment baseSegment = baseSegments.get(i);

            DocumentPair bestMatch = findBestMatch(
                    baseSegment,
                    compareSegments,
                    matchedCompareIndices
            );

            if (bestMatch != null) {
                matchedPairs.add(bestMatch);
                matchedBaseIndices.add(i);
                matchedCompareIndices.add(
                        compareSegments.indexOf(
                                getCompareSegmentByPages(
                                        compareSegments,
                                        bestMatch.getCompareStartPage(),
                                        bestMatch.getCompareEndPage()
                                )
                        )
                );
            }
        }

        return matchedPairs;
    }

    /**
     * Find the best matching segment for a base segment
     * @param baseSegment Base segment to match
     * @param compareSegments Available comparison segments
     * @param excludedIndices Indices of already matched segments
     * @return Best matching document pair
     */
    private DocumentPair findBestMatch(
            DocumentSegment baseSegment,
            List<DocumentSegment> compareSegments,
            Set<Integer> excludedIndices) {

        double bestSimilarity = 0;
        DocumentPair bestMatch = null;

        for (int j = 0; j < compareSegments.size(); j++) {
            // Skip already matched segments
            if (excludedIndices.contains(j)) continue;

            DocumentSegment compareSegment = compareSegments.get(j);

            // Calculate similarity
            double similarity = similarityScorer.calculateSimilarity(baseSegment, compareSegment);

            // Check against threshold
            if (similarity >= similarityThreshold && similarity > bestSimilarity) {
                bestSimilarity = similarity;
                bestMatch = new DocumentPair(
                        baseSegment.getStartPage(),
                        baseSegment.getEndPage(),
                        compareSegment.getStartPage(),
                        compareSegment.getEndPage(),
                        similarity
                );
            }
        }

        return bestMatch;
    }

    /**
     * Add unmatched segments to the result
     * @param matchedPairs Existing matched pairs
     * @param baseSegments Base document segments
     * @param compareSegments Compare document segments
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

    /**
     * Find a comparison segment by its page range
     * @param segments List of segments to search
     * @param startPage Start page
     * @param endPage End page
     * @return Matching segment or null
     */
    private DocumentSegment getCompareSegmentByPages(
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
     * Extract full text from a document segment
     * @param segment Document segment
     * @param document Source document
     * @return Extracted full text
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
     * Extract keywords from a document segment
     * @param segment Document segment
     * @param document Source document
     * @return List of extracted keywords
     */
    private List<String> extractKeywords(
            DocumentSegment segment,
            PDFDocumentModel document) {

        return document.getPages().stream()
                .skip(segment.getStartPage())
                .limit(segment.getEndPage() - segment.getStartPage() + 1)
                .flatMap(page -> page.getTextElements().stream())
                .filter(el -> el.getFontSize() > 12 || "bold".equalsIgnoreCase(el.getFontStyle()))
                .map(TextElement::getText)
                .distinct()
                .limit(50)
                .collect(Collectors.toList());
    }

    /**
     * Extract page dimensions for a segment
     * @param segment Document segment
     * @param document Source document
     * @return List of page dimensions
     */
    private List<float[]> extractPageDimensions(
            DocumentSegment segment,
            PDFDocumentModel document) {

        return document.getPages().stream()
                .skip(segment.getStartPage())
                .limit(segment.getEndPage() - segment.getStartPage() + 1)
                .map(page -> new float[]{page.getWidth(), page.getHeight()})
                .collect(Collectors.toList());
    }

    /**
     * Count images in a document segment
     * @param segment Document segment
     * @param document Source document
     * @return Number of images
     */
    private int countImages(
            DocumentSegment segment,
            PDFDocumentModel document) {

        return document.getPages().stream()
                .skip(segment.getStartPage())
                .limit(segment.getEndPage() - segment.getStartPage() + 1)
                .mapToInt(page -> page.getImages().size())
                .sum();
    }

    // Configuration methods
    public void setSimilarityThreshold(double threshold) {
        this.similarityThreshold = threshold;
    }

    public void setStrictMatching(boolean strictMatching) {
        this.strictMatching = strictMatching;
    }

    /**
     * Get the current similarity threshold
     * @return Current similarity threshold
     */
    public double getSimilarityThreshold() {
        return similarityThreshold;
    }

    /**
     * Check if strict matching is enabled
     * @return True if strict matching is enabled
     */
    public boolean isStrictMatching() {
        return strictMatching;
    }
}