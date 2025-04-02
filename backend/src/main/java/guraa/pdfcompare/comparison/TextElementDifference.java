package guraa.pdfcompare.comparison;

/**
 * Represents a difference in a text element between two pages.
 */
public class TextElementDifference {
    private String baseText;
    private String compareText;
    private float[] basePosition;
    private float[] comparePosition;
    private String baseFontName;
    private String compareFontName;
    private float baseFontSize;
    private float compareFontSize;
    private boolean contentDifferent;
    private boolean positionDifferent;
    private boolean styleDifferent;
    private boolean onlyInBase;
    private boolean onlyInCompare;
    private guraa.pdfcompare.core.TextElement baseElement;
    private guraa.pdfcompare.core.TextElement compareElement;

    /**
     * Default constructor
     */
    public TextElementDifference() {
    }

    /**
     * Get the base text
     * @return Base text
     */
    public String getBaseText() {
        return baseText;
    }

    /**
     * Set the base text
     * @param baseText Base text
     */
    public void setBaseText(String baseText) {
        this.baseText = baseText;
    }

    /**
     * Get the compare text
     * @return Compare text
     */
    public String getCompareText() {
        return compareText;
    }

    /**
     * Set the compare text
     * @param compareText Compare text
     */
    public void setCompareText(String compareText) {
        this.compareText = compareText;
    }

    /**
     * Get the base position
     * @return Base position
     */
    public float[] getBasePosition() {
        return basePosition;
    }

    /**
     * Set the base position
     * @param basePosition Base position
     */
    public void setBasePosition(float[] basePosition) {
        this.basePosition = basePosition;
    }

    /**
     * Get the compare position
     * @return Compare position
     */
    public float[] getComparePosition() {
        return comparePosition;
    }

    /**
     * Set the compare position
     * @param comparePosition Compare position
     */
    public void setComparePosition(float[] comparePosition) {
        this.comparePosition = comparePosition;
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
     * Check if the content is different
     * @return true if the content is different
     */
    public boolean isContentDifferent() {
        return contentDifferent;
    }

    /**
     * Set whether the content is different
     * @param contentDifferent Whether the content is different
     */
    public void setContentDifferent(boolean contentDifferent) {
        this.contentDifferent = contentDifferent;
    }

    /**
     * Check if the position is different
     * @return true if the position is different
     */
    public boolean isPositionDifferent() {
        return positionDifferent;
    }

    /**
     * Set whether the position is different
     * @param positionDifferent Whether the position is different
     */
    public void setPositionDifferent(boolean positionDifferent) {
        this.positionDifferent = positionDifferent;
    }

    /**
     * Check if the style is different
     * @return true if the style is different
     */
    public boolean isStyleDifferent() {
        return styleDifferent;
    }

    /**
     * Set whether the style is different
     * @param styleDifferent Whether the style is different
     */
    public void setStyleDifferent(boolean styleDifferent) {
        this.styleDifferent = styleDifferent;
    }
    
    /**
     * Check if the element is only in the base document
     * @return true if the element is only in the base document
     */
    public boolean isOnlyInBase() {
        return onlyInBase;
    }
    
    /**
     * Set whether the element is only in the base document
     * @param onlyInBase Whether the element is only in the base document
     */
    public void setOnlyInBase(boolean onlyInBase) {
        this.onlyInBase = onlyInBase;
    }
    
    /**
     * Check if the element is only in the compare document
     * @return true if the element is only in the compare document
     */
    public boolean isOnlyInCompare() {
        return onlyInCompare;
    }
    
    /**
     * Set whether the element is only in the compare document
     * @param onlyInCompare Whether the element is only in the compare document
     */
    public void setOnlyInCompare(boolean onlyInCompare) {
        this.onlyInCompare = onlyInCompare;
    }
    
    /**
     * Get the base text element
     * @return Base text element
     */
    public guraa.pdfcompare.core.TextElement getBaseElement() {
        return baseElement;
    }
    
    /**
     * Set the base text element
     * @param baseElement Base text element
     */
    public void setBaseElement(guraa.pdfcompare.core.TextElement baseElement) {
        this.baseElement = baseElement;
        if (baseElement != null) {
            this.baseText = baseElement.getText();
            this.baseFontName = baseElement.getFontName();
            this.baseFontSize = baseElement.getFontSize();
            this.basePosition = new float[] { baseElement.getX(), baseElement.getY() };
        }
    }
    
    /**
     * Get the compare text element
     * @return Compare text element
     */
    public guraa.pdfcompare.core.TextElement getCompareElement() {
        return compareElement;
    }
    
    /**
     * Set the compare text element
     * @param compareElement Compare text element
     */
    public void setCompareElement(guraa.pdfcompare.core.TextElement compareElement) {
        this.compareElement = compareElement;
        if (compareElement != null) {
            this.compareText = compareElement.getText();
            this.compareFontName = compareElement.getFontName();
            this.compareFontSize = compareElement.getFontSize();
            this.comparePosition = new float[] { compareElement.getX(), compareElement.getY() };
        }
    }
}
