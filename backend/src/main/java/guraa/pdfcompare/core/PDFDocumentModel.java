package guraa.pdfcompare.core;

import java.util.List;
import java.util.Map;

class PDFDocumentModel {
    private String fileName;
    private int pageCount;
    private Map<String, String> metadata;
    private List<PDFPageModel> pages;

    // Getters and setters
    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public int getPageCount() {
        return pageCount;
    }

    public void setPageCount(int pageCount) {
        this.pageCount = pageCount;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public List<PDFPageModel> getPages() {
        return pages;
    }

    public void setPages(List<PDFPageModel> pages) {
        this.pages = pages;
    }
}