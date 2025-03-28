package guraa.pdfcompare.model;

import java.util.Map;

/**
 * Request model for PDF comparison operations
 */
public class ComparisonRequest {
    private String baseFileId;
    private String compareFileId;
    private Map<String, Object> options;

    // Getters and setters
    public String getBaseFileId() {
        return baseFileId;
    }

    public void setBaseFileId(String baseFileId) {
        this.baseFileId = baseFileId;
    }

    public String getCompareFileId() {
        return compareFileId;
    }

    public void setCompareFileId(String compareFileId) {
        this.compareFileId = compareFileId;
    }

    public Map<String, Object> getOptions() {
        return options;
    }

    public void setOptions(Map<String, Object> options) {
        this.options = options;
    }
}