package guraa.pdfcompare.service;


/**
 * Potential match between base and comparison documents.
 */
public class PotentialMatch {
    private final int baseIndex;
    private final int compareIndex;
    private final double similarity;

    public PotentialMatch(int baseIndex, int compareIndex, double similarity) {
        this.baseIndex = baseIndex;
        this.compareIndex = compareIndex;
        this.similarity = similarity;
    }

    public int getBaseIndex() { return baseIndex; }
    public int getCompareIndex() { return compareIndex; }
    public double getSimilarity() { return similarity; }
}