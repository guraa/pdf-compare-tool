package guraa.pdfcompare.service;

/**
 * Represents a summary of a page-level comparison between two PDF documents.
 * Contains statistics about matched, unmatched, and modified pages.
 */
public class PageLevelComparisonSummary {
    private int matchedPageCount;
    private int unmatchedBasePageCount;
    private int unmatchedComparePageCount;
    private int identicalPageCount;
    private int pagesWithDifferencesCount;
    private int totalDifferences;

    /**
     * Get the number of pages that were successfully matched between the documents
     * @return Number of matched pages
     */
    public int getMatchedPageCount() {
        return matchedPageCount;
    }

    /**
     * Set the number of pages that were successfully matched between the documents
     * @param matchedPageCount Number of matched pages
     */
    public void setMatchedPageCount(int matchedPageCount) {
        this.matchedPageCount = matchedPageCount;
    }

    /**
     * Get the number of pages in the base document that have no match in the compare document
     * @return Number of unmatched base pages
     */
    public int getUnmatchedBasePageCount() {
        return unmatchedBasePageCount;
    }

    /**
     * Set the number of pages in the base document that have no match in the compare document
     * @param unmatchedBasePageCount Number of unmatched base pages
     */
    public void setUnmatchedBasePageCount(int unmatchedBasePageCount) {
        this.unmatchedBasePageCount = unmatchedBasePageCount;
    }

    /**
     * Get the number of pages in the compare document that have no match in the base document
     * @return Number of unmatched compare pages
     */
    public int getUnmatchedComparePageCount() {
        return unmatchedComparePageCount;
    }

    /**
     * Set the number of pages in the compare document that have no match in the base document
     * @param unmatchedComparePageCount Number of unmatched compare pages
     */
    public void setUnmatchedComparePageCount(int unmatchedComparePageCount) {
        this.unmatchedComparePageCount = unmatchedComparePageCount;
    }

    /**
     * Get the number of matched pages that are identical (no differences)
     * @return Number of identical pages
     */
    public int getIdenticalPageCount() {
        return identicalPageCount;
    }

    /**
     * Set the number of matched pages that are identical (no differences)
     * @param identicalPageCount Number of identical pages
     */
    public void setIdenticalPageCount(int identicalPageCount) {
        this.identicalPageCount = identicalPageCount;
    }

    /**
     * Get the number of matched pages that contain at least one difference
     * @return Number of pages with differences
     */
    public int getPagesWithDifferencesCount() {
        return pagesWithDifferencesCount;
    }

    /**
     * Set the number of matched pages that contain at least one difference
     * @param pagesWithDifferencesCount Number of pages with differences
     */
    public void setPagesWithDifferencesCount(int pagesWithDifferencesCount) {
        this.pagesWithDifferencesCount = pagesWithDifferencesCount;
    }

    /**
     * Get the total number of differences found across all matched pages
     * @return Total number of differences
     */
    public int getTotalDifferences() {
        return totalDifferences;
    }

    /**
     * Set the total number of differences found across all matched pages
     * @param totalDifferences Total number of differences
     */
    public void setTotalDifferences(int totalDifferences) {
        this.totalDifferences = totalDifferences;
    }

    /**
     * Calculate the total number of pages in the base document
     * @return Total base document page count
     */
    public int getTotalBasePageCount() {
        return matchedPageCount + unmatchedBasePageCount;
    }

    /**
     * Calculate the total number of pages in the compare document
     * @return Total compare document page count
     */
    public int getTotalComparePageCount() {
        return matchedPageCount + unmatchedComparePageCount;
    }

    /**
     * Calculate the percentage of matched pages that are identical
     * @return Percentage of identical pages among matched pages
     */
    public double getIdenticalPagePercentage() {
        if (matchedPageCount == 0) {
            return 0.0;
        }

        return (double) identicalPageCount / matchedPageCount * 100.0;
    }

    /**
     * Calculate the overall match percentage between the documents
     * @return Percentage of pages that were successfully matched
     */
    public double getDocumentMatchPercentage() {
        int totalPages = getTotalBasePageCount() + getTotalComparePageCount();
        if (totalPages == 0) {
            return 0.0;
        }

        return (double) (matchedPageCount * 2) / totalPages * 100.0;
    }

    @Override
    public String toString() {
        return "PageLevelComparisonSummary{" +
                "matchedPages=" + matchedPageCount +
                ", unmatchedBasePages=" + unmatchedBasePageCount +
                ", unmatchedComparePages=" + unmatchedComparePageCount +
                ", identicalPages=" + identicalPageCount +
                ", pagesWithDifferences=" + pagesWithDifferencesCount +
                ", totalDifferences=" + totalDifferences +
                ", matchPercentage=" + String.format("%.1f%%", getDocumentMatchPercentage()) +
                '}';
    }
}