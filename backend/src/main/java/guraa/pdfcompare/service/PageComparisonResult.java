package guraa.pdfcompare.service;

import guraa.pdfcompare.comparison.TextComparisonResult;
import guraa.pdfcompare.comparison.TextElementDifference;
import guraa.pdfcompare.comparison.ImageDifference;
import guraa.pdfcompare.comparison.FontDifference;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the result of comparing two pages.
 * This is a service-level class that wraps the core comparison result.
 */
public class PageComparisonResult {
    private PagePair pagePair;
    private String changeType;  // "IDENTICAL", "MODIFIED", "ADDITION", "DELETION"
    private boolean hasDifferences;
    private int totalDifferences;
    private String error;
    private CustomPageDifference customPageDifference;

    // References to the core result components
    private TextComparisonResult textDifferences;
    private List<TextElementDifference> textElementDifferences;
    private List<ImageDifference> imageDifferences;
    private List<FontDifference> fontDifferences;

    // Additional metadata
    private float[] baseDimensions;
    private float[] compareDimensions;
    private boolean dimensionsDifferent;
    private boolean onlyInCompare;
    private boolean onlyInBase;
    private float pageNumber;
    private int originalBasePageNumber;
    private int originalComparePageNumber;
    private int basePageIndex;
    private int comparePageIndex;
    private PageDifference pageDifference;

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
     * Get the page number
     * @return Page number
     */
    public float getPageNumber() {
        return pageNumber;
    }

    /**
     * Set the page number
     * @param pageNumber Page number
     */
    public void setPageNumber(float pageNumber) {
        this.pageNumber = pageNumber;
    }
    
    /**
     * Get the base page index
     * @return Base page index
     */
    public int getBasePageIndex() {
        return basePageIndex;
    }
    
    /**
     * Set the base page index
     * @param basePageIndex Base page index
     */
    public void setBasePageIndex(int basePageIndex) {
        this.basePageIndex = basePageIndex;
    }
    
    /**
     * Get the compare page index
     * @return Compare page index
     */
    public int getComparePageIndex() {
        return comparePageIndex;
    }

    /**
     * Set the compare page index
     * @param comparePageIndex Compare page index
     */
    public void setComparePageIndex(int comparePageIndex) {
        this.comparePageIndex = comparePageIndex;
    }
    
    /**
     * Get the original base page number
     * @return Original base page number
     */
    public int getOriginalBasePageNumber() {
        return originalBasePageNumber;
    }
    
    /**
     * Set the original base page number
     * @param originalBasePageNumber Original base page number
     */
    public void setOriginalBasePageNumber(int originalBasePageNumber) {
        this.originalBasePageNumber = originalBasePageNumber;
    }
    
    /**
     * Get the original compare page number
     * @return Original compare page number
     */
    public int getOriginalComparePageNumber() {
        return originalComparePageNumber;
    }
    
    /**
     * Set the original compare page number
     * @param originalComparePageNumber Original compare page number
     */
    public void setOriginalComparePageNumber(int originalComparePageNumber) {
        this.originalComparePageNumber = originalComparePageNumber;
    }

    /**
     * Check if the page is only in the base document
     * @return true if the page is only in the base document
     */
    public boolean isOnlyInBase() {
        return onlyInBase;
    }

    /**
     * Set whether the page is only in the base document
     * @param onlyInBase Whether the page is only in the base document
     */
    public void setOnlyInBase(boolean onlyInBase) {
        this.onlyInBase = onlyInBase;
    }

    /**
     * Check if the page is only in the compare document
     * @return true if the page is only in the compare document
     */
    public boolean isOnlyInCompare() {
        return onlyInCompare;
    }

    /**
     * Set whether the page is only in the compare document
     * @param onlyInCompare Whether the page is only in the compare document
     */
    public void setOnlyInCompare(boolean onlyInCompare) {
        this.onlyInCompare = onlyInCompare;
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
     * Get the custom page difference
     * @return Custom page difference
     */
    public CustomPageDifference getCustomPageDifference() {
        return customPageDifference;
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

        if (changeType == null) {
            return "Unknown change type (null)";
        }

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

    /**
     * Get the page difference
     * @return Page difference
     */
    public PageDifference getPageDifference() {
        return pageDifference;
    }
    
    /**
     * Set the page difference
     * @param pageDifference Page difference
     */
    public void setPageDifference(PageDifference pageDifference) {
        this.pageDifference = pageDifference;
    }
    
    /**
     * Set the custom page difference
     * @param customPageDifference Custom page difference
     */
    public void setCustomPageDifference(CustomPageDifference customPageDifference) {
        this.customPageDifference = customPageDifference;
        
        // Create a standard page difference from the custom one
        if (customPageDifference != null) {
            PageDifference difference = new PageDifference();
            difference.setPageNumber(customPageDifference.getPageNumber());
            difference.setOnlyInBase(customPageDifference.isBasePageExists() && !customPageDifference.isComparePageExists());
            difference.setOnlyInCompare(customPageDifference.isComparePageExists() && !customPageDifference.isBasePageExists());
            this.pageDifference = difference;
        }
    }
    
    /**
     * Extract page differences
     * @param detailed Whether to include detailed differences
     * @return List of page differences
     */
    public List<PageDifference> extractPageDifferences(boolean detailed) {
        List<PageDifference> differences = new ArrayList<>();
        
        if (pageDifference != null) {
            differences.add(pageDifference);
        } else {
            // Create a new page difference from the comparison result
            PageDifference difference = new PageDifference();
            difference.setPageNumber((int)pageNumber);
            difference.setOnlyInBase(onlyInBase);
            difference.setOnlyInCompare(onlyInCompare);
            difference.setDimensionsDifferent(dimensionsDifferent);
            difference.setBaseDimensions(baseDimensions);
            difference.setCompareDimensions(compareDimensions);
            
            // Count differences by type
            if (textDifferences != null && textDifferences.getDifferences() != null) {
                difference.setTextDifferenceCount(textDifferences.getDifferences().size());
            }
            
            if (imageDifferences != null) {
                difference.setImageDifferenceCount(imageDifferences.size());
            }
            
            if (fontDifferences != null) {
                difference.setFontDifferenceCount(fontDifferences.size());
            }
            
            if (textElementDifferences != null) {
                int styleDiffs = 0;
                for (TextElementDifference diff : textElementDifferences) {
                    if (diff.isStyleDifferent()) {
                        styleDiffs++;
                    }
                }
                difference.setStyleDifferenceCount(styleDiffs);
            }
            
            differences.add(difference);
        }
        
        return differences;
    }

    @Override
    public String toString() {
        String summaryText;
        try {
            summaryText = getSummary();
        } catch (Exception e) {
            summaryText = "Error generating summary: " + e.getMessage();
        }
        
        return "PageComparisonResult{" +
                "changeType='" + (changeType != null ? changeType : "null") + '\'' +
                ", hasDifferences=" + hasDifferences +
                ", totalDifferences=" + totalDifferences +
                ", summary='" + summaryText + '\'' +
                '}';
    }
}
