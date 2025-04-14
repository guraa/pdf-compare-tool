package guraa.pdfcompare.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.tools.imageio.ImageIOUtil;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Enhanced PDF renderer with robust error handling and consistent output.
 * Provides methods to render PDF pages with consistent settings, error handling,
 * and fallback mechanisms for problematic documents.
 */
@Slf4j
public class PdfRenderer {

    private static final int DEFAULT_DPI = 150;
    private static final ImageType DEFAULT_IMAGE_TYPE = ImageType.RGB;
    private static final int MAX_RETRIES = 3;

    /**
     * Renders all pages of a PDF document with consistent settings.
     *
     * @param document The PDF document to render
     * @param outputDir The directory to save rendered images
     * @param dpi The DPI setting for rendering (default 150)
     * @param imageType The image type (default RGB)
     * @return Number of successfully rendered pages
     * @throws IOException If there's an error rendering pages
     */
    public static int renderAllPages(PDDocument document, Path outputDir, int dpi, ImageType imageType) throws IOException {
        if (document == null) {
            throw new IllegalArgumentException("Document cannot be null");
        }

        // Ensure output directory exists
        Files.createDirectories(outputDir);

        // Apply default values if not specified
        if (dpi <= 0) dpi = DEFAULT_DPI;
        if (imageType == null) imageType = DEFAULT_IMAGE_TYPE;

        int pageCount = document.getNumberOfPages();
        int successCount = 0;

        // Create a robust renderer
        PDFRenderer renderer = new PDFRenderer(document);

        // Render each page
        for (int i = 0; i < pageCount; i++) {
            try {
                // Render page with retry mechanism
                BufferedImage image = renderPageWithRetry(renderer, i, dpi, imageType);

                // Save the rendered image
                String filename = String.format("page_%d.png", i + 1);
                File outputFile = outputDir.resolve(filename).toFile();
                ImageIOUtil.writeImage(image, outputFile.getAbsolutePath(), dpi);

                successCount++;
            } catch (Exception e) {
                log.error("Failed to render page {} after multiple attempts: {}", i + 1, e.getMessage());

                // Create a placeholder image for failed rendering
                createPlaceholderImage(outputDir, i + 1, "Rendering failed for page " + (i + 1));
            }
        }

        return successCount;
    }

    /**
     * Renders a specific page with retry mechanism for improved reliability.
     *
     * @param renderer The PDF renderer
     * @param pageIndex The zero-based page index to render
     * @param dpi The DPI setting for rendering
     * @param imageType The image type
     * @return The rendered page as a BufferedImage
     * @throws IOException If rendering fails after all retries
     */
    private static BufferedImage renderPageWithRetry(PDFRenderer renderer, int pageIndex, int dpi, ImageType imageType) throws IOException {
        int attempts = 0;

        while (attempts < MAX_RETRIES) {
            try {
                return renderer.renderImageWithDPI(pageIndex, dpi, imageType);
            } catch (Exception e) {
                attempts++;

                if (attempts >= MAX_RETRIES) {
                    log.warn("All standard rendering attempts failed for page {}, trying fallback approach", pageIndex + 1);

                    // Try with emergency rendering approach
                    try {
                        return renderPageWithFallback(renderer, pageIndex, dpi);
                    } catch (Exception fallbackEx) {
                        log.error("Fallback rendering also failed for page {}: {}", pageIndex + 1, fallbackEx.getMessage());
                        throw new IOException("Failed to render page after multiple attempts", fallbackEx);
                    }
                }

                log.warn("Rendering attempt {} failed for page {}: {}", attempts, pageIndex + 1, e.getMessage());
            }
        }

        // This should never happen due to the throw in the loop above
        throw new IOException("Failed to render page after exhausting all retry attempts");
    }

