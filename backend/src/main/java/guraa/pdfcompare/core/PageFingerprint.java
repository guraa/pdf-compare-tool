package guraa.pdfcompare.core;

import java.util.*;

/**
 * Represents a fingerprint of a PDF page, containing various features that can be used
 * for matching and comparison purposes.
 */
public class PageFingerprint {
    private String sourceType;  // "base" or "compare"
    private int pageIndex;      // 0-based page index in the source document
    private PDFPageModel page;  // Reference to the original page model

    // Text content features
    private String text;                // Full normalized text content
    private int textHash;               // Hash of text for quick exact matches
    private Set<String> significantWords = new HashSet<>();  // Important keywords

    // Structural features
    private Map<String, Integer> fontDistribution = new HashMap<>();  // Font usage
    private int elementCount;           // Number of text elements
    private List<Double> textPositionDistribution;  // Y-positions of text elements

    // Visual features
    private boolean hasImages;          // Whether the page has images
    private int imageCount;             // Number of images on page
    private Map<String, Object> imageFeatures = new HashMap<>();  // Additional image info

    // Layout features
    private float width;                // Page width
    private float height;               // Page height
    private float contentArea;          // Area covered by content elements



    private String Name;
    private String Family;


    private Map<String, Object> Data;
    // Additional features that might be useful for matching
    private Map<String, Object> additionalFeatures = new HashMap<>();



    /**
     * Constructor with source type and page index
     * @param sourceType Source type (base or compare)
     * @param pageIndex Page index
     */
    public PageFingerprint(String sourceType, int pageIndex) {
        this.sourceType = sourceType;
        this.pageIndex = pageIndex;
    }

    // Getters and setters

    public Map<String, Object> getData() {
        return Data;
    }
    public void setData(Map<String, Object> data) {
        Data = data;
    }

    public String getName() {
        return Name;
    }

    public void setName(String name) {
        Name = name;
    }

    public String getFamily() {
        return Family;
    }

    public void setFamily(String family) {
        Family = family;
    }



    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public int getPageIndex() {
        return pageIndex;
    }

    public void setPageIndex(int pageIndex) {
        this.pageIndex = pageIndex;
    }

    public PDFPageModel getPage() {
        return page;
    }

    public void setPage(PDFPageModel page) {
        this.page = page;

        // Auto-extract basic dimensions if page is set
        if (page != null) {
            this.width = page.getWidth();
            this.height = page.getHeight();
        }
    }

    public String getText() {
        return text != null ? text : "";
    }

    public void setText(String text) {
        this.text = text;
    }

    public int getTextHash() {
        return textHash;
    }

    public void setTextHash(int textHash) {
        this.textHash = textHash;
    }

    public Set<String> getSignificantWords() {
        return significantWords;
    }

    public void setSignificantWords(Set<String> significantWords) {
        this.significantWords = significantWords != null ? significantWords : new HashSet<>();
    }

    public Map<String, Integer> getFontDistribution() {
        return fontDistribution;
    }

    public void setFontDistribution(Map<String, Integer> fontDistribution) {
        this.fontDistribution = fontDistribution != null ? fontDistribution : new HashMap<>();
    }

    public int getElementCount() {
        return elementCount;
    }

    public void setElementCount(int elementCount) {
        this.elementCount = elementCount;
    }

    public List<Double> getTextPositionDistribution() {
        return textPositionDistribution;
    }

    public void setTextPositionDistribution(List<Double> textPositionDistribution) {
        this.textPositionDistribution = textPositionDistribution;
    }

    public boolean isHasImages() {
        return hasImages;
    }

    public void setHasImages(boolean hasImages) {
        this.hasImages = hasImages;
    }

    public int getImageCount() {
        return imageCount;
    }

    public void setImageCount(int imageCount) {
        this.imageCount = imageCount;
    }

    public Map<String, Object> getImageFeatures() {
        return imageFeatures;
    }

    public void setImageFeatures(Map<String, Object> imageFeatures) {
        this.imageFeatures = imageFeatures != null ? imageFeatures : new HashMap<>();
    }

    public float getWidth() {
        return width;
    }

    public void setWidth(float width) {
        this.width = width;
    }

    public float getHeight() {
        return height;
    }

    public void setHeight(float height) {
        this.height = height;
    }

    public float getContentArea() {
        return contentArea;
    }

    public void setContentArea(float contentArea) {
        this.contentArea = contentArea;
    }

    public Map<String, Object> getAdditionalFeatures() {
        return additionalFeatures;
    }

    public void setAdditionalFeatures(Map<String, Object> additionalFeatures) {
        this.additionalFeatures = additionalFeatures != null ? additionalFeatures : new HashMap<>();
    }

    /**
     * Add an additional feature
     * @param key Feature key
     * @param value Feature value
     */
    public void addFeature(String key, Object value) {
        if (additionalFeatures == null) {
            additionalFeatures = new HashMap<>();
        }
        additionalFeatures.put(key, value);
    }

    /**
     * Get an additional feature
     * @param key Feature key
     * @return Feature value or null if not found
     */
    public Object getFeature(String key) {
        return additionalFeatures != null ? additionalFeatures.get(key) : null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("PageFingerprint{")
                .append("source=").append(sourceType)
                .append(", pageIndex=").append(pageIndex)
                .append(", words=").append(significantWords != null ? significantWords.size() : 0)
                .append(", elements=").append(elementCount)
                .append(", images=").append(imageCount)
                .append(", dimensions=").append(width).append("x").append(height)
                .append("}");
        return sb.toString();
    }
}