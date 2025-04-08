package guraa.pdfcompare.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * Enhanced PDF renderer with robust error handling and fallback mechanisms.
 * This class wraps PDFBox's PDFRenderer with additional error handling.
 */
@Slf4j
public class PdfRenderer {
    private final PDDocument document;
    private int defaultDPI = 150;
    private ImageType imageType = ImageType.RGB;
    private PDFRenderer pdfRenderer;

    /**
     * Create a new PdfRenderer for the given document.
     *
     * @param document The PDF document to render
     */
    public PdfRenderer(PDDocument document) {
        this.document = document;
        this.pdfRenderer = new PDFRenderer(document);
    }

    /**
     * Set the default DPI for rendering.
     *
     * @param dpi The DPI value
     * @return This renderer for method chaining
     */
    public PdfRenderer setDefaultDPI(int dpi) {
        this.defaultDPI = dpi;
        return this;
    }

    /**
     * Set the image type for rendering.
     *
     * @param imageType The image type
     * @return This renderer for method chaining
     */
    public PdfRenderer setImageType(ImageType imageType) {
        this.imageType = imageType;
        return this;
    }

    /**
     * Render a page with robust error handling.
     *
     * @param pageIndex The zero-based page index
     * @return The rendered page as a BufferedImage
     * @throws IOException If rendering fails
     */
    public BufferedImage renderPage(int pageIndex) throws IOException {
        try {
            // Try standard rendering first
            return pdfRenderer.renderImageWithDPI(pageIndex, defaultDPI, imageType);
        } catch (Exception e) {
            log.warn("Standard rendering failed for page {}, trying fallback: {}", pageIndex + 1, e.getMessage());
            
            // Try fallback rendering with simpler settings
            try {
                // Create a new renderer instance in case the original one is in a bad state
                pdfRenderer = new PDFRenderer(document);
                return pdfRenderer.renderImageWithDPI(pageIndex, defaultDPI, ImageType.BINARY);
            } catch (Exception e2) {
                log.warn("Fallback rendering failed for page {}, trying emergency rendering: {}", 
                        pageIndex + 1, e2.getMessage());
                
                // Emergency rendering - create a blank image with basic page dimensions
                try {
                    return createEmergencyRendering(pageIndex);
                } catch (Exception e3) {
                    log.error("All rendering methods failed for page {}", pageIndex + 1, e3);
                    throw new IOException("Failed to render page " + (pageIndex + 1) + 
                            " after multiple attempts", e);
                }
            }
        }
    }
    
    /**
     * Create an emergency rendering when all other methods fail.
     * This creates a blank white image with the page dimensions.
     *
     * @param pageIndex The page index
     * @return A blank image with the page dimensions
     */
    private BufferedImage createEmergencyRendering(int pageIndex) throws IOException {
        PDPage page = document.getPage(pageIndex);
        
        // Get page dimensions
        float widthPt = page.getMediaBox().getWidth();
        float heightPt = page.getMediaBox().getHeight();
        
        // Convert to pixels at the specified DPI
        int widthPx = Math.round(widthPt * defaultDPI / 72f);
        int heightPx = Math.round(heightPt * defaultDPI / 72f);
        
        // Create a blank white image
        BufferedImage image = new BufferedImage(widthPx, heightPx, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = image.createGraphics();
        
        // Fill with white
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, widthPx, heightPx);
        
        // Add a message
        g.setColor(java.awt.Color.RED);
        g.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 14));
        g.drawString("Rendering failed for this page", 50, 50);
        
        g.dispose();
        
        return image;
    }
}
