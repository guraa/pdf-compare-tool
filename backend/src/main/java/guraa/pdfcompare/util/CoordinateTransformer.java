package guraa.pdfcompare.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDPage;
import org.springframework.stereotype.Component;

import java.awt.geom.Point2D;

/**
 * Utility class for transforming coordinates between PDF space and display space.
 *
 * PDF coordinates use bottom-left origin with Y-axis pointing upward.
 * Display coordinates use top-left origin with Y-axis pointing downward.
 *
 * This class ensures consistent coordinate transformation across the application.
 */
@Slf4j
@Component
public class CoordinateTransformer {

    /**
     * Transform a point from PDF space to display space.
     *
     * @param pdfX X coordinate in PDF space
     * @param pdfY Y coordinate in PDF space
     * @param pageHeight Height of the PDF page
     * @return Point in display space
     */
    public Point2D pdfToDisplay(double pdfX, double pdfY, double pageHeight) {
        // X remains the same
        double displayX = pdfX;

        // Y is flipped, with the origin at the top
        double displayY = pageHeight - pdfY;

        return new Point2D.Double(displayX, displayY);
    }

    /**
     * Transform a point from PDF space to display space.
     *
     * @param pdfPoint Point in PDF space
     * @param pageHeight Height of the PDF page
     * @return Point in display space
     */
    public Point2D pdfToDisplay(Point2D pdfPoint, double pageHeight) {
        return pdfToDisplay(pdfPoint.getX(), pdfPoint.getY(), pageHeight);
    }

    /**
     * Transform a rectangle from PDF space to display space.
     * This method handles both the position and size of the rectangle.
     *
     * @param pdfX X coordinate in PDF space (bottom-left of rectangle)
     * @param pdfY Y coordinate in PDF space (bottom-left of rectangle)
     * @param width Width of the rectangle
     * @param height Height of the rectangle
     * @param pageHeight Height of the PDF page
     * @return Rectangle coordinates in display space (top-left, width, height)
     */
    public Rectangle pdfRectToDisplay(double pdfX, double pdfY, double width, double height, double pageHeight) {
        // In PDF space, Y is at the bottom of the rectangle
        // In display space, Y should be at the top of the rectangle
        double displayX = pdfX;
        double displayY = pageHeight - (pdfY + height);

        return new Rectangle(displayX, displayY, width, height);
    }

    /**
     * Extract page height from a PDPage object.
     *
     * @param page PDPage object
     * @return Page height
     */
    public float getPageHeight(PDPage page) {
        if (page == null || page.getMediaBox() == null) {
            log.warn("Unable to get page height, using default value of 792 (US Letter)");
            return 792f; // Default to US Letter height
        }
        return page.getMediaBox().getHeight();
    }

    /**
     * Inner class to represent a rectangle with position and dimensions.
     */
    public static class Rectangle {
        private final double x;
        private final double y;
        private final double width;
        private final double height;

        public Rectangle(double x, double y, double width, double height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        public double getWidth() {
            return width;
        }

        public double getHeight() {
            return height;
        }

        public double getRight() {
            return x + width;
        }

        public double getBottom() {
            return y + height;
        }

        @Override
        public String toString() {
            return "Rectangle{" +
                    "x=" + x +
                    ", y=" + y +
                    ", width=" + width +
                    ", height=" + height +
                    '}';
        }
    }
}