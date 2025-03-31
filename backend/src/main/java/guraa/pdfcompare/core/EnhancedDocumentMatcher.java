package guraa.pdfcompare.core;

import guraa.pdfcompare.comparison.PDFComparisonResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Enhanced document matching strategy with more sophisticated matching techniques
 */
public class EnhancedDocumentMatcher {
    private static final Logger logger = LoggerFactory.getLogger(EnhancedDocumentMatcher.class);

    // Configuration parameters
    private double similarityThreshold = 0.5;  // Increased from 0.1
    private int minDocumentPages = 3;          // Increased from 1
    private boolean useAdvancedMatching = true;

    // Scoring components
    private final TextSimilarityCalculator textSimilarityCalculator;
    private final ContentTypeDetector contentTypeDetector;
    private final DocumentSimilarityScorer similarityScorer;

    public EnhancedDocumentMatcher() {
        this.textSimilarityCalculator = new TextSimilarityCalculator();
        this.contentTypeDetector = new ContentTypeDetector();
        this.similarityScorer = new DocumentSimilarityScorer();
    }

    /**
     * Match documents between two multi-document PDFs
     *
     * @param baseDocument The base PDF document model
     * @param compareDocument The comparison PDF document model
     * @return List of document pairs
     */
    public List<SmartDocumentMatcher.DocumentPair> matchDocuments(
            PDFDocumentModel baseDocument,
            PDFDocumentModel compareDocument) {

        // Segment documents
        List<SmartDocumentMatcher.DocumentSegment> baseSegments =
                segmentDocuments(baseDocument);
        List<SmartDocumentMatcher.DocumentSegment> compareSegments =
                segmentDocuments(compareDocument);

        // Extract features for segments
        baseSegments.forEach(segment -> extractSegmentFeatures(segment, baseDocument));
        compareSegments.forEach(segment -> extractSegmentFeatures(segment, compareDocument));

        // Match segments
        List<SmartDocumentMatcher.DocumentPair> matchedPairs =
                performAdvancedSegmentMatching(baseSegments, compareSegments);

        // Add unmatched segments
        addUnmatchedSegments(matchedPairs, baseSegments, compareSegments);

        return matchedPairs;
    }

    /**
     * Segment documents into potential individual document chunks
     */
    private List<SmartDocumentMatcher.DocumentSegment> segmentDocuments(PDFDocumentModel document) {
        List<SmartDocumentMatcher.DocumentSegment> segments = new ArrayList<>();
        int currentStart = 0;

        for (int i = 0; i < document.getPageCount(); i++) {
            PDFPageModel page = document.getPages().get(i);

            // Detect potential document start
            if (isDocumentStart(page) && (i - currentStart >= minDocumentPages)) {
                // Create segment from previous pages
                SmartDocumentMatcher.DocumentSegment segment = new SmartDocumentMatcher.DocumentSegment(
                        currentStart,
                        i - 1,
                        extractDocumentTitle(document.getPages().subList(currentStart, i))
                );
                segments.add(segment);

                // Reset start for new segment
                currentStart = i;
            }
        }

        // Add final segment
        if (document.getPageCount() - currentStart >= minDocumentPages) {
            SmartDocumentMatcher.DocumentSegment finalSegment = new SmartDocumentMatcher.DocumentSegment(
                    currentStart,
                    document.getPageCount() - 1,
                    extractDocumentTitle(document.getPages().subList(currentStart, document.getPageCount()))
            );
            segments.add(finalSegment);
        }

        return segments;
    }

    /**
     * Extract features for a document segment
     */
    private void extractSegmentFeatures(
            SmartDocumentMatcher.DocumentSegment segment,
            PDFDocumentModel document) {

        Map<String, Object> features = new HashMap<>();

        // Text features
        features.put("fullText", extractFullText(segment, document));
        features.put("keywords", extractKeywords(segment, document));
        features.put("contentType", contentTypeDetector.detectContentType(segment, document));

        // Layout features
        features.put("pageDimensions", extractPageDimensions(segment, document));
        features.put("imageCount", countImages(segment, document));

        segment.setFeatures(features);
    }

