package guraa.pdfcompare.model.difference;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Represents a difference between fonts in two PDF documents.
 * This class stores information about the font properties and
 * how they differ between the documents.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class FontDifference extends Difference {

    /**
     * The font name in the base document.
     */
    private String baseFontName;

    /**
     * The font name in the compare document.
     */
    private String compareFontName;

    /**
     * The font family in the base document.
     */
    private String baseFontFamily;

    /**
     * The font family in the compare document.
     */
    private String compareFontFamily;

    /**
     * The font style in the base document.
     */
    private String baseFontStyle;

    /**
     * The font style in the compare document.
     */
    private String compareFontStyle;

    /**
     * The font size in the base document.
     */
    private float baseFontSize;

    /**
     * The font size in the compare document.
     */
    private float compareFontSize;

    /**
     * The font encoding in the base document.
     */
    private String baseFontEncoding;

    /**
     * The font encoding in the compare document.
     */
    private String compareFontEncoding;

    /**
     * Whether the font is embedded in the base document.
     */
    private boolean baseFontEmbedded;

    /**
     * Whether the font is embedded in the compare document.
     */
    private boolean compareFontEmbedded;

    /**
     * The number of times the font is used in the base document.
     */
    private int baseUsageCount;

    /**
     * The number of times the font is used in the compare document.
     */
    private int compareUsageCount;

    /**
     * Check if the font family has changed.
     *
     * @return true if the font family has changed, false otherwise
     */
    public boolean hasFamilyChanged() {
        return baseFontFamily != null && compareFontFamily != null &&
                !baseFontFamily.equals(compareFontFamily);
    }

    /**
     * Check if the font style has changed.
     *
     * @return true if the font style has changed, false otherwise
     */
    public boolean hasStyleChanged() {
        return baseFontStyle != null && compareFontStyle != null &&
                !baseFontStyle.equals(compareFontStyle);
    }

    /**
     * Check if the font size has changed.
     *
     * @return true if the font size has changed, false otherwise
     */
    public boolean hasSizeChanged() {
        return baseFontSize != compareFontSize;
    }

    /**
     * Check if the font encoding has changed.
     *
     * @return true if the font encoding has changed, false otherwise
     */
    public boolean hasEncodingChanged() {
        return baseFontEncoding != null && compareFontEncoding != null &&
                !baseFontEncoding.equals(compareFontEncoding);
    }

    /**
     * Check if the font embedding has changed.
     *
     * @return true if the font embedding has changed, false otherwise
     */
    public boolean hasEmbeddingChanged() {
        return baseFontEmbedded != compareFontEmbedded;
    }

    /**
     * Get the font size difference.
     *
     * @return The font size difference
     */
    public float getFontSizeDifference() {
        return compareFontSize - baseFontSize;
    }

    /**
     * Get the font size difference percentage.
     *
     * @return The font size difference as a percentage
     */
    public float getFontSizeDifferencePercentage() {
        if (baseFontSize == 0) {
            return 0;
        }
        return (compareFontSize - baseFontSize) / baseFontSize * 100;
    }

    /**
     * Get the usage count difference.
     *
     * @return The usage count difference
     */
    public int getUsageCountDifference() {
        return compareUsageCount - baseUsageCount;
    }

    /**
     * Get the usage count difference percentage.
     *
     * @return The usage count difference as a percentage
     */
    public float getUsageCountDifferencePercentage() {
        if (baseUsageCount == 0) {
            return 0;
        }
        return (float) (compareUsageCount - baseUsageCount) / baseUsageCount * 100;
    }
}
