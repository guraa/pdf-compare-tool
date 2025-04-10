package guraa.pdfcompare.service;

import guraa.pdfcompare.model.difference.Difference;
import guraa.pdfcompare.model.difference.TextDifference;
import guraa.pdfcompare.model.difference.StyleDifference;
import guraa.pdfcompare.util.TextElement;
import guraa.pdfcompare.util.DifferenceCalculator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service for comparing text elements between documents.
 */
@Slf4j
@Service
public class TextElementComparisonService {

    /**
     * Compare text elements between two pages.
     *
     * @param baseElements Text elements from the base page
     * @param compareElements Text elements from the comparison page
     * @param differenceCalculator The difference calculator
     * @return List of differences
     */
    public static List<Difference> compareTextElements(
            List<TextElement> baseElements,
            List<TextElement> compareElements,
            DifferenceCalculator differenceCalculator) {

        List<Difference> differences = new ArrayList<>();

        // Match elements based on similar position and text
        Map<TextElement, TextElement> matches = matchTextElements(baseElements, compareElements);

        // Track elements that have been matched
        Set<TextElement> matchedBaseElements = new HashSet<>(matches.keySet());
        Set<TextElement> matchedCompareElements = new HashSet<>(matches.values());

        // Find style differences in matched elements
        for (Map.Entry<TextElement, TextElement> match : matches.entrySet()) {
            TextElement baseElement = match.getKey();
            TextElement compareElement = match.getValue();

            // Compare font size
            boolean fontSizeDifferent = Math.abs(baseElement.getFontSize() - compareElement.getFontSize()) > 0.1;

            String baseFontName = baseElement.getFontName();
            String compareFontName = compareElement.getFontName();

            baseFontName = baseFontName.contains("+") ? baseFontName.substring(baseFontName.indexOf("+") + 1) : baseFontName;
            compareFontName = compareFontName.contains("+") ? compareFontName.substring(compareFontName.indexOf("+") + 1) : compareFontName;

            boolean fontNameDifferent = !baseFontName.equals(compareFontName);

            // Compare color
            boolean colorDifferent = !compareColors(baseElement.getColor(), compareElement.getColor());

            if (fontSizeDifferent || fontNameDifferent || colorDifferent) {
                // Create style difference
                String diffId = UUID.randomUUID().toString();

                // Create description
                StringBuilder description = new StringBuilder("Style differs for text \"")
                        .append(baseElement.getText())
                        .append("\": ");

                if (fontSizeDifferent) {
                    description.append("Font size changed from ")
                            .append(baseElement.getFontSize())
                            .append(" to ")
                            .append(compareElement.getFontSize())
                            .append(". ");
                }

                if (fontNameDifferent) {
                    description.append("Font changed from \"")
                            .append(baseElement.getFontName())
                            .append("\" to \"")
                            .append(compareElement.getFontName())
                            .append("\". ");
                }

                if (colorDifferent) {
                    description.append("Color changed from ")
                            .append(baseElement.getColor())
                            .append(" to ")
                            .append(compareElement.getColor())
                            .append(".");
                }

                // Create style difference
                StyleDifference diff = StyleDifference.builder()
                        .id(diffId)
                        .type("style")
                        .changeType("modified")
                        .severity("minor")
                        .description(description.toString())
                        .text(baseElement.getText())
                        .baseColor(baseElement.getColor())
                        .compareColor(compareElement.getColor())
                        .build();

                // Set position and bounds - using the base element's coordinates
                differenceCalculator.setPositionAndBounds(
                        diff,
                        baseElement.getX(),
                        baseElement.getY(),
                        baseElement.getWidth(),
                        baseElement.getHeight());

                differences.add(diff);
            }
        }

        // Elements only in base document (deleted)
        for (TextElement element : baseElements) {
            if (!matchedBaseElements.contains(element)) {
                // Create text difference for deleted element
                String diffId = UUID.randomUUID().toString();

                TextDifference diff = TextDifference.builder()
                        .id(diffId)
                        .type("text")
                        .changeType("deleted")
                        .severity("minor")
                        .text(element.getText())
                        .baseText(element.getText())
                        .description("Text \"" + element.getText() + "\" deleted")
                        .build();

                // Set position and bounds from the element
                differenceCalculator.setPositionAndBounds(
                        diff,
                        element.getX(),
                        element.getY(),
                        element.getWidth(),
                        element.getHeight());

                differences.add(diff);
            }
        }

        // Elements only in compare document (added)
        for (TextElement element : compareElements) {
            if (!matchedCompareElements.contains(element)) {
                // Create text difference for added element
                String diffId = UUID.randomUUID().toString();

                TextDifference diff = TextDifference.builder()
                        .id(diffId)
                        .type("text")
                        .changeType("added")
                        .severity("minor")
                        .text(element.getText())
                        .compareText(element.getText())
                        .description("Text \"" + element.getText() + "\" added")
                        .build();

                // Set position and bounds from the compare element
                differenceCalculator.setPositionAndBounds(
                        diff,
                        element.getX(),
                        element.getY(),
                        element.getWidth(),
                        element.getHeight());

                differences.add(diff);
            }
        }

        return differences;
    }

