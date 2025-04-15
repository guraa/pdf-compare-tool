package guraa.pdfcompare.comparison;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a difference between text elements in two PDF documents.
 * This class stores information about the text content, position, and
 * formatting differences.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TextElementDifference {

    /**
     * Unique identifier for this difference.
     */
    private String id;

    /**
     * The type of difference (text, font, style, etc.).
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
     * The text content in the base document.
     */
    private String baseText;

    /**
     * The text content in the compare document.
     */
    private String compareText;

    /**
     * The page number in the base document (1-based).
     */
    private int basePageNumber;

    /**
     * The page number in the compare document (1-based).
     */
    private int comparePageNumber;

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
     * The similarity score between the text elements (0.0 to 1.0).
     */
    private double similarityScore;

    /**
     * The start index of the difference in the base text.
     */
    private int baseStartIndex;

    /**
     * The end index of the difference in the base text.
     */
    private int baseEndIndex;

    /**
     * The start index of the difference in the compare text.
     */
    private int compareStartIndex;

    /**
     * The end index of the difference in the compare text.
     */
    private int compareEndIndex;

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
}
