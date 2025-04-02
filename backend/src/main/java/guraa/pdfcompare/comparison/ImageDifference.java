package guraa.pdfcompare.comparison;

/**
 * Represents a difference in an image between two pages.
 */
public class ImageDifference {
    private float[] basePosition;
    private float[] comparePosition;
    private float[] baseDimensions;
    private float[] compareDimensions;
    private boolean onlyInBase;
    private boolean onlyInCompare;
    private boolean positionDifferent;
    private boolean sizeDifferent;
    private boolean contentDifferent;
    private double similarityScore;

    /**
     * Default constructor
     */
    public ImageDifference() {
    }

    /**
     * Get the base position
     * @return Base position
     */
    public float[] getBasePosition() {
        return basePosition;
    }

    /**
     * Set the base position
     * @param basePosition Base position
     */
    public void setBasePosition(float[] basePosition) {
        this.basePosition = basePosition;
    }

    /**
     * Get the compare position
     * @return Compare position
     */
    public float[] getComparePosition() {
        return comparePosition;
    }

    /**
     * Set the compare position
     * @param comparePosition Compare position
     */
    public void setComparePosition(float[] comparePosition) {
        this.comparePosition = comparePosition;
    }

    /**
     * Get the base dimensions
     * @return Base dimensions
     */
    public float[] getBaseDimensions() {
        return baseDimensions;
    }

    /**
     * Set the base dimensions
     * @param baseDimensions Base dimensions
     */
    public void setBaseDimensions(float[] baseDimensions) {
        this.baseDimensions = baseDimensions;
    }

    /**
     * Get the compare dimensions
     * @return Compare dimensions
     */
    public float[] getCompareDimensions() {
        return compareDimensions;
    }

    /**
     * Set the compare dimensions
     * @param compareDimensions Compare dimensions
     */
    public void setCompareDimensions(float[] compareDimensions) {
        this.compareDimensions = compareDimensions;
    }

    /**
     * Check if the image is only in the base document
     * @return true if the image is only in the base document
     */
    public boolean isOnlyInBase() {
        return onlyInBase;
    }

    /**
     * Set whether the image is only in the base document
     * @param onlyInBase Whether the image is only in the base document
     */
    public void setOnlyInBase(boolean onlyInBase) {
        this.onlyInBase = onlyInBase;
    }

    /**
     * Check if the image is only in the compare document
     * @return true if the image is only in the compare document
     */
    public boolean isOnlyInCompare() {
        return onlyInCompare;
    }

    /**
     * Set whether the image is only in the compare document
     * @param onlyInCompare Whether the image is only in the compare document
     */
    public void setOnlyInCompare(boolean onlyInCompare) {
        this.onlyInCompare = onlyInCompare;
    }

    /**
     * Check if the position is different
     * @return true if the position is different
     */
    public boolean isPositionDifferent() {
        return positionDifferent;
    }

    /**
     * Set whether the position is different
     * @param positionDifferent Whether the position is different
     */
    public void setPositionDifferent(boolean positionDifferent) {
        this.positionDifferent = positionDifferent;
    }

    /**
     * Check if the size is different
     * @return true if the size is different
     */
    public boolean isSizeDifferent() {
        return sizeDifferent;
    }

    /**
     * Set whether the size is different
     * @param sizeDifferent Whether the size is different
     */
    public void setSizeDifferent(boolean sizeDifferent) {
        this.sizeDifferent = sizeDifferent;
    }

    /**
     * Check if the content is different
     * @return true if the content is different
     */
    public boolean isContentDifferent() {
        return contentDifferent;
    }

    /**
     * Set whether the content is different
     * @param contentDifferent Whether the content is different
     */
    public void setContentDifferent(boolean contentDifferent) {
        this.contentDifferent = contentDifferent;
    }

    /**
     * Get the similarity score
     * @return Similarity score
     */
    public double getSimilarityScore() {
        return similarityScore;
    }

    /**
     * Set the similarity score
     * @param similarityScore Similarity score
     */
    public void setSimilarityScore(double similarityScore) {
        this.similarityScore = similarityScore;
    }
    
    // Image element references
    private guraa.pdfcompare.core.ImageElement baseImage;
    private guraa.pdfcompare.core.ImageElement compareImage;
    private boolean dimensionsDifferent;
    private boolean formatDifferent;
    
    /**
     * Get the base image element
     * @return Base image element
     */
    public guraa.pdfcompare.core.ImageElement getBaseImage() {
        return baseImage;
    }
    
    /**
     * Set the base image element
     * @param baseImage Base image element
     */
    public void setBaseImage(guraa.pdfcompare.core.ImageElement baseImage) {
        this.baseImage = baseImage;
        if (baseImage != null) {
            this.basePosition = new float[] { baseImage.getX(), baseImage.getY() };
            this.baseDimensions = new float[] { baseImage.getWidth(), baseImage.getHeight() };
        }
    }
    
    /**
     * Get the compare image element
     * @return Compare image element
     */
    public guraa.pdfcompare.core.ImageElement getCompareImage() {
        return compareImage;
    }
    
    /**
     * Set the compare image element
     * @param compareImage Compare image element
     */
    public void setCompareImage(guraa.pdfcompare.core.ImageElement compareImage) {
        this.compareImage = compareImage;
        if (compareImage != null) {
            this.comparePosition = new float[] { compareImage.getX(), compareImage.getY() };
            this.compareDimensions = new float[] { compareImage.getWidth(), compareImage.getHeight() };
        }
    }
    
    /**
     * Check if the dimensions are different
     * @return true if the dimensions are different
     */
    public boolean isDimensionsDifferent() {
        return dimensionsDifferent;
    }
    
    /**
     * Set whether the dimensions are different
     * @param dimensionsDifferent Whether the dimensions are different
     */
    public void setDimensionsDifferent(boolean dimensionsDifferent) {
        this.dimensionsDifferent = dimensionsDifferent;
    }
    
    /**
     * Check if the format is different
     * @return true if the format is different
     */
    public boolean isFormatDifferent() {
        return formatDifferent;
    }
    
    /**
     * Set whether the format is different
     * @param formatDifferent Whether the format is different
     */
    public void setFormatDifferent(boolean formatDifferent) {
        this.formatDifferent = formatDifferent;
    }
}
