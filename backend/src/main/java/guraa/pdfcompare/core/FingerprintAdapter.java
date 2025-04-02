package guraa.pdfcompare.service;

import guraa.pdfcompare.core.PageFingerprint;
import guraa.pdfcompare.core.PDFPageModel;
import java.util.*;

/**
 * Adapter for creating and managing PageFingerprints for enhanced matching
 */
public class FingerprintAdapter {

    /**
     * Create a fingerprint for a page with enhanced matching features
     *
     * @param page The PDF page model
     * @param sourceType Source type ("base" or "compare")
     * @param pageIndex Page index
     * @return Page fingerprint with enhanced features
     */
    public static PageFingerprint createEnhancedFingerprint(PDFPageModel page, String sourceType, int pageIndex) {
        PageFingerprint fingerprint = new PageFingerprint();

        // Set basic properties that exist in core PageFingerprint
        fingerprint.setName(sourceType + "-page-" + pageIndex);
        fingerprint.setFamily(sourceType);

        // Store page index and text in additional data
        Map<String, Object> additionalData = new HashMap<>();
        additionalData.put("pageIndex", pageIndex);
        additionalData.put("sourceType", sourceType);

        // Extract text features
        String text = page.getText() != null ? page.getText() : "";
        additionalData.put("text", text);
        additionalData.put("textHash", text.hashCode());

        // Extract significant words
        Set<String> significantWords = extractKeywords(text);
        additionalData.put("significantWords", significantWords);

        // Extract font information if available
        if (page.getTextElements() != null && !page.getTextElements().isEmpty()) {
            Map<String, Integer> fontDistribution = new HashMap<>();
            List<Double> textPositions = new ArrayList<>();

            for (guraa.pdfcompare.core.TextElement element : page.getTextElements()) {
                if (element.getFontName() != null) {
                    fontDistribution.merge(element.getFontName(), 1, Integer::sum);
                }

                if (element.getY() > 0 && element.getText() != null && !element.getText().trim().isEmpty()) {
                    textPositions.add((double)element.getY());
                }
            }

            additionalData.put("fontDistribution", fontDistribution);
            additionalData.put("elementCount", page.getTextElements().size());

            // Sort text positions and store them
            if (!textPositions.isEmpty()) {
                Collections.sort(textPositions);
                additionalData.put("textPositions", textPositions);
            }
        }

        // Store image information
        if (page.getImages() != null && !page.getImages().isEmpty()) {
            additionalData.put("hasImages", true);
            additionalData.put("imageCount", page.getImages().size());
        } else {
            additionalData.put("hasImages", false);
            additionalData.put("imageCount", 0);
        }

        // Store dimensions
        additionalData.put("width", page.getWidth());
        additionalData.put("height", page.getHeight());

        // Store the page itself for direct access
        additionalData.put("page", page);

        // Set the additional data
        fingerprint.setData(additionalData);

        return fingerprint;
    }

    /**
     * Get a value from the fingerprint's additional data
     *
     * @param fingerprint The page fingerprint
     * @param key The key to retrieve
     * @param defaultValue The default value if key is not found
     * @return The value or default if not found
     */
    @SuppressWarnings("unchecked")
    public static <T> T getValue(PageFingerprint fingerprint, String key, T defaultValue) {
        if (fingerprint == null || fingerprint.getData() == null) {
            return defaultValue;
        }

        Object value = fingerprint.getData().get(key);
        if (value == null) {
            return defaultValue;
        }

        try {
            return (T) value;
        } catch (ClassCastException e) {
            return defaultValue;
        }
    }

    /**
     * Get the page index from a fingerprint
     *
     * @param fingerprint The page fingerprint
     * @return The page index or -1 if not found
     */
    public static int getPageIndex(PageFingerprint fingerprint) {
        return getValue(fingerprint, "pageIndex", -1);
    }

    /**
     * Get the source type from a fingerprint
     *
     * @param fingerprint The page fingerprint
     * @return The source type or empty string if not found
     */
    public static String getSourceType(PageFingerprint fingerprint) {
        return getValue(fingerprint, "sourceType", "");
    }

