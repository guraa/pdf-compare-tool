package guraa.pdfcompare.service;

import guraa.pdfcompare.core.PDFPageModel;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Represents a fingerprint of a PDF page, containing various features that can be used
 * for matching and comparison purposes.
 */
public class PageFingerprint {
    private String sourceType;  // "base" or "compare"
    private int pageIndex;
    private PDFPageModel page;
    private String text;
    private int textHash;
    private Set<String> significantWords = new HashSet<>();
    private Map<String, Integer> fontDistribution = new HashMap<>();
    private int elementCount;
    private boolean hasImages;
    private int imageCount;

    // Getters and setters
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }

    public int getPageIndex() { return pageIndex; }
    public void setPageIndex(int pageIndex) { this.pageIndex = pageIndex; }

    public PDFPageModel getPage() { return page; }
    public void setPage(PDFPageModel page) { this.page = page; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public int getTextHash() { return textHash; }
    public void setTextHash(int textHash) { this.textHash = textHash; }

    public Set<String> getSignificantWords() { return significantWords; }
    public void setSignificantWords(Set<String> significantWords) { this.significantWords = significantWords; }

    public Map<String, Integer> getFontDistribution() { return fontDistribution; }
    public void setFontDistribution(Map<String, Integer> fontDistribution) { this.fontDistribution = fontDistribution; }

    public int getElementCount() { return elementCount; }
    public void setElementCount(int elementCount) { this.elementCount = elementCount; }

    public boolean isHasImages() { return hasImages; }
    public void setHasImages(boolean hasImages) { this.hasImages = hasImages; }

    public int getImageCount() { return imageCount; }
    public void setImageCount(int imageCount) { this.imageCount = imageCount; }
}