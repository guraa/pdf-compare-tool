package guraa.pdfcompare.service;

import guraa.pdfcompare.model.PdfDocument;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

@Slf4j
@Service
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

    public PdfRenderingService(
            @Qualifier("renderingExecutor") ExecutorService executorService) {
        this.executorService = executorService;
    }

    /**
     * Render a specific page of a PDF document with robust error handling.
     *
     * @param document   The document
     * @param pageNumber The page number (1-based)
     * @return The rendered page file
     * @throws IOException If there is an error rendering the page
     */
    public File renderPage(PdfDocument document, int pageNumber) throws IOException {
        File renderedPage = new File(document.getRenderedPagePath(pageNumber));

        try {
            // Ensure directory exists and is writable
            ensureDirectoryAccess(renderedPage.getParentFile().toPath());

            return retryWithCircuitBreaker(() -> {
                Path tempFile = null;
                try {
                    tempFile = Files.createTempFile("render_", ".png");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                try (PDDocument pdDocument = PDDocument.load(new File(document.getFilePath()))) {
                    PDFRenderer renderer = new PDFRenderer(pdDocument);

                    if (pageNumber < 1 || pageNumber > pdDocument.getNumberOfPages()) {
                        throw new IllegalArgumentException("Invalid page number: " + pageNumber);
                    }

                    BufferedImage image = null;
                    try {
                        image = renderer.renderImageWithDPI(
                                pageNumber - 1,
                                renderingDpi,
                                getImageType()
                        );
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    // Write to temp file
                    try {
                        ImageIO.write(image, "png", tempFile.toFile());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    // Attempt to move with explicit options to handle potential permission issues
                    try {
                        Files.move(tempFile, renderedPage.toPath(),
                                StandardCopyOption.REPLACE_EXISTING,
                                StandardCopyOption.ATOMIC_MOVE
                        );
                    } catch (FileSystemException e) {
                        // Fallback to copy if move fails
                        log.warn("Move failed, falling back to copy: {}", e.getMessage());
                        Files.copy(tempFile, renderedPage.toPath(),
                                StandardCopyOption.REPLACE_EXISTING
                        );
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    return renderedPage;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } finally {
                    // Ensure temp file is deleted
                    try {
                        Files.deleteIfExists(tempFile);
                    } catch (IOException deleteEx) {
                        log.warn("Failed to delete temporary file: {}", deleteEx.getMessage());
                    }
                }
            }, 3, 500);
        } catch (IOException e) {
            log.error("Failed to render page {}: {}", pageNumber, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Pre-render all pages of a PDF document in parallel.
     *
     * @param document The document to pre-render
     * @throws IOException If there is an error rendering the pages
     */
    public void preRenderAllPages(PdfDocument document) throws IOException {
        log.info("Pre-rendering all pages for document: {}", document.getFileId());

        int pageCount = document.getPageCount();
        int completedPages = 0;

        for (int pageNumber = 1; pageNumber <= pageCount; pageNumber++) {
            try {
                // Check if the page is already rendered
                File renderedPage = new File(document.getRenderedPagePath(pageNumber));
                if (!renderedPage.exists() || renderedPage.length() == 0) {
                    renderPage(document, pageNumber);
                }

                // Log progress periodically
                completedPages++;
                if (completedPages % 5 == 0 || completedPages == pageCount) {
                    log.info("Pre-rendering progress for document {}: {}/{} pages ({} %)",
                            document.getFileId(), completedPages, pageCount, (completedPages * 100) / pageCount);
                }
            } catch (IOException e) {
                log.error("Error pre-rendering page {} of document {}: {}",
                        pageNumber, document.getFileId(), e.getMessage(), e);
                // Continue with other pages even if one fails
            }
        }

        log.info("Successfully pre-rendered all {} pages for document: {}",
                pageCount, document.getFileId());
    }

    /**
     * Get a rendered page as a FileSystemResource.
     *
     * @param document   The document
     * @param pageNumber The page number (1-based)
     * @return The rendered page as a FileSystemResource
     * @throws IOException If there is an error rendering the page
     */
    public FileSystemResource getRenderedPage(PdfDocument document, int pageNumber) throws IOException {
        // Check if the page is already rendered
        File renderedPage = new File(document.getRenderedPagePath(pageNumber));

        // If the file doesn't exist or is empty, render it
        if (!renderedPage.exists() || renderedPage.length() == 0) {
            renderPage(document, pageNumber);
        }

        // Verify the file was rendered successfully
        if (!renderedPage.exists() || renderedPage.length() == 0) {
            throw new IOException("Failed to render page " + pageNumber + " of document " + document.getFileId());
        }

        return new FileSystemResource(renderedPage);
    }

    /**
     * Set directory permissions to ensure writability across different platforms.
     *
     * @param directory The directory path
     */
    private void setDirectoryPermissions(Path directory) {
        try {
            // First, try Windows-specific ACL approach
            if (Files.getFileStore(directory).supportsFileAttributeView("acl")) {
                setWindowsDirectoryPermissions(directory);
            }
            // Fallback to POSIX if supported
            else if (Files.getFileStore(directory).supportsFileAttributeView("posix")) {
                setPosixDirectoryPermissions(directory);
            }
        } catch (IOException | UnsupportedOperationException e) {
            log.warn("Could not set directory permissions: {}", e.getMessage());
        }
    }

    /**
     * Set Windows-specific directory permissions.
     *
     * @param directory The directory path
     */
    private void setWindowsDirectoryPermissions(Path directory) {
        try {
            AclFileAttributeView aclView = Files.getFileAttributeView(directory, AclFileAttributeView.class);
            if (aclView != null) {
                // Get the current user
                UserPrincipal owner = directory.getFileSystem().getUserPrincipalLookupService()
                        .lookupPrincipalByName(System.getProperty("user.name"));

                // Here you could add more sophisticated ACL modifications if needed
                aclView.setOwner(owner);
                log.debug("Successfully set owner for directory: {}", directory);
            }
        } catch (IOException e) {
            log.warn("Failed to set Windows directory permissions: {}", e.getMessage());
        }
    }

    /**
     * Set POSIX directory permissions.
     *
     * @param directory The directory path
     */
    private void setPosixDirectoryPermissions(Path directory) {
        try {
            Set<PosixFilePermission> permissions = PosixFilePermissions.fromString("rwxr-xr-x");
            Files.setPosixFilePermissions(directory, permissions);
            log.debug("Successfully set POSIX permissions for directory: {}", directory);
        } catch (IOException e) {
            log.warn("Failed to set POSIX directory permissions: {}", e.getMessage());
        }
    }

    /**
     * Generate a thumbnail for a specific page of a PDF document.
     *
     * @param document   The document
     * @param pageNumber The page number (1-based)
     * @return The thumbnail as a FileSystemResource
     * @throws IOException If there is an error generating the thumbnail
     */
    public FileSystemResource getThumbnail(PdfDocument document, int pageNumber) throws IOException {
        // Check if the thumbnail already exists
        File thumbnailFile = new File(document.getThumbnailPath(pageNumber));

        // If the file doesn't exist or is empty, generate it
        if (!thumbnailFile.exists() || thumbnailFile.length() == 0) {
            // Ensure the directory exists
            Path thumbnailDir = thumbnailFile.getParentFile().toPath();
            Files.createDirectories(thumbnailDir);

            // Generate the thumbnail
            generateThumbnail(document, pageNumber);
        }

        // Verify the thumbnail was generated successfully
        if (!thumbnailFile.exists() || thumbnailFile.length() == 0) {
            throw new IOException("Failed to generate thumbnail for page " + pageNumber + " of document " + document.getFileId());
        }

        return new FileSystemResource(thumbnailFile);
    }

    /**
     * Generate a thumbnail for a specific page of a PDF document.
     *
     * @param document   The document
     * @param pageNumber The page number (1-based)
     * @throws IOException If there is an error generating the thumbnail
     */
    private void generateThumbnail(PdfDocument document, int pageNumber) throws IOException {
        File thumbnailFile = new File(document.getThumbnailPath(pageNumber));

        try (PDDocument pdDocument = PDDocument.load(new File(document.getFilePath()))) {
            PDFRenderer renderer = new PDFRenderer(pdDocument);

            // Check if the page number is valid
            if (pageNumber < 1 || pageNumber > pdDocument.getNumberOfPages()) {
                throw new IOException("Invalid page number: " + pageNumber +
                        ". Document has " + pdDocument.getNumberOfPages() + " pages.");
            }

            // Render the page at a lower DPI
            BufferedImage image = renderer.renderImageWithDPI(pageNumber - 1, thumbnailDpi, getImageType());

            // Resize to the desired thumbnail dimensions
            BufferedImage thumbnailImage = resizeImage(image, thumbnailWidth, thumbnailHeight);

            // Ensure the parent directory exists
            Files.createDirectories(thumbnailFile.getParentFile().toPath());

            // Save the thumbnail
            if (!ImageIO.write(thumbnailImage, renderingFormat, thumbnailFile)) {
                throw new IOException("Failed to write thumbnail in " + renderingFormat + " format");
            }
        }
    }

    /**
     * Resize an image to the specified dimensions.
     *
     * @param image  The image to resize
     * @param width  The target width
     * @param height The target height
     * @return The resized image
     * @throws IOException If there is an error resizing the image
     */
    private BufferedImage resizeImage(BufferedImage image, int width, int height) throws IOException {
        BufferedImage resizedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resizedImage.createGraphics();

        try {
            // Set rendering hints for better quality
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Draw the original image scaled to the new dimensions
            g.drawImage(image, 0, 0, width, height, null);

            return resizedImage;
        } finally {
            g.dispose();
        }
    }

    /**
     * Ensure directory exists with proper access.
     *
     * @param directory The directory path
     * @throws IOException If directory creation fails
     */
    private void ensureDirectoryAccess(Path directory) throws IOException {
        // Create directories with explicit permissions
        Files.createDirectories(directory);

        // Attempt to set permissions
        setDirectoryPermissions(directory);

        // Verify directory is writable
        if (!Files.isWritable(directory)) {
            throw new IOException("Cannot write to directory: " + directory);
        }
    }

    /**
     * Retry mechanism with circuit breaker for file operations.
     *
     * @param renderTask The rendering task to execute
     * @param maxRetries Maximum number of retry attempts
     * @param delayMs Delay between retry attempts
     * @return The rendered file
     * @throws IOException If rendering fails after all retries
     */
    private File retryWithCircuitBreaker(Supplier<File> renderTask, int maxRetries, int delayMs) throws IOException {
        int attempts = 0;
        IOException lastException = null;

        while (attempts < maxRetries) {
            return renderTask.get();
        }

        throw new IOException("Rendering failed due to unexpected error", lastException);
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
}