package guraa.pdfcompare.comparison;

import guraa.pdfcompare.core.DocumentPair;

import java.util.List;
import java.util.Map;

/**
 * Main class representing the result of a PDF comparison
 */
public class PDFComparisonResult {
    private Map<String, MetadataDifference> metadataDifferences;
    private boolean pageCountDifferent;
    private int basePageCount;
    private int comparePageCount;
    private List<PageComparisonResult> pageDifferences;
    private int totalDifferences;
    private int totalTextDifferences;
    private int totalImageDifferences;
    private int totalFontDifferences;
    private int totalStyleDifferences;
    private String matchingId;

    // New field for document pairs
    private List<DocumentPair> documentPairs;

    // Getters and setters
    public Map<String, MetadataDifference> getMetadataDifferences() {
        return metadataDifferences;
    }

    public void setMetadataDifferences(Map<String, MetadataDifference> metadataDifferences) {
        this.metadataDifferences = metadataDifferences;
    }

    public boolean isPageCountDifferent() {
        return pageCountDifferent;
    }

    public void setPageCountDifferent(boolean pageCountDifferent) {
        this.pageCountDifferent = pageCountDifferent;
    }

    public int getBasePageCount() {
        return basePageCount;
    }

    public void setBasePageCount(int basePageCount) {
        this.basePageCount = basePageCount;
    }

    public int getComparePageCount() {
        return comparePageCount;
    }

    public void setComparePageCount(int comparePageCount) {
        this.comparePageCount = comparePageCount;
    }

    public List<PageComparisonResult> getPageDifferences() {
        return pageDifferences;
    }

    public String getMatchingId() {
        return matchingId;
    }

    public void setMatchingId(String matchingId) {
        this.matchingId = matchingId;
    }

    public void setPageDifferences(List<PageComparisonResult> pageDifferences) {
        this.pageDifferences = pageDifferences;
    }

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

    /**
     * Get the document pairs associated with this comparison
     * @return List of document pairs
     */
    public List<DocumentPair> getDocumentPairs() {
        return documentPairs;
    }

    /**
     * Set the document pairs for this comparison
     * @param documentPairs List of document pairs
     */
    public void setDocumentPairs(List<DocumentPair> documentPairs) {
        this.documentPairs = documentPairs;
    }
}