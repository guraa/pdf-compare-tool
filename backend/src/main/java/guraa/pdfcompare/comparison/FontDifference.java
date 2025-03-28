package guraa.pdfcompare.comparison;

import guraa.pdfcompare.core.FontInfo;

/**
 * Class representing difference between fonts
 */
public class FontDifference {
    private FontInfo baseFont;
    private FontInfo compareFont;
    private boolean onlyInBase;
    private boolean onlyInCompare;
    private boolean embeddingDifferent;
    private boolean subsetDifferent;

    // Getters and setters
    public FontInfo getBaseFont() {
        return baseFont;
    }

    public void setBaseFont(FontInfo baseFont) {
        this.baseFont = baseFont;
    }

    public FontInfo getCompareFont() {
        return compareFont;
    }

    public void setCompareFont(FontInfo compareFont) {
        this.compareFont = compareFont;
    }

    public boolean isOnlyInBase() {
        return onlyInBase;
    }

    public void setOnlyInBase(boolean onlyInBase) {
        this.onlyInBase = onlyInBase;
    }

    public boolean isOnlyInCompare() {
        return onlyInCompare;
    }

    public void setOnlyInCompare(boolean onlyInCompare) {
        this.onlyInCompare = onlyInCompare;
    }

    public boolean isEmbeddingDifferent() {
        return embeddingDifferent;
    }

    public void setEmbeddingDifferent(boolean embeddingDifferent) {
        this.embeddingDifferent = embeddingDifferent;
    }

    public boolean isSubsetDifferent() {
        return subsetDifferent;
    }

    public void setSubsetDifferent(boolean subsetDifferent) {
        this.subsetDifferent = subsetDifferent;
    }
}