package guraa.pdfcompare.service;

/**
 * Represents differences found between two pages in a PDF comparison.
 * This class is used to store information about the differences between pages.
 */
public class PageDifference {
    private int pageNumber;
    private boolean onlyInBase;
    private boolean onlyInCompare;
    private boolean dimensionsDifferent;
    private float[] baseDimensions;
    private float[] compareDimensions;
    private int textDifferenceCount;
    private int imageDifferenceCount;
    private int fontDifferenceCount;
    private int styleDifferenceCount;
    
    public PageDifference() {
    }
    
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
    
    public int getTextDifferenceCount() {
        return textDifferenceCount;
    }
    
    public void setTextDifferenceCount(int textDifferenceCount) {
        this.textDifferenceCount = textDifferenceCount;
    }
    
    public int getImageDifferenceCount() {
        return imageDifferenceCount;
    }
    
    public void setImageDifferenceCount(int imageDifferenceCount) {
        this.imageDifferenceCount = imageDifferenceCount;
    }
    
    public int getFontDifferenceCount() {
        return fontDifferenceCount;
    }
    
    public void setFontDifferenceCount(int fontDifferenceCount) {
        this.fontDifferenceCount = fontDifferenceCount;
    }
    
    public int getStyleDifferenceCount() {
        return styleDifferenceCount;
    }
    
    public void setStyleDifferenceCount(int styleDifferenceCount) {
        this.styleDifferenceCount = styleDifferenceCount;
    }
}
