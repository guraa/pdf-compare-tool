package guraa.pdfcompare.service;

import guraa.pdfcompare.model.PdfDocument;
import guraa.pdfcompare.util.PdfRenderer;
import guraa.pdfcompare.util.PdfLoader;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;

/**
 * Service for rendering PDF documents as images with consistent settings.
 * Handles full page rendering, thumbnails, and error recovery.
 */
@Slf4j
@Service
public class PdfRenderingService {

    @Value("${app.rendering.page-dpi:150}")
    private int pageDpi;

    @Value("${app.rendering.thumbnail-dpi:72}")
    private int thumbnailDpi;

    @Value("${app.rendering.thumbnail-max-width:300}")
    private int thumbnailMaxWidth;

    @Value("${app.rendering.thumbnail-max-height:400}")
    private int thumbnailMaxHeight;

    @Value("${app.rendering.image-type:RGB}")
    private String imageTypeStr;

    /**
     * Get the configured image type.
     *
     * @return ImageType to use for rendering
     */
    private ImageType getImageType() {
        if ("BINARY".equalsIgnoreCase(imageTypeStr)) {
            return ImageType.BINARY;
        } else if ("GRAY".equalsIgnoreCase(imageTypeStr)) {
            return ImageType.GRAY;
        } else if ("ARGB".equalsIgnoreCase(imageTypeStr)) {
            return ImageType.ARGB;
        } else {
            return ImageType.RGB; // Default
        }
    }

    /**
     * Process a PDF document by rendering all pages and creating thumbnails.
     * This method handles error recovery and ensures consistent output.
     *
     * @param document The PDF document to process
     * @return CompletableFuture that completes when processing is done
     */
    @Async
    public CompletableFuture<Integer> processPdfPages(PdfDocument document) {
        if (document == null || document.getFilePath() == null) {
            log.error("Cannot process PDF: document or file path is null");
            return CompletableFuture.completedFuture(0);
        }

        try {
            // Create necessary directories
            Path documentDir = Paths.get("uploads", "documents", document.getFileId());
            Path pagesDir = documentDir.resolve("pages");
            Path thumbnailsDir = documentDir.resolve("thumbnails");

            Files.createDirectories(pagesDir);
            Files.createDirectories(thumbnailsDir);

            // Load the PDF with robust error handling
            try (PDDocument pdf = PdfLoader.loadDocumentWithFallbackOptions(new File(document.getFilePath()))) {
                log.info("Processing PDF document: {} ({} pages)", document.getFileName(), pdf.getNumberOfPages());

                // Render all pages with consistent settings
                int pagesRendered = PdfRenderer.renderAllPages(
                        pdf, pagesDir, pageDpi, getImageType());

                log.info("Rendered {} of {} pages for document {}",
                        pagesRendered, pdf.getNumberOfPages(), document.getFileId());

                // Create thumbnails with consistent settings
                int thumbnailsCreated = PdfRenderer.createThumbnails(
                        pdf, thumbnailsDir, thumbnailDpi, thumbnailMaxWidth, thumbnailMaxHeight);

                log.info("Created {} thumbnails for document {}", thumbnailsCreated, document.getFileId());

                return CompletableFuture.completedFuture(pagesRendered);
            }
        } catch (Exception e) {
            log.error("Error processing PDF {}: {}", document.getFileId(), e.getMessage(), e);
            return CompletableFuture.completedFuture(0);
        }
    }

    /**
     * Get a rendered page image with robust error handling.
     * If the page doesn't exist, it will be rendered on-demand.
     *
     * @param document The PDF document
     * @param pageNumber The page number (1-based)
     * @return FileSystemResource for the rendered page image
     * @throws IOException If there's an error getting the page
     */
    public static FileSystemResource getRenderedPage(PdfDocument document, int pageNumber) throws IOException {
        if (document == null) {
            throw new IllegalArgumentException("Document cannot be null");
        }

        // Get the path to the rendered page
        Path pagesDir = Paths.get("uploads", "documents", document.getFileId(), "pages");
        File pageFile = pagesDir.resolve("page_" + pageNumber + ".png").toFile();

        // If the rendered page doesn't exist, create it on-demand
        if (!pageFile.exists()) {
            log.info("Rendering page {} of document {} on-demand", pageNumber, document.getFileId());

            Files.createDirectories(pagesDir);

            try (PDDocument pdf = PdfLoader.loadDocumentWithFallbackOptions(new File(document.getFilePath()))) {
                // Check if page number is valid
                if (pageNumber < 1 || pageNumber > pdf.getNumberOfPages()) {
                    log.error("Page number {} is out of bounds (max: {})",
                            pageNumber, pdf.getNumberOfPages());

                    // Create a placeholder for invalid page number
                    PdfRenderer.createPlaceholderImage(
                            pagesDir, pageNumber, "Page " + pageNumber + " does not exist");

                    return new FileSystemResource(pageFile);
                }

                boolean success = PdfRenderer.renderPage(
                        pdf, pageNumber, pageFile, pageDpi, getImageType());

                if (!success) {
                    log.error("Failed to render page {} on-demand", pageNumber);
                }
            } catch (Exception e) {
                log.error("Error rendering page {} on-demand: {}", pageNumber, e.getMessage());

                // Create a placeholder for failed rendering
                PdfRenderer.createPlaceholderImage(
                        pagesDir, pageNumber, "Error rendering page " + pageNumber);
            }
        }

        return new FileSystemResource(pageFile);
    }

