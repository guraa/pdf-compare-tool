package guraa.pdfcompare.util;

import guraa.pdfcompare.model.difference.Difference;
import guraa.pdfcompare.model.difference.ImageDifference;
import guraa.pdfcompare.model.difference.TextDifference;
import guraa.pdfcompare.service.PageDifference;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Utility class for handling coordinate data in difference objects.
 * Helps ensure all difference types have proper coordinate information for display.
 */
@Slf4j
public class DifferenceCoordinateUtils {

    /**
     * Private constructor to prevent instantiation.
     */
    private DifferenceCoordinateUtils() {
        // Utility class, no instances allowed
    }

    /**
     * Extract a PageDifference from a Difference, preserving coordinate information.
     *
     * @param difference The source difference
     * @return A PageDifference with coordinates
     */
    public static PageDifference createPageDifference(Difference difference) {
        // Start with basic properties
        PageDifference.PageDifferenceBuilder builder = PageDifference.builder()
                .id(difference.getId() != null ? difference.getId() : UUID.randomUUID().toString())
                .type(difference.getType())
                .severity(difference.getSeverity())
                .description(difference.getDescription())
                .basePageNumber(difference.getBasePageNumber())
                .comparePageNumber(difference.getComparePageNumber());

        // Add coordinates for display
        if (difference.hasValidCoordinates()) {
            // Use the existing coordinates
            builder.x(difference.getX())
                    .y(difference.getY())
                    .width(difference.getWidth())
                    .height(difference.getHeight());
        } else {
            // Try to extract coordinates based on difference type
            if (difference instanceof TextDifference) {
                extractTextDifferenceCoordinates((TextDifference) difference, builder);
            } else if (difference instanceof ImageDifference) {
                extractImageDifferenceCoordinates((ImageDifference) difference, builder);
            } else {
                // Use default values for other difference types
                builder.x(100).y(100).width(100).height(20);
            }
        }

        return builder.build();
    }

    /**
     * Extract coordinates from a TextDifference.
     *
     * @param difference The TextDifference
     * @param builder The PageDifference builder
     */
    private static void extractTextDifferenceCoordinates(
            TextDifference difference, PageDifference.PageDifferenceBuilder builder) {

        if (difference.isAddition() && difference.getCompareX() > 0) {
            // For additions, use compare coordinates
            builder.x(difference.getCompareX())
                    .y(difference.getCompareY())
                    .width(difference.getCompareWidth() > 0 ? difference.getCompareWidth() : 100)
                    .height(difference.getCompareHeight() > 0 ? difference.getCompareHeight() : 14);
        } else if (difference.isDeletion() && difference.getBaseX() > 0) {
            // For deletions, use base coordinates
            builder.x(difference.getBaseX())
                    .y(difference.getBaseY())
                    .width(difference.getBaseWidth() > 0 ? difference.getBaseWidth() : 100)
                    .height(difference.getBaseHeight() > 0 ? difference.getBaseHeight() : 14);
        } else if (difference.isModification()) {
            // For modifications, use average or whichever is available
            double x = 0, y = 0, width = 0, height = 0;

            if (difference.getBaseX() > 0 && difference.getCompareX() > 0) {
                // Both available, use average
                x = (difference.getBaseX() + difference.getCompareX()) / 2;
                y = (difference.getBaseY() + difference.getCompareY()) / 2;
                width = Math.max(difference.getBaseWidth(), difference.getCompareWidth());
                height = Math.max(difference.getBaseHeight(), difference.getCompareHeight());
            } else if (difference.getBaseX() > 0) {
                // Only base coordinates available
                x = difference.getBaseX();
                y = difference.getBaseY();
                width = difference.getBaseWidth();
                height = difference.getBaseHeight();
            } else if (difference.getCompareX() > 0) {
                // Only compare coordinates available
                x = difference.getCompareX();
                y = difference.getCompareY();
                width = difference.getCompareWidth();
                height = difference.getCompareHeight();
            } else {
                // No coordinates available, use defaults
                x = 100;
                y = 100;
                width = 100;
                height = 14;
            }

            // Ensure width and height are non-zero
            width = width > 0 ? width : 100;
            height = height > 0 ? height : 14;

            builder.x(x).y(y).width(width).height(height);
        } else {
            // Use default values
            builder.x(100).y(100).width(100).height(14);
        }
    }

