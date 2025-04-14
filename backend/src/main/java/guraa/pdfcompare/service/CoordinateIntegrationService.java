package guraa.pdfcompare.service;

import guraa.pdfcompare.model.difference.Difference;
import guraa.pdfcompare.util.CoordinateTransformer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDPage;
import org.springframework.stereotype.Service;

import java.awt.geom.Point2D;
import java.util.List;

/**
 * Service that integrates all coordinate transformation functionality.
 * This service helps ensure consistent coordinate handling across all comparison services.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CoordinateIntegrationService {

    private final CoordinateTransformer transformer;

    /**
     * Verify all differences have coordinates in display space.
     * This can be used as a final verification step before returning differences to clients.
     *
     * @param differences List of differences to verify
     * @param pageWidth Width of the page
     * @param pageHeight Height of the page
     * @return True if all differences have valid coordinates
     */
    public boolean verifyDifferenceCoordinates(List<Difference> differences, double pageWidth, double pageHeight) {
        if (differences == null || differences.isEmpty()) {
            return true;
        }

        boolean allValid = true;
        for (Difference diff : differences) {
            boolean hasPosition = diff.getX() != 0 || diff.getY() != 0 || diff.getWidth() != 0 || diff.getHeight() != 0;
            boolean hasBounds = diff.getLeft() != 0 || diff.getTop() != 0 || diff.getRight() != 0 || diff.getBottom() != 0;

            if (!hasPosition && !hasBounds) {
                log.warn("Difference {} has no position or bounds information", diff.getId());
                allValid = false;

                // Set default position
                setDefaultPosition(diff, pageWidth, pageHeight);
            } else if (!hasPosition && hasBounds) {
                // Copy bounds to position
                diff.setX(diff.getLeft());
                diff.setY(diff.getTop());
                diff.setWidth(diff.getRight() - diff.getLeft());
                diff.setHeight(diff.getBottom() - diff.getTop());
            } else if (hasPosition && !hasBounds) {
                // Copy position to bounds
                diff.setLeft(diff.getX());
                diff.setTop(diff.getY());
                diff.setRight(diff.getX() + diff.getWidth());
                diff.setBottom(diff.getY() + diff.getHeight());
            }

            // Verify coordinates are within page bounds
            if (diff.getX() < 0 || diff.getY() < 0 ||
                    diff.getX() + diff.getWidth() > pageWidth ||
                    diff.getY() + diff.getHeight() > pageHeight) {

                log.warn("Difference {} has coordinates outside page bounds: x={}, y={}, width={}, height={}",
                        diff.getId(), diff.getX(), diff.getY(), diff.getWidth(), diff.getHeight());

                // Fix coordinates to stay within page bounds
                fixCoordinatesToPageBounds(diff, pageWidth, pageHeight);
            }
        }

        return allValid;
    }

    /**
     * Fix coordinates to stay within page bounds.
     *
     * @param diff The difference to fix
     * @param pageWidth Width of the page
     * @param pageHeight Height of the page
     */
    private void fixCoordinatesToPageBounds(Difference diff, double pageWidth, double pageHeight) {
        // Ensure coordinates are within page bounds
        if (diff.getX() < 0) diff.setX(0);
        if (diff.getY() < 0) diff.setY(0);

        // Ensure width and height stay within page bounds
        if (diff.getX() + diff.getWidth() > pageWidth) {
            diff.setWidth(pageWidth - diff.getX());
        }

        if (diff.getY() + diff.getHeight() > pageHeight) {
            diff.setHeight(pageHeight - diff.getY());
        }

        // Minimum size to ensure visibility
        if (diff.getWidth() < 10) diff.setWidth(10);
        if (diff.getHeight() < 10) diff.setHeight(10);

        // Update bounds to match position
        diff.setLeft(diff.getX());
        diff.setTop(diff.getY());
        diff.setRight(diff.getX() + diff.getWidth());
        diff.setBottom(diff.getY() + diff.getHeight());
    }

    /**
     * Set a default position for a difference that has no coordinates.
     *
     * @param diff The difference to position
     * @param pageWidth Width of the page
     * @param pageHeight Height of the page
     */
    private void setDefaultPosition(Difference diff, double pageWidth, double pageHeight) {
        // Choose position based on difference type
        double x, y, width, height;

        if ("image".equals(diff.getType())) {
            // Images near middle of page
            x = pageWidth * 0.2;
            y = pageHeight * 0.4;
            width = pageWidth * 0.6;
            height = pageHeight * 0.2;
        } else if ("font".equals(diff.getType())) {
            // Fonts near top of page
            x = pageWidth * 0.1;
            y = pageHeight * 0.1;
            width = pageWidth * 0.8;
            height = pageHeight * 0.03;
        } else if ("text".equals(diff.getType())) {
            // Text differences spread out
            x = pageWidth * 0.1;
            y = pageHeight * 0.3;
            width = pageWidth * 0.8;
            height = pageHeight * 0.05;
        } else {
            // Default position
            x = pageWidth * 0.1;
            y = pageHeight * 0.2;
            width = pageWidth * 0.8;
            height = pageHeight * 0.04;
        }

        // Set position and bounds
        diff.setX(x);
        diff.setY(y);
        diff.setWidth(width);
        diff.setHeight(height);

        diff.setLeft(x);
        diff.setTop(y);
        diff.setRight(x + width);
        diff.setBottom(y + height);
    }

    /**
     * Transform a point from PDF space to display space.
     *
     * @param pdfX X coordinate in PDF space
     * @param pdfY Y coordinate in PDF space
     * @param page The PDPage for height information
     * @return Point in display space
     */
    public Point2D transformPointFromPage(double pdfX, double pdfY, PDPage page) {
        double pageHeight = transformer.getPageHeight(page);
        return transformer.pdfToDisplay(pdfX, pdfY, pageHeight);
    }

    /**
     * Transform a rectangle from PDF space to display space.
     *
     * @param pdfX X coordinate in PDF space
     * @param pdfY Y coordinate in PDF space
     * @param width Width in PDF space
     * @param height Height in PDF space
     * @param page The PDPage for height information
     * @return Rectangle in display space
     */
    public CoordinateTransformer.Rectangle transformRectFromPage(
            double pdfX, double pdfY, double width, double height, PDPage page) {

        double pageHeight = transformer.getPageHeight(page);
        return transformer.pdfRectToDisplay(pdfX, pdfY, width, height, pageHeight);
    }
}