    /**
     * Get the text from a fingerprint
     *
     * @param fingerprint The page fingerprint
     * @return The text or empty string if not found
     */
    public static String getText(PageFingerprint fingerprint) {
        return getValue(fingerprint, "text", "");
    }

    /**
     * Get the text hash from a fingerprint
     *
     * @param fingerprint The page fingerprint
     * @return The text hash or 0 if not found
     */
    public static int getTextHash(PageFingerprint fingerprint) {
        return getValue(fingerprint, "textHash", 0);
    }

    /**
     * Get the significant words from a fingerprint
     *
     * @param fingerprint The page fingerprint
     * @return The significant words or empty set if not found
     */
    @SuppressWarnings("unchecked")
    public static Set<String> getSignificantWords(PageFingerprint fingerprint) {
        return getValue(fingerprint, "significantWords", Collections.emptySet());
    }

    /**
     * Get the font distribution from a fingerprint
     *
     * @param fingerprint The page fingerprint
     * @return The font distribution or empty map if not found
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Integer> getFontDistribution(PageFingerprint fingerprint) {
        return getValue(fingerprint, "fontDistribution", Collections.emptyMap());
    }

    /**
     * Get the element count from a fingerprint
     *
     * @param fingerprint The page fingerprint
     * @return The element count or 0 if not found
     */
    public static int getElementCount(PageFingerprint fingerprint) {
        return getValue(fingerprint, "elementCount", 0);
    }

    /**
     * Get the text positions from a fingerprint
     *
     * @param fingerprint The page fingerprint
     * @return The text positions or empty list if not found
     */
    @SuppressWarnings("unchecked")
    public static List<Double> getTextPositions(PageFingerprint fingerprint) {
        return getValue(fingerprint, "textPositions", Collections.emptyList());
    }

    /**
     * Check if a fingerprint has images
     *
     * @param fingerprint The page fingerprint
     * @return True if the page has images
     */
    public static boolean hasImages(PageFingerprint fingerprint) {
        return getValue(fingerprint, "hasImages", false);
    }

    /**
     * Get the image count from a fingerprint
     *
     * @param fingerprint The page fingerprint
     * @return The image count or 0 if not found
     */
    public static int getImageCount(PageFingerprint fingerprint) {
        return getValue(fingerprint, "imageCount", 0);
    }

    /**
     * Get the page width from a fingerprint
     *
     * @param fingerprint The page fingerprint
     * @return The width or 0 if not found
     */
    public static float getWidth(PageFingerprint fingerprint) {
        return getValue(fingerprint, "width", 0.0f);
    }

    /**
     * Get the page height from a fingerprint
     *
     * @param fingerprint The page fingerprint
     * @return The height or 0 if not found
     */
    public static float getHeight(PageFingerprint fingerprint) {
        return getValue(fingerprint, "height", 0.0f);
    }

    /**
     * Get the page model from a fingerprint
     *
     * @param fingerprint The page fingerprint
     * @return The page model or null if not found
     */
    public static PDFPageModel getPage(PageFingerprint fingerprint) {
        return getValue(fingerprint, "page", null);
    }

    /**
     * Extract significant words (keywords) from text
     */
    private static Set<String> extractKeywords(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptySet();
        }

        // List of common stop words to filter out
        Set<String> stopWords = new HashSet<>(Arrays.asList(
                "a", "an", "and", "are", "as", "at", "be", "but", "by", "for", "if", "in", "into",
                "is", "it", "no", "not", "of", "on", "or", "such", "that", "the", "their", "then",
                "there", "these", "they", "this", "to", "was", "will", "with"
        ));

        // Extract words, convert to lowercase, and filter stop words
        Set<String> keywords = new HashSet<>();
        String[] words = text.toLowerCase().split("\\s+");

        for (String word : words) {
            if (word.length() > 2 && !stopWords.contains(word)) {
                keywords.add(word);
            }
        }

        return keywords;
    }
}