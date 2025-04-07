package guraa.pdfcompare.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.rendering.RenderDestination;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An enhanced PDF renderer that provides robust error handling for problematic PDFs.
 * Handles issues such as Ascii85 stream errors, DataFormatExceptions, and array index bounds errors.
 *
 * This is a renamed version of RobustPdfRenderer to maintain consistent naming with the existing codebase.
 */
@Slf4j
public class PdfRenderer {

    private final PDDocument document;
    private final PDFRenderer renderer;
    private final Map<Integer, Integer> problemPages = new ConcurrentHashMap<>();

    // Rendering options
    private int defaultDPI = 150;
    private int fallbackDPI = 72;
    private ImageType imageType = ImageType.RGB;

    /**
     * Create a robust renderer for the given document.
     *
     * @param document The PDF document to render
     */
    public PdfRenderer(PDDocument document) {
        this.document = document;
        this.renderer = new PDFRenderer(document);
        configureRenderer();
    }

    /**
     * Configure the underlying renderer with optimal settings for error handling.
     */
    private void configureRenderer() {
        // Set rendering hints for more robust operation
        renderer.setSubsamplingAllowed(true);

        // Set additional options if needed
        // This would be the place to add more configuration if PDFBox adds options
    }

    /**
     * Set the default DPI for rendering.
     *
     * @param dpi The dots per inch value
     * @return This instance for method chaining
     */
    public PdfRenderer setDefaultDPI(int dpi) {
        this.defaultDPI = dpi;
        return this;
    }

    /**
     * Set the fallback DPI to use for problematic pages.
     *
     * @param dpi The dots per inch value for fallback rendering
     * @return This instance for method chaining
     */
    public PdfRenderer setFallbackDPI(int dpi) {
        this.fallbackDPI = dpi;
        return this;
    }

    /**
     * Set the image type to use for rendering.
     *
     * @param imageType The image type (RGB, ARGB, BINARY or GRAY)
     * @return This instance for method chaining
     */
    public PdfRenderer setImageType(ImageType imageType) {
        this.imageType = imageType;
        return this;
    }

    /**
     * Render a PDF page with robust error handling.
     *
     * @param pageIndex The zero-based page index
     * @return The rendered page as a BufferedImage
     * @throws IOException If the page cannot be rendered after all fallback strategies
     */
    public BufferedImage renderPage(int pageIndex) throws IOException {
        // First try with standard settings
        try {
            if (!problemPages.containsKey(pageIndex)) {
                return renderer.renderImageWithDPI(pageIndex, defaultDPI, imageType);
            } else {
                // We already know this is a problem page, use fallback right away
                return renderWithFallback(pageIndex, problemPages.get(pageIndex));
            }
        } catch (Exception e) {
            log.warn("Error rendering page {} with standard settings: {}", pageIndex + 1, e.getMessage());

            // Mark as a problem page
            problemPages.putIfAbsent(pageIndex, 1);

            // Try with fallback methods
            return renderWithFallback(pageIndex, 1);
        }
    }

    /**
     * Render a page using progressively more robust fallback strategies.
     *
     * @param pageIndex The zero-based page index
     * @param attemptLevel The current fallback attempt level (1-5)
     * @return The rendered page as a BufferedImage
     * @throws IOException If all fallback strategies fail
     */
    private BufferedImage renderWithFallback(int pageIndex, int attemptLevel) throws IOException {
        // Update the problem level for this page
        problemPages.put(pageIndex, attemptLevel);

        // Try different fallback strategies based on the attempt level
        switch (attemptLevel) {
            case 1:
                // First fallback: try with reduced DPI
                try {
                    log.debug("Fallback 1: Trying reduced DPI for page {}", pageIndex + 1);
                    return renderer.renderImageWithDPI(pageIndex, fallbackDPI, imageType);
                } catch (Exception e) {
                    log.warn("Fallback 1 failed for page {}: {}", pageIndex + 1, e.getMessage());
                    return renderWithFallback(pageIndex, attemptLevel + 1);
                }

            case 2:
                // Second fallback: try with minimal settings and BINARY image type
                try {
                    log.debug("Fallback 2: Trying BINARY image type for page {}", pageIndex + 1);
                    return renderer.renderImageWithDPI(pageIndex, fallbackDPI, ImageType.BINARY);
                } catch (Exception e) {
                    log.warn("Fallback 2 failed for page {}: {}", pageIndex + 1, e.getMessage());
                    return renderWithFallback(pageIndex, attemptLevel + 1);
                }

            case 3:
                // Third fallback: try with background rendering trick
                // This avoids some graphics operations that might cause problems
                try {
                    log.debug("Fallback 3: Using background rendering trick for page {}", pageIndex + 1);
                    return renderPageWithBGTrick(pageIndex);
                } catch (Exception e) {
                    log.warn("Fallback 3 failed for page {}: {}", pageIndex + 1, e.getMessage());
                    return renderWithFallback(pageIndex, attemptLevel + 1);
                }

            case 4:
                // Fourth fallback: try a completely separate approach with page size info only
                try {
                    log.debug("Fallback 4: Creating basic placeholder image for page {}", pageIndex + 1);
                    return createPagePlaceholder(pageIndex);
                } catch (Exception e) {
                    log.warn("Fallback 4 failed for page {}: {}", pageIndex + 1, e.getMessage());
                    return renderWithFallback(pageIndex, attemptLevel + 1);
                }

            default:
                // Last resort: create a generic error page
                log.error("All rendering fallbacks failed for page {}, creating error page", pageIndex + 1);
                return createErrorPage(pageIndex);
        }
    }

