package guraa.pdfcompare.service;

import guraa.pdfcompare.model.PdfDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * Service for rendering PDF pages as images.
 * This service provides methods for rendering PDF pages as images,
 * which can be used for visual comparison.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfRenderingService {

    private final ExecutorService executorService;

    @Value("${app.rendering.dpi:300}")
    private float renderingDpi;

    @Value("${app.rendering.image-type:RGB}")
    private String renderingImageType;

    @Value("${app.rendering.format:png}")
    private String renderingFormat;

    // Cache of rendering tasks
    private final ConcurrentHashMap<String, CompletableFuture<Void>> renderingTasks = new ConcurrentHashMap<>();

    /**
     * Pre-render all pages of a PDF document.
     *
     * @param document The PDF document
     * @throws IOException If there is an error rendering the pages
     */
    public void preRenderAllPages(PdfDocument document) throws IOException {
        String cacheKey = document.getFileId() + "_all";
        
        // Check if we already have a rendering task for this document
        renderingTasks.computeIfAbsent(cacheKey, key -> {
            return CompletableFuture.runAsync(() -> {
                try {
                    doPreRenderAllPages(document);
                } catch (IOException e) {
                    log.error("Error pre-rendering document: {}", e.getMessage(), e);
                    throw new RuntimeException(e);
                }
            }, executorService);
        }).join(); // Wait for the task to complete
    }

    /**
     * Perform the actual pre-rendering of all pages.
     *
     * @param document The PDF document
     * @throws IOException If there is an error rendering the pages
     */
    private void doPreRenderAllPages(PdfDocument document) throws IOException {
        // Create the directory for rendered pages if it doesn't exist
        Path renderedPagesDir = Paths.get(document.getRenderedPagesDir(), document.getFileId());
        if (!Files.exists(renderedPagesDir)) {
            Files.createDirectories(renderedPagesDir);
        }
        
        // Open the PDF document
        try (PDDocument pdDocument = PDDocument.load(new File(document.getFilePath()))) {
            PDFRenderer renderer = new PDFRenderer(pdDocument);
            
            // Render each page
            for (int i = 0; i < pdDocument.getNumberOfPages(); i++) {
                int pageNumber = i + 1; // 1-based page number
                
                // Check if the page is already rendered
                File renderedPage = new File(document.getRenderedPagePath(pageNumber));
                if (renderedPage.exists()) {
                    continue;
                }
                
                // Render the page
                BufferedImage image = renderer.renderImageWithDPI(i, renderingDpi, getImageType());
                
                // Save the image
                ImageIO.write(image, renderingFormat, renderedPage);
            }
        }
    }

    /**
     * Render a specific page of a PDF document.
     *
     * @param document The PDF document
     * @param pageNumber The page number (1-based)
     * @return The rendered page as a file
     * @throws IOException If there is an error rendering the page
     */
    public File renderPage(PdfDocument document, int pageNumber) throws IOException {
        // Check if the page is already rendered
        File renderedPage = new File(document.getRenderedPagePath(pageNumber));
        if (renderedPage.exists()) {
            return renderedPage;
        }
        
        // Create the directory for rendered pages if it doesn't exist
        Path renderedPagesDir = Paths.get(document.getRenderedPagesDir(), document.getFileId());
        if (!Files.exists(renderedPagesDir)) {
            Files.createDirectories(renderedPagesDir);
        }
        
        // Open the PDF document
        try (PDDocument pdDocument = PDDocument.load(new File(document.getFilePath()))) {
            PDFRenderer renderer = new PDFRenderer(pdDocument);
            
            // Render the page
            BufferedImage image = renderer.renderImageWithDPI(pageNumber - 1, renderingDpi, getImageType());
            
            // Save the image
            ImageIO.write(image, renderingFormat, renderedPage);
            
            return renderedPage;
        }
    }

    /**
     * Render a thumbnail for a specific page of a PDF document.
     *
     * @param document The PDF document
     * @param pageNumber The page number (1-based)
     * @param width The width of the thumbnail
     * @param height The height of the thumbnail
     * @return The thumbnail as a file
     * @throws IOException If there is an error rendering the thumbnail
     */
    public File renderThumbnail(PdfDocument document, int pageNumber, int width, int height) throws IOException {
        // Check if the thumbnail is already rendered
        File thumbnail = new File(document.getThumbnailPath(pageNumber));
        if (thumbnail.exists()) {
            return thumbnail;
        }
        
        // Create the directory for thumbnails if it doesn't exist
        Path thumbnailsDir = Paths.get(document.getThumbnailsDir(), document.getFileId());
        if (!Files.exists(thumbnailsDir)) {
            Files.createDirectories(thumbnailsDir);
        }
        
        // Open the PDF document
        try (PDDocument pdDocument = PDDocument.load(new File(document.getFilePath()))) {
            PDFRenderer renderer = new PDFRenderer(pdDocument);
            
            // Render the page
            BufferedImage image = renderer.renderImageWithDPI(pageNumber - 1, renderingDpi, getImageType());
            
            // Scale the image to the desired size
            BufferedImage scaledImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            scaledImage.createGraphics().drawImage(image, 0, 0, width, height, null);
            
            // Save the image
            ImageIO.write(scaledImage, renderingFormat, thumbnail);
            
            return thumbnail;
        }
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
     * Delete all rendered pages for a document.
     *
     * @param document The PDF document
     * @throws IOException If there is an error deleting the rendered pages
     */
    public void deleteRenderedPages(PdfDocument document) throws IOException {
        // Delete the rendered pages directory
        Path renderedPagesDir = Paths.get(document.getRenderedPagesDir(), document.getFileId());
        if (Files.exists(renderedPagesDir)) {
            Files.walk(renderedPagesDir)
                    .sorted((p1, p2) -> -p1.compareTo(p2))
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            log.error("Error deleting rendered page: {}", e.getMessage(), e);
                        }
                    });
        }
        
        // Delete the thumbnails directory
        Path thumbnailsDir = Paths.get(document.getThumbnailsDir(), document.getFileId());
        if (Files.exists(thumbnailsDir)) {
            Files.walk(thumbnailsDir)
                    .sorted((p1, p2) -> -p1.compareTo(p2))
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            log.error("Error deleting thumbnail: {}", e.getMessage(), e);
                        }
                    });
        }
    }
}
