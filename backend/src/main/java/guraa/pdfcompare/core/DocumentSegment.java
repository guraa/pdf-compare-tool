package guraa.pdfcompare.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a segment of a document with its characteristics
 */
public class DocumentSegment {
    private final int startPage;
    private final int endPage;
    private final String title;
    private Map<String, Object> features;

    /**
     * Constructor for DocumentSegment
     * @param startPage Starting page number of the segment
     * @param endPage Ending page number of the segment
     * @param title Title of the document segment
     */
    public DocumentSegment(int startPage, int endPage, String title) {
        this.startPage = startPage;
        this.endPage = endPage;
        this.title = title;
        this.features = new HashMap<>();  // Initialize with empty map
    }

    /**
     * Get the starting page of the segment
     * @return Starting page number
     */
    public int getStartPage() {
        return startPage;
    }

    /**
     * Get the ending page of the segment
     * @return Ending page number
     */
    public int getEndPage() {
        return endPage;
    }

    /**
     * Get the title of the segment
     * @return Segment title
     */
    public String getTitle() {
        return title;
    }

    /**
     * Get the features of the segment
     * @return Map of segment features
     */
    public Map<String, Object> getFeatures() {
        // Ensure we never return null
        if (features == null) {
            features = new HashMap<>();
        }
        return features;
    }

    /**
     * Set the features of the segment
     * @param features Map of segment features
     */
    public void setFeatures(Map<String, Object> features) {
        // Ensure we never store null
        this.features = features != null ? features : new HashMap<>();
    }

    /**
     * Get the number of pages in the segment
     * @return Number of pages
     */
    public int getPageCount() {
        return endPage - startPage + 1;
    }

    @Override
    public String toString() {
        return String.format("DocumentSegment{startPage=%d, endPage=%d, title='%s', pageCount=%d}",
                startPage, endPage, title, getPageCount());
    }
}