package guraa.pdfcompare.comparison;

/**
 * Represents a difference in text between two pages.
 */
public class TextDifference {
    private String baseText;
    private String compareText;
    private int baseStartIndex;
    private int baseEndIndex;
    private int compareStartIndex;
    private int compareEndIndex;
    private String differenceType; // "ADDITION", "DELETION", "MODIFICATION"

    /**
     * Default constructor
     */
    public TextDifference() {
    }

    /**
     * Constructor with all fields
     * @param baseText Base text
     * @param compareText Compare text
     * @param baseStartIndex Base start index
     * @param baseEndIndex Base end index
     * @param compareStartIndex Compare start index
     * @param compareEndIndex Compare end index
     * @param differenceType Difference type
     */
    public TextDifference(String baseText, String compareText, int baseStartIndex, int baseEndIndex,
                          int compareStartIndex, int compareEndIndex, String differenceType) {
        this.baseText = baseText;
        this.compareText = compareText;
        this.baseStartIndex = baseStartIndex;
        this.baseEndIndex = baseEndIndex;
        this.compareStartIndex = compareStartIndex;
        this.compareEndIndex = compareEndIndex;
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
     * Get the base start index
     * @return Base start index
     */
    public int getBaseStartIndex() {
        return baseStartIndex;
    }

    /**
     * Set the base start index
     * @param baseStartIndex Base start index
     */
    public void setBaseStartIndex(int baseStartIndex) {
        this.baseStartIndex = baseStartIndex;
    }

    /**
     * Get the base end index
     * @return Base end index
     */
    public int getBaseEndIndex() {
        return baseEndIndex;
    }

    /**
     * Set the base end index
     * @param baseEndIndex Base end index
     */
    public void setBaseEndIndex(int baseEndIndex) {
        this.baseEndIndex = baseEndIndex;
    }

    /**
     * Get the compare start index
     * @return Compare start index
     */
    public int getCompareStartIndex() {
        return compareStartIndex;
    }

    /**
     * Set the compare start index
     * @param compareStartIndex Compare start index
     */
    public void setCompareStartIndex(int compareStartIndex) {
        this.compareStartIndex = compareStartIndex;
    }

    /**
     * Get the compare end index
     * @return Compare end index
     */
    public int getCompareEndIndex() {
        return compareEndIndex;
    }

    /**
     * Set the compare end index
     * @param compareEndIndex Compare end index
     */
    public void setCompareEndIndex(int compareEndIndex) {
        this.compareEndIndex = compareEndIndex;
    }

    /**
     * Get the difference type
     * @return Difference type
     */
    public String getDifferenceType() {
        return differenceType;
    }

    /**
     * Set the difference type
     * @param differenceType Difference type
     */
    public void setDifferenceType(String differenceType) {
        this.differenceType = differenceType;
    }

    /**
     * Check if the difference is an addition
     * @return true if the difference is an addition
     */
    public boolean isAddition() {
        return "ADDITION".equals(differenceType);
    }

    /**
     * Check if the difference is a deletion
     * @return true if the difference is a deletion
     */
    public boolean isDeletion() {
        return "DELETION".equals(differenceType);
    }

    /**
     * Check if the difference is a modification
     * @return true if the difference is a modification
     */
    public boolean isModification() {
        return "MODIFICATION".equals(differenceType);
    }
}
