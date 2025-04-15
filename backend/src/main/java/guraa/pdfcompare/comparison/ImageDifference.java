package guraa.pdfcompare.comparison;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a difference between images in two PDF documents.
 * This class stores information about the image properties and
 * how they differ between the documents.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageDifference {

    /**
     * Unique identifier for this difference.
     */
    private String id;

    /**
     * The type of difference (image).
     */
    private String type;

    /**
     * The change type (added, deleted, modified).
     */
    private String changeType;

    /**
     * The severity of the difference (major, minor, cosmetic).
     */
    private String severity;

    /**
     * A description of the difference.
     */
    private String description;

    /**
     * The x-coordinate of the image.
     */
    private double x;

    /**
     * The y-coordinate of the image.
     */
    private double y;

    /**
     * The width of the image.
     */
    private double width;

    /**
     * The height of the image.
     */
    private double height;

    /**
     * The hash of the base image.
     */
    private String baseImageHash;

    /**
     * The hash of the compare image.
     */
    private String compareImageHash;

    /**
     * The path to the base image.
     */
    private String baseImagePath;

    /**
     * The path to the compare image.
     */
    private String compareImagePath;

    /**
     * The width of the base image.
     */
    private int baseWidth;

    /**
     * The height of the base image.
     */
    private int baseHeight;

    /**
     * The width of the compare image.
     */
    private int compareWidth;

    /**
     * The height of the compare image.
     */
    private int compareHeight;

    /**
     * The similarity score between the images (0.0 to 1.0).
     */
    private double similarityScore;

    /**
     * The page number in the base document (1-based).
     */
    private int basePageNumber;

    /**
     * The page number in the compare document (1-based).
     */
    private int comparePageNumber;

    /**
     * The path to the difference image.
     */
    private String differenceImagePath;

    /**
     * Check if this difference is an addition.
     *
     * @return true if this difference is an addition, false otherwise
     */
    public boolean isAddition() {
        return "added".equals(changeType);
    }

    /**
     * Check if this difference is a deletion.
     *
     * @return true if this difference is a deletion, false otherwise
     */
    public boolean isDeletion() {
        return "deleted".equals(changeType);
    }

    /**
     * Check if this difference is a modification.
     *
     * @return true if this difference is a modification, false otherwise
     */
    public boolean isModification() {
        return "modified".equals(changeType);
    }

    /**
     * Check if this difference is a major difference.
     *
     * @return true if this difference is a major difference, false otherwise
     */
    public boolean isMajor() {
        return "major".equals(severity);
    }

    /**
     * Check if this difference is a minor difference.
     *
     * @return true if this difference is a minor difference, false otherwise
     */
    public boolean isMinor() {
        return "minor".equals(severity);
    }

    /**
     * Check if this difference is a cosmetic difference.
     *
     * @return true if this difference is a cosmetic difference, false otherwise
     */
    public boolean isCosmetic() {
        return "cosmetic".equals(severity);
    }

    /**
     * Get the area of the image.
     *
     * @return The area in square pixels
     */
    public double getArea() {
        return width * height;
    }

    /**
     * Get the area of the base image.
     *
     * @return The area in square pixels
     */
    public int getBaseArea() {
        return baseWidth * baseHeight;
    }

    /**
     * Get the area of the compare image.
     *
     * @return The area in square pixels
     */
    public int getCompareArea() {
        return compareWidth * compareHeight;
    }

    /**
     * Get the aspect ratio of the image.
     *
     * @return The aspect ratio (width / height)
     */
    public double getAspectRatio() {
        return width / height;
    }

    /**
     * Get the aspect ratio of the base image.
     *
     * @return The aspect ratio (width / height)
     */
    public double getBaseAspectRatio() {
        return (double) baseWidth / baseHeight;
    }

    /**
     * Get the aspect ratio of the compare image.
     *
     * @return The aspect ratio (width / height)
     */
    public double getCompareAspectRatio() {
        return (double) compareWidth / compareHeight;
    }

    /**
     * Get the aspect ratio difference.
     *
     * @return The aspect ratio difference
     */
    public double getAspectRatioDifference() {
        return getCompareAspectRatio() - getBaseAspectRatio();
    }

    /**
     * Get the area difference.
     *
     * @return The area difference in square pixels
     */
    public int getAreaDifference() {
        return getCompareArea() - getBaseArea();
    }

    /**
     * Get the area difference percentage.
     *
     * @return The area difference as a percentage
     */
    public double getAreaDifferencePercentage() {
        if (getBaseArea() == 0) {
            return 0.0;
        }
        return (double) getAreaDifference() / getBaseArea() * 100;
    }

    /**
     * Get the width difference.
     *
     * @return The width difference in pixels
     */
    public int getWidthDifference() {
        return compareWidth - baseWidth;
    }

    /**
     * Get the height difference.
     *
     * @return The height difference in pixels
     */
    public int getHeightDifference() {
        return compareHeight - baseHeight;
    }

    /**
     * Get the width difference percentage.
     *
     * @return The width difference as a percentage
     */
    public double getWidthDifferencePercentage() {
        if (baseWidth == 0) {
            return 0.0;
        }
        return (double) getWidthDifference() / baseWidth * 100;
    }

    /**
     * Get the height difference percentage.
     *
     * @return The height difference as a percentage
     */
    public double getHeightDifferencePercentage() {
        if (baseHeight == 0) {
            return 0.0;
        }
        return (double) getHeightDifference() / baseHeight * 100;
    }
}
