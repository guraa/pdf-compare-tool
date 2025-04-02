package guraa.pdfcompare.service;

import guraa.pdfcompare.comparison.PageComparisonResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents the result of a page-level comparison between two PDF documents.
 * Includes matched pages, unmatched pages, and similarity information.
 */
public class PageLevelComparisonResult {
    private List<PagePair> matchedPages = new ArrayList<>();
    private List<Integer> unmatchedBasePages = new ArrayList<>();
    private List<Integer> unmatchedComparePages = new ArrayList<>();
    private double[][] similarityMatrix;
    private List<guraa.pdfcompare.comparison.PageComparisonResult> pageComparisonResults = new ArrayList<>();
    private PageLevelComparisonSummary summary;
    private Map<String, Object> metadata = new HashMap<>();
    
    public PageLevelComparisonResult() {
    }
    
    public PageLevelComparisonResult(List<PagePair> matchedPages, 
                                    List<guraa.pdfcompare.comparison.PageComparisonResult> pageComparisonResults,
                                    PageLevelComparisonSummary summary) {
        this.matchedPages = matchedPages;
        this.pageComparisonResults = pageComparisonResults;
        this.summary = summary;
    }
    
    // Alias methods for compatibility
    public List<PagePair> getPagePairs() {
        return matchedPages;
    }
    
    public List<PageComparisonResult> getPageResults() {
        return pageComparisonResults;
    }

    public List<PagePair> getMatchedPages() {
        return matchedPages;
    }

    public void setMatchedPages(List<PagePair> matchedPages) {
        this.matchedPages = matchedPages;
    }

    public List<Integer> getUnmatchedBasePages() {
        return unmatchedBasePages;
    }

    public void setUnmatchedBasePages(List<Integer> unmatchedBasePages) {
        this.unmatchedBasePages = unmatchedBasePages;
    }

    public List<Integer> getUnmatchedComparePages() {
        return unmatchedComparePages;
    }

    public void setUnmatchedComparePages(List<Integer> unmatchedComparePages) {
        this.unmatchedComparePages = unmatchedComparePages;
    }

    public double[][] getSimilarityMatrix() {
        return similarityMatrix;
    }

    public void setSimilarityMatrix(double[][] similarityMatrix) {
        this.similarityMatrix = similarityMatrix;
    }

    public List<PageComparisonResult> getPageComparisonResults() {
        return pageComparisonResults;
    }

    public void setPageComparisonResults(List<PageComparisonResult> pageComparisonResults) {
        this.pageComparisonResults = pageComparisonResults;
    }

    public PageLevelComparisonSummary getSummary() {
        return summary;
    }

    public void setSummary(PageLevelComparisonSummary summary) {
        this.summary = summary;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public void addMetadata(String key, Object value) {
        this.metadata.put(key, value);
    }
}
