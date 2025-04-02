package guraa.pdfcompare.comparison;

import guraa.pdfcompare.service.PagePair;

import java.util.List;

/**
 * Represents the result of comparing two pages.
 * This is a service-level class that wraps the core PageComparisonResult.
 */
public class PageComparisonResult {
    private PagePair pagePair;
    private String changeType;  // "IDENTICAL", "MODIFIED", "ADDITION", "DELETION"
    private boolean hasDifferences;
    private int totalDifferences;
    private String error;

    // References to the core result components
    private TextComparisonResult textDifferences;
    private List<TextElementDifference> textElementDifferences;
    private List<ImageDifference> imageDifferences;
    private List<FontDifference> fontDifferences;

    // Additional metadata
    private float[] baseDimensions;
    private float[] compareDimensions;
    private boolean dimensionsDifferent;

    /**
     * Default constructor
     */
    public PageComparisonResult() {
    }

    /**
     * Constructor with page pair
     * @param pagePair The page pair being compared
     */
    public PageComparisonResult(PagePair pagePair) {
        this.pagePair = pagePair;
    }

    /**
     * Check if this represents a matched page pair
     * @return true if the page pair is matched
     */
    public boolean isMatched() {
        return pagePair != null && pagePair.isMatched();
    }

    /**
     * Get the page pair
     * @return The page pair
     */
    public PagePair getPagePair() {
        return pagePair;
    }

    /**
     * Set the page pair
     * @param pagePair The page pair
     */
    public void setPagePair(PagePair pagePair) {
        this.pagePair = pagePair;
    }

    /**
     * Get the change type
     * @return The change type
     */
    public String getChangeType() {
        return changeType;
    }

    /**
     * Set the change type
     * @param changeType The change type
     */
    public void setChangeType(String changeType) {
        this.changeType = changeType;
    }

    /**
     * Check if the page has differences
     * @return true if there are differences
     */
    public boolean isHasDifferences() {
        return hasDifferences;
    }

    /**
     * Set whether the page has differences
     * @param hasDifferences Whether the page has differences
     */
    public void setHasDifferences(boolean hasDifferences) {
        this.hasDifferences = hasDifferences;
    }

    /**
     * Get the total number of differences
     * @return Total differences
     */
    public int getTotalDifferences() {
        return totalDifferences;
    }

    /**
     * Set the total number of differences
     * @param totalDifferences Total differences
     */
    public void setTotalDifferences(int totalDifferences) {
        this.totalDifferences = totalDifferences;
    }

    /**
     * Get the error message
     * @return Error message
     */
    public String getError() {
        return error;
    }

    /**
     * Set the error message
     * @param error Error message
     */
    public void setError(String error) {
        this.error = error;
    }

    /**
     * Check if there's an error
     * @return true if there's an error
     */
    public boolean hasError() {
        return error != null && !error.isEmpty();
    }

    /**
     * Get the text differences
     * @return Text differences
     */
    public TextComparisonResult getTextDifferences() {
        return textDifferences;
    }

    /**
     * Set the text differences
     * @param textDifferences Text differences
     */
    public void setTextDifferences(TextComparisonResult textDifferences) {
        this.textDifferences = textDifferences;
    }

    /**
     * Get the text element differences
     * @return Text element differences
     */
    public List<TextElementDifference> getTextElementDifferences() {
        return textElementDifferences;
    }

    /**
     * Set the text element differences
     * @param textElementDifferences Text element differences
     */
    public void setTextElementDifferences(List<TextElementDifference> textElementDifferences) {
        this.textElementDifferences = textElementDifferences;
    }

    /**
     * Get the image differences
     * @return Image differences
     */
    public List<ImageDifference> getImageDifferences() {
        return imageDifferences;
    }

    /**
     * Set the image differences
     * @param imageDifferences Image differences
     */
    public void setImageDifferences(List<ImageDifference> imageDifferences) {
        this.imageDifferences = imageDifferences;
    }

    /**
     * Get the font differences
     * @return Font differences
     */
    public List<FontDifference> getFontDifferences() {
        return fontDifferences;
    }

    /**
     * Set the font differences
     * @param fontDifferences Font differences
     */
    public void setFontDifferences(List<FontDifference> fontDifferences) {
        this.fontDifferences = fontDifferences;
    }

    /**
     * Get the base dimensions
     * @return Base dimensions
     */
    public float[] getBaseDimensions() {
        return baseDimensions;
    }

    /**
     * Set the base dimensions
     * @param baseDimensions Base dimensions
     */
    public void setBaseDimensions(float[] baseDimensions) {
        this.baseDimensions = baseDimensions;
    }

    /**
     * Get the compare dimensions
     * @return Compare dimensions
     */
    public float[] getCompareDimensions() {
        return compareDimensions;
    }

    /**
     * Set the compare dimensions
     * @param compareDimensions Compare dimensions
     */
    public void setCompareDimensions(float[] compareDimensions) {
        this.compareDimensions = compareDimensions;
    }

    /**
     * Check if dimensions are different
     * @return true if dimensions are different
     */
    public boolean isDimensionsDifferent() {
        return dimensionsDifferent;
    }

    /**
     * Set whether dimensions are different
     * @param dimensionsDifferent Whether dimensions are different
     */
    public void setDimensionsDifferent(boolean dimensionsDifferent) {
        this.dimensionsDifferent = dimensionsDifferent;
    }

    /**
     * Check if the result indicates an addition
     * @return true if the result is an addition
     */
    public boolean isAddition() {
        return "ADDITION".equals(changeType);
    }

    /**
     * Check if the result indicates a deletion
     * @return true if the result is a deletion
     */
    public boolean isDeletion() {
        return "DELETION".equals(changeType);
    }

    /**
     * Check if the result indicates a modification
     * @return true if the result is a modification
     */
    public boolean isModification() {
        return "MODIFIED".equals(changeType);
    }

    /**
     * Check if the result indicates identical pages
     * @return true if the pages are identical
     */
    public boolean isIdentical() {
        return "IDENTICAL".equals(changeType);
    }

    /**
     * Get a summary of the comparison
     * @return Summary string
     */
    public String getSummary() {
        if (hasError()) {
            return "Error: " + error;
        }

        StringBuilder summary = new StringBuilder();

        switch (changeType) {
            case "ADDITION":
                summary.append("Page added in compare document");
                break;
            case "DELETION":
                summary.append("Page deleted from base document");
                break;
            case "MODIFIED":
                summary.append("Page modified with ").append(totalDifferences).append(" differences");
                break;
            case "IDENTICAL":
                summary.append("Pages are identical");
                break;
            default:
                summary.append("Unknown change type");
        }

        return summary.toString();
    }

    @Override
    public String toString() {
        return "PageComparisonResult{" +
                "changeType='" + changeType + '\'' +
                ", hasDifferences=" + hasDifferences +
                ", totalDifferences=" + totalDifferences +
                ", summary='" + getSummary() + '\'' +
                '}';
    }
}