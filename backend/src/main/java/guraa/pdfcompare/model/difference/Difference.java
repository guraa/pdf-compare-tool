package guraa.pdfcompare.model.difference;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Base class for all types of differences between PDF documents.
 * This class provides common properties and methods for all difference types.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class Difference {

    /**
     * Unique identifier for this difference.
     */
    private String id;

    /**
     * The type of difference (text, image, font, style, metadata, etc.).
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
     * The page number in the base document (1-based).
     */
    private int basePageNumber;

    /**
     * The page number in the compare document (1-based).
     */
    private int comparePageNumber;

    /**
     * The x-coordinate for display purposes.
     * This represents where the difference should be visualized on the page.
     */
    private double x;

    /**
     * The y-coordinate for display purposes.
     * This represents where the difference should be visualized on the page.
     */
    private double y;

    /**
     * The width for display purposes.
     * This represents the width of the visualized difference.
     */
    private double width;

    /**
     * The height for display purposes.
     * This represents the height of the visualized difference.
     */
    private double height;

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

    /**
     * Check if this difference has valid coordinate information for display.
     *
     * @return true if the difference has valid coordinates, false otherwise
     */
    public boolean hasValidCoordinates() {
        return width > 0 && height > 0;
    }

    /**
     * Set the display coordinates for this difference.
     *
     * @param x The x-coordinate
     * @param y The y-coordinate
     * @param width The width
     * @param height The height
     * @return This difference
     */
    public Difference setDisplayCoordinates(double x, double y, double width, double height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        return this;
    }
}