    /**
     * Fallback rendering approach for problematic pages.
     * Attempts to render with different settings to recover from common rendering issues.
     *
     * @param renderer The PDF renderer
     * @param pageIndex The zero-based page index to render
     * @param dpi The DPI setting for rendering
     * @return The rendered page as a BufferedImage
     * @throws IOException If fallback rendering also fails
     */
    private static BufferedImage renderPageWithFallback(PDFRenderer renderer, int pageIndex, int dpi) throws IOException {
        // Try with lower DPI
        try {
            return renderer.renderImageWithDPI(pageIndex, Math.min(dpi, 72), ImageType.RGB);
        } catch (Exception e) {
            log.warn("Lower DPI rendering failed: {}", e.getMessage());
        }

        // Try with binary image type (black and white)
        try {
            return renderer.renderImageWithDPI(pageIndex, Math.min(dpi, 72), ImageType.BINARY);
        } catch (Exception e) {
            log.warn("Binary rendering failed: {}", e.getMessage());
        }

        // Last resort: Create a simplified rendering from the page dimensions
        try {
            PDPage page = renderer.getDocument().getPage(pageIndex);
            float widthPt = page.getMediaBox().getWidth();
            float heightPt = page.getMediaBox().getHeight();

            // Convert to pixels at the specified DPI
            int widthPx = Math.round(widthPt * dpi / 72f);
            int heightPx = Math.round(heightPt * dpi / 72f);

            // Create a blank white image with page dimensions
            BufferedImage image = new BufferedImage(
                    Math.max(widthPx, 100),  // Ensure minimum width
                    Math.max(heightPx, 100), // Ensure minimum height
                    BufferedImage.TYPE_INT_RGB);

            // Fill with white background
            Graphics2D g = image.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, image.getWidth(), image.getHeight());

            // Add error text
            g.setColor(Color.RED);
            g.setFont(new Font("Arial", Font.BOLD, 14));
            String errorText = "Error rendering page " + (pageIndex + 1);
            int textWidth = g.getFontMetrics().stringWidth(errorText);
            g.drawString(errorText, (image.getWidth() - textWidth) / 2, image.getHeight() / 2);
            g.dispose();

            return image;
        } catch (Exception e) {
            log.error("Emergency rendering failed: {}", e.getMessage());
            throw new IOException("All rendering attempts failed for page " + (pageIndex + 1), e);
        }
    }

    /**
     * Creates a placeholder image for pages that failed to render.
     *
     * @param outputDir The output directory
     * @param pageNumber The page number (1-based)
     * @param message The error message to display
     */
    public static void createPlaceholderImage(Path outputDir, int pageNumber, String message) {
        try {
            // Create a blank image (standard letter size at target DPI)
            int width = (int)(8.5 * DEFAULT_DPI);
            int height = (int)(11 * DEFAULT_DPI);
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

            // Fill with white background
            Graphics2D g = image.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);

            // Add error text
            g.setColor(Color.RED);
            g.setFont(new Font("Arial", Font.BOLD, 18));
            g.drawString(message, 50, height / 2 - 20);

            // Add additional instructions
            g.setFont(new Font("Arial", Font.PLAIN, 14));
            g.drawString("This page could not be rendered properly.", 50, height / 2 + 20);

            g.dispose();

            // Save the placeholder image
            String filename = String.format("page_%d.png", pageNumber);
            File outputFile = outputDir.resolve(filename).toFile();
            ImageIO.write(image, "PNG", outputFile);

            log.info("Created placeholder image for page {}", pageNumber);
        } catch (Exception e) {
            log.error("Error creating placeholder image for page {}: {}", pageNumber, e.getMessage());
        }
    }

    /**
     * Renders a single page with consistent settings.
     *
     * @param document The PDF document
     * @param pageNumber The page number (1-based)
     * @param outputFile The output file to save the rendered image
     * @param dpi The DPI setting for rendering (default 150)
     * @param imageType The image type (default RGB)
     * @return True if rendering was successful, false otherwise
     */
    public static boolean renderPage(PDDocument document, int pageNumber, File outputFile, int dpi, ImageType imageType) {
        if (document == null) {
            log.error("Cannot render page: document is null");
            return false;
        }

        try {
            int pageIndex = pageNumber - 1; // Convert 1-based to 0-based

            // Validate page number
            if (pageIndex < 0 || pageIndex >= document.getNumberOfPages()) {
                log.error("Invalid page number: {}", pageNumber);
                return false;
            }

            // Apply default values if not specified
            if (dpi <= 0) dpi = DEFAULT_DPI;
            if (imageType == null) imageType = DEFAULT_IMAGE_TYPE;

            // Create renderer
            PDFRenderer renderer = new PDFRenderer(document);

            // Render page with retry mechanism
            BufferedImage image = renderPageWithRetry(renderer, pageIndex, dpi, imageType);

            // Ensure output directory exists
            Files.createDirectories(outputFile.getParentFile().toPath());

            // Save the rendered image
            ImageIOUtil.writeImage(image, outputFile.getAbsolutePath(), dpi);

            return true;
        } catch (Exception e) {
            log.error("Error rendering page {}: {}", pageNumber, e.getMessage());

            // Create a placeholder image
            try {
                createPlaceholderImage(outputFile.getParentFile().toPath(), pageNumber, "Rendering failed for page " + pageNumber);
                return false;
            } catch (Exception placeholderEx) {
                log.error("Error creating placeholder image: {}", placeholderEx.getMessage());
                return false;
            }
        }
    }

    /**
     * Creates thumbnails for all pages in a PDF document.
     *
     * @param document The PDF document
     * @param outputDir The directory to save thumbnails
     * @param thumbnailDpi The DPI for thumbnails (typically lower than full page)
     * @param maxWidth Maximum thumbnail width
     * @param maxHeight Maximum thumbnail height
     * @return Number of successfully created thumbnails
     */
    public static int createThumbnails(PDDocument document, Path outputDir, int thumbnailDpi, int maxWidth, int maxHeight) {
        if (document == null) {
            log.error("Cannot create thumbnails: document is null");
            return 0;
        }

        try {
            // Ensure output directory exists
            Files.createDirectories(outputDir);

            int pageCount = document.getNumberOfPages();
            int successCount = 0;

            // Create a robust renderer
            PDFRenderer renderer = new PDFRenderer(document);

            // Process each page
            for (int i = 0; i < pageCount; i++) {
                try {
                    // Render with lower DPI for thumbnails
                    BufferedImage fullImage = renderPageWithRetry(renderer, i, thumbnailDpi, ImageType.RGB);

                    // Scale to thumbnail dimensions
                    BufferedImage thumbnail = scaleToThumbnail(fullImage, maxWidth, maxHeight);

                    // Save the thumbnail
                    String filename = String.format("page_%d_thumbnail.png", i + 1);
                    File outputFile = outputDir.resolve(filename).toFile();
                    ImageIOUtil.writeImage(thumbnail, outputFile.getAbsolutePath(), thumbnailDpi);

                    successCount++;
                } catch (Exception e) {
                    log.error("Error creating thumbnail for page {}: {}", i + 1, e.getMessage());

                    // Create a simplified placeholder thumbnail
                    createSimplePlaceholderThumbnail(outputDir, i + 1, maxWidth, maxHeight);
                }
            }

            return successCount;
        } catch (Exception e) {
            log.error("Error creating thumbnails: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Scale an image to thumbnail dimensions while preserving aspect ratio.
     *
     * @param image The input image
     * @param maxWidth Maximum width of the thumbnail
     * @param maxHeight Maximum height of the thumbnail
     * @return The scaled thumbnail image
     */
    private static BufferedImage scaleToThumbnail(BufferedImage image, int maxWidth, int maxHeight) {
        double scale = Math.min(
                (double) maxWidth / image.getWidth(),
                (double) maxHeight / image.getHeight()
        );

        int scaledWidth = (int) (image.getWidth() * scale);
        int scaledHeight = (int) (image.getHeight() * scale);

        BufferedImage thumbnail = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = thumbnail.createGraphics();

        // Configure for quality scaling
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.drawImage(image, 0, 0, scaledWidth, scaledHeight, null);
        g.dispose();

        return thumbnail;
    }

    /**
     * Creates a simple placeholder thumbnail for pages that failed to render.
     *
     * @param outputDir The output directory
     * @param pageNumber The page number (1-based)
     * @param width Thumbnail width
     * @param height Thumbnail height
     */
    private static void createSimplePlaceholderThumbnail(Path outputDir, int pageNumber, int width, int height) {
        try {
            BufferedImage thumbnail = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = thumbnail.createGraphics();

            // Fill with light gray background
            g.setColor(new Color(240, 240, 240));
            g.fillRect(0, 0, width, height);

            // Draw a red X
            g.setColor(Color.RED);
            g.setStroke(new BasicStroke(2));
            g.drawLine(5, 5, width - 5, height - 5);
            g.drawLine(width - 5, 5, 5, height - 5);

            // Add page number
            g.setFont(new Font("Arial", Font.BOLD, 12));
            String text = "Page " + pageNumber;
            int textWidth = g.getFontMetrics().stringWidth(text);
            g.drawString(text, (width - textWidth) / 2, height - 10);

            g.dispose();

            // Save the placeholder thumbnail
            String filename = String.format("page_%d_thumbnail.png", pageNumber);
            File outputFile = outputDir.resolve(filename).toFile();
            ImageIO.write(thumbnail, "PNG", outputFile);
        } catch (Exception e) {
            log.error("Error creating placeholder thumbnail: {}", e.getMessage());
        }
    }
}