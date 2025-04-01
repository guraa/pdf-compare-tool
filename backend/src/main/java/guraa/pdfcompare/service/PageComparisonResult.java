package guraa.pdfcompare.service;

import guraa.pdfcompare.comparison.*;
import guraa.pdfcompare.core.CustomPageDifference;

import java.util.List;

/**
 * Represents the result of comparing a pair of pages from two PDF documents.
 * Contains information about differences found between the pages, their types,
 * and statistics about the comparison.
 */
public class PageComparisonResult {
    private PagePair pagePair;
    private boolean hasDifferences;
    private int totalDifferences;
    private PDFComparisonResult comparisonResult;
    private PageDifference pageDifference;
    private CustomPageDifference customPageDifference;
    private String changeType;  // "IDENTICAL", "MODIFIED", "ADDITION", "DELETION"
    private String error;
    
    // Fields to support compatibility with comparison.PageComparisonResult
    private int pageNumber;
    private boolean onlyInBase;
    private boolean onlyInCompare;
    private boolean dimensionsDifferent;
    private float[] baseDimensions;
    private float[] compareDimensions;
    private TextComparisonResult textDifferences;
    private List<TextElementDifference> textElementDifferences;
    private List<ImageDifference> imageDifferences;
    private List<FontDifference> fontDifferences;

    /**
     * Default constructor
     */
    public PageComparisonResult() {
    }

    /**
     * Create a result with a page pair
     * @param pagePair The page pair being compared
     */
    public PageComparisonResult(PagePair pagePair) {
        this.pagePair = pagePair;
    }

    /**
     * Create a result for a comparison with differences
     * @param pagePair The page pair being compared
     * @param totalDifferences The number of differences found
     * @param hasDifferences Whether differences were found
     */
    public PageComparisonResult(PagePair pagePair, int totalDifferences, boolean hasDifferences) {
        this.pagePair = pagePair;
        this.totalDifferences = totalDifferences;
        this.hasDifferences = hasDifferences;
    }

    /**
     * Get the page pair this result is for
     * @return The page pair
     */
    public PagePair getPagePair() {
        return pagePair;
    }

    /**
     * Set the page pair this result is for
     * @param pagePair The page pair
     */
    public void setPagePair(PagePair pagePair) {
        this.pagePair = pagePair;
    }

    /**
     * Check if the comparison found differences
     * @return true if differences were found
     */
    public boolean isHasDifferences() {
        return hasDifferences;
    }

    /**
     * Set whether the comparison found differences
     * @param hasDifferences true if differences were found
     */
    public void setHasDifferences(boolean hasDifferences) {
        this.hasDifferences = hasDifferences;
    }

    /**
     * Get the total number of differences found
     * @return Number of differences
     */
    public int getTotalDifferences() {
        return totalDifferences;
    }

    /**
     * Set the total number of differences found
     * @param totalDifferences Number of differences
     */
    public void setTotalDifferences(int totalDifferences) {
        this.totalDifferences = totalDifferences;
    }

    /**
     * Get the full comparison result for the pages
     * @return Comparison result object
     */
    public PDFComparisonResult getComparisonResult() {
        return comparisonResult;
    }

    /**
     * Set the full comparison result for the pages
     * @param comparisonResult Comparison result object
     */
    public void setComparisonResult(PDFComparisonResult comparisonResult) {
        this.comparisonResult = comparisonResult;
        
        // If we have a comparison result with page differences, extract the first one
        if (comparisonResult != null && comparisonResult.getPageDifferences() != null 
                && !comparisonResult.getPageDifferences().isEmpty()) {
            guraa.pdfcompare.comparison.PageComparisonResult pageResult = 
                    comparisonResult.getPageDifferences().get(0);
            
            // Copy properties from the comparison result to this object for compatibility
            this.pageNumber = pageResult.getPageNumber();
            this.onlyInBase = pageResult.isOnlyInBase();
            this.onlyInCompare = pageResult.isOnlyInCompare();
            this.dimensionsDifferent = pageResult.isDimensionsDifferent();
            this.baseDimensions = pageResult.getBaseDimensions();
            this.compareDimensions = pageResult.getCompareDimensions();
            this.textDifferences = pageResult.getTextDifferences();
            this.textElementDifferences = pageResult.getTextElementDifferences();
            this.imageDifferences = pageResult.getImageDifferences();
            this.fontDifferences = pageResult.getFontDifferences();
        }
    }

