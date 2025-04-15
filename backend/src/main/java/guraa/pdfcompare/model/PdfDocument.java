package guraa.pdfcompare.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Represents a PDF document.
 * This class stores information about a PDF document,
 * such as its file path, metadata, and page count.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PdfDocument {

    /**
     * Unique identifier for this document.
     */
    @Builder.Default
    private String fileId = UUID.randomUUID().toString();

    /**
     * The original file name.
     */
    private String fileName;

    /**
     * The file path.
     */
    private String filePath;

    /**
     * The number of pages in the document.
     */
    private int pageCount;

    /**
     * The title of the document.
     */
    private String title;

    /**
     * The author of the document.
     */
    private String author;

    /**
     * The subject of the document.
     */
    private String subject;

    /**
     * The keywords of the document.
     */
    private String keywords;

    /**
     * The creator of the document.
     */
    private String creator;

    /**
     * The producer of the document.
     */
    private String producer;

    /**
     * The creation date of the document.
     */
    private String creationDate;

    /**
     * The modification date of the document.
     */
    private String modificationDate;

    /**
     * Whether the document is encrypted.
     */
    private boolean encrypted;

    /**
     * Whether the document is password-protected.
     */
    private boolean passwordProtected;

    /**
     * The base directory for rendered pages.
     */
    @Builder.Default
    private String renderedPagesDir = "rendered";

    /**
     * The base directory for thumbnails.
     */
    @Builder.Default
    private String thumbnailsDir = "thumbnails";

    /**
     * The base directory for extracted text.
     */
    @Builder.Default
    private String extractedTextDir = "text";

    /**
     * The base directory for extracted images.
     */
    @Builder.Default
    private String extractedImagesDir = "images";

    /**
     * Load a PDF document from a file.
     *
     * @param file The file
     * @return The PDF document
     * @throws IOException If there is an error loading the document
     */
    public static PdfDocument fromFile(File file) throws IOException {
        try (PDDocument pdDocument = PDDocument.load(file)) {
            PdfDocument document = PdfDocument.builder()
                    .fileName(file.getName())
                    .filePath(file.getAbsolutePath())
                    .pageCount(pdDocument.getNumberOfPages())
                    .build();
            
            // Extract metadata
            document.extractMetadata(pdDocument);
            
            return document;
        }
    }

    /**
     * Extract metadata from a PDF document.
     *
     * @param pdDocument The PDF document
     */
    private void extractMetadata(PDDocument pdDocument) {
        // Extract metadata from the PDF document
        if (pdDocument.getDocumentInformation() != null) {
            title = pdDocument.getDocumentInformation().getTitle();
            author = pdDocument.getDocumentInformation().getAuthor();
            subject = pdDocument.getDocumentInformation().getSubject();
            keywords = pdDocument.getDocumentInformation().getKeywords();
            creator = pdDocument.getDocumentInformation().getCreator();
            producer = pdDocument.getDocumentInformation().getProducer();
            creationDate = pdDocument.getDocumentInformation().getCreationDate() != null ?
                    pdDocument.getDocumentInformation().getCreationDate().toString() : null;
            modificationDate = pdDocument.getDocumentInformation().getModificationDate() != null ?
                    pdDocument.getDocumentInformation().getModificationDate().toString() : null;
        }
        
        encrypted = pdDocument.isEncrypted();
    }

    /**
     * Get the path to the rendered page.
     *
     * @param pageNumber The page number (1-based)
     * @return The path to the rendered page
     */
    public String getRenderedPagePath(int pageNumber) {
        return Paths.get(renderedPagesDir, fileId, pageNumber + ".png").toString();
    }

    /**
     * Get the path to the thumbnail.
     *
     * @param pageNumber The page number (1-based)
     * @return The path to the thumbnail
     */
    public String getThumbnailPath(int pageNumber) {
        return Paths.get(thumbnailsDir, fileId, pageNumber + ".png").toString();
    }

    /**
     * Get the path to the extracted text.
     *
     * @param pageNumber The page number (1-based)
     * @return The path to the extracted text
     */
    public String getExtractedTextPath(int pageNumber) {
        return Paths.get(extractedTextDir, fileId, pageNumber + ".txt").toString();
    }

    /**
     * Get the path to the extracted images directory.
     *
     * @param pageNumber The page number (1-based)
     * @return The path to the extracted images directory
     */
    public String getExtractedImagesPath(int pageNumber) {
        return Paths.get(extractedImagesDir, fileId, String.valueOf(pageNumber)).toString();
    }

    /**
     * Get the file extension.
     *
     * @return The file extension
     */
    public String getFileExtension() {
        if (fileName == null) {
            return "";
        }
        
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == fileName.length() - 1) {
            return "";
        }
        
        return fileName.substring(lastDotIndex + 1);
    }

    /**
     * Get the file size in bytes.
     *
     * @return The file size in bytes
     */
    public long getFileSize() {
        if (filePath == null) {
            return 0;
        }
        
        File file = new File(filePath);
        if (!file.exists()) {
            return 0;
        }
        
        return file.length();
    }

    /**
     * Get the file size in a human-readable format.
     *
     * @return The file size in a human-readable format
     */
    public String getHumanReadableFileSize() {
        long size = getFileSize();
        
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", size / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
        }
    }
}
