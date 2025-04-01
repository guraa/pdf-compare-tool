package guraa.pdfcompare.service;

/**
 * Represents a pair of matching pages from two PDF documents.
 */
public class PagePair {
    private PageFingerprint baseFingerprint;
    private PageFingerprint compareFingerprint;
    private double similarityScore;

    public PagePair(PageFingerprint baseFingerprint, PageFingerprint compareFingerprint, double similarityScore) {
        this.baseFingerprint = baseFingerprint;
        this.compareFingerprint = compareFingerprint;
        this.similarityScore = similarityScore;
    }

    /**
     * Check if this page pair represents a valid match between two pages
     * @return true if both the base fingerprint and compare fingerprint are not null
     */
    public boolean isMatched() {
        return baseFingerprint != null && compareFingerprint != null;
    }

    public PageFingerprint getBaseFingerprint() { return baseFingerprint; }
    public PageFingerprint getCompareFingerprint() { return compareFingerprint; }
    public double getSimilarityScore() { return similarityScore; }
}