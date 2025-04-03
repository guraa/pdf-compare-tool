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
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.image.DataBufferByte;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class DifferenceCalculator {

    // SSIM constants
    private static final double K1 = 0.01;
    private static final double K2 = 0.03;
    private static final int WINDOW_SIZE = 8;

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
     * This method now uses Structural Similarity Index (SSIM) which is better than
     * pixel-by-pixel comparison for detecting visual differences.
     *
     * @param baseImage The base image
     * @param compareImage The comparison image
     * @return Difference score between 0.0 (identical) and 1.0 (completely different)
     */
    public double compareImages(BufferedImage baseImage, BufferedImage compareImage) {
        // If either image is null, they are completely different
        if (baseImage == null || compareImage == null) {
            return 1.0;
        }

        // For different-sized images, we'll use both SSIM and pixel-based comparison
        boolean differentDimensions = baseImage.getWidth() != compareImage.getWidth() ||
                baseImage.getHeight() != compareImage.getHeight();

        // Calculate SSIM score (1.0 = identical, 0.0 = completely different)
        double ssimScore = calculateSSIM(baseImage, compareImage);

        // Convert to a difference score (0.0 = identical, 1.0 = completely different)
        double ssimDiff = 1.0 - ((ssimScore + 1) / 2.0);

        // If dimensions are different, combine with a pixel-based approach
        if (differentDimensions) {
            double pixelDiff = calculatePixelBasedDifference(baseImage, compareImage);

            // Weight both scores, giving more importance to SSIM
            return 0.7 * ssimDiff + 0.3 * pixelDiff;
        }

        return ssimDiff;
    }

    /**
     * Calculate the traditional pixel-based difference for images.
     */
    private double calculatePixelBasedDifference(BufferedImage baseImage, BufferedImage compareImage) {
        // If images are very different sizes, they are very different
        double sizeDiffRatio = 1.0;
        if (baseImage != null && compareImage != null) {
            int maxWidth = Math.max(baseImage.getWidth(), compareImage.getWidth());
            int maxHeight = Math.max(baseImage.getHeight(), compareImage.getHeight());
            int minWidth = Math.min(baseImage.getWidth(), compareImage.getWidth());
            int minHeight = Math.min(baseImage.getHeight(), compareImage.getHeight());

            double areaRatio = (double)(minWidth * minHeight) / (maxWidth * maxHeight);
            sizeDiffRatio = 1.0 - areaRatio;
        }

        if (sizeDiffRatio > 0.5) {
            return 0.9; // Very different sizes
        }

        int width = Math.min(baseImage.getWidth(), compareImage.getWidth());
        int height = Math.min(baseImage.getHeight(), compareImage.getHeight());
        int totalPixels = width * height;
        int differentPixels = 0;

        // Compare pixel by pixel in the common area
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

        // Return ratio of different pixels, adjusted by size difference
        return (double) differentPixels / totalPixels * (0.5 + sizeDiffRatio * 0.5);
    }

    /**
     * Calculate the Structural Similarity Index (SSIM) between two images.
     * SSIM provides a better measure of image similarity than simple pixel-by-pixel
     * comparison, taking into account luminance, contrast, and structure.
     *
     * @param image1 First image
     * @param image2 Second image
     * @return SSIM index between -1 and 1 (1 means identical)
     */
    public double calculateSSIM(BufferedImage image1, BufferedImage image2) {
        // Handle null cases
        if (image1 == null || image2 == null) {
            return -1.0;
        }

        // Convert images to grayscale for SSIM computation
        BufferedImage img1Gray = convertToGrayscale(image1);
        BufferedImage img2Gray = convertToGrayscale(image2);

        // Resize if the images have different dimensions
        if (img1Gray.getWidth() != img2Gray.getWidth() ||
                img1Gray.getHeight() != img2Gray.getHeight()) {
            // For different sized images, we'll return a low similarity
            return -0.5;
        }

        try {
            // Extract data
            byte[] pixelsImg1 = ((DataBufferByte) img1Gray.getRaster().getDataBuffer()).getData();
            byte[] pixelsImg2 = ((DataBufferByte) img2Gray.getRaster().getDataBuffer()).getData();

            int width = img1Gray.getWidth();
            int height = img1Gray.getHeight();

            // Calculate SSIM for each window and average
            double ssimSum = 0;
            int windowCount = 0;

            // The L constant (dynamic range)
            double L = 255; // For 8-bit grayscale

            // Constants for stability
            double c1 = (K1 * L) * (K1 * L);
            double c2 = (K2 * L) * (K2 * L);

            // Process image in windows
            for (int y = 0; y <= height - WINDOW_SIZE; y += WINDOW_SIZE) {
                for (int x = 0; x <= width - WINDOW_SIZE; x += WINDOW_SIZE) {
                    double[] stats = calculateWindowStats(pixelsImg1, pixelsImg2, x, y, width, height);

                    double mean1 = stats[0];
                    double mean2 = stats[1];
                    double variance1 = stats[2];
                    double variance2 = stats[3];
                    double covariance = stats[4];

                    // Calculate SSIM for this window
                    double numerator = (2 * mean1 * mean2 + c1) * (2 * covariance + c2);
                    double denominator = (mean1 * mean1 + mean2 * mean2 + c1) *
                            (variance1 + variance2 + c2);

                    double ssim = numerator / denominator;
                    ssimSum += ssim;
                    windowCount++;
                }
            }

            // Average SSIM over all windows
            return windowCount > 0 ? ssimSum / windowCount : 0;
        } catch (Exception e) {
            log.error("Error calculating SSIM", e);
            // Fall back to a simple approach
            return -0.5;
        }
    }

    /**
     * Convert an image to grayscale.
     *
     * @param image The color image
     * @return Grayscale version of the image
     */
    private BufferedImage convertToGrayscale(BufferedImage image) {
        BufferedImage result = new BufferedImage(
                image.getWidth(),
                image.getHeight(),
                BufferedImage.TYPE_BYTE_GRAY);

        ColorConvertOp op = new ColorConvertOp(
                ColorSpace.getInstance(ColorSpace.CS_GRAY), null);
        op.filter(image, result);

        return result;
    }

    /**
     * Calculate statistics for a window in the images.
     *
     * @param pixels1 Pixels from first image
     * @param pixels2 Pixels from second image
     * @param startX X coordinate of window start
     * @param startY Y coordinate of window start
     * @param width Image width
     * @param height Image height
     * @return Array with [mean1, mean2, variance1, variance2, covariance]
     */
    private double[] calculateWindowStats(byte[] pixels1, byte[] pixels2,
                                          int startX, int startY, int width, int height) {
        double mean1 = 0, mean2 = 0;
        double variance1 = 0, variance2 = 0;
        double covariance = 0;

        // First pass: calculate means
        for (int y = startY; y < startY + WINDOW_SIZE && y < height; y++) {
            for (int x = startX; x < startX + WINDOW_SIZE && x < width; x++) {
                int pos = y * width + x;
                // Convert to unsigned [0,255]
                int val1 = pixels1[pos] & 0xFF;
                int val2 = pixels2[pos] & 0xFF;

                mean1 += val1;
                mean2 += val2;
            }
        }

        mean1 /= (WINDOW_SIZE * WINDOW_SIZE);
        mean2 /= (WINDOW_SIZE * WINDOW_SIZE);

        // Second pass: calculate variances and covariance
        for (int y = startY; y < startY + WINDOW_SIZE && y < height; y++) {
            for (int x = startX; x < startX + WINDOW_SIZE && x < width; x++) {
                int pos = y * width + x;
                int val1 = pixels1[pos] & 0xFF;
                int val2 = pixels2[pos] & 0xFF;

                variance1 += (val1 - mean1) * (val1 - mean1);
                variance2 += (val2 - mean2) * (val2 - mean2);
                covariance += (val1 - mean1) * (val2 - mean2);
            }
        }

        variance1 /= (WINDOW_SIZE * WINDOW_SIZE - 1);
        variance2 /= (WINDOW_SIZE * WINDOW_SIZE - 1);
        covariance /= (WINDOW_SIZE * WINDOW_SIZE - 1);

        return new double[] { mean1, mean2, variance1, variance2, covariance };
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