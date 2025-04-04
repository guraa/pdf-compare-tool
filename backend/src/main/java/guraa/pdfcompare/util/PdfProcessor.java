package guraa.pdfcompare.util;

import guraa.pdfcompare.model.PdfDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.tools.imageio.ImageIOUtil;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PdfProcessor {

    private final TextExtractor textExtractor;
    private final ImageExtractor imageExtractor;
    private final FontAnalyzer fontAnalyzer;

    private static final int DEFAULT_DPI = 150;
    private static final int THUMBNAIL_DPI = 72;

    /**
     * Process a PDF file and extract its metadata and structure.
     *
     * @param uploadedFile The uploaded PDF file
     * @return PdfDocument object with extracted metadata
     * @throws IOException If there's an error processing the file
     */
    public PdfDocument processPdf(File uploadedFile) throws IOException {
        String fileId = UUID.randomUUID().toString();
        String fileName = uploadedFile.getName();

        // Create output directory for this document
        Path documentsDir = Paths.get("uploads", "documents", fileId);
        Files.createDirectories(documentsDir);

        // Copy the original file to our storage
        File storedFile = documentsDir.resolve("original.pdf").toFile();
        FileUtils.copyFile(uploadedFile, storedFile);

        // Calculate MD5 hash for the document
        String contentHash = calculateMD5(storedFile);

        try (PDDocument document = PDDocument.load(storedFile)) {
            // Extract metadata
            Map<String, String> metadata = extractMetadata(document);

            // Create directories for rendered pages
            Path pagesDir = documentsDir.resolve("pages");
            Files.createDirectories(pagesDir);

            // Create directory for rendered page thumbnails
            Path thumbnailsDir = documentsDir.resolve("thumbnails");
            Files.createDirectories(thumbnailsDir);

            // Create directory for extracted text
            Path textDir = documentsDir.resolve("text");
            Files.createDirectories(textDir);

            // Create directory for extracted images
            Path imagesDir = documentsDir.resolve("images");
            Files.createDirectories(imagesDir);

            // Create directory for fonts
            Path fontsDir = documentsDir.resolve("fonts");
            Files.createDirectories(fontsDir);

            // Render each page as an image
            PDFRenderer renderer = new PDFRenderer(document);
            int pageCount = document.getNumberOfPages();

            // Process each page
            for (int i = 0; i < pageCount; i++) {
                // Render the page to an image (full resolution)
                BufferedImage image = renderer.renderImageWithDPI(i, DEFAULT_DPI, ImageType.RGB);

                // Save the rendered page
                File pageImageFile = pagesDir.resolve(String.format("page_%d.png", i + 1)).toFile();
                ImageIOUtil.writeImage(image, pageImageFile.getAbsolutePath(), DEFAULT_DPI);

                // Generate a thumbnail for quick preview
                BufferedImage thumbnail = renderer.renderImageWithDPI(i, THUMBNAIL_DPI, ImageType.RGB);
                File thumbnailFile = thumbnailsDir.resolve(String.format("page_%d_thumbnail.png", i + 1)).toFile();
                ImageIOUtil.writeImage(thumbnail, thumbnailFile.getAbsolutePath(), THUMBNAIL_DPI);

                // Extract text from the page
                String pageText = textExtractor.extractTextFromPage(document, i);
                File pageTextFile = textDir.resolve(String.format("page_%d.txt", i + 1)).toFile();
                FileUtils.writeStringToFile(pageTextFile, pageText, "UTF-8");

                // Extract detailed text elements with position information
                List<TextElement> textElements = textExtractor.extractTextElementsFromPage(document, i);
                File textElementsFile = textDir.resolve(String.format("page_%d_elements.json", i + 1)).toFile();
                FileUtils.writeStringToFile(textElementsFile, convertElementsToJson(textElements), "UTF-8");

                // Extract images from the page
                imageExtractor.extractImagesFromPage(document, i, imagesDir);

                // Extract fonts from the page
                fontAnalyzer.analyzeFontsOnPage(document, i, fontsDir);
            }

            // Build and return the PdfDocument object
            return PdfDocument.builder()
                    .fileId(fileId)
                    .fileName(fileName)
                    .filePath(storedFile.getAbsolutePath())
                    .fileSize(storedFile.length())
                    .pageCount(pageCount)
                    .metadata(metadata)
                    .uploadDate(LocalDateTime.now())
                    .processedDate(LocalDateTime.now())
                    .processed(true)
                    .contentHash(contentHash)
                    .build();
        }
    }

    /**
     * Extract metadata from a PDF document.
     *
     * @param document The PDF document
     * @return Map of metadata key-value pairs
     */
    // Within the PdfProcessor class, update the extractMetadata method to handle dates properly:

    private Map<String, String> extractMetadata(PDDocument document) {
        Map<String, String> metadata = new HashMap<>();
        PDDocumentInformation info = document.getDocumentInformation();

        if (info.getTitle() != null) metadata.put("title", info.getTitle());
        if (info.getAuthor() != null) metadata.put("author", info.getAuthor());
        if (info.getSubject() != null) metadata.put("subject", info.getSubject());
        if (info.getKeywords() != null) metadata.put("keywords", info.getKeywords());
        if (info.getCreator() != null) metadata.put("creator", info.getCreator());
        if (info.getProducer() != null) metadata.put("producer", info.getProducer());

        // Format dates as ISO strings to prevent Calendar object serialization issues
        if (info.getCreationDate() != null) {
            try {
                metadata.put("creationDate", info.getCreationDate().getTime().toString());
            } catch (Exception e) {
                log.warn("Could not format creation date", e);
            }
        }

        if (info.getModificationDate() != null) {
            try {
                metadata.put("modificationDate", info.getModificationDate().getTime().toString());
            } catch (Exception e) {
                log.warn("Could not format modification date", e);
            }
        }

        // Extract custom metadata with length validation
        for (String key : info.getMetadataKeys()) {
            if (!metadata.containsKey(key) && info.getCustomMetadataValue(key) != null) {
                String value = info.getCustomMetadataValue(key);
                // Truncate if too long for the database
                if (value.length() > 3900) {
                    value = value.substring(0, 3900) + "...";
                }
                metadata.put(key, value);
            }
        }

        return metadata;
    }

    /**
     * Truncate metadata value to prevent database column overflow.
     *
     * @param value The metadata value
     * @return Truncated value
     */
    private String truncateMetadata(String value) {
        if (value == null) return null;
        // Limit to 3900 characters to be safe (column is 4000)
        return value.length() > 3900 ? value.substring(0, 3900) + "..." : value;
    }

    /**
     * Calculate MD5 hash of a file.
     *
     * @param file The file to hash
     * @return MD5 hash string
     */
    private String calculateMD5(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }
            byte[] hash = md.digest();

            // Convert byte array to hex string
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("Failed to calculate MD5 hash", e);
            throw new IOException("Failed to calculate MD5 hash", e);
        }
    }

    /**
     * Get a rendered page image for a PDF document.
     *
     * @param pdfDocument The PDF document
     * @param pageNumber The page number (1-based index)
     * @return File reference to the rendered page image
     */
    public File getRenderedPage(PdfDocument pdfDocument, int pageNumber) {
        Path pageImagePath = Paths.get("uploads", "documents", pdfDocument.getFileId(),
                "pages", String.format("page_%d.png", pageNumber));
        return pageImagePath.toFile();
    }

    /**
     * Get a rendered page thumbnail for a PDF document.
     *
     * @param pdfDocument The PDF document
     * @param pageNumber The page number (1-based index)
     * @return File reference to the rendered page thumbnail
     */
    public File getPageThumbnail(PdfDocument pdfDocument, int pageNumber) {
        Path thumbnailPath = Paths.get("uploads", "documents", pdfDocument.getFileId(),
                "thumbnails", String.format("page_%d_thumbnail.png", pageNumber));
        return thumbnailPath.toFile();
    }

    /**
     * Convert TextElement list to JSON string.
     *
     * @param elements List of text elements
     * @return JSON string representation
     */
    private String convertElementsToJson(List<TextElement> elements) {
        StringBuilder json = new StringBuilder("[\n");
        for (int i = 0; i < elements.size(); i++) {
            TextElement element = elements.get(i);
            json.append("  {\n");
            json.append("    \"id\": \"").append(element.getId()).append("\",\n");
            json.append("    \"text\": \"").append(escapeJson(element.getText())).append("\",\n");
            json.append("    \"x\": ").append(element.getX()).append(",\n");
            json.append("    \"y\": ").append(element.getY()).append(",\n");
            json.append("    \"width\": ").append(element.getWidth()).append(",\n");
            json.append("    \"height\": ").append(element.getHeight()).append(",\n");
            json.append("    \"fontSize\": ").append(element.getFontSize()).append(",\n");
            json.append("    \"fontName\": \"").append(escapeJson(element.getFontName())).append("\",\n");
            if (element.getColor() != null) {
                json.append("    \"color\": \"").append(escapeJson(element.getColor())).append("\",\n");
            }
            json.append("    \"rotation\": ").append(element.getRotation()).append(",\n");
            json.append("    \"isBold\": ").append(element.isBold()).append(",\n");
            json.append("    \"isItalic\": ").append(element.isItalic()).append(",\n");
            json.append("    \"isEmbedded\": ").append(element.isEmbedded()).append(",\n");
            json.append("    \"lineId\": \"").append(element.getLineId()).append("\",\n");
            json.append("    \"wordId\": \"").append(element.getWordId()).append("\"\n");
            json.append("  }").append(i < elements.size() - 1 ? ",\n" : "\n");
        }
        json.append("]");
        return json.toString();
    }

    /**
     * Escape special characters in JSON strings.
     *
     * @param str The string to escape
     * @return Escaped string
     */
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}