    /**
     * Match text elements between base and comparison pages.
     *
     * @param baseElements Text elements from base page
     * @param compareElements Text elements from comparison page
     * @return Map of matched elements
     */
    private static Map<TextElement, TextElement> matchTextElements(
            List<TextElement> baseElements,
            List<TextElement> compareElements) {

        Map<TextElement, TextElement> matches = new HashMap<>();

        // Create a list of potential matches
        List<ElementMatch> potentialMatches = new ArrayList<>();

        for (TextElement baseElement : baseElements) {
            for (TextElement compareElement : compareElements) {
                // Calculate similarity based on text content and position
                double textSimilarity = calculateTextSimilarity(
                        baseElement.getText(), compareElement.getText());

                // Calculate position similarity
                double positionSimilarity = 1.0 -
                        (Math.abs(baseElement.getX() - compareElement.getX()) +
                                Math.abs(baseElement.getY() - compareElement.getY())) / 100.0;

                // Ensure position similarity is between 0 and 1
                positionSimilarity = Math.max(0.0, Math.min(1.0, positionSimilarity));

                // Combine similarities
                double overallSimilarity = 0.7 * textSimilarity + 0.3 * positionSimilarity;

                // Only consider matches with at least 50% similarity
                if (overallSimilarity >= 0.5) {
                    potentialMatches.add(new ElementMatch(
                            baseElement, compareElement, overallSimilarity));
                }
            }
        }

        // Sort potential matches by similarity (highest first)
        potentialMatches.sort((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()));

        // Assign matches greedily
        Set<TextElement> matchedBaseElements = new HashSet<>();
        Set<TextElement> matchedCompareElements = new HashSet<>();

        for (ElementMatch match : potentialMatches) {
            TextElement baseElement = match.getBaseElement();
            TextElement compareElement = match.getCompareElement();

            // Skip if either element is already matched
            if (matchedBaseElements.contains(baseElement) ||
                    matchedCompareElements.contains(compareElement)) {
                continue;
            }

            // Create match
            matches.put(baseElement, compareElement);

            // Mark as matched
            matchedBaseElements.add(baseElement);
            matchedCompareElements.add(compareElement);
        }

        return matches;
    }

    /**
     * Compare two colors.
     *
     * @param color1 First color
     * @param color2 Second color
     * @return True if colors are similar
     */
    private static boolean compareColors(String color1, String color2) {
        if (color1 == null || color2 == null) {
            return color1 == color2;
        }

        if (color1.equals(color2)) {
            return true;
        }

        // Parse RGB values
        int[] rgb1 = parseRgb(color1);
        int[] rgb2 = parseRgb(color2);

        if (rgb1 == null || rgb2 == null) {
            return false;
        }

        // Calculate color distance
        double distance = Math.sqrt(
                Math.pow(rgb1[0] - rgb2[0], 2) +
                        Math.pow(rgb1[1] - rgb2[1], 2) +
                        Math.pow(rgb1[2] - rgb2[2], 2)
        );

        // Colors are similar if distance is less than a threshold
        return distance < 30; // Threshold of 30 (out of 441.67 max)
    }

