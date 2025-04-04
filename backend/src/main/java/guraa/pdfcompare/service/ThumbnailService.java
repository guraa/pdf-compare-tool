package guraa.pdfcompare.service;

import guraa.pdfcompare.model.PdfDocument;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.tools.imageio.ImageIOUtil;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Service for generating and retrieving thumbnails of PDF pages.
 */
@Slf4j
@Service
public class ThumbnailService {

    private static final int THUMBNAIL_DPI = 72;
    private static final int THUMBNAIL_MAX_WIDTH = 300;
    private static final int THUMBNAIL_MAX_HEIGHT = 400;

    /**
     * Generate thumbnails for all pages in a PDF document.
     *
     * @param document The PDF document
     * @throws IOException If there's an error generating thumbnails
     */
    public void generateThumbnails(PdfDocument document) throws IOException {
        File pdfFile = new File(document.getFilePath());
        Path thumbnailDir = Paths.get("uploads", "documents", document.getFileId(), "thumbnails");

        // Create thumbnails directory if it doesn't exist
        if (!Files.exists(thumbnailDir)) {
            Files.createDirectories(thumbnailDir);
        }

        try (PDDocument pdf = PDDocument.load(pdfFile)) {
            PDFRenderer renderer = new PDFRenderer(pdf);

            // Generate thumbnail for each page
            for (int i = 0; i < pdf.getNumberOfPages(); i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, THUMBNAIL_DPI, ImageType.RGB);

                // Resize if the image is too large
                image = resizeIfNeeded(image);

                // Save thumbnail
                File thumbnailFile = thumbnailDir.resolve(String.format("page_%d_thumbnail.png", i + 1)).toFile();
                ImageIOUtil.writeImage(image, thumbnailFile.getAbsolutePath(), THUMBNAIL_DPI);
            }
        }
    }

    /**
     * Get a page thumbnail for a PDF document.
     *
     * @param document   The PDF document
     * @param pageNumber The page number (1-based)
     * @return Resource for the thumbnail, or null if not found
     */
    public FileSystemResource getThumbnail(PdfDocument document, int pageNumber) {
        Path thumbnailPath = Paths.get("uploads", "documents", document.getFileId(),
                "thumbnails", String.format("page_%d_thumbnail.png", pageNumber));

        File thumbnailFile = thumbnailPath.toFile();

        if (!thumbnailFile.exists()) {
            try {
                // If thumbnail doesn't exist, try to generate it
                generatePageThumbnail(document, pageNumber);

                // Check if generation was successful
                if (thumbnailFile.exists()) {
                    return new FileSystemResource(thumbnailFile);
                }
            } catch (IOException e) {
                log.error("Error generating thumbnail for page {}: {}", pageNumber, e.getMessage());
                return null;
            }
        }

        return new FileSystemResource(thumbnailFile);
    }

    /**
     * Generate a thumbnail for a specific page.
     *
     * @param document   The PDF document
     * @param pageNumber The page number (1-based)
     * @throws IOException If there's an error generating the thumbnail
     */
    public void generatePageThumbnail(PdfDocument document, int pageNumber) throws IOException {
        File pdfFile = new File(document.getFilePath());
        Path thumbnailDir = Paths.get("uploads", "documents", document.getFileId(), "thumbnails");

        // Create thumbnails directory if it doesn't exist
        if (!Files.exists(thumbnailDir)) {
            Files.createDirectories(thumbnailDir);
        }

        // Generate thumbnail for the specific page
        try (PDDocument pdf = PDDocument.load(pdfFile)) {
            if (pageNumber > pdf.getNumberOfPages() || pageNumber < 1) {
                throw new IllegalArgumentException("Invalid page number: " + pageNumber);
            }

            PDFRenderer renderer = new PDFRenderer(pdf);
            BufferedImage image = renderer.renderImageWithDPI(pageNumber - 1, THUMBNAIL_DPI, ImageType.RGB);

            // Resize if the image is too large
            image = resizeIfNeeded(image);

            // Save thumbnail
            File thumbnailFile = thumbnailDir.resolve(String.format("page_%d_thumbnail.png", pageNumber)).toFile();
            ImageIOUtil.writeImage(image, thumbnailFile.getAbsolutePath(), THUMBNAIL_DPI);
        }
    }

    /**
     * Resize an image if it exceeds the maximum dimensions.
     *
     * @param image The image to resize
     * @return Resized image or the original if within limits
     */
    private BufferedImage resizeIfNeeded(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        // Check if resizing is needed
        if (width <= THUMBNAIL_MAX_WIDTH && height <= THUMBNAIL_MAX_HEIGHT) {
            return image;
        }

        // Calculate scale factor to fit within max dimensions
        double scale = Math.min(
                (double) THUMBNAIL_MAX_WIDTH / width,
                (double) THUMBNAIL_MAX_HEIGHT / height
        );

        // Calculate new dimensions
        int newWidth = (int) (width * scale);
        int newHeight = (int) (height * scale);

        // Create resized image
        BufferedImage resized = new BufferedImage(newWidth, newHeight, image.getType());
        Graphics2D g = resized.createGraphics();

        // Use better quality settings for resizing
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.drawImage(image, 0, 0, newWidth, newHeight, null);
        g.dispose();

        return resized;
    }
}