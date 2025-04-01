package guraa.pdfcompare.service;

/**
 * Summary of the page-level comparison results.
 */
public class PageLevelComparisonSummary {
    private int totalDifferences;
    private int totalTextDifferences;
    private int totalImageDifferences;
    private int totalFontDifferences;
    private int totalStyleDifferences;
    private int totalMatchedPages;
    private int totalUnmatchedBasePages;
    private int totalUnmatchedComparePages;
    private double overallSimilarityScore;
    private int identicalPageCount;
    private int pagesWithDifferencesCount;

    public int getTotalDifferences() {
        return totalDifferences;
    }

    public void setTotalDifferences(int totalDifferences) {
        this.totalDifferences = totalDifferences;
    }

    public int getTotalTextDifferences() {
        return totalTextDifferences;
    }

    public void setTotalTextDifferences(int totalTextDifferences) {
        this.totalTextDifferences = totalTextDifferences;
    }

    public int getTotalImageDifferences() {
        return totalImageDifferences;
    }

    public void setTotalImageDifferences(int totalImageDifferences) {
        this.totalImageDifferences = totalImageDifferences;
    }

    public int getTotalFontDifferences() {
        return totalFontDifferences;
    }

    public void setTotalFontDifferences(int totalFontDifferences) {
        this.totalFontDifferences = totalFontDifferences;
    }

    public int getTotalStyleDifferences() {
        return totalStyleDifferences;
    }

    public void setTotalStyleDifferences(int totalStyleDifferences) {
        this.totalStyleDifferences = totalStyleDifferences;
    }

    public int getTotalMatchedPages() {
        return totalMatchedPages;
    }

    public void setTotalMatchedPages(int totalMatchedPages) {
        this.totalMatchedPages = totalMatchedPages;
    }

    public int getTotalUnmatchedBasePages() {
        return totalUnmatchedBasePages;
    }

    public void setTotalUnmatchedBasePages(int totalUnmatchedBasePages) {
        this.totalUnmatchedBasePages = totalUnmatchedBasePages;
    }

    public int getTotalUnmatchedComparePages() {
        return totalUnmatchedComparePages;
    }

    public void setTotalUnmatchedComparePages(int totalUnmatchedComparePages) {
        this.totalUnmatchedComparePages = totalUnmatchedComparePages;
    }

    public double getOverallSimilarityScore() {
        return overallSimilarityScore;
    }

    public void setOverallSimilarityScore(double overallSimilarityScore) {
        this.overallSimilarityScore = overallSimilarityScore;
    }
    
    public int getIdenticalPageCount() {
        return identicalPageCount;
    }
    
    public void setIdenticalPageCount(int identicalPageCount) {
        this.identicalPageCount = identicalPageCount;
    }
    
    public int getPagesWithDifferencesCount() {
        return pagesWithDifferencesCount;
    }
    
    public void setPagesWithDifferencesCount(int pagesWithDifferencesCount) {
        this.pagesWithDifferencesCount = pagesWithDifferencesCount;
    }
    
    public int getMatchedPageCount() {
        return totalMatchedPages;
    }
    
    public void setMatchedPageCount(int matchedPageCount) {
        this.totalMatchedPages = matchedPageCount;
    }
    
    public int getUnmatchedBasePageCount() {
        return totalUnmatchedBasePages;
    }
    
    public void setUnmatchedBasePageCount(int unmatchedBasePageCount) {
        this.totalUnmatchedBasePages = unmatchedBasePageCount;
    }
    
    public int getUnmatchedComparePageCount() {
        return totalUnmatchedComparePages;
    }
    
    public void setUnmatchedComparePageCount(int unmatchedComparePageCount) {
        this.totalUnmatchedComparePages = unmatchedComparePageCount;
    }
}
