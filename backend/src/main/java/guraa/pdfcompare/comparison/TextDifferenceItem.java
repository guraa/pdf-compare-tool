package guraa.pdfcompare.comparison;

/**
 * Represents a text difference item between two pages.
 */
public class TextDifferenceItem {
    private String baseText;
    private String compareText;
    private int lineNumber;
    private TextDifferenceType differenceType;

    /**
     * Default constructor
     */
    public TextDifferenceItem() {
    }

    /**
     * Constructor with all fields
     * @param baseText Base text
     * @param compareText Compare text
     * @param lineNumber Line number
     * @param differenceType Difference type
     */
    public TextDifferenceItem(String baseText, String compareText, int lineNumber, TextDifferenceType differenceType) {
        this.baseText = baseText;
        this.compareText = compareText;
        this.lineNumber = lineNumber;
        this.differenceType = differenceType;
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
     * Get the line number
     * @return Line number
     */
    public int getLineNumber() {
        return lineNumber;
    }

    /**
     * Set the line number
     * @param lineNumber Line number
     */
    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    /**
     * Get the difference type
     * @return Difference type
     */
    public TextDifferenceType getDifferenceType() {
        return differenceType;
    }

    /**
     * Set the difference type
     * @param differenceType Difference type
     */
    public void setDifferenceType(TextDifferenceType differenceType) {
        this.differenceType = differenceType;
    }
}
