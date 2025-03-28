package guraa.pdfcompare.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Service for file storage operations
 */
@Service
public class FileStorageService {

    private final Path fileStorageLocation;

    public FileStorageService() {
        // Create storage directory in system temp directory
        this.fileStorageLocation = Paths.get(System.getProperty("java.io.tmpdir"), "pdf-compare")
                .toAbsolutePath().normalize();

        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (Exception ex) {
            throw new RuntimeException("Could not create the directory where the uploaded files will be stored.", ex);
        }
    }

    /**
     * Store a file
     * @param file The file to store
     * @return The file ID
     * @throws IOException If there's an error storing the file
     */
    public String storeFile(MultipartFile file) throws IOException {
        // Generate file ID
        String fileId = UUID.randomUUID().toString();

        // Generate file name
        String fileName = StringUtils.cleanPath(file.getOriginalFilename());
        String extension = fileName.substring(fileName.lastIndexOf("."));
        String storedFileName = fileId + extension;

        // Store file
        Path targetLocation = this.fileStorageLocation.resolve(storedFileName);
        Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

        return fileId;
    }

    /**
     * Get file path by ID
     * @param fileId The file ID
     * @return The file path
     */
    public String getFilePath(String fileId) {
        // Find the file
        File[] files = this.fileStorageLocation.toFile().listFiles(
                (dir, name) -> name.startsWith(fileId));

        if (files == null || files.length == 0) {
            throw new RuntimeException("File not found: " + fileId);
        }

        return files[0].getAbsolutePath();
    }


    /**
     * Get file by ID
     * @param fileId The file ID
     * @return The file
     */
    public File getFile(String fileId) {
        // Find the file
        File[] files = this.fileStorageLocation.toFile().listFiles(
                (dir, name) -> name.startsWith(fileId));

        if (files == null || files.length == 0) {
            throw new RuntimeException("File not found: " + fileId);
        }

        return files[0];
    }

    /**
     * Render a page as an image
     * @param fileId The file ID
     * @param pageNumber The page number
     * @return The page as a resource
     * @throws IOException If there's an error rendering the page
     */
    public Resource getPageAsImage(String fileId, int pageNumber) throws IOException {
        File pdfFile = getFile(fileId);

        try (PDDocument document = PDDocument.load(pdfFile)) {
            // Check page number
            if (pageNumber < 1 || pageNumber > document.getNumberOfPages()) {
                throw new RuntimeException("Invalid page number: " + pageNumber);
            }

            // Render page
            PDFRenderer renderer = new PDFRenderer(document);
            BufferedImage image = renderer.renderImageWithDPI(pageNumber - 1, 150);

            // Convert to PNG
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);

            return new ByteArrayResource(output.toByteArray());
        } catch (Exception e) {
            throw new IOException("Error rendering PDF page: " + e.getMessage(), e);
        }
    }



}