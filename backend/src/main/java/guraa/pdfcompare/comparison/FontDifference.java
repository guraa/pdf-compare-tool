package guraa.pdfcompare.comparison;

/**
 * Represents a difference in a font between two pages.
 */
public class FontDifference {
    private String baseFontName;
    private String compareFontName;
    private boolean onlyInBase;
    private boolean onlyInCompare;
    private boolean nameChanged;
    private boolean sizeChanged;
    private boolean styleChanged;
    private float baseFontSize;
    private float compareFontSize;
    private String baseStyle;
    private String compareStyle;

    /**
     * Default constructor
     */
    public FontDifference() {
    }

    /**
     * Get the base font name
     * @return Base font name
     */
    public String getBaseFontName() {
        return baseFontName;
    }

    /**
     * Set the base font name
     * @param baseFontName Base font name
     */
    public void setBaseFontName(String baseFontName) {
        this.baseFontName = baseFontName;
    }

    /**
     * Get the compare font name
     * @return Compare font name
     */
    public String getCompareFontName() {
        return compareFontName;
    }

    /**
     * Set the compare font name
     * @param compareFontName Compare font name
     */
    public void setCompareFontName(String compareFontName) {
        this.compareFontName = compareFontName;
    }

    /**
     * Check if the font is only in the base document
     * @return true if the font is only in the base document
     */
    public boolean isOnlyInBase() {
        return onlyInBase;
    }

    /**
     * Set whether the font is only in the base document
     * @param onlyInBase Whether the font is only in the base document
     */
    public void setOnlyInBase(boolean onlyInBase) {
        this.onlyInBase = onlyInBase;
    }

    /**
     * Check if the font is only in the compare document
     * @return true if the font is only in the compare document
     */
    public boolean isOnlyInCompare() {
        return onlyInCompare;
    }

    /**
     * Set whether the font is only in the compare document
     * @param onlyInCompare Whether the font is only in the compare document
     */
    public void setOnlyInCompare(boolean onlyInCompare) {
        this.onlyInCompare = onlyInCompare;
    }

    /**
     * Check if the name has changed
     * @return true if the name has changed
     */
    public boolean isNameChanged() {
        return nameChanged;
    }

    /**
     * Set whether the name has changed
     * @param nameChanged Whether the name has changed
     */
    public void setNameChanged(boolean nameChanged) {
        this.nameChanged = nameChanged;
    }

    /**
     * Check if the size has changed
     * @return true if the size has changed
     */
    public boolean isSizeChanged() {
        return sizeChanged;
    }

    /**
     * Set whether the size has changed
     * @param sizeChanged Whether the size has changed
     */
    public void setSizeChanged(boolean sizeChanged) {
        this.sizeChanged = sizeChanged;
    }

    /**
     * Check if the style has changed
     * @return true if the style has changed
     */
    public boolean isStyleChanged() {
        return styleChanged;
    }

    /**
     * Set whether the style has changed
     * @param styleChanged Whether the style has changed
     */
    public void setStyleChanged(boolean styleChanged) {
        this.styleChanged = styleChanged;
    }
    
    // Font info references
    private guraa.pdfcompare.core.FontInfo baseFont;
    private guraa.pdfcompare.core.FontInfo compareFont;
    private boolean embeddingDifferent;
    private boolean subsetDifferent;
    
    /**
     * Get the base font info
     * @return Base font info
     */
    public guraa.pdfcompare.core.FontInfo getBaseFont() {
        return baseFont;
    }
    
    /**
     * Set the base font info
     * @param baseFont Base font info
     */
    public void setBaseFont(guraa.pdfcompare.core.FontInfo baseFont) {
        this.baseFont = baseFont;
        if (baseFont != null) {
            this.baseFontName = baseFont.getName();
        }
    }
    
    /**
     * Get the compare font info
     * @return Compare font info
     */
    public guraa.pdfcompare.core.FontInfo getCompareFont() {
        return compareFont;
    }
    
    /**
     * Set the compare font info
     * @param compareFont Compare font info
     */
    public void setCompareFont(guraa.pdfcompare.core.FontInfo compareFont) {
        this.compareFont = compareFont;
        if (compareFont != null) {
            this.compareFontName = compareFont.getName();
        }
    }
    
    /**
     * Check if the embedding is different
     * @return true if the embedding is different
     */
    public boolean isEmbeddingDifferent() {
        return embeddingDifferent;
    }
    
    /**
     * Set whether the embedding is different
     * @param embeddingDifferent Whether the embedding is different
     */
    public void setEmbeddingDifferent(boolean embeddingDifferent) {
        this.embeddingDifferent = embeddingDifferent;
    }
    
    /**
     * Check if the subset is different
     * @return true if the subset is different
     */
    public boolean isSubsetDifferent() {
        return subsetDifferent;
    }
    
    /**
     * Set whether the subset is different
     * @param subsetDifferent Whether the subset is different
     */
    public void setSubsetDifferent(boolean subsetDifferent) {
        this.subsetDifferent = subsetDifferent;
    }

    /**
     * Get the base font size
     * @return Base font size
     */
    public float getBaseFontSize() {
        return baseFontSize;
    }

    /**
     * Set the base font size
     * @param baseFontSize Base font size
     */
    public void setBaseFontSize(float baseFontSize) {
        this.baseFontSize = baseFontSize;
    }

    /**
     * Get the compare font size
     * @return Compare font size
     */
    public float getCompareFontSize() {
        return compareFontSize;
    }

    /**
     * Set the compare font size
     * @param compareFontSize Compare font size
     */
    public void setCompareFontSize(float compareFontSize) {
        this.compareFontSize = compareFontSize;
    }

    /**
     * Get the base style
     * @return Base style
     */
    public String getBaseStyle() {
        return baseStyle;
    }

    /**
     * Set the base style
     * @param baseStyle Base style
     */
    public void setBaseStyle(String baseStyle) {
        this.baseStyle = baseStyle;
    }

    /**
     * Get the compare style
     * @return Compare style
     */
    public String getCompareStyle() {
        return compareStyle;
    }

    /**
     * Set the compare style
     * @param compareStyle Compare style
     */
    public void setCompareStyle(String compareStyle) {
        this.compareStyle = compareStyle;
    }
}
