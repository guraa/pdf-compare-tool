package guraa.pdfcompare.comparison;

import guraa.pdfcompare.core.TextElement;

/**
 * Class representing difference between text elements
 */
class TextElementDifference {
    private TextElement baseElement;
    private TextElement compareElement;
    private boolean onlyInBase;
    private boolean onlyInCompare;
    private boolean styleDifferent;

    // Getters and setters
    public TextElement getBaseElement() {
        return baseElement;
    }

    public void setBaseElement(TextElement baseElement) {
        this.baseElement = baseElement;
    }

    public TextElement getCompareElement() {
        return compareElement;
    }

    public void setCompareElement(TextElement compareElement) {
        this.compareElement = compareElement;
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

    public boolean isStyleDifferent() {
        return styleDifferent;
    }

    public void setStyleDifferent(boolean styleDifferent) {
        this.styleDifferent = styleDifferent;
    }
}