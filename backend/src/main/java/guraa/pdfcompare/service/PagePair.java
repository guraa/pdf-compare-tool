package guraa.pdfcompare.service;

import guraa.pdfcompare.core.PageFingerprint;

/**
 * Represents a pair of matched pages from base and compare documents.
 */
public class PagePair {
    private PageFingerprint baseFingerprint;
    private PageFingerprint compareFingerprint;
    private double similarityScore;

    /**
     * Default constructor
     */
    public PagePair() {
    }

    /**
     * Constructor with all fields
     *
     * @param baseFingerprint Base page fingerprint
     * @param compareFingerprint Compare page fingerprint
     * @param similarityScore Similarity score between pages
     */
    public PagePair(PageFingerprint baseFingerprint, PageFingerprint compareFingerprint, double similarityScore) {
        this.baseFingerprint = baseFingerprint;
        this.compareFingerprint = compareFingerprint;
        this.similarityScore = similarityScore;
    }

    /**
     * Check if both pages are present (matched pair)
     *
     * @return true if both fingerprints are present
     */
    public boolean isMatched() {
        return baseFingerprint != null && compareFingerprint != null;
    }

    /**
     * Get the base page fingerprint
     *
     * @return Base page fingerprint
     */
    public PageFingerprint getBaseFingerprint() {
        return baseFingerprint;
    }

    /**
     * Set the base page fingerprint
     *
     * @param baseFingerprint Base page fingerprint
     */
    public void setBaseFingerprint(PageFingerprint baseFingerprint) {
        this.baseFingerprint = baseFingerprint;
    }

    /**
     * Get the compare page fingerprint
     *
     * @return Compare page fingerprint
     */
    public PageFingerprint getCompareFingerprint() {
        return compareFingerprint;
    }

    /**
     * Set the compare page fingerprint
     *
     * @param compareFingerprint Compare page fingerprint
     */
    public void setCompareFingerprint(PageFingerprint compareFingerprint) {
        this.compareFingerprint = compareFingerprint;
    }

    /**
     * Get the similarity score
     *
     * @return Similarity score (0.0-1.0)
     */
    public double getSimilarityScore() {
        return similarityScore;
    }

    /**
     * Set the similarity score
     *
     * @param similarityScore Similarity score (0.0-1.0)
     */
    public void setSimilarityScore(double similarityScore) {
        this.similarityScore = similarityScore;
    }

    /**
     * Get the base page index
     *
     * @return Base page index or -1 if no base page
     */
    public int getBasePageIndex() {
        return baseFingerprint != null ? guraa.pdfcompare.service.FingerprintAdapter.getPageIndex(baseFingerprint) : -1;
    }

    /**
     * Get the compare page index
     *
     * @return Compare page index or -1 if no compare page
     */
    public int getComparePageIndex() {
        return compareFingerprint != null ? guraa.pdfcompare.service.FingerprintAdapter.getPageIndex(compareFingerprint) : -1;
    }

    /**
     * Get a string representation of the page pair
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("PagePair{");

        if (baseFingerprint != null) {
            sb.append("basePage=").append(guraa.pdfcompare.service.FingerprintAdapter.getPageIndex(baseFingerprint) + 1);
        } else {
            sb.append("basePage=none");
        }

        sb.append(", ");

        if (compareFingerprint != null) {
            sb.append("comparePage=").append(guraa.pdfcompare.service.FingerprintAdapter.getPageIndex(compareFingerprint) + 1);
        } else {
            sb.append("comparePage=none");
        }

        sb.append(", similarity=").append(String.format("%.2f", similarityScore))
                .append(", matched=").append(isMatched())
                .append("}");

        return sb.toString();
    }
}