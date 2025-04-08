package guraa.pdfcompare.service;

import lombok.Getter;
import lombok.ToString;

/**
 * Represents a document boundary within a PDF.
 * This class is used to track the start and end pages of a logical document
 * when a PDF contains multiple documents.
 */
@Getter
@ToString
public class DocumentBoundary {
    private final int startPage;
    private final int endPage;
    private boolean matched = false;

    /**
     * Constructor for document boundary.
     *
     * @param startPage The starting page index (0-based)
     * @param endPage The ending page index (0-based)
     */
    public DocumentBoundary(int startPage, int endPage) {
        this.startPage = startPage;
        this.endPage = endPage;
    }

    /**
     * Check if this document boundary has been matched.
     *
     * @return true if matched, false otherwise
     */
    public boolean isMatched() {
        return matched;
    }

    /**
     * Set the matched status of this document boundary.
     *
     * @param matched The matched status to set
     */
    public void setMatched(boolean matched) {
        this.matched = matched;
    }

    /**
     * Get the number of pages in this document.
     *
     * @return The number of pages
     */
    public int getPageCount() {
        return endPage - startPage + 1;
    }

    /**
     * Check if this document boundary contains the specified page.
     *
     * @param pageIndex The page index to check
     * @return true if the page is within this document boundary, false otherwise
     */
    public boolean containsPage(int pageIndex) {
        return pageIndex >= startPage && pageIndex <= endPage;
    }


    /**
     * Convert an absolute page index to a relative page index (within this document).
     *
     * @param absolutePageIndex The absolute page index
     * @return The relative page index
     * @throws IllegalArgumentException If the absolute page index is outside this document
     */
    public int toRelativePageIndex(int absolutePageIndex) {
        if (!containsPage(absolutePageIndex)) {
            throw new IllegalArgumentException("Absolute page index not in this document: " + absolutePageIndex);
        }
        return absolutePageIndex - startPage;
    }
}