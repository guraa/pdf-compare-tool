package guraa.pdfcompare.model.difference;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/**
 * Represents a difference between images in two PDF documents.
 * This class stores information about the image properties and
 * how they differ between the documents, with enhanced coordinate support.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ImageDifference extends Difference {

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
     * The x-coordinate in the base document.
     */
    private double baseX;

    /**
     * The y-coordinate in the base document.
     */
    private double baseY;

    /**
     * The x-coordinate in the compare document.
     */
    private double compareX;

    /**
     * The y-coordinate in the compare document.
     */
    private double compareY;

    /**
     * The similarity score between the images (0.0 to 1.0).
     */
    private double similarityScore;

    /**
     * The path to the difference image.
     */
    private String differenceImagePath;

    /**
     * Factory method to create an image addition difference.
     *
     * @param imagePath The path to the added image
     * @param pageNumber The page number where image was added
     * @param x The x-coordinate
     * @param y The y-coordinate
     * @param width The width
     * @param height The height
     * @param imageHash The image hash
     * @return The image difference
     */
    public static ImageDifference createAddition(
            String imagePath, int pageNumber, double x, double y,
            double width, double height, String imageHash) {

        return ImageDifference.builder()
                .id(UUID.randomUUID().toString())
                .compareImagePath(imagePath)
                .comparePageNumber(pageNumber)
                .type("image")
                .changeType("added")
                .severity("major")
                .x(x)
                .y(y)
                .width(width)
                .height(height)
                .compareX(x)
                .compareY(y)
                .compareWidth((int) width)
                .compareHeight((int) height)
                .compareImageHash(imageHash)
                .build();
    }

    /**
     * Factory method to create an image deletion difference.
     *
     * @param imagePath The path to the deleted image
     * @param pageNumber The page number where image was deleted
     * @param x The x-coordinate
     * @param y The y-coordinate
     * @param width The width
     * @param height The height
     * @param imageHash The image hash
     * @return The image difference
     */
    public static ImageDifference createDeletion(
            String imagePath, int pageNumber, double x, double y,
            double width, double height, String imageHash) {

        return ImageDifference.builder()
                .id(UUID.randomUUID().toString())
                .baseImagePath(imagePath)
                .basePageNumber(pageNumber)
                .type("image")
                .changeType("deleted")
                .severity("major")
                .x(x)
                .y(y)
                .width(width)
                .height(height)
                .baseX(x)
                .baseY(y)
                .baseWidth((int) width)
                .baseHeight((int) height)
                .baseImageHash(imageHash)
                .build();
    }

    /**
     * Factory method to create an image modification difference.
     *
     * @param baseImagePath The path to the base image
     * @param compareImagePath The path to the compare image
     * @param basePageNumber The page number in base document
     * @param comparePageNumber The page number in compare document
     * @param baseX The x-coordinate in base document
     * @param baseY The y-coordinate in base document
     * @param baseWidth The width in base document
     * @param baseHeight The height in base document
     * @param compareX The x-coordinate in compare document
     * @param compareY The y-coordinate in compare document
     * @param compareWidth The width in compare document
     * @param compareHeight The height in compare document
     * @param similarityScore The similarity score
     * @param baseImageHash The base image hash
     * @param compareImageHash The compare image hash
     * @return The image difference
     */
    public static ImageDifference createModification(
            String baseImagePath, String compareImagePath,
            int basePageNumber, int comparePageNumber,
            double baseX, double baseY, double baseWidth, double baseHeight,
            double compareX, double compareY, double compareWidth, double compareHeight,
            double similarityScore, String baseImageHash, String compareImageHash) {

        // Use a sensible value for display coordinates (average of base and compare)
        double displayX = (baseX + compareX) / 2;
        double displayY = (baseY + compareY) / 2;
        double displayWidth = Math.max(baseWidth, compareWidth);
        double displayHeight = Math.max(baseHeight, compareHeight);

        // Determine severity based on similarity score
        String severity;
        if (similarityScore < 0.7) {
            severity = "major";
        } else if (similarityScore < 0.9) {
            severity = "minor";
        } else {
            severity = "cosmetic";
        }

        return ImageDifference.builder()
                .id(UUID.randomUUID().toString())
                .baseImagePath(baseImagePath)
                .compareImagePath(compareImagePath)
                .basePageNumber(basePageNumber)
                .comparePageNumber(comparePageNumber)
                .type("image")
                .changeType("modified")
                .severity(severity)
                .x(displayX)
                .y(displayY)
                .width(displayWidth)
                .height(displayHeight)
                .baseX(baseX)
                .baseY(baseY)
                .compareX(compareX)
                .compareY(compareY)
                .baseWidth((int) baseWidth)
                .baseHeight((int) baseHeight)
                .compareWidth((int) compareWidth)
                .compareHeight((int) compareHeight)
                .similarityScore(similarityScore)
                .baseImageHash(baseImageHash)
                .compareImageHash(compareImageHash)
                .build();
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
        return width / (height > 0 ? height : 1); // Avoid division by zero
    }

    /**
     * Get the aspect ratio of the base image.
     *
     * @return The aspect ratio (width / height)
     */
    public double getBaseAspectRatio() {
        return (double) baseWidth / (baseHeight > 0 ? baseHeight : 1);
    }

    /**
     * Get the aspect ratio of the compare image.
     *
     * @return The aspect ratio (width / height)
     */
    public double getCompareAspectRatio() {
        return (double) compareWidth / (compareHeight > 0 ? compareHeight : 1);
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

    /**
     * Get the x-coordinate difference.
     *
     * @return The x-coordinate difference
     */
    public double getXDifference() {
        return compareX - baseX;
    }

    /**
     * Get the y-coordinate difference.
     *
     * @return The y-coordinate difference
     */
    public double getYDifference() {
        return compareY - baseY;
    }

    /**
     * Ensure this difference has valid display coordinates.
     * If coordinates are missing or zero, they are calculated from base/compare values.
     */
    public void ensureDisplayCoordinates() {
        // If we already have display coordinates, we're good
        if (getX() > 0 && getY() > 0 && getWidth() > 0 && getHeight() > 0) {
            return;
        }

        // Otherwise, calculate display coordinates based on what we have
        double displayX = 0, displayY = 0, displayWidth = 0, displayHeight = 0;

        if (isAddition()) {
            // For additions, use compare values
            displayX = compareX > 0 ? compareX : 100;
            displayY = compareY > 0 ? compareY : 100;
            displayWidth = compareWidth > 0 ? compareWidth : 100;
            displayHeight = compareHeight > 0 ? compareHeight : 100;
        } else if (isDeletion()) {
            // For deletions, use base values
            displayX = baseX > 0 ? baseX : 100;
            displayY = baseY > 0 ? baseY : 100;
            displayWidth = baseWidth > 0 ? baseWidth : 100;
            displayHeight = baseHeight > 0 ? baseHeight : 100;
        } else {
            // For modifications, use average or max values
            if (baseWidth > 0 && compareWidth > 0) {
                displayX = (baseX + compareX) / 2;
                displayY = (baseY + compareY) / 2;
                displayWidth = Math.max(baseWidth, compareWidth);
                displayHeight = Math.max(baseHeight, compareHeight);
            } else if (baseWidth > 0) {
                displayX = baseX;
                displayY = baseY;
                displayWidth = baseWidth;
                displayHeight = baseHeight;
            } else if (compareWidth > 0) {
                displayX = compareX;
                displayY = compareY;
                displayWidth = compareWidth;
                displayHeight = compareHeight;
            } else {
                // Default values
                displayX = 100;
                displayY = 100;
                displayWidth = 100;
                displayHeight = 100;
            }
        }

        // Set the display coordinates
        setX(displayX);
        setY(displayY);
        setWidth(displayWidth);
        setHeight(displayHeight);
    }
}