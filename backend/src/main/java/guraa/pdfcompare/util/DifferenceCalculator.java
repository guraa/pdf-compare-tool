package guraa.pdfcompare.util;

import guraa.pdfcompare.model.difference.Difference;
import guraa.pdfcompare.model.difference.TextDifference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Utility class for calculating differences between PDF documents with enhanced coordinate handling.
 * Uses the CoordinateTransformer for consistent coordinate transformations.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DifferenceCalculator {

    private static final double MINOR_THRESHOLD = 0.1;  // 10% difference
    private static final double MAJOR_THRESHOLD = 0.3;  // 30% difference
    private static final double CRITICAL_THRESHOLD = 0.6; // 60% difference

    private final CoordinateTransformer coordinateTransformer;

    /**
     * Set position and bounds for a difference with proper coordinate handling.
     * This method ensures every difference has proper coordinates.
     *
     * @param difference The difference to update with position information
     * @param x X-coordinate of the difference (display space - already transformed)
     * @param y Y-coordinate of the difference (display space - already transformed)
     * @param width Width of the difference
     * @param height Height of the difference
     */
    public void setPositionAndBounds(Difference difference, double x, double y, double width, double height) {
        difference.setX(x);
        difference.setY(y);
        difference.setWidth(width);
        difference.setHeight(height);

        // Set bounds for highlighting - these are in display coordinates
        difference.setLeft(x);
        difference.setTop(y);
        difference.setRight(x + width);
        difference.setBottom(y + height);
    }

    /**
     * Set position and bounds for a difference using PDF coordinates.
     * Automatically transforms from PDF space to display space.
     *
     * @param difference The difference to update with position information
     * @param pdfX X-coordinate of the difference in PDF space
     * @param pdfY Y-coordinate of the difference in PDF space
     * @param width Width of the difference
     * @param height Height of the difference
     * @param pageHeight Height of the page for coordinate transformation
     */
    public void setPdfPositionAndBounds(Difference difference, double pdfX, double pdfY,
                                        double width, double height, double pageHeight) {
        // Transform from PDF space to display space
        CoordinateTransformer.Rectangle displayRect = coordinateTransformer.pdfRectToDisplay(
                pdfX, pdfY, width, height, pageHeight);

        // Set position in display space
        setPositionAndBounds(difference,
                displayRect.getX(),
                displayRect.getY(),
                displayRect.getWidth(),
                displayRect.getHeight());
    }

    /**
     * Compare text content between two pages.
     *
     * @param baseText Text from base page
     * @param compareText Text from comparison page
     * @param comparisonMethod Method to use for comparison (e.g., "word", "character")
     * @return List of text differences
     */
    public List<TextDifference> compareText(String baseText, String compareText, String comparisonMethod) {
        List<TextDifference> differences = new ArrayList<>();

        if (baseText == null || compareText == null) {
            if (baseText != null && !baseText.trim().isEmpty()) {
                // Base has text, compare doesn't - entire text is deleted
                TextDifference diff = TextDifference.builder()
                        .id(UUID.randomUUID().toString())
                        .type("text")
                        .changeType("deleted")
                        .severity("major")
                        .text(baseText)
                        .baseText(baseText)
                        .description("Text deleted")
                        .build();
                differences.add(diff);
            } else if (compareText != null && !compareText.trim().isEmpty()) {
                // Compare has text, base doesn't - entire text is added
                TextDifference diff = TextDifference.builder()
                        .id(UUID.randomUUID().toString())
                        .type("text")
                        .changeType("added")
                        .severity("major")
                        .text(compareText)
                        .compareText(compareText)
                        .description("Text added")
                        .build();
                differences.add(diff);
            }

            return differences;
        }

        if (baseText.equals(compareText)) {
            return differences; // No differences
        }

        // Choose comparison method based on configuration
        if ("character".equalsIgnoreCase(comparisonMethod)) {
            return compareCharacters(baseText, compareText);
        } else {
            return compareWords(baseText, compareText);
        }
    }

    /**
     * Compare text by words.
     *
     * @param baseText Base text
     * @param compareText Comparison text
     * @return List of text differences
     */
    private List<TextDifference> compareWords(String baseText, String compareText) {
        List<TextDifference> differences = new ArrayList<>();

        // Split texts into words
        String[] baseWords = baseText.split("\\s+");
        String[] compareWords = compareText.split("\\s+");

        // Simple word-by-word comparison
        int i = 0, j = 0;
        while (i < baseWords.length || j < compareWords.length) {
            if (i >= baseWords.length) {
                // All remaining compare words are added
                StringBuilder added = new StringBuilder();
                while (j < compareWords.length) {
                    added.append(compareWords[j++]).append(" ");
                }

                TextDifference diff = TextDifference.builder()
                        .id(UUID.randomUUID().toString())
                        .type("text")
                        .changeType("added")
                        .severity("minor")
                        .text(added.toString().trim())
                        .compareText(added.toString().trim())
                        .description("Text added: \"" + added.toString().trim() + "\"")
                        .build();
                differences.add(diff);
                break;
            } else if (j >= compareWords.length) {
                // All remaining base words are deleted
                StringBuilder deleted = new StringBuilder();
                while (i < baseWords.length) {
                    deleted.append(baseWords[i++]).append(" ");
                }

                TextDifference diff = TextDifference.builder()
                        .id(UUID.randomUUID().toString())
                        .type("text")
                        .changeType("deleted")
                        .severity("minor")
                        .text(deleted.toString().trim())
                        .baseText(deleted.toString().trim())
                        .description("Text deleted: \"" + deleted.toString().trim() + "\"")
                        .build();
                differences.add(diff);
                break;
            } else if (baseWords[i].equals(compareWords[j])) {
                // Words match, move to next words
                i++;
                j++;
            } else {
                // Words don't match, try to find next match
                int nextMatchI = findNextMatch(baseWords, compareWords[j], i);
                int nextMatchJ = findNextMatch(compareWords, baseWords[i], j);

                if (nextMatchI == -1 && nextMatchJ == -1) {
                    // Modified word
                    TextDifference diff = TextDifference.builder()
                            .id(UUID.randomUUID().toString())
                            .type("text")
                            .changeType("modified")
                            .severity("minor")
                            .text(baseWords[i])
                            .baseText(baseWords[i])
                            .compareText(compareWords[j])
                            .description("Text changed from \"" + baseWords[i] + "\" to \"" + compareWords[j] + "\"")
                            .build();
                    differences.add(diff);
                    i++;
                    j++;
                } else if (nextMatchI == -1 || (nextMatchJ != -1 && nextMatchJ - j < nextMatchI - i)) {
                    // Added word(s)
                    StringBuilder added = new StringBuilder();
                    int originalJ = j;
                    while (j < nextMatchJ) {
                        added.append(compareWords[j++]).append(" ");
                    }

                    TextDifference diff = TextDifference.builder()
                            .id(UUID.randomUUID().toString())
                            .type("text")
                            .changeType("added")
                            .severity("minor")
                            .text(added.toString().trim())
                            .compareText(added.toString().trim())
                            .description("Text added: \"" + added.toString().trim() + "\"")
                            .build();
                    differences.add(diff);
                } else {
                    // Deleted word(s)
                    StringBuilder deleted = new StringBuilder();
                    int originalI = i;
                    while (i < nextMatchI) {
                        deleted.append(baseWords[i++]).append(" ");
                    }

                    TextDifference diff = TextDifference.builder()
                            .id(UUID.randomUUID().toString())
                            .type("text")
                            .changeType("deleted")
                            .severity("minor")
                            .text(deleted.toString().trim())
                            .baseText(deleted.toString().trim())
                            .description("Text deleted: \"" + deleted.toString().trim() + "\"")
                            .build();
                    differences.add(diff);
                }
            }
        }

        return differences;
    }

    /**
     * Compare text by characters using the Longest Common Subsequence algorithm.
     *
     * @param baseText Base text
     * @param compareText Comparison text
     * @return List of text differences
     */
    private List<TextDifference> compareCharacters(String baseText, String compareText) {
        List<TextDifference> differences = new ArrayList<>();

        // Build the LCS table
        int[][] lcs = new int[baseText.length() + 1][compareText.length() + 1];

        for (int i = 1; i <= baseText.length(); i++) {
            for (int j = 1; j <= compareText.length(); j++) {
                if (baseText.charAt(i - 1) == compareText.charAt(j - 1)) {
                    lcs[i][j] = lcs[i - 1][j - 1] + 1;
                } else {
                    lcs[i][j] = Math.max(lcs[i - 1][j], lcs[i][j - 1]);
                }
            }
        }

        // Backtrack to find differences
        int i = baseText.length();
        int j = compareText.length();

        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && baseText.charAt(i - 1) == compareText.charAt(j - 1)) {
                // Characters match
                i--;
                j--;
            } else if (j > 0 && (i == 0 || lcs[i][j - 1] >= lcs[i - 1][j])) {
                // Character added
                int endJ = j;
                while (j > 0 && (i == 0 || lcs[i][j - 1] >= lcs[i - 1][j])) {
                    j--;
                }

                String added = compareText.substring(j, endJ);
                TextDifference diff = TextDifference.builder()
                        .id(UUID.randomUUID().toString())
                        .type("text")
                        .changeType("added")
                        .severity("minor")
                        .text(added)
                        .compareText(added)
                        .description("Text added: \"" + added + "\"")
                        .build();
                differences.add(diff);
            } else if (i > 0) {
                // Character deleted
                int endI = i;
                while (i > 0 && (j == 0 || lcs[i - 1][j] > lcs[i][j - 1])) {
                    i--;
                }

                String deleted = baseText.substring(i, endI);
                TextDifference diff = TextDifference.builder()
                        .id(UUID.randomUUID().toString())
                        .type("text")
                        .changeType("deleted")
                        .severity("minor")
                        .text(deleted)
                        .baseText(deleted)
                        .description("Text deleted: \"" + deleted + "\"")
                        .build();
                differences.add(diff);
            }
        }

        return differences;
    }

    /**
     * Find the next occurrence of a word in an array.
     *
     * @param words Array of words to search
     * @param target Target word to find
     * @param startIndex Index to start searching from
     * @return Index of the next match, or -1 if not found
     */
    private int findNextMatch(String[] words, String target, int startIndex) {
        for (int i = startIndex; i < words.length; i++) {
            if (words[i].equals(target)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Compare images and calculate difference score.
     *
     * @param baseImage Base image
     * @param compareImage Comparison image
     * @return Difference score between 0.0 and 1.0
     */
    public double compareImages(byte[] baseImage, byte[] compareImage) {
        if (baseImage == null || compareImage == null) {
            return 1.0; // Maximum difference
        }

        try {
            BufferedImage img1 = ImageIO.read(new ByteArrayInputStream(baseImage));
            BufferedImage img2 = ImageIO.read(new ByteArrayInputStream(compareImage));

            if (img1 == null || img2 == null) {
                return 1.0; // Maximum difference
            }

            // Resize images to common size for comparison
            int width = Math.min(img1.getWidth(), img2.getWidth());
            int height = Math.min(img1.getHeight(), img2.getHeight());

            // Calculate difference
            long diffPixels = 0;
            long totalPixels = width * height;

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int rgb1 = img1.getRGB(x, y);
                    int rgb2 = img2.getRGB(x, y);

                    if (rgb1 != rgb2) {
                        diffPixels++;
                    }
                }
            }

            return (double) diffPixels / totalPixels;
        } catch (IOException e) {
            log.error("Error comparing images: {}", e.getMessage(), e);
            return 1.0; // Maximum difference on error
        }
    }

    /**
     * Calculate severity based on difference type and score.
     *
     * @param differenceType Type of difference
     * @param differenceScore Difference score between 0.0 and 1.0
     * @return Severity level ("minor", "major", or "critical")
     */
    public String calculateSeverity(String differenceType, double differenceScore) {
        if ("text".equals(differenceType)) {
            if (differenceScore < MINOR_THRESHOLD) {
                return "minor";
            } else if (differenceScore < MAJOR_THRESHOLD) {
                return "major";
            } else {
                return "critical";
            }
        } else if ("image".equals(differenceType)) {
            if (differenceScore < MINOR_THRESHOLD) {
                return "minor";
            } else if (differenceScore < CRITICAL_THRESHOLD) {
                return "major";
            } else {
                return "critical";
            }
        } else {
            return "minor"; // Default for other types
        }
    }

    /**
     * Estimate coordinates for differences without position data.
     * Uses a page percentage-based approach to place differences in reasonable locations.
     *
     * @param difference The difference to assign coordinates to
     * @param pageWidth Width of the page
     * @param pageHeight Height of the page
     * @param relativeY Relative Y position (0.0-1.0) to use if no other reference is available
     */
    public void estimatePositionForDifference(Difference difference, double pageWidth, double pageHeight, double relativeY) {
        // If coordinates are already set, don't override them
        if (difference.getX() != 0 || difference.getY() != 0) {
            return;
        }

        // Default width and height (percentage of page)
        double defaultWidth = pageWidth * 0.5;
        double defaultHeight = pageHeight * 0.05;

        // Calculate absolute Y position from relative Y
        double absoluteY = pageHeight * relativeY;

        // Set position based on difference type
        double x = pageWidth * 0.1; // Default to 10% from left
        double y = absoluteY; // Use calculated absolute Y position

        if ("font".equals(difference.getType())) {
            // For font differences, place near the top of the page
            y = pageHeight * 0.1;
            defaultHeight = pageHeight * 0.03;
        } else if ("metadata".equals(difference.getType())) {
            // For metadata differences, place at the top
            y = pageHeight * 0.05;
            defaultHeight = pageHeight * 0.03;
        } else if ("image".equals(difference.getType())) {
            // For image differences, use a larger area
            defaultWidth = pageWidth * 0.6;
            defaultHeight = pageHeight * 0.25;
        }

        // Set the estimated position and bounds
        setPositionAndBounds(difference, x, y, defaultWidth, defaultHeight);
    }

    /**
     * Apply consistent adjustment to all differences in a list.
     * This can be used to align differences when scaling or repositioning is needed.
     *
     * @param differences List of differences to adjust
     * @param scaleX X scale factor
     * @param scaleY Y scale factor
     * @param offsetX X offset
     * @param offsetY Y offset
     */
    public void adjustDifferencePositions(List<Difference> differences,
                                          double scaleX, double scaleY,
                                          double offsetX, double offsetY) {
        if (differences == null || differences.isEmpty()) {
            return;
        }

        for (Difference diff : differences) {
            // Adjust position
            double newX = diff.getX() * scaleX + offsetX;
            double newY = diff.getY() * scaleY + offsetY;
            double newWidth = diff.getWidth() * scaleX;
            double newHeight = diff.getHeight() * scaleY;

            // Update position and bounds
            setPositionAndBounds(diff, newX, newY, newWidth, newHeight);
        }
    }
}