    /**
     * Parse RGB values from a CSS color string.
     *
     * @param color The color string (like "rgb(r,g,b)")
     * @return Array of RGB values
     */
    private static int[] parseRgb(String color) {
        if (color == null) {
            return null;
        }

        // Handle rgb() format
        if (color.startsWith("rgb(") && color.endsWith(")")) {
            String[] parts = color.substring(4, color.length() - 1).split(",");
            if (parts.length >= 3) {
                try {
                    int r = Integer.parseInt(parts[0].trim());
                    int g = Integer.parseInt(parts[1].trim());
                    int b = Integer.parseInt(parts[2].trim());
                    return new int[]{ r, g, b };
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }

        // Handle hex format (#rrggbb)
        if (color.startsWith("#") && (color.length() == 7 || color.length() == 4)) {
            try {
                if (color.length() == 7) {
                    int r = Integer.parseInt(color.substring(1, 3), 16);
                    int g = Integer.parseInt(color.substring(3, 5), 16);
                    int b = Integer.parseInt(color.substring(5, 7), 16);
                    return new int[]{ r, g, b };
                } else {
                    // Short form #rgb
                    int r = Integer.parseInt(color.substring(1, 2) + color.substring(1, 2), 16);
                    int g = Integer.parseInt(color.substring(2, 3) + color.substring(2, 3), 16);
                    int b = Integer.parseInt(color.substring(3, 4) + color.substring(3, 4), 16);
                    return new int[]{ r, g, b };
                }
            } catch (NumberFormatException e) {
                return null;
            }
        }

        return null;
    }

    /**
     * Calculate similarity between two text strings.
     *
     * @param text1 First text
     * @param text2 Second text
     * @return Similarity score between 0.0 and 1.0
     */
    private static double calculateTextSimilarity(String text1, String text2) {
        if (text1 == null || text2 == null) {
            return 0.0;
        }

        if (text1.equals(text2)) {
            return 1.0;
        }

        // Calculate Levenshtein distance
        int[][] distance = new int[text1.length() + 1][text2.length() + 1];

        for (int i = 0; i <= text1.length(); i++) {
            distance[i][0] = i;
        }

        for (int j = 0; j <= text2.length(); j++) {
            distance[0][j] = j;
        }

        for (int i = 1; i <= text1.length(); i++) {
            for (int j = 1; j <= text2.length(); j++) {
                if (text1.charAt(i - 1) == text2.charAt(j - 1)) {
                    distance[i][j] = distance[i - 1][j - 1];
                } else {
                    distance[i][j] = Math.min(
                            distance[i - 1][j] + 1,     // Delete
                            Math.min(
                                    distance[i][j - 1] + 1,     // Insert
                                    distance[i - 1][j - 1] + 1  // Substitute
                            )
                    );
                }
            }
        }

        int maxLength = Math.max(text1.length(), text2.length());
        if (maxLength == 0) {
            return 1.0; // Both strings are empty
        }

        return 1.0 - (double) distance[text1.length()][text2.length()] / maxLength;
    }

    /**
     * Text element match structure.
     */
    private static class ElementMatch {
        private final TextElement baseElement;
        private final TextElement compareElement;
        private final double similarity;

        public ElementMatch(TextElement baseElement,
                            TextElement compareElement,
                            double similarity) {
            this.baseElement = baseElement;
            this.compareElement = compareElement;
            this.similarity = similarity;
        }

        public TextElement getBaseElement() { return baseElement; }
        public TextElement getCompareElement() { return compareElement; }
        public double getSimilarity() { return similarity; }
    }
}