    /**
     * Perform advanced segment matching
     */
    private List<SmartDocumentMatcher.DocumentPair> performAdvancedSegmentMatching(
            List<SmartDocumentMatcher.DocumentSegment> baseSegments,
            List<SmartDocumentMatcher.DocumentSegment> compareSegments) {

        List<SmartDocumentMatcher.DocumentPair> matchedPairs = new ArrayList<>();
        Set<Integer> matchedBaseIndices = new HashSet<>();
        Set<Integer> matchedCompareIndices = new HashSet<>();

        // Sort segments by potential match score
        for (int i = 0; i < baseSegments.size(); i++) {
            SmartDocumentMatcher.DocumentSegment baseSegment = baseSegments.get(i);

            double bestScore = 0;
            int bestMatchIndex = -1;

            for (int j = 0; j < compareSegments.size(); j++) {
                if (matchedCompareIndices.contains(j)) continue;

                SmartDocumentMatcher.DocumentSegment compareSegment = compareSegments.get(j);

                double similarity = similarityScorer.calculateSimilarity(baseSegment, compareSegment);

                if (similarity > bestScore && similarity >= similarityThreshold) {
                    bestScore = similarity;
                    bestMatchIndex = j;
                }
            }

            // Create matched pair if a good match is found
            if (bestMatchIndex != -1) {
                SmartDocumentMatcher.DocumentPair pair = new SmartDocumentMatcher.DocumentPair(
                        baseSegment.getStartPage(),
                        baseSegment.getEndPage(),
                        compareSegments.get(bestMatchIndex).getStartPage(),
                        compareSegments.get(bestMatchIndex).getEndPage(),
                        bestScore
                );

                matchedPairs.add(pair);
                matchedBaseIndices.add(i);
                matchedCompareIndices.add(bestMatchIndex);
            }
        }

        return matchedPairs;
    }

    /**
     * Add unmatched segments to the result
     */
    private void addUnmatchedSegments(
            List<SmartDocumentMatcher.DocumentPair> matchedPairs,
            List<SmartDocumentMatcher.DocumentSegment> baseSegments,
            List<SmartDocumentMatcher.DocumentSegment> compareSegments) {

        // Add unmatched base segments
        for (int i = 0; i < baseSegments.size(); i++) {
            SmartDocumentMatcher.DocumentSegment segment = baseSegments.get(i);
            boolean matched = matchedPairs.stream()
                    .anyMatch(pair -> pair.getBaseStartPage() == segment.getStartPage());

            if (!matched) {
                matchedPairs.add(new SmartDocumentMatcher.DocumentPair(
                        segment.getStartPage(),
                        segment.getEndPage(),
                        -1,
                        -1,
                        0.0
                ));
            }
        }

        // Add unmatched compare segments
        for (int i = 0; i < compareSegments.size(); i++) {
            SmartDocumentMatcher.DocumentSegment segment = compareSegments.get(i);
            boolean matched = matchedPairs.stream()
                    .anyMatch(pair -> pair.getCompareStartPage() == segment.getStartPage());

            if (!matched) {
                matchedPairs.add(new SmartDocumentMatcher.DocumentPair(
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
     * Determine if a page is likely the start of a document
     */
    private boolean isDocumentStart(PDFPageModel page) {
        // More sophisticated document start detection
        if (page.getPageNumber() == 1) return true;

        // Look for title-like text elements
        if (page.getTextElements() != null && !page.getTextElements().isEmpty()) {
            return page.getTextElements().stream()
                    .filter(el -> el.getY() < page.getHeight() * 0.3)  // Top 30% of page
                    .anyMatch(el ->
                            el.getFontSize() > 14 &&  // Large font
                                    el.getText().length() > 5 &&  // Not too short
                                    el.getText().length() < 100  // Not too long
                    );
        }

        return false;
    }

    /**
     * Extract document title from pages
     */
    private String extractDocumentTitle(List<PDFPageModel> pages) {
        // Try to extract title from first page
        if (!pages.isEmpty()) {
            PDFPageModel firstPage = pages.get(0);

            // Find largest text elements near top of page
            List<TextElement> titleCandidates = firstPage.getTextElements().stream()
                    .filter(el ->
                            el.getY() < firstPage.getHeight() * 0.3 &&  // Top 30% of page
                                    el.getFontSize() > 14 &&  // Larger font
                                    el.getText().length() > 5 &&  // Not too short
                                    el.getText().length() < 100  // Not too long
                    )
                    .sorted(Comparator.comparingDouble(TextElement::getFontSize).reversed())
                    .collect(Collectors.toList());

            return !titleCandidates.isEmpty() ?
                    titleCandidates.get(0).getText() :
                    "Untitled Document";
        }

        return "Untitled Document";
    }

    /**
     * Extract full text from a document segment
     */
    private String extractFullText(
            SmartDocumentMatcher.DocumentSegment segment,
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
     */
    private List<String> extractKeywords(
            SmartDocumentMatcher.DocumentSegment segment,
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
     */
    private List<float[]> extractPageDimensions(
            SmartDocumentMatcher.DocumentSegment segment,
            PDFDocumentModel document) {

        return document.getPages().stream()
                .skip(segment.getStartPage())
                .limit(segment.getEndPage() - segment.getStartPage() + 1)
                .map(page -> new float[]{page.getWidth(), page.getHeight()})
                .collect(Collectors.toList());
    }

    /**
     * Count images in a document segment
     */
    private int countImages(
            SmartDocumentMatcher.DocumentSegment segment,
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

    public void setMinDocumentPages(int minPages) {
        this.minDocumentPages = minPages;
    }

    public void setUseAdvancedMatching(boolean useAdvanced) {
        this.useAdvancedMatching = useAdvanced;
    }
}