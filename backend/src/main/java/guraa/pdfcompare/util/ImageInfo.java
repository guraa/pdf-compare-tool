package guraa.pdfcompare.util;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Information about an image in a PDF document.
 * This class stores information about an image, such as its
 * dimensions, position, and hash.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageInfo {

    /**
     * Unique identifier for this image.
     */
    private String id;

    /**
     * The path to the image file.
     */
    private String path;

    /**
     * The width of the image in pixels.
     */
    private int width;

    /**
     * The height of the image in pixels.
     */
    private int height;

    /**
     * The x-coordinate of the image in the PDF page.
     */
    private double x;

    /**
     * The y-coordinate of the image in the PDF page.
     */
    private double y;

    /**
     * The page number where the image appears (1-based).
     */
    private int pageNumber;

    /**
     * The hash of the image, used for quick comparison.
     */
    private String hash;

    /**
     * The image format (e.g., JPEG, PNG).
     */
    private String format;

    /**
     * The color space of the image (e.g., RGB, CMYK).
     */
    private String colorSpace;

    /**
     * The bits per component of the image.
     */
    private int bitsPerComponent;

    /**
     * Whether the image is a mask.
     */
    private boolean isMask;

    /**
     * Whether the image has a mask.
     */
    private boolean hasMask;

    /**
     * The interpolation method used for the image.
     */
    private String interpolation;

    /**
     * The rendering intent of the image.
     */
    private String renderingIntent;

    /**
     * The compression method used for the image.
     */
    private String compression;

    /**
     * Get the area of the image.
     *
     * @return The area in square pixels
     */
    public int getArea() {
        return width * height;
    }

    /**
     * Get the aspect ratio of the image.
     *
     * @return The aspect ratio (width / height)
     */
    public double getAspectRatio() {
        return (double) width / height;
    }

    /**
     * Check if the image is landscape.
     *
     * @return true if the image is landscape, false otherwise
     */
    public boolean isLandscape() {
        return width > height;
    }

    /**
     * Check if the image is portrait.
     *
     * @return true if the image is portrait, false otherwise
     */
    public boolean isPortrait() {
        return height > width;
    }

    /**
     * Check if the image is square.
     *
     * @return true if the image is square, false otherwise
     */
    public boolean isSquare() {
        return width == height;
    }
}
