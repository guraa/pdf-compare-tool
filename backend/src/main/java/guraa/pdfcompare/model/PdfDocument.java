package guraa.pdfcompare.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;

import javax.persistence.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
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
@Entity
@Table(name = "pdf_documents")
public class PdfDocument {

    /**
     * Unique identifier for this document.
     */
    @Id
    private String fileId;

    /**
     * The original file name.
     */
    @Column(name = "file_name")
    private String fileName;

    /**
     * The file path.
     */
    @Column(name = "file_path")
    private String filePath;

    /**
     * The number of pages in the document.
     */
    @Column(name = "page_count")
    private int pageCount;

    /**
     * The content hash of the document.
     */
    @Column(name = "content_hash")
    private String contentHash;

    /**
     * The upload date of the document.
     */
    @Column(name = "upload_date")
    private LocalDateTime uploadDate;

    /**
     * The title of the document.
     */
    @Column(name = "title")
    private String title;

    /**
     * The author of the document.
     */
    @Column(name = "author")
    private String author;

    /**
     * The subject of the document.
     */
    @Column(name = "subject")
    private String subject;

    /**
     * The keywords of the document.
     */
    @Column(name = "keywords")
    private String keywords;

    /**
     * The creator of the document.
     */
    @Column(name = "creator")
    private String creator;

    /**
     * The producer of the document.
     */
    @Column(name = "producer")
    private String producer;

    /**
     * The creation date of the document.
     */
    @Column(name = "creation_date")
    private String creationDate;

    /**
     * The modification date of the document.
     */
    @Column(name = "modification_date")
    private String modificationDate;

    /**
     * Whether the document is encrypted.
     */
    @Column(name = "encrypted")
    private boolean encrypted;

    /**
     * The base directory for rendered pages.
     */
    @Builder.Default
    @Transient
    private String renderedPagesDir = "uploads/documents";

    /**
     * The base directory for thumbnails.
     */
    @Builder.Default
    @Transient
    private String thumbnailsDir = "uploads/thumbnails";

    /**
     * The base directory for extracted text.
     */
    @Builder.Default
    @Transient
    private String extractedTextDir = "uploads/text";

    /**
     * The base directory for extracted images.
     */
    @Builder.Default
    @Transient
    private String extractedImagesDir = "uploads/images";

    /**
     * Get the metadata of the document.
     *
     * @return Map containing the document metadata
     */
    @Transient
    public Map<String, String> getMetadata() {
        Map<String, String> metadata = new HashMap<>();

        if (title != null) metadata.put("Title", title);
        if (author != null) metadata.put("Author", author);
        if (subject != null) metadata.put("Subject", subject);
        if (keywords != null) metadata.put("Keywords", keywords);
        if (creator != null) metadata.put("Creator", creator);
        if (producer != null) metadata.put("Producer", producer);
        if (creationDate != null) metadata.put("Creation Date", creationDate);
        if (modificationDate != null) metadata.put("Modification Date", modificationDate);

        metadata.put("Page Count", String.valueOf(pageCount));
        metadata.put("File Size", getHumanReadableFileSize());
        metadata.put("Encrypted", String.valueOf(encrypted));

        return metadata;
    }

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
                    .fileId(UUID.randomUUID().toString())
                    .fileName(file.getName())
                    .filePath(file.getAbsolutePath())
                    .pageCount(pdDocument.getNumberOfPages())
                    .uploadDate(LocalDateTime.now())
                    .build();

            // Extract metadata
            if (pdDocument.getDocumentInformation() != null) {
                document.title = pdDocument.getDocumentInformation().getTitle();
                document.author = pdDocument.getDocumentInformation().getAuthor();
                document.subject = pdDocument.getDocumentInformation().getSubject();
                document.keywords = pdDocument.getDocumentInformation().getKeywords();
                document.creator = pdDocument.getDocumentInformation().getCreator();
                document.producer = pdDocument.getDocumentInformation().getProducer();
                document.creationDate = pdDocument.getDocumentInformation().getCreationDate() != null ?
                        pdDocument.getDocumentInformation().getCreationDate().toString() : null;
                document.modificationDate = pdDocument.getDocumentInformation().getModificationDate() != null ?
                        pdDocument.getDocumentInformation().getModificationDate().toString() : null;
            }

            document.encrypted = pdDocument.isEncrypted();

            return document;
        }
    }

    /**
     * Get the path to the rendered page.
     *
     * @param pageNumber The page number (1-based)
     * @return The path to the rendered page
     */
    public String getRenderedPagePath(int pageNumber) {
        return Paths.get(renderedPagesDir, fileId, "pages", "page_" + pageNumber + ".png").toString();
    }

    /**
     * Get the path to the thumbnail.
     *
     * @param pageNumber The page number (1-based)
     * @return The path to the thumbnail
     */
    public String getThumbnailPath(int pageNumber) {
        return Paths.get(thumbnailsDir, fileId, "thumbnails", "page_" + pageNumber + "_thumbnail.png").toString();
    }

    /**
     * Get the path to the extracted text.
     *
     * @param pageNumber The page number (1-based)
     * @return The path to the extracted text
     */
    public String getExtractedTextPath(int pageNumber) {
        return Paths.get(extractedTextDir, fileId, "page_" + pageNumber + ".txt").toString();
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