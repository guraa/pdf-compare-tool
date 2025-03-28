package guraa.pdfcompare.comparison;


import java.util.List;

/**
 * Class representing text comparison results
 */
class TextComparisonResult {
    private List<TextDifferenceItem> differences;
    private int differenceCount;

    // Getters and setters
    public List<TextDifferenceItem> getDifferences() {
        return differences;
    }

    public void setDifferences(List<TextDifferenceItem> differences) {
        this.differences = differences;
    }

    public int getDifferenceCount() {
        return differenceCount;
    }

    public void setDifferenceCount(int differenceCount) {
        this.differenceCount = differenceCount;
    }
}

