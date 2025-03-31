package guraa.pdfcompare.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.List;
import java.util.Objects;

/**
 * Advanced document similarity scoring mechanism
 */
public class DocumentSimilarityScorer {
    private static final Logger logger = LoggerFactory.getLogger(DocumentSimilarityScorer.class);

    // Weights for different similarity components
    private static final double TEXT_SIMILARITY_WEIGHT = 0.4;
    private static final double CONTENT_TYPE_WEIGHT = 0.2;
    private static final double LAYOUT_WEIGHT = 0.2;
    private static final double IMAGE_WEIGHT = 0.1;
    private static final double TITLE_WEIGHT = 0.1;

    // Injected components
    private final TextSimilarityCalculator textSimilarityCalculator;
    private final ContentTypeDetector contentTypeDetector;

    public DocumentSimilarityScorer() {
        this.textSimilarityCalculator = new TextSimilarityCalculator();
        this.contentTypeDetector = new ContentTypeDetector();
    }

    /**
     * Calculate comprehensive similarity between two document segments
     *
     * @param baseSegment First document segment
     * @param compareSegment Second document segment to compare against
     * @return Similarity score between 0 and 1
     */
    public double calculateSimilarity(
            SmartDocumentMatcher.DocumentSegment baseSegment,
            SmartDocumentMatcher.DocumentSegment compareSegment) {

        logger.debug("Calculating similarity between segments");

        // Extract features
        Map<String, Object> baseFeatures = baseSegment.getFeatures();
        Map<String, Object> compareFeatures = compareSegment.getFeatures();

        // Calculate individual similarity components
        double textSimilarity = calculateTextSimilarity(baseFeatures, compareFeatures);
        double contentTypeSimilarity = calculateContentTypeSimilarity(baseFeatures, compareFeatures);
        double layoutSimilarity = calculateLayoutSimilarity(baseFeatures, compareFeatures);
        double imageSimilarity = calculateImageSimilarity(baseFeatures, compareFeatures);
        double titleSimilarity = calculateTitleSimilarity(baseSegment, compareSegment);

        // Combine similarities with weighted average
        double overallSimilarity = (
                (textSimilarity * TEXT_SIMILARITY_WEIGHT) +
                        (contentTypeSimilarity * CONTENT_TYPE_WEIGHT) +
                        (layoutSimilarity * LAYOUT_WEIGHT) +
                        (imageSimilarity * IMAGE_WEIGHT) +
                        (titleSimilarity * TITLE_WEIGHT)
        );

        logger.debug("Similarity breakdown:");
        logger.debug("Text Similarity: {}", textSimilarity);
        logger.debug("Content Type Similarity: {}", contentTypeSimilarity);
        logger.debug("Layout Similarity: {}", layoutSimilarity);
        logger.debug("Image Similarity: {}", imageSimilarity);
        logger.debug("Title Similarity: {}", titleSimilarity);
        logger.debug("Overall Similarity: {}", overallSimilarity);

        return overallSimilarity;
    }

    /**
     * Calculate text similarity
     */
    private double calculateTextSimilarity(
            Map<String, Object> baseFeatures,
            Map<String, Object> compareFeatures) {

        String baseText = (String) baseFeatures.getOrDefault("fullText", "");
        String compareText = (String) compareFeatures.getOrDefault("fullText", "");

        return textSimilarityCalculator.calculateTextSimilarity(baseText, compareText);
    }

    /**
     * Calculate content type similarity
     */
    private double calculateContentTypeSimilarity(
            Map<String, Object> baseFeatures,
            Map<String, Object> compareFeatures) {

        String baseContentType = (String) baseFeatures.getOrDefault("contentType", "GENERIC_DOCUMENT");
        String compareContentType = (String) compareFeatures.getOrDefault("contentType", "GENERIC_DOCUMENT");

        // Exact match gives full score, otherwise 0
        return baseContentType.equals(compareContentType) ? 1.0 : 0.0;
    }

    /**
     * Calculate layout similarity based on page dimensions
     */
    private double calculateLayoutSimilarity(
            Map<String, Object> baseFeatures,
            Map<String, Object> compareFeatures) {

        @SuppressWarnings("unchecked")
        List<float[]> basePageDimensions = (List<float[]>) baseFeatures.getOrDefault("pageDimensions", List.of());
        @SuppressWarnings("unchecked")
        List<float[]> comparePageDimensions = (List<float[]>) compareFeatures.getOrDefault("pageDimensions", List.of());

        // If no dimensions, return 0
        if (basePageDimensions.isEmpty() || comparePageDimensions.isEmpty()) {
            return 0.0;
        }

        // Compare first page dimensions
        float[] baseFirstPage = basePageDimensions.get(0);
        float[] compareFirstPage = comparePageDimensions.get(0);

        // Calculate dimension similarity
        double widthSimilarity = 1.0 - Math.abs(baseFirstPage[0] - compareFirstPage[0]) /
                Math.max(baseFirstPage[0], compareFirstPage[0]);
        double heightSimilarity = 1.0 - Math.abs(baseFirstPage[1] - compareFirstPage[1]) /
                Math.max(baseFirstPage[1], compareFirstPage[1]);

        return (widthSimilarity + heightSimilarity) / 2.0;
    }

    /**
     * Calculate image similarity based on image count
     */
    private double calculateImageSimilarity(
            Map<String, Object> baseFeatures,
            Map<String, Object> compareFeatures) {

        Integer baseImageCount = (Integer) baseFeatures.getOrDefault("imageCount", 0);
        Integer compareImageCount = (Integer) compareFeatures.getOrDefault("imageCount", 0);

        // If both have no images, consider them similar
        if (baseImageCount == 0 && compareImageCount == 0) {
            return 1.0;
        }

        // Calculate image count similarity
        return 1.0 - Math.abs(baseImageCount - compareImageCount) /
                (double) Math.max(baseImageCount, compareImageCount);
    }

    /**
     * Calculate title similarity
     */
    private double calculateTitleSimilarity(
            SmartDocumentMatcher.DocumentSegment baseSegment,
            SmartDocumentMatcher.DocumentSegment compareSegment) {

        String baseTitle = baseSegment.getTitle();
        String compareTitle = compareSegment.getTitle();

        // If both titles are null or empty, consider them similar
        if ((baseTitle == null || baseTitle.trim().isEmpty()) &&
                (compareTitle == null || compareTitle.trim().isEmpty())) {
            return 1.0;
        }

        // Use text similarity for title comparison
        return textSimilarityCalculator.calculateTextSimilarity(
                Objects.toString(baseTitle, ""),
                Objects.toString(compareTitle, "")
        );
    }
}