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
                // Render the page to an image
                BufferedImage image = renderer.renderImageWithDPI(i, DEFAULT_DPI, ImageType.RGB);

                // Save the rendered page
                File pageImageFile = pagesDir.resolve(String.format("page_%d.png", i + 1)).toFile();
                ImageIOUtil.writeImage(image, pageImageFile.getAbsolutePath(), DEFAULT_DPI);

                // Extract text from the page
                String pageText = textExtractor.extractTextFromPage(document, i);
                File pageTextFile = textDir.resolve(String.format("page_%d.txt", i + 1)).toFile();
                FileUtils.writeStringToFile(pageTextFile, pageText, "UTF-8");

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
    private Map<String, String> extractMetadata(PDDocument document) {
        Map<String, String> metadata = new HashMap<>();
        PDDocumentInformation info = document.getDocumentInformation();

        if (info.getTitle() != null) metadata.put("title", info.getTitle());
        if (info.getAuthor() != null) metadata.put("author", info.getAuthor());
        if (info.getSubject() != null) metadata.put("subject", info.getSubject());
        if (info.getKeywords() != null) metadata.put("keywords", info.getKeywords());
        if (info.getCreator() != null) metadata.put("creator", info.getCreator());
        if (info.getProducer() != null) metadata.put("producer", info.getProducer());
        if (info.getCreationDate() != null) metadata.put("creationDate", info.getCreationDate().toString());
        if (info.getModificationDate() != null) metadata.put("modificationDate", info.getModificationDate().toString());

        return metadata;
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
}