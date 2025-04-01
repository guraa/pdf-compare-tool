package guraa.pdfcompare.service;

/**
 * Represents a pair of matched pages from base and compare documents.
 */
public class PagePair {
    private int basePageIndex;
    private int comparePageIndex;
    private double similarity;
    private PageFingerprint baseFingerprint;
    private PageFingerprint compareFingerprint;

    public PagePair() {
    }

    public PagePair(int basePageIndex, int comparePageIndex, double similarity) {
        this.basePageIndex = basePageIndex;
        this.comparePageIndex = comparePageIndex;
        this.similarity = similarity;
    }
    
    public PagePair(PageFingerprint baseFingerprint, PageFingerprint compareFingerprint, double similarity) {
        this.baseFingerprint = baseFingerprint;
        this.compareFingerprint = compareFingerprint;
        this.similarity = similarity;
        
        if (baseFingerprint != null) {
            this.basePageIndex = baseFingerprint.getPageIndex();
        }
        
        if (compareFingerprint != null) {
            this.comparePageIndex = compareFingerprint.getPageIndex();
        }
    }

    public int getBasePageIndex() {
        return basePageIndex;
    }

    public void setBasePageIndex(int basePageIndex) {
        this.basePageIndex = basePageIndex;
    }

    public int getComparePageIndex() {
        return comparePageIndex;
    }

    public void setComparePageIndex(int comparePageIndex) {
        this.comparePageIndex = comparePageIndex;
    }

    public double getSimilarity() {
        return similarity;
    }

    public void setSimilarity(double similarity) {
        this.similarity = similarity;
    }
    
    // Alias method for compatibility
    public double getSimilarityScore() {
        return similarity;
    }
    
    public PageFingerprint getBaseFingerprint() {
        return baseFingerprint;
    }
    
    public void setBaseFingerprint(PageFingerprint baseFingerprint) {
        this.baseFingerprint = baseFingerprint;
    }
    
    public PageFingerprint getCompareFingerprint() {
        return compareFingerprint;
    }
    
    public void setCompareFingerprint(PageFingerprint compareFingerprint) {
        this.compareFingerprint = compareFingerprint;
    }
    
    public boolean isMatched() {
        return baseFingerprint != null && compareFingerprint != null;
    }

    @Override
    public String toString() {
        return "PagePair{" +
                "basePageIndex=" + basePageIndex +
                ", comparePageIndex=" + comparePageIndex +
                ", similarity=" + similarity +
                '}';
    }
}