    /**
     * Get the page difference information
     * @return Page difference object
     */
    public PageDifference getPageDifference() {
        return pageDifference;
    }

    /**
     * Set the page difference information
     * @param pageDifference Page difference object
     */
    public void setPageDifference(PageDifference pageDifference) {
        this.pageDifference = pageDifference;
    }

    /**
     * Get the custom page difference information
     * @return Custom page difference object
     */
    public CustomPageDifference getCustomPageDifference() {
        return customPageDifference;
    }

    /**
     * Set the custom page difference information
     * @param customPageDifference Custom page difference object
     */
    public void setCustomPageDifference(CustomPageDifference customPageDifference) {
        this.customPageDifference = customPageDifference;
    }

    /**
     * Get the type of change between the pages (IDENTICAL, MODIFIED, ADDITION, DELETION)
     * @return Change type
     */
    public String getChangeType() {
        if (changeType != null) {
            return changeType;
        }

        if (!hasDifferences && pagePair.isMatched()) {
            return "IDENTICAL";
        } else if (pagePair.isMatched()) {
            return "MODIFIED";
        } else if (pagePair.getBaseFingerprint() != null) {
            return "DELETION";
        } else {
            return "ADDITION";
        }
    }

    /**
     * Set the type of change between the pages
     * @param changeType Change type (IDENTICAL, MODIFIED, ADDITION, DELETION)
     */
    public void setChangeType(String changeType) {
        this.changeType = changeType;
    }

    /**
     * Get any error that occurred during comparison
     * @return Error message
     */
    public String getError() {
        return error;
    }

    /**
     * Set an error that occurred during comparison
     * @param error Error message
     */
    public void setError(String error) {
        this.error = error;
    }

    /**
     * Check if there was an error during comparison
     * @return true if an error occurred
     */
    public boolean hasError() {
        return error != null && !error.isEmpty();
    }

    /**
     * Check if this page was added in the compare document
     * @return true if this is an added page
     */
    public boolean isAddition() {
        return "ADDITION".equals(getChangeType());
    }

    /**
     * Check if this page was deleted from the base document
     * @return true if this is a deleted page
     */
    public boolean isDeletion() {
        return "DELETION".equals(getChangeType());
    }

    /**
     * Check if this page was modified
     * @return true if this is a modified page
     */
    public boolean isModification() {
        return "MODIFIED".equals(getChangeType());
    }

    /**
     * Check if this page is identical in both documents
     * @return true if this page is identical
     */
    public boolean isIdentical() {
        return "IDENTICAL".equals(getChangeType());
    }

    /**
     * Get a summary of the comparison result
     * @return Summary string
     */
    public String getSummary() {
        if (hasError()) {
            return "Error: " + error;
        }

        StringBuilder summary = new StringBuilder();
        String type = getChangeType();

        switch (type) {
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
    
    // Methods to support compatibility with comparison.PageComparisonResult
    
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

    public TextComparisonResult getTextDifferences() {
        return textDifferences;
    }

    public void setTextDifferences(TextComparisonResult textDifferences) {
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

    @Override
    public String toString() {
        return "PageComparisonResult{" +
                "changeType='" + getChangeType() + '\'' +
                ", hasDifferences=" + hasDifferences +
                ", totalDifferences=" + totalDifferences +
                ", summary='" + getSummary() + '\'' +
                '}';
    }
}
