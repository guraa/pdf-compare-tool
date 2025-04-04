package guraa.pdfcompare.controller;

import guraa.pdfcompare.model.PdfDocument;
import guraa.pdfcompare.service.PdfService;
import guraa.pdfcompare.service.ThumbnailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.http.fileupload.FileItemIterator;
import org.apache.tomcat.util.http.fileupload.FileItemStream;
import org.apache.tomcat.util.http.fileupload.servlet.ServletFileUpload;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/pdfs")
@RequiredArgsConstructor
public class PdfController {

    private final PdfService pdfService;
    private final ThumbnailService thumbnailService;


    /**
     * Upload a PDF file.
     *
     * @param file The file to upload
     * @return Information about the uploaded file
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadPdf(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body("{\"error\": \"File is empty\"}");
            }

            if (!file.getContentType().equals("application/pdf")) {
                return ResponseEntity.badRequest().body("{\"error\": \"Only PDF files are allowed\"}");
            }

            PdfDocument document = pdfService.storePdf(file);
            Map<String, Object> response = new HashMap<>();
            response.put("fileId", document.getFileId());
            response.put("fileName", document.getFileName());
            response.put("pageCount", document.getPageCount());
            response.put("fileSize", document.getFileSize());
            
            // Add a flag to indicate if this was a reused document
            response.put("reused", document.getUploadDate().isBefore(LocalDateTime.now().minusMinutes(1)));

            return ResponseEntity.ok().body(response);
        } catch (IOException e) {
            log.error("Failed to upload PDF", e);
            return ResponseEntity.internalServerError().body("{\"error\": \"Failed to upload PDF: " + e.getMessage() + "\"}");
        }
    }

    /**
     * Get information about a PDF document.
     *
     * @param fileId The file ID
     * @return Information about the document
     */
    @GetMapping("/document/{fileId}")
    public ResponseEntity<?> getDocumentInfo(@PathVariable String fileId) {
        try {
            PdfDocument document = pdfService.getDocumentById(fileId);
            Map<String, Object> response = new HashMap<>();
            response.put("fileId", document.getFileId());
            response.put("fileName", document.getFileName());
            response.put("pageCount", document.getPageCount());
            response.put("fileSize", document.getFileSize());
            response.put("metadata", document.getMetadata());

            return ResponseEntity.ok().body(response);
        } catch (Exception e) {
            log.error("Failed to get document info", e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get a specific page from a PDF document as an image.
     *
     * @param fileId The file ID
     * @param pageNumber The page number (1-based)
     * @return The page as an image
     */
    @GetMapping("/document/{fileId}/page/{pageNumber}")
    public ResponseEntity<?> getDocumentPage(@PathVariable String fileId,
                                             @PathVariable int pageNumber) {
        try {
            // Get the rendered page image
            FileSystemResource pageImage = pdfService.getRenderedPage(fileId, pageNumber);

            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .body(pageImage);
        } catch (Exception e) {
            log.error("Failed to get document page", e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Compare two PDF documents.
     *
     * @param request The comparison request
     * @return The comparison ID
     */
    @PostMapping("/compare")
    public ResponseEntity<?> comparePdfs(@RequestBody CompareRequest request) {
        try {
            String comparisonId = pdfService.comparePdfs(
                    request.getBaseFileId(),
                    request.getCompareFileId(),
                    request.getOptions());

            Map<String, Object> response = new HashMap<>();
            response.put("comparisonId", comparisonId);

            return ResponseEntity.ok().body(response);
        } catch (Exception e) {
            log.error("Failed to compare PDFs", e);
            return ResponseEntity.internalServerError().body("{\"error\": \"Failed to compare PDFs: " + e.getMessage() + "\"}");
        }
    }



    /**
     * Upload a PDF file with streaming support for large files.
     * This is particularly helpful for very large PDFs.
     *
     * @param request The HTTP request
     * @return Information about the uploaded file
     */
    @PostMapping("/upload/stream")
    public ResponseEntity<?> uploadPdfStreaming(HttpServletRequest request) {
        try {
            // Check if content type is multipart
            if (!request.getContentType().startsWith("multipart/")) {
                return ResponseEntity.badRequest().body("{\"error\": \"Expected multipart request\"}");
            }

            // Create a temporary file to store the uploaded content
            Path tempDir = Files.createTempDirectory("pdf-upload");
            File tempFile = Files.createTempFile(tempDir, "upload-", ".pdf").toFile();

            try {
                // Process the multipart request to find the file part
                ServletFileUpload upload = new ServletFileUpload();
                FileItemIterator iterator = upload.getItemIterator(request);

                String fileName = "document.pdf"; // Default name if none provided

                while (iterator.hasNext()) {
                    FileItemStream item = iterator.next();

                    if (!item.isFormField()) {
                        // Found a file upload
                        String name = item.getName();
                        if (name != null && !name.isEmpty()) {
                            fileName = name;
                        }

                        // Stream the file to disk
                        try (InputStream inputStream = item.openStream();
                             FileOutputStream outputStream = new FileOutputStream(tempFile)) {

                            byte[] buffer = new byte[8192];
                            int bytesRead;
                            while ((bytesRead = inputStream.read(buffer)) != -1) {
                                outputStream.write(buffer, 0, bytesRead);
                            }
                        }

                        // Break after processing the file part
                        break;
                    }
                }

                // Check if it's a PDF file
                if (!fileName.toLowerCase().endsWith(".pdf")) {
                    return ResponseEntity.badRequest().body("{\"error\": \"Only PDF files are allowed\"}");
                }

                // Process the PDF - this will now check for existing documents with the same content hash
                PdfDocument document = pdfService.processPdfFile(tempFile, fileName);

                // Create response
                Map<String, Object> response = new HashMap<>();
                response.put("fileId", document.getFileId());
                response.put("fileName", document.getFileName());
                response.put("pageCount", document.getPageCount());
                response.put("fileSize", document.getFileSize());
                
                // Add a flag to indicate if this was a reused document
                response.put("reused", document.getUploadDate().isBefore(LocalDateTime.now().minusMinutes(1)));

                return ResponseEntity.ok().body(response);

            } finally {
                // Clean up temporary files
                tempFile.delete();
                Files.deleteIfExists(tempDir);
            }
        } catch (Exception e) {
            log.error("Failed to upload PDF via streaming", e);
            return ResponseEntity.internalServerError().body("{\"error\": \"Failed to upload PDF: " + e.getMessage() + "\"}");
        }
    }
    /**
     * Get a thumbnail for a specific page from a PDF document.
     *
     * @param fileId The file ID
     * @param pageNumber The page number (1-based)
     * @return The thumbnail as an image
     */
    @GetMapping("/document/{fileId}/thumbnail/{pageNumber}")
    public ResponseEntity<?> getDocumentThumbnail(@PathVariable String fileId,
                                                  @PathVariable int pageNumber) {
        try {
            // Get the document
            PdfDocument document = pdfService.getDocumentById(fileId);

            // Get the thumbnail
            FileSystemResource thumbnailImage = thumbnailService.getThumbnail(document, pageNumber);

            if (thumbnailImage == null || !thumbnailImage.exists()) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .body(thumbnailImage);
        } catch (Exception e) {
            log.error("Failed to get document thumbnail", e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Request object for PDF comparison.
     */
    public static class CompareRequest {
        private String baseFileId;
        private String compareFileId;
        private Map<String, Object> options;

        // Getters and setters
        public String getBaseFileId() { return baseFileId; }
        public void setBaseFileId(String baseFileId) { this.baseFileId = baseFileId; }

        public String getCompareFileId() { return compareFileId; }
        public void setCompareFileId(String compareFileId) { this.compareFileId = compareFileId; }

        public Map<String, Object> getOptions() { return options; }
        public void setOptions(Map<String, Object> options) { this.options = options; }
    }
}
