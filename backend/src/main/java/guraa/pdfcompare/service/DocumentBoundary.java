package guraa.pdfcompare.service;

/**
 * Document boundary class.
 *
 */
public class DocumentBoundary {
    private final int startPage;
    private final int endPage;
    private boolean matched = false;

    public DocumentBoundary(int startPage, int endPage) {
        this.startPage = startPage;
        this.endPage = endPage;
    }

    public int getStartPage() { return startPage; }
    public int getEndPage() { return endPage; }
    public boolean isMatched() { return matched; }
    public void setMatched(boolean matched) { this.matched = matched; }
}
