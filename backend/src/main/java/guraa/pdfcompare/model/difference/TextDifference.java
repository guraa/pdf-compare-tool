package guraa.pdfcompare.model.difference;

import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a difference between text content in two PDF documents.
 * This class stores information about the text content and
 * how it differs between the documents.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TextDifference extends Difference {

    /**
     * The text content in the base document.
     */
    private String baseText;

    /**
     * The text content in the compare document.
     */
    private String compareText;

    /**
     * The start index of the difference in the base text.
     */
    private int startIndex;

    /**
     * The end index of the difference in the base text.
     */
    private int endIndex;

    /**
     * The length of the difference.
     */
    private int length;

    /**
     * The x-coordinate in the base document.
     */
    private double baseX;

    /**
     * The y-coordinate in the base document.
     */
    private double baseY;

    /**
     * The width in the base document.
     */
    private double baseWidth;

    /**
     * The height in the base document.
     */
    private double baseHeight;

    /**
     * The x-coordinate in the compare document.
     */
    private double compareX;

    /**
     * The y-coordinate in the compare document.
     */
    private double compareY;

    /**
     * The width in the compare document.
     */
    private double compareWidth;

    /**
     * The height in the compare document.
     */
    private double compareHeight;

    /**
     * The font name in the base document.
     */
    private String baseFont;

    /**
     * The font name in the compare document.
     */
    private String compareFont;

    /**
     * The font size in the base document.
     */
    private float baseFontSize;

    /**
     * The font size in the compare document.
     */
    private float compareFontSize;

    /**
     * The text color in the base document.
     */
    private String baseColor;

    /**
     * The text color in the compare document.
     */
    private String compareColor;

    /**
     * Get the length of the base text.
     *
     * @return The length of the base text, or 0 if the base text is null
     */
    public int getBaseTextLength() {
        return baseText != null ? baseText.length() : 0;
    }

    /**
     * Get the length of the compare text.
     *
     * @return The length of the compare text, or 0 if the compare text is null
     */
    public int getCompareTextLength() {
        return compareText != null ? compareText.length() : 0;
    }

    /**
     * Get the maximum text length.
     *
     * @return The maximum of the base text length and the compare text length
     */
    public int getMaxTextLength() {
        return Math.max(getBaseTextLength(), getCompareTextLength());
    }

    /**
     * Get the minimum text length.
     *
     * @return The minimum of the base text length and the compare text length
     */
    public int getMinTextLength() {
        return Math.min(getBaseTextLength(), getCompareTextLength());
    }

    /**
     * Get the text length difference.
     *
     * @return The absolute difference between the base text length and the compare text length
     */
    public int getTextLengthDifference() {
        return Math.abs(getBaseTextLength() - getCompareTextLength());
    }

    /**
     * Get the text length difference percentage.
     *
     * @return The text length difference as a percentage of the maximum text length
     */
    public double getTextLengthDifferencePercentage() {
        int maxLength = getMaxTextLength();
        if (maxLength == 0) {
            return 0.0;
        }
        return (double) getTextLengthDifference() / maxLength;
    }

    /**
     * Check if the font has changed.
     *
     * @return true if the font has changed, false otherwise
     */
    public boolean hasFontChanged() {
        return baseFont != null && compareFont != null &&
                !baseFont.equals(compareFont);
    }

    /**
     * Check if the font size has changed.
     *
     * @return true if the font size has changed, false otherwise
     */
    public boolean hasFontSizeChanged() {
        return baseFontSize != compareFontSize;
    }

    /**
     * Check if the color has changed.
     *
     * @return true if the color has changed, false otherwise
     */
    public boolean hasColorChanged() {
        return baseColor != null && compareColor != null &&
                !baseColor.equals(compareColor);
    }

    /**
     * Check if the position has changed.
     *
     * @return true if the position has changed, false otherwise
     */
    public boolean hasPositionChanged() {
        return baseX != compareX || baseY != compareY;
    }

    /**
     * Check if the size has changed.
     *
     * @return true if the size has changed, false otherwise
     */
    public boolean hasSizeChanged() {
        return baseWidth != compareWidth || baseHeight != compareHeight;
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
     * Get the width difference.
     *
     * @return The width difference
     */
    public double getWidthDifference() {
        return compareWidth - baseWidth;
    }

    /**
     * Get the height difference.
     *
     * @return The height difference
     */
    public double getHeightDifference() {
        return compareHeight - baseHeight;
    }
}
