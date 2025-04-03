package guraa.pdfcompare.service;

/**
 * Document match between base and comparison PDFs.
 */
public class DocumentMatch {
    private final int baseDocumentIndex;
    private final int compareDocumentIndex;
    private final double similarityScore;

    public DocumentMatch(int baseDocumentIndex, int compareDocumentIndex, double similarityScore) {
        this.baseDocumentIndex = baseDocumentIndex;
        this.compareDocumentIndex = compareDocumentIndex;
        this.similarityScore = similarityScore;
    }

    public int getBaseDocumentIndex() { return baseDocumentIndex; }
    public int getCompareDocumentIndex() { return compareDocumentIndex; }
    public double getSimilarityScore() { return similarityScore; }
}