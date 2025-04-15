package guraa.pdfcompare.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a difference between two pages.
 * This class stores summary information about a difference,
 * such as its type, severity, and description.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageDifference {

    /**
     * Unique identifier for this difference.
     */
    private String id;

    /**
     * The type of difference (text, image, font, style, metadata, etc.).
     */
    private String type;

    /**
     * The severity of the difference (major, minor, cosmetic).
     */
    private String severity;

    /**
     * A description of the difference.
     */
    private String description;

    /**
     * The page number in the base document (1-based).
     */
    private int basePageNumber;

    /**
     * The page number in the compare document (1-based).
     */
    private int comparePageNumber;

    /**
     * The x-coordinate of the difference.
     */
    private double x;

    /**
     * The y-coordinate of the difference.
     */
    private double y;

    /**
     * The width of the difference.
     */
    private double width;

    /**
     * The height of the difference.
     */
    private double height;

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
     * Check if this difference is a text difference.
     *
     * @return true if this difference is a text difference, false otherwise
     */
    public boolean isTextDifference() {
        return "text".equals(type);
    }

    /**
     * Check if this difference is an image difference.
     *
     * @return true if this difference is an image difference, false otherwise
     */
    public boolean isImageDifference() {
        return "image".equals(type);
    }

    /**
     * Check if this difference is a font difference.
     *
     * @return true if this difference is a font difference, false otherwise
     */
    public boolean isFontDifference() {
        return "font".equals(type);
    }

    /**
     * Check if this difference is a style difference.
     *
     * @return true if this difference is a style difference, false otherwise
     */
    public boolean isStyleDifference() {
        return "style".equals(type);
    }

    /**
     * Check if this difference is a metadata difference.
     *
     * @return true if this difference is a metadata difference, false otherwise
     */
    public boolean isMetadataDifference() {
        return "metadata".equals(type);
    }
}