    /**
     * Get a thumbnail for a specific page with robust error handling.
     * If the thumbnail doesn't exist, it will be created on-demand.
     *
     * @param document The PDF document
     * @param pageNumber The page number (1-based)
     * @return FileSystemResource for the thumbnail image
     * @throws IOException If there's an error getting the thumbnail
     */
    public static FileSystemResource getThumbnail(PdfDocument document, int pageNumber) throws IOException {
        if (document == null) {
            throw new IllegalArgumentException("Document cannot be null");
        }

        // Get the path to the thumbnail
        Path thumbnailsDir = Paths.get("uploads", "documents", document.getFileId(), "thumbnails");
        File thumbnailFile = thumbnailsDir.resolve("page_" + pageNumber + "_thumbnail.png").toFile();

        // If the thumbnail doesn't exist, create it on-demand
        if (!thumbnailFile.exists()) {
            log.info("Creating thumbnail for page {} of document {} on-demand", pageNumber, document.getFileId());

            Files.createDirectories(thumbnailsDir);

            try (PDDocument pdf = PdfLoader.loadDocumentWithFallbackOptions(new File(document.getFilePath()))) {
                // Check if page number is valid
                if (pageNumber < 1 || pageNumber > pdf.getNumberOfPages()) {
                    log.error("Page number {} is out of bounds for thumbnails (max: {})",
                            pageNumber, pdf.getNumberOfPages());

                    // Create a simple placeholder thumbnail for invalid page number
                    BufferedImage placeholder = new BufferedImage(
                            thumbnailMaxWidth, thumbnailMaxHeight, BufferedImage.TYPE_INT_RGB);
                    Graphics2D g = placeholder.createGraphics();
                    g.setColor(Color.WHITE);
                    g.fillRect(0, 0, thumbnailMaxWidth, thumbnailMaxHeight);
                    g.setColor(Color.RED);
                    g.setFont(new Font("Arial", Font.BOLD, 12));
                    g.drawString("Invalid Page: " + pageNumber, 10, thumbnailMaxHeight / 2);
                    g.dispose();

                    ImageIO.write(placeholder, "PNG", thumbnailFile);
                    return new FileSystemResource(thumbnailFile);
                }

                // Render the page and create a thumbnail
                BufferedImage fullImage = pdf.getRenderer().renderImageWithDPI(
                        pageNumber - 1, thumbnailDpi, getImageType());

                // Scale to thumbnail dimensions
                double scale = Math.min(
                        (double) thumbnailMaxWidth / fullImage.getWidth(),
                        (double) thumbnailMaxHeight / fullImage.getHeight()
                );

                int scaledWidth = (int) (fullImage.getWidth() * scale);
                int scaledHeight = (int) (fullImage.getHeight() * scale);

                BufferedImage thumbnail = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = thumbnail.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g.drawImage(fullImage, 0, 0, scaledWidth, scaledHeight, null);
                g.dispose();

                ImageIO.write(thumbnail, "PNG", thumbnailFile);
            } catch (Exception e) {
                log.error("Error creating thumbnail for page {} on-demand: {}", pageNumber, e.getMessage());

                // Create a simple placeholder for failed thumbnail
                BufferedImage placeholder = new BufferedImage(
                        thumbnailMaxWidth, thumbnailMaxHeight, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = placeholder.createGraphics();
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, thumbnailMaxWidth, thumbnailMaxHeight);
                g.setColor(Color.RED);
                g.setFont(new Font("Arial", Font.BOLD, 12));
                g.drawString("Error", 10, 20);
                g.drawString("Page " + pageNumber, 10, 40);
                g.dispose();

                ImageIO.write(placeholder, "PNG", thumbnailFile);
            }
        }

        return new FileSystemResource(thumbnailFile);
    }
}