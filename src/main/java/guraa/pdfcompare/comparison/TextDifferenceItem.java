package guraa.pdfcompare.comparison;


/**
 * Class representing a single text difference item
 */
class TextDifferenceItem {
    private int lineNumber;
    private String baseText;
    private String compareText;
    private TextDifferenceType differenceType;

    // Getters and setters
    public int getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    public String getBaseText() {
        return baseText;
    }

    public void setBaseText(String baseText) {
        this.baseText = baseText;
    }

    public String getCompareText() {
        return compareText;
    }

    public void setCompareText(String compareText) {
        this.compareText = compareText;
    }

    public TextDifferenceType getDifferenceType() {
        return differenceType;
    }

    public void setDifferenceType(TextDifferenceType differenceType) {
        this.differenceType = differenceType;
    }
}