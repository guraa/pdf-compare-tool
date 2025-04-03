package guraa.pdfcompare.util;

import com.github.difflib.DiffUtils;
import com.github.difflib.algorithm.DiffException;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;
import guraa.pdfcompare.model.difference.Difference;
import guraa.pdfcompare.model.difference.TextDifference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class DifferenceCalculator {

    /**
     * Compare two text strings and generate text differences.
     *
     * @param baseText The base text
     * @param compareText The comparison text
     * @param method Comparison method ("exact", "smart", "fuzzy")
     * @return List of TextDifference objects
     */
    public List<TextDifference> compareText(String baseText, String compareText, String method) {
        List<TextDifference> differences = new ArrayList<>();

        if (baseText == null && compareText == null) {
            return differences;
        }

        if (baseText == null) {
            // Text only in compare document
            TextDifference diff = TextDifference.builder()
                    .id(UUID.randomUUID().toString())
                    .type("text")
                    .changeType("added")
                    .severity("major")
                    .compareText(compareText)
                    .description("Text added in comparison document")
                    .build();
            differences.add(diff);
            return differences;
        }

        if (compareText == null) {
            // Text only in base document
            TextDifference diff = TextDifference.builder()
                    .id(UUID.randomUUID().toString())
                    .type("text")
                    .changeType("deleted")
                    .severity("major")
                    .baseText(baseText)
                    .description("Text deleted in comparison document")
                    .build();
            differences.add(diff);
            return differences;
        }

        // Case where both texts exist - use diff algorithm
        try {
            List<String> baseLines;
            List<String> compareLines;

            // Apply different preprocessing based on method
            if ("exact".equals(method)) {
                baseLines = Arrays.asList(baseText.split("\n"));
                compareLines = Arrays.asList(compareText.split("\n"));
            } else if ("smart".equals(method)) {
                // Normalize whitespace and handle as separate words
                baseLines = Arrays.asList(baseText.replaceAll("\\s+", " ").trim().split(" "));
                compareLines = Arrays.asList(compareText.replaceAll("\\s+", " ").trim().split(" "));
            } else if ("fuzzy".equals(method)) {
                // For fuzzy matching, we'd normally apply more extensive preprocessing
                // Here's a simple approach using character-level diffing
                baseLines = Arrays.asList(baseText.replaceAll("\\s+", " ").trim().split(""));
                compareLines = Arrays.asList(compareText.replaceAll("\\s+", " ").trim().split(""));
            } else {
                // Default to word-level comparison
                baseLines = Arrays.asList(baseText.replaceAll("\\s+", " ").trim().split(" "));
                compareLines = Arrays.asList(compareText.replaceAll("\\s+", " ").trim().split(" "));
            }

            // Calculate differences
            Patch<String> patch = DiffUtils.diff(baseLines, compareLines);

            // Process each delta (change)
            for (AbstractDelta<String> delta : patch.getDeltas()) {
                String diffId = UUID.randomUUID().toString();
                String changeType;
                String severity = "minor";
                String description;

                StringBuilder baseStr = new StringBuilder();
                StringBuilder compareStr = new StringBuilder();

                // Get text from delta source and target
                for (String s : delta.getSource().getLines()) {
                    baseStr.append(s).append(" ");
                }
                for (String s : delta.getTarget().getLines()) {
                    compareStr.append(s).append(" ");
                }

                // Determine change type and description
                switch (delta.getType()) {
                    case INSERT:
                        changeType = "added";
                        description = "Text added: " + compareStr.toString().trim();
                        break;
                    case DELETE:
                        changeType = "deleted";
                        description = "Text deleted: " + baseStr.toString().trim();
                        break;
                    case CHANGE:
                        changeType = "modified";
                        description = "Text changed from \"" + baseStr.toString().trim() +
                                "\" to \"" + compareStr.toString().trim() + "\"";
                        break;
                    default:
                        changeType = "unknown";
                        description = "Unknown change type";
                }

                // Create text difference object
                TextDifference diff = TextDifference.builder()
                        .id(diffId)
                        .type("text")
                        .changeType(changeType)
                        .severity(severity)
                        .baseText(baseStr.toString().trim())
                        .compareText(compareStr.toString().trim())
                        .description(description)
                        .build();

                differences.add(diff);
            }
        } catch (DiffException e) {
            log.error("Error calculating text differences", e);
        }

        return differences;
    }

    /**
     * Compare two images and calculate their differences.
     *
     * @param baseImage The base image
     * @param compareImage The comparison image
     * @return Difference score between 0.0 (identical) and 1.0 (completely different)
     */
    public double compareImages(BufferedImage baseImage, BufferedImage compareImage) {
        // If images are different sizes, they are different
        if (baseImage.getWidth() != compareImage.getWidth() ||
                baseImage.getHeight() != compareImage.getHeight()) {
            return 1.0;
        }

        int width = baseImage.getWidth();
        int height = baseImage.getHeight();
        int totalPixels = width * height;
        int differentPixels = 0;

        // Compare pixel by pixel
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int baseRGB = baseImage.getRGB(x, y);
                int compareRGB = compareImage.getRGB(x, y);

                if (baseRGB != compareRGB) {
                    // Extract RGB components
                    Color baseColor = new Color(baseRGB);
                    Color compareColor = new Color(compareRGB);

                    // Calculate color distance
                    double colorDistance = getColorDistance(baseColor, compareColor);

                    // Consider pixels different if the color distance is above a threshold
                    if (colorDistance > 0.1) {
                        differentPixels++;
                    }
                }
            }
        }

        // Return ratio of different pixels
        return (double) differentPixels / totalPixels;
    }

    /**
     * Calculate the Euclidean distance between two colors in RGB space.
     *
     * @param c1 First color
     * @param c2 Second color
     * @return Distance between 0.0 (identical) and 1.0 (maximally different)
     */
    private double getColorDistance(Color c1, Color c2) {
        double rDiff = (c1.getRed() - c2.getRed()) / 255.0;
        double gDiff = (c1.getGreen() - c2.getGreen()) / 255.0;
        double bDiff = (c1.getBlue() - c2.getBlue()) / 255.0;

        return Math.sqrt(rDiff * rDiff + gDiff * gDiff + bDiff * bDiff) / Math.sqrt(3.0);
    }

    /**
     * Create a visual diff of two images, highlighting differences.
     *
     * @param baseImage The base image
     * @param compareImage The comparison image
     * @return New image with differences highlighted
     */
    public BufferedImage createVisualDiff(BufferedImage baseImage, BufferedImage compareImage) {
        // Use largest dimensions to create output image
        int width = Math.max(baseImage.getWidth(), compareImage.getWidth());
        int height = Math.max(baseImage.getHeight(), compareImage.getHeight());

        BufferedImage diffImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        // Overlay background with a light gray color
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                diffImage.setRGB(x, y, Color.LIGHT_GRAY.getRGB());
            }
        }

        // Find common dimensions
        int commonWidth = Math.min(baseImage.getWidth(), compareImage.getWidth());
        int commonHeight = Math.min(baseImage.getHeight(), compareImage.getHeight());

        // Compare pixel by pixel in common area
        for (int y = 0; y < commonHeight; y++) {
            for (int x = 0; x < commonWidth; x++) {
                int baseRGB = baseImage.getRGB(x, y);
                int compareRGB = compareImage.getRGB(x, y);

                if (baseRGB == compareRGB) {
                    // Identical pixels shown in grayscale
                    Color color = new Color(baseRGB);
                    int gray = (color.getRed() + color.getGreen() + color.getBlue()) / 3;
                    Color grayColor = new Color(gray, gray, gray);
                    diffImage.setRGB(x, y, grayColor.getRGB());
                } else {
                    // Highlight different pixels in red
                    diffImage.setRGB(x, y, Color.RED.getRGB());
                }
            }
        }

        // Highlight areas only in base image (blue)
        for (int y = 0; y < baseImage.getHeight(); y++) {
            for (int x = 0; x < baseImage.getWidth(); x++) {
                if (x >= commonWidth || y >= commonHeight) {
                    diffImage.setRGB(x, y, Color.BLUE.getRGB());
                }
            }
        }

        // Highlight areas only in compare image (green)
        for (int y = 0; y < compareImage.getHeight(); y++) {
            for (int x = 0; x < compareImage.getWidth(); x++) {
                if (x >= commonWidth || y >= commonHeight) {
                    diffImage.setRGB(x, y, Color.GREEN.getRGB());
                }
            }
        }

        return diffImage;
    }

    /**
     * Determine severity of a difference based on change magnitude.
     *
     * @param differenceType The type of difference
     * @param sizeFactor Relative size of the difference (0.0 to 1.0)
     * @return Severity level ("critical", "major", "minor", "info")
     */
    public String calculateSeverity(String differenceType, double sizeFactor) {
        // Adjust thresholds based on difference type
        double criticalThreshold;
        double majorThreshold;
        double minorThreshold;

        switch (differenceType) {
            case "text":
                criticalThreshold = 0.8;
                majorThreshold = 0.4;
                minorThreshold = 0.1;
                break;
            case "image":
                criticalThreshold = 0.7;
                majorThreshold = 0.3;
                minorThreshold = 0.1;
                break;
            case "font":
                criticalThreshold = 0.9;
                majorThreshold = 0.5;
                minorThreshold = 0.2;
                break;
            case "style":
                criticalThreshold = 0.9;
                majorThreshold = 0.6;
                minorThreshold = 0.3;
                break;
            case "metadata":
                criticalThreshold = 0.9;
                majorThreshold = 0.7;
                minorThreshold = 0.4;
                break;
            default:
                criticalThreshold = 0.8;
                majorThreshold = 0.5;
                minorThreshold = 0.2;
        }

        // Determine severity based on thresholds
        if (sizeFactor >= criticalThreshold) {
            return "critical";
        } else if (sizeFactor >= majorThreshold) {
            return "major";
        } else if (sizeFactor >= minorThreshold) {
            return "minor";
        } else {
            return "info";
        }
    }

    /**
     * Set position and bounds for a difference.
     *
     * @param difference The difference object to update
     * @param x X-coordinate
     * @param y Y-coordinate
     * @param width Width of the difference
     * @param height Height of the difference
     */
    public void setPositionAndBounds(Difference difference, double x, double y, double width, double height) {
        Difference.Position position = new Difference.Position(x, y, 0);
        Difference.Bounds bounds = new Difference.Bounds(width, height);

        difference.setPosition(position);
        difference.setBounds(bounds);
    }
}