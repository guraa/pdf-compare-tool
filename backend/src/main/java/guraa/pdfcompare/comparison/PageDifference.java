package guraa.pdfcompare.comparison;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents the differences between pages in a PDF comparison
 */
public class PageDifference {
    private int pageNumber;
    private boolean onlyInBase;
    private boolean onlyInCompare;
    private boolean dimensionsDifferent;
    private float[] baseDimensions;
    private float[] compareDimensions;
    private List<TextDifferenceItem> textDifferences;
    private List<TextElementDifference> textElementDifferences;
    private List<ImageDifference> imageDifferences;
    private List<FontDifference> fontDifferences;

    public PageDifference() {
        textDifferences = new ArrayList<>();
        textElementDifferences = new ArrayList<>();
        imageDifferences = new ArrayList<>();
        fontDifferences = new ArrayList<>();
    }

    /**
     * Extract page differences as a list of items for API response
     * @param isBase Whether to extract differences from base or comparison document
     * @return List of difference items
     */
    public List<Map<String, Object>> extractPageDifferences(boolean isBase) {
        List<Map<String, Object>> differences = new ArrayList<>();

        // If page exists only in one document
        if (onlyInBase && !isBase) {
            Map<String, Object> diff = new HashMap<>();
            diff.put("id", "page-" + pageNumber + "-missing");
            diff.put("type", "structure");
            diff.put("description", "Page only exists in base document");
            diff.put("severity", "critical");
            diff.put("changeType", "deleted");
            differences.add(diff);
            return differences;
        }

        if (onlyInCompare && isBase) {
            Map<String, Object> diff = new HashMap<>();
            diff.put("id", "page-" + pageNumber + "-missing");
            diff.put("type", "structure");
            diff.put("description", "Page only exists in comparison document");
            diff.put("severity", "critical");
            diff.put("changeType", "added");
            differences.add(diff);
            return differences;
        }

        // Add other differences processing as needed
        // This is a simplified version

        return differences;
    }

    // Getters and setters
    public int getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(int pageNumber) {
        this.pageNumber = pageNumber;
    }

    public boolean isOnlyInBase() {
        return onlyInBase;
    }

    public void setOnlyInBase(boolean onlyInBase) {
        this.onlyInBase = onlyInBase;
    }

    public boolean isOnlyInCompare() {
        return onlyInCompare;
    }

    public void setOnlyInCompare(boolean onlyInCompare) {
        this.onlyInCompare = onlyInCompare;
    }

    public boolean isDimensionsDifferent() {
        return dimensionsDifferent;
    }

    public void setDimensionsDifferent(boolean dimensionsDifferent) {
        this.dimensionsDifferent = dimensionsDifferent;
    }

    public float[] getBaseDimensions() {
        return baseDimensions;
    }

    public void setBaseDimensions(float[] baseDimensions) {
        this.baseDimensions = baseDimensions;
    }

    public float[] getCompareDimensions() {
        return compareDimensions;
    }

    public void setCompareDimensions(float[] compareDimensions) {
        this.compareDimensions = compareDimensions;
    }

    public List<TextDifferenceItem> getTextDifferences() {
        return textDifferences;
    }

    public void setTextDifferences(List<TextDifferenceItem> textDifferences) {
        this.textDifferences = textDifferences;
    }

    public List<TextElementDifference> getTextElementDifferences() {
        return textElementDifferences;
    }

    public void setTextElementDifferences(List<TextElementDifference> textElementDifferences) {
        this.textElementDifferences = textElementDifferences;
    }

    public List<ImageDifference> getImageDifferences() {
        return imageDifferences;
    }

    public void setImageDifferences(List<ImageDifference> imageDifferences) {
        this.imageDifferences = imageDifferences;
    }

    public List<FontDifference> getFontDifferences() {
        return fontDifferences;
    }

    public void setFontDifferences(List<FontDifference> fontDifferences) {
        this.fontDifferences = fontDifferences;
    }
}