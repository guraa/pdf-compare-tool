package guraa.pdfcompare.service;

import java.util.List;

/**
 * Represents the result of a page-level comparison between two PDF documents.
 * Contains lists of page pairs and their comparison results, as well as a summary.
 */
public class PageLevelComparisonResult {
    private List<PagePair> pagePairs;
    private List<PageComparisonResult> pageResults;
    private PageLevelComparisonSummary summary;

    public PageLevelComparisonResult(List<PagePair> pagePairs, List<PageComparisonResult> pageResults,
                                     PageLevelComparisonSummary summary) {
        this.pagePairs = pagePairs;
        this.pageResults = pageResults;
        this.summary = summary;
    }

    public List<PagePair> getPagePairs() { return pagePairs; }
    public List<PageComparisonResult> getPageResults() { return pageResults; }
    public PageLevelComparisonSummary getSummary() { return summary; }
}