    /**
     * Render a page using a background trick that avoids some problematic operations.
     * This can get around some PDFBox rendering issues.
     *
     * @param pageIndex The zero-based page index
     * @return The rendered page as a BufferedImage
     * @throws IOException If rendering fails
     */
    private BufferedImage renderPageWithBGTrick(int pageIndex) throws IOException {
        // Get the page and its dimensions
        PDPage page = document.getPage(pageIndex);
        PDRectangle cropBox = page.getCropBox();

        // Create a blank image with the right dimensions
        int width = Math.round(cropBox.getWidth() * fallbackDPI / 72f);
        int height = Math.round(cropBox.getHeight() * fallbackDPI / 72f);

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        // Fill with white background
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);

        // Try to render the content over the white background with a try-catch
        // to avoid failing the whole operation if content rendering fails
        try {
            // Configure custom rendering hints
            Map<String, Object> hints = new HashMap<>();
            hints.put(RenderDestination.EXPORT.toString(), Boolean.TRUE);

            // Use standard rendering via drawPage (safer than reflection)
            renderer.renderPageToGraphics(pageIndex, g, fallbackDPI / 72f);

        } catch (Exception e) {
            log.warn("Content rendering failed during background trick for page {}: {}",
                    pageIndex + 1, e.getMessage());
            // Draw error message on the page
            g.setColor(Color.RED);
            g.setFont(new Font("Arial", Font.BOLD, 12));
            g.drawString("Content rendering partially failed", 10, 20);
        }

        g.dispose();
        return image;
    }

    /**
     * Create a basic placeholder image using only the page dimensions.
     *
     * @param pageIndex The zero-based page index
     * @return A basic placeholder image
     * @throws IOException If creating the placeholder fails
     */
    private BufferedImage createPagePlaceholder(int pageIndex) throws IOException {
        try {
            // Get the page and its dimensions
            PDPage page = document.getPage(pageIndex);
            PDRectangle cropBox = page.getCropBox();

            // Create a blank image with the right dimensions
            int width = Math.round(cropBox.getWidth() * fallbackDPI / 72f);
            int height = Math.round(cropBox.getHeight() * fallbackDPI / 72f);

            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();

            // Configure high quality rendering
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            // Fill with white background
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);

            // Draw a light gray border
            g.setColor(Color.LIGHT_GRAY);
            g.drawRect(0, 0, width - 1, height - 1);

            // Draw a message
            g.setColor(Color.BLACK);
            g.setFont(new Font("Arial", Font.PLAIN, 12));
            g.drawString("Page " + (pageIndex + 1), 10, 20);
            g.drawString("Content not rendered - page may have complex elements", 10, 40);

            g.dispose();
            return image;
        } catch (Exception e) {
            log.error("Error creating page placeholder for page {}: {}", pageIndex + 1, e.getMessage());
            throw new IOException("Failed to create page placeholder", e);
        }
    }

    /**
     * Create a generic error page when all other rendering methods fail.
     *
     * @param pageIndex The zero-based page index
     * @return An error page image
     */
    private BufferedImage createErrorPage(int pageIndex) {
        int width = 612; // Standard letter width at 72 DPI
        int height = 792; // Standard letter height at 72 DPI

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        // Fill with white background
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);

        // Draw a red border
        g.setColor(Color.RED);
        g.drawRect(1, 1, width - 3, height - 3);

        // Draw error message
        g.setFont(new Font("Arial", Font.BOLD, 16));
        g.drawString("Error Rendering Page " + (pageIndex + 1), 50, 100);

        g.setFont(new Font("Arial", Font.PLAIN, 12));
        g.drawString("This page could not be rendered due to PDF format issues.", 50, 150);
        g.drawString("The page may contain damaged or unsupported content.", 50, 170);

        g.dispose();
        return image;
    }
}