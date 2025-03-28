package guraa.pdfcompare.comparison;

import guraa.pdfcompare.core.ImageElement;

/**
 * Class representing difference between images
 */
public class ImageDifference {
    private ImageElement baseImage;
    private ImageElement compareImage;
    private boolean onlyInBase;
    private boolean onlyInCompare;
    private boolean dimensionsDifferent;
    private boolean positionDifferent;
    private boolean formatDifferent;

    // Getters and setters
    public ImageElement getBaseImage() {
        return baseImage;
    }

    public void setBaseImage(ImageElement baseImage) {
        this.baseImage = baseImage;
    }

    public ImageElement getCompareImage() {
        return compareImage;
    }

    public void setCompareImage(ImageElement compareImage) {
        this.compareImage = compareImage;
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

    public boolean isDimensionsDifferent() {
        return dimensionsDifferent;
    }

    public void setDimensionsDifferent(boolean dimensionsDifferent) {
        this.dimensionsDifferent = dimensionsDifferent;
    }

    public boolean isPositionDifferent() {
        return positionDifferent;
    }

    public void setPositionDifferent(boolean positionDifferent) {
        this.positionDifferent = positionDifferent;
    }

    public boolean isFormatDifferent() {
        return formatDifferent;
    }

    public void setFormatDifferent(boolean formatDifferent) {
        this.formatDifferent = formatDifferent;
    }
}