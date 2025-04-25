package guraa.pdfcompare.model.difference;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/**
 * Represents a difference between text content in two PDF documents.
 * This class stores information about the text content and
 * how it differs between the documents, including spatial information.
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
     * Similarity score between the text elements (0.0 to 1.0).
     */
    private double similarityScore;

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
     * Indicates if this is a text difference
     */
    private boolean textDifference;

    /**
     * Length of the text in the base document
     */
    private int baseTextLength;

    /**
     * Length of the text in the compare document
     */
    private int compareTextLength;

    /**
     * Indicates if this is an addition
     */
    private boolean addition;

    /**
     * Indicates if this is a deletion
     */
    private boolean deletion;

    /**
     * Indicates if this is a modification
     */
    private boolean modification;

    /**
     * X-coordinate difference between base and compare documents
     */
    private double xdifference;

    /**
     * Y-coordinate difference between base and compare documents
     */
    private double ydifference;

    /**
     * Width difference between base and compare documents
     */
    private double widthDifference;

    /**
     * Height difference between base and compare documents
     */
    private double heightDifference;

    /**
     * Factory method to create a text difference for added text.
     *
     * @param text The added text
     * @param pageNumber The page number where text was added (in compare document)
     * @param x The x-coordinate
     * @param y The y-coordinate
     * @param width The width
     * @param height The height
     * @return The text difference
     */
    public static TextDifference createAddition(
            String text, int pageNumber,
            double x, double y, double width, double height) {

        return TextDifference.builder()
                .id(UUID.randomUUID().toString())
                .compareText(text)
                .comparePageNumber(pageNumber)
                .type("text")
                .changeType("added")
                .severity(text.length() > 50 ? "major" : "minor")
                .textDifference(true)
                .addition(true)
                .compareX(x)
                .compareY(y)
                .compareWidth(width)
                .compareHeight(height)
                // Store display coordinates directly
                .baseX(0)
                .baseY(0)
                .baseWidth(0)
                .baseHeight(0)
                .build();
    }

    /**
     * Factory method to create a text difference for deleted text.
     *
     * @param text The deleted text
     * @param pageNumber The page number where text was deleted (in base document)
     * @param x The x-coordinate
     * @param y The y-coordinate
     * @param width The width
     * @param height The height
     * @return The text difference
     */
    public static TextDifference createDeletion(
            String text, int pageNumber,
            double x, double y, double width, double height) {

        return TextDifference.builder()
                .id(UUID.randomUUID().toString())
                .baseText(text)
                .basePageNumber(pageNumber)
                .type("text")
                .changeType("deleted")
                .severity(text.length() > 50 ? "major" : "minor")
                .textDifference(true)
                .deletion(true)
                .baseX(x)
                .baseY(y)
                .baseWidth(width)
                .baseHeight(height)
                // Store display coordinates directly
                .compareX(0)
                .compareY(0)
                .compareWidth(0)
                .compareHeight(0)
                .build();
    }

    /**
     * Factory method to create a text difference for modified text.
     *
     * @param baseText The base text
     * @param compareText The compare text
     * @param basePageNumber The page number in base document
     * @param comparePageNumber The page number in compare document
     * @param similarity The similarity score
     * @param baseX The x-coordinate in base document
     * @param baseY The y-coordinate in base document
     * @param baseWidth The width in base document
     * @param baseHeight The height in base document
     * @param compareX The x-coordinate in compare document
     * @param compareY The y-coordinate in compare document
     * @param compareWidth The width in compare document
     * @param compareHeight The height in compare document
     * @return The text difference
     */
    public static TextDifference createModification(
            String baseText, String compareText,
            int basePageNumber, int comparePageNumber,
            double similarity,
            double baseX, double baseY, double baseWidth, double baseHeight,
            double compareX, double compareY, double compareWidth, double compareHeight) {

        // Calculate the display coordinates (averages for better visualization)
        double displayX = (baseX + compareX) / 2;
        double displayY = (baseY + compareY) / 2;
        double displayWidth = Math.max(baseWidth, compareWidth);
        double displayHeight = Math.max(baseHeight, compareHeight);

        String severity = "cosmetic";
        int contentDiff = computeLevenshteinDistance(baseText, compareText);
        if (contentDiff > baseText.length() * 0.5) severity = "major";
        else if (contentDiff > baseText.length() * 0.2) severity = "minor";

        return TextDifference.builder()
                .id(UUID.randomUUID().toString())
                .baseText(baseText)
                .compareText(compareText)
                .basePageNumber(basePageNumber)
                .comparePageNumber(comparePageNumber)
                .baseTextLength(baseText.length())
                .compareTextLength(compareText.length())
                .type("text")
                .changeType("modified")
                .severity(severity)
                .textDifference(true)
                .modification(true)
                .similarityScore(similarity)
                .baseX(baseX)
                .baseY(baseY)
                .baseWidth(baseWidth)
                .baseHeight(baseHeight)
                .compareX(compareX)
                .compareY(compareY)
                .compareWidth(compareWidth)
                .compareHeight(compareHeight)
                .xdifference(compareX - baseX)
                .ydifference(compareY - baseY)
                .widthDifference(compareWidth - baseWidth)
                .heightDifference(compareHeight - baseHeight)
                .build();
    }

    /**
     * Calculate Levenshtein distance between two strings.
     * This is a helper method to measure string similarity.
     *
     * @param s1 The first string
     * @param s2 The second string
     * @return The Levenshtein distance
     */
    private static int computeLevenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];

        for (int i = 0; i <= s1.length(); i++) {
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0) dp[i][j] = j;
                else if (j == 0) dp[i][j] = i;
                else {
                    dp[i][j] = Math.min(
                            Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                            dp[i - 1][j - 1] + (s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1)
                    );
                }
            }
        }

        return dp[s1.length()][s2.length()];
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

    /**
     * Set the similarity score.
     *
     * @param similarityScore The similarity score
     * @return This text difference
     */
    public TextDifference similarityScore(double similarityScore) {
        this.similarityScore = similarityScore;
        return this;
    }
}