package guraa.pdfcompare.service;

import guraa.pdfcompare.model.difference.Difference;
import guraa.pdfcompare.model.difference.StyleDifference;
import guraa.pdfcompare.util.DifferenceCalculator;
import guraa.pdfcompare.util.TextElement;
import lombok.extern.slf4j.Slf4j;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Service for comparing text elements between documents.
 * Detects style differences such as font, size, color, and positioning.
 */
@Slf4j
public class TextElementComparisonService {

    /**
     * Compare text elements between two pages.
     *
     * @param baseElements The text elements from the base page
     * @param compareElements The text elements from the comparison page
     * @param calculator The difference calculator for creating difference objects
     * @return List of style differences
     */
    public static List<Difference> compareTextElements(
            List<TextElement> baseElements,
            List<TextElement> compareElements,
            DifferenceCalculator calculator) {

        // Handle null or empty inputs
        if (baseElements == null || baseElements.isEmpty() ||
                compareElements == null || compareElements.isEmpty()) {
            return Collections.emptyList();
        }

        List<Difference> differences = new ArrayList<>();

        try {
            // Sort elements by Y position then X position
            List<TextElement> sortedBaseElements = new ArrayList<>(baseElements);
            List<TextElement> sortedCompareElements = new ArrayList<>(compareElements);

            Collections.sort(sortedBaseElements,
                    Comparator.comparing(TextElement::getY).thenComparing(TextElement::getX));
            Collections.sort(sortedCompareElements,
                    Comparator.comparing(TextElement::getY).thenComparing(TextElement::getX));

            // Match text elements with similar positions
            for (TextElement baseElement : sortedBaseElements) {
                // Skip null elements
                if (baseElement == null || baseElement.getText() == null || baseElement.getText().isEmpty()) {
                    continue;
                }

                TextElement matchingElement = findMatchingElement(baseElement, sortedCompareElements);

                if (matchingElement != null) {
                    // Check for style differences
                    boolean fontDifferent = !baseElement.getFontName().equals(matchingElement.getFontName());
                    boolean sizeDifferent = Math.abs(baseElement.getFontSize() - matchingElement.getFontSize()) > 0.1f;

                    // Check color difference - handle potential null colors
                    boolean colorDifferent = false;
                    Color baseColor = baseElement.getColor();
                    Color compareColor = matchingElement.getColor();

                    if ((baseColor == null && compareColor != null) ||
                            (baseColor != null && compareColor == null)) {
                        colorDifferent = true;
                    } else if (baseColor != null && compareColor != null) {
                        colorDifferent = !baseColor.equals(compareColor);
                    }

                    // If there are style differences, create a difference object
                    if (fontDifferent || sizeDifferent || colorDifferent) {
                        StyleDifference diff = createStyleDifference(baseElement, matchingElement,
                                fontDifferent, sizeDifferent, colorDifferent);

                        // Set position from base element
                        // Use setPositionAndBounds instead of the non-existent setPositionForDifference
                        calculator.setPositionAndBounds(diff, baseElement.getX(), baseElement.getY(),
                                baseElement.getWidth(), baseElement.getHeight());

                        differences.add(diff);
                    }
                }
            }

            // Find elements unique to base document (deleted)
            for (TextElement baseElement : sortedBaseElements) {
                if (baseElement == null || baseElement.getText() == null || baseElement.getText().isEmpty()) {
                    continue;
                }

                boolean found = false;
                for (TextElement compareElement : sortedCompareElements) {
                    if (compareElement == null) {
                        continue;
                    }

                    if (areSimilar(baseElement, compareElement)) {
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    // Create a style difference for deleted element
                    StyleDifference diff = createDeletedStyleDifference(baseElement);
                    // Use setPositionAndBounds instead of the non-existent setPositionForDifference
                    calculator.setPositionAndBounds(diff, baseElement.getX(), baseElement.getY(),
                            baseElement.getWidth(), baseElement.getHeight());
                    differences.add(diff);
                }
            }

            // Find elements unique to compare document (added)
            for (TextElement compareElement : sortedCompareElements) {
                if (compareElement == null || compareElement.getText() == null || compareElement.getText().isEmpty()) {
                    continue;
                }

                boolean found = false;
                for (TextElement baseElement : sortedBaseElements) {
                    if (baseElement == null) {
                        continue;
                    }

                    if (areSimilar(compareElement, baseElement)) {
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    // Create a style difference for added element
                    StyleDifference diff = createAddedStyleDifference(compareElement);
                    // Use setPositionAndBounds instead of the non-existent setPositionForDifference
                    calculator.setPositionAndBounds(diff, compareElement.getX(), compareElement.getY(),
                            compareElement.getWidth(), compareElement.getHeight());
                    differences.add(diff);
                }
            }
        } catch (Exception e) {
            log.warn("Error comparing text elements: {}", e.getMessage(), e);
        }

        return differences;
    }

    /**
     * Find a matching text element in the target list.
     * A match is determined by similar text content and position.
     *
     * @param sourceElement The source text element
     * @param targetElements The list of target elements to search
     * @return The matching element or null if not found
     */
    private static TextElement findMatchingElement(TextElement sourceElement, List<TextElement> targetElements) {
        if (sourceElement == null || targetElements == null || targetElements.isEmpty()) {
            return null;
        }

        String sourceText = sourceElement.getText();
        if (sourceText == null || sourceText.isEmpty()) {
            return null;
        }

        // Find elements with the exact same text
        List<TextElement> sameTextElements = new ArrayList<>();
        for (TextElement target : targetElements) {
            if (target != null && sourceText.equals(target.getText())) {
                sameTextElements.add(target);
            }
        }

        if (sameTextElements.isEmpty()) {
            // If no exact text match, try to find similar elements
            for (TextElement target : targetElements) {
                if (target != null && areSimilar(sourceElement, target)) {
                    sameTextElements.add(target);
                }
            }
        }

        if (sameTextElements.isEmpty()) {
            return null;
        }

        // Find the closest element by position
        TextElement closest = null;
        double minDistance = Double.MAX_VALUE;

        for (TextElement element : sameTextElements) {
            double distance = sourceElement.distanceTo(element);
            if (distance < minDistance) {
                minDistance = distance;
                closest = element;
            }
        }

        // Only consider it a match if it's within a reasonable distance
        if (minDistance < 100) { // Arbitrary threshold, adjust as needed
            return closest;
        }

        return null;
    }

    /**
     * Determine if two text elements are similar.
     * Similar means they have similar text content or position.
     *
     * @param element1 The first text element
     * @param element2 The second text element
     * @return true if the elements are similar, false otherwise
     */
    private static boolean areSimilar(TextElement element1, TextElement element2) {
        if (element1 == null || element2 == null) {
            return false;
        }

        if (element1.getText() == null || element2.getText() == null) {
            return false;
        }

        // Check if the text is the same
        if (element1.getText().equals(element2.getText())) {
            return true;
        }

        // Check if the text is similar (case-insensitive)
        if (element1.getText().equalsIgnoreCase(element2.getText())) {
            return true;
        }

        // Check if one text contains the other
        if (element1.getText().contains(element2.getText()) ||
                element2.getText().contains(element1.getText())) {
            return true;
        }

        // Check if position is very close
        double distance = element1.distanceTo(element2);
        if (distance < 10) { // Arbitrary threshold, adjust as needed
            return true;
        }

        return false;
    }

    /**
     * Create a style difference object for modified elements.
     *
     * @param baseElement The base element
     * @param compareElement The compare element
     * @param fontDifferent Whether the font is different
     * @param sizeDifferent Whether the size is different
     * @param colorDifferent Whether the color is different
     * @return The style difference object
     */
    private static StyleDifference createStyleDifference(
            TextElement baseElement,
            TextElement compareElement,
            boolean fontDifferent,
            boolean sizeDifferent,
            boolean colorDifferent) {

        StyleDifference diff = new StyleDifference();
        diff.setId(UUID.randomUUID().toString());
        diff.setType("style");
        diff.setChangeType("modified");

        // Determine severity based on the nature of the changes
        if (fontDifferent) {
            diff.setSeverity("minor");
        } else if (sizeDifferent) {
            diff.setSeverity("cosmetic");
        } else {
            diff.setSeverity("cosmetic");
        }

        // Build description
        StringBuilder description = new StringBuilder("Style changed for text '");
        description.append(baseElement.getText()).append("': ");

        if (fontDifferent) {
            description.append("Font changed from '")
                    .append(baseElement.getFontName())
                    .append("' to '")
                    .append(compareElement.getFontName())
                    .append("'. ");
        }

        if (sizeDifferent) {
            description.append("Size changed from ")
                    .append(String.format("%.1f", baseElement.getFontSize()))
                    .append(" to ")
                    .append(String.format("%.1f", compareElement.getFontSize()))
                    .append(". ");
        }

        if (colorDifferent) {
            Color baseColor = baseElement.getColor();
            Color compareColor = compareElement.getColor();
            description.append("Color changed");

            if (baseColor != null && compareColor != null) {
                description.append(" from RGB(")
                        .append(baseColor.getRed()).append(",")
                        .append(baseColor.getGreen()).append(",")
                        .append(baseColor.getBlue())
                        .append(") to RGB(")
                        .append(compareColor.getRed()).append(",")
                        .append(compareColor.getGreen()).append(",")
                        .append(compareColor.getBlue())
                        .append(")");
            }

            description.append(". ");
        }

        diff.setDescription(description.toString().trim());

        // Set style properties
        diff.setText(baseElement.getText());
        diff.setBaseStyle(createStyleObject(baseElement));
        diff.setCompareStyle(createStyleObject(compareElement));

        return diff;
    }

    /**
     * Create a style difference object for deleted elements.
     *
     * @param baseElement The deleted element
     * @return The style difference object
     */
    private static StyleDifference createDeletedStyleDifference(TextElement baseElement) {
        StyleDifference diff = new StyleDifference();
        diff.setId(UUID.randomUUID().toString());
        diff.setType("style");
        diff.setChangeType("deleted");
        diff.setSeverity("minor");

        diff.setDescription("Style element deleted: '" + baseElement.getText() + "'");
        diff.setText(baseElement.getText());
        diff.setBaseStyle(createStyleObject(baseElement));

        return diff;
    }

    /**
     * Create a style difference object for added elements.
     *
     * @param compareElement The added element
     * @return The style difference object
     */
    private static StyleDifference createAddedStyleDifference(TextElement compareElement) {
        StyleDifference diff = new StyleDifference();
        diff.setId(UUID.randomUUID().toString());
        diff.setType("style");
        diff.setChangeType("added");
        diff.setSeverity("minor");

        diff.setDescription("Style element added: '" + compareElement.getText() + "'");
        diff.setText(compareElement.getText());
        diff.setCompareStyle(createStyleObject(compareElement));

        return diff;
    }

    /**
     * Create a style object from a text element.
     *
     * @param element The text element
     * @return Style object as a string
     */
    private static String createStyleObject(TextElement element) {
        if (element == null) {
            return "{}";
        }

        StringBuilder style = new StringBuilder("{");
        style.append("\"font\":\"").append(element.getFontName()).append("\",");
        style.append("\"size\":").append(element.getFontSize()).append(",");

        Color color = element.getColor();
        if (color != null) {
            style.append("\"color\":\"rgb(")
                    .append(color.getRed()).append(",")
                    .append(color.getGreen()).append(",")
                    .append(color.getBlue())
                    .append(")\",");
        }

        style.append("\"x\":").append(element.getX()).append(",");
        style.append("\"y\":").append(element.getY()).append(",");
        style.append("\"width\":").append(element.getWidth()).append(",");
        style.append("\"height\":").append(element.getHeight());
        style.append("}");

        return style.toString();
    }
}
