package guraa.pdfcompare.service;

import guraa.pdfcompare.model.PdfDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;

/**
 * Service for rendering PDF pages as images.
 * This service provides methods for rendering PDF pages as images and thumbnails.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfRenderingService {

    private final ExecutorService executorService;

    @Value("${app.rendering.dpi:300}")
    private float renderingDpi;

    @Value("${app.rendering.thumbnail-dpi:72}")
    private float thumbnailDpi;

    @Value("${app.rendering.image-type:RGB}")
    private String renderingImageType;

    @Value("${app.rendering.format:png}")
    private String renderingFormat;

    @Value("${app.rendering.thumbnail-width:200}")
    private int thumbnailWidth;

    @Value("${app.rendering.thumbnail-height:280}")
    private int thumbnailHeight;

    /**
     * Get the rendered page image for a document.
     *
     * @param document The document
     * @param pageNumber The page number (1-based)
     * @return The rendered page as a FileSystemResource
     * @throws IOException If there is an error rendering the page
     */
    public FileSystemResource getRenderedPage(PdfDocument document, int pageNumber) throws IOException {
        // Check if the page is already rendered
        File renderedPage = new File(document.getRenderedPagePath(pageNumber));

        // If the file doesn't exist, render it
        if (!renderedPage.exists()) {
            // Ensure the directory exists
            Path renderedPageDir = renderedPage.getParentFile().toPath();
            if (!Files.exists(renderedPageDir)) {
                Files.createDirectories(renderedPageDir);
            }

            renderPage(document, pageNumber);
        }

        return new FileSystemResource(renderedPage);
    }

    /**
     * Get the thumbnail image for a document page.
     *
     * @param document The document
     * @param pageNumber The page number (1-based)
     * @return The thumbnail as a FileSystemResource
     * @throws IOException If there is an error generating the thumbnail
     */
    public FileSystemResource getThumbnail(PdfDocument document, int pageNumber) throws IOException {
        // Check if the thumbnail already exists
        File thumbnailFile = new File(document.getThumbnailPath(pageNumber));

        // If the file doesn't exist, generate it
        if (!thumbnailFile.exists()) {
            // Ensure the directory exists
            Path thumbnailDir = thumbnailFile.getParentFile().toPath();
            if (!Files.exists(thumbnailDir)) {
                Files.createDirectories(thumbnailDir);
            }

            generateThumbnail(document, pageNumber);
        }

        return new FileSystemResource(thumbnailFile);
    }

    /**
     * Render a specific page of a PDF document.
     *
     * @param document The document
     * @param pageNumber The page number (1-based)
     * @return The rendered page file
     * @throws IOException If there is an error rendering the page
     */
    public File renderPage(PdfDocument document, int pageNumber) throws IOException {
        File renderedPage = new File(document.getRenderedPagePath(pageNumber));

        // Create parent directories if they don't exist
        Path renderedPageDir = renderedPage.getParentFile().toPath();
        if (!Files.exists(renderedPageDir)) {
            Files.createDirectories(renderedPageDir);
        }

        // Open the PDF document
        try (PDDocument pdDocument = PDDocument.load(new File(document.getFilePath()))) {
            PDFRenderer renderer = new PDFRenderer(pdDocument);

            // Render the page
            BufferedImage image = renderer.renderImageWithDPI(pageNumber - 1, renderingDpi, getImageType());

            // Save the image
            ImageIO.write(image, renderingFormat, renderedPage);
        }

        return renderedPage;
    }

    /**
     * Generate a thumbnail for a specific page of a PDF document.
     *
     * @param document The document
     * @param pageNumber The page number (1-based)
     * @throws IOException If there is an error generating the thumbnail
     */
    private void generateThumbnail(PdfDocument document, int pageNumber) throws IOException {
        File thumbnailFile = new File(document.getThumbnailPath(pageNumber));

        // Open the PDF document
        try (PDDocument pdDocument = PDDocument.load(new File(document.getFilePath()))) {
            PDFRenderer renderer = new PDFRenderer(pdDocument);

            // Render the page at a lower DPI
            BufferedImage image = renderer.renderImageWithDPI(pageNumber - 1, thumbnailDpi, getImageType());

            // Resize to the desired thumbnail dimensions
            BufferedImage thumbnailImage = resizeImage(image, thumbnailWidth, thumbnailHeight);

            // Save the thumbnail
            ImageIO.write(thumbnailImage, renderingFormat, thumbnailFile);
        }
    }

    /**
     * Resize an image to the specified dimensions.
     *
     * @param originalImage The original image
     * @param targetWidth The target width
     * @param targetHeight The target height
     * @return The resized image
     */
    private BufferedImage resizeImage(BufferedImage originalImage, int targetWidth, int targetHeight) {
        BufferedImage resizedImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        resizedImage.createGraphics().drawImage(originalImage, 0, 0, targetWidth, targetHeight, null);
        return resizedImage;
    }

    /**
     * Get the image type for rendering.
     *
     * @return The image type
     */
    private ImageType getImageType() {
        switch (renderingImageType.toUpperCase()) {
            case "BINARY":
                return ImageType.BINARY;
            case "GRAY":
                return ImageType.GRAY;
            case "ARGB":
                return ImageType.ARGB;
            case "RGB":
            default:
                return ImageType.RGB;
        }
    }

    /**
     * Pre-render all pages of a document.
     *
     * @param document The document
     * @throws IOException If there is an error pre-rendering the pages
     */
    public void preRenderAllPages(PdfDocument document) throws IOException {
        for (int pageNumber = 1; pageNumber <= document.getPageCount(); pageNumber++) {
            renderPage(document, pageNumber);
        }
    }
}