    /**
     * Extract coordinates from an ImageDifference.
     * Updated to handle Java 17 compatibility.
     *
     * @param difference The ImageDifference
     * @param builder The PageDifference builder
     */
    private static void extractImageDifferenceCoordinates(
            ImageDifference difference, PageDifference.PageDifferenceBuilder builder) {

        // For Java 17 compatibility, handle potential null safely
        if (difference == null) {
            // Use default coordinates if difference is null
            builder.x(100).y(100).width(100).height(100);
            return;
        }

        // Try to ensure display coordinates are set on the image difference
        try {
            // Try to call ensureDisplayCoordinates safely
            Method ensureMethod = difference.getClass().getMethod("ensureDisplayCoordinates");
            ensureMethod.invoke(difference);
        } catch (Exception e) {
            // Safely ignore if the method doesn't exist or can't be called
            // We'll handle coordinates normally below
            log.debug("Could not ensure display coordinates via reflection: {}", e.getMessage());
        }

        // Get coordinate values with proper defaults
        double x = difference.getX();
        double y = difference.getY();
        double width = difference.getWidth();
        double height = difference.getHeight();

        // Try to use base/compare coordinates if main ones are missing
        if (x == 0 && y == 0) {
            if (difference.isAddition()) {
                // For additions, check compareX/Y first
                try {
                    double compareX = difference.getCompareX();
                    double compareY = difference.getCompareY();

                    if (compareX > 0 || compareY > 0) {
                        x = compareX;
                        y = compareY;
                    }
                } catch (Exception e) {
                    // If getCompareX/Y doesn't exist or fails, ignore
                }
            } else if (difference.isDeletion()) {
                // For deletions, check baseX/Y first
                try {
                    double baseX = difference.getBaseX();
                    double baseY = difference.getBaseY();

                    if (baseX > 0 || baseY > 0) {
                        x = baseX;
                        y = baseY;
                    }
                } catch (Exception e) {
                    // If getBaseX/Y doesn't exist or fails, ignore
                }
            }
        }

        // If width/height are zero or NaN, set defaults
        if (width <= 0 || Double.isNaN(width)) width = 400;
        if (height <= 0 || Double.isNaN(height)) height = 300;

        // Make sure x/y are reasonable
        if (x <= 0 || Double.isNaN(x)) x = 100;
        if (y <= 0 || Double.isNaN(y)) y = 100;

        // Set the coordinates on the builder
        builder.x(x)
                .y(y)
                .width(width)
                .height(height);
    }

    /**
     * Ensure a difference has valid coordinates.
     * If coordinates are missing, this will set default values.
     *
     * @param difference The difference to update
     * @return The updated difference
     */
    public static Difference ensureValidCoordinates(Difference difference) {
        if (!difference.hasValidCoordinates()) {
            // Assign default values based on difference type
            if (difference instanceof TextDifference) {
                ensureTextDifferenceCoordinates((TextDifference) difference);
            } else if (difference instanceof ImageDifference) {
                ensureImageDifferenceCoordinates((ImageDifference) difference);
            } else {
                // Default values for other types
                difference.setDisplayCoordinates(100, 100, 100, 20);
            }
        }
        return difference;
    }

    /**
     * Ensure a TextDifference has valid coordinates.
     *
     * @param difference The TextDifference to update
     *                        */
    private static void ensureTextDifferenceCoordinates(TextDifference difference) {
        // Apply the same logic as in extractTextDifferenceCoordinates
        double x = 100, y = 100, width = 100, height = 14;

        if (difference.isAddition() && difference.getCompareX() > 0) {
            x = difference.getCompareX();
            y = difference.getCompareY();
            width = difference.getCompareWidth() > 0 ? difference.getCompareWidth() : 100;
            height = difference.getCompareHeight() > 0 ? difference.getCompareHeight() : 14;
        } else if (difference.isDeletion() && difference.getBaseX() > 0) {
            x = difference.getBaseX();
            y = difference.getBaseY();
            width = difference.getBaseWidth() > 0 ? difference.getBaseWidth() : 100;
            height = difference.getBaseHeight() > 0 ? difference.getBaseHeight() : 14;
        } else if (difference.isModification()) {
            if (difference.getBaseX() > 0 && difference.getCompareX() > 0) {
                x = (difference.getBaseX() + difference.getCompareX()) / 2;
                y = (difference.getBaseY() + difference.getCompareY()) / 2;
                width = Math.max(difference.getBaseWidth(), difference.getCompareWidth());
                height = Math.max(difference.getBaseHeight(), difference.getCompareHeight());
            } else if (difference.getBaseX() > 0) {
                x = difference.getBaseX();
                y = difference.getBaseY();
                width = difference.getBaseWidth();
                height = difference.getBaseHeight();
            } else if (difference.getCompareX() > 0) {
                x = difference.getCompareX();
                y = difference.getCompareY();
                width = difference.getCompareWidth();
                height = difference.getCompareHeight();
            }
        }

        // Set the coordinates on the difference
        difference.setDisplayCoordinates(x, y, width > 0 ? width : 100, height > 0 ? height : 14);
    }

    /**
     * Ensure an ImageDifference has valid coordinates.
     *
     * @param difference The ImageDifference to update
     */
    private static void ensureImageDifferenceCoordinates(ImageDifference difference) {
        // Just ensure coordinates are positive numbers
        double x = difference.getX() > 0 ? difference.getX() : 100;
        double y = difference.getY() > 0 ? difference.getY() : 100;
        double width = difference.getWidth() > 0 ? difference.getWidth() : 100;
        double height = difference.getHeight() > 0 ? difference.getHeight() : 100;

        difference.setDisplayCoordinates(x, y, width, height);
    }
}