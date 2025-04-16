package guraa.pdfcompare.controller;

import guraa.pdfcompare.model.PdfDocument;
import guraa.pdfcompare.service.PdfRenderingService;
import guraa.pdfcompare.service.PdfService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.http.fileupload.FileItemIterator;
import org.apache.tomcat.util.http.fileupload.FileItemStream;
import org.apache.tomcat.util.http.fileupload.servlet.ServletFileUpload;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
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
    private final PdfRenderingService pdfRenderingService;

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
                return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
            }

            if (!file.getContentType().equals("application/pdf")) {
                return ResponseEntity.badRequest().body(Map.of("error", "Only PDF files are allowed"));
            }

            // Create a temporary file to store the upload
            Path tempFile = Files.createTempFile("pdf-upload", ".pdf");
            file.transferTo(tempFile.toFile());

            // Process the PDF
            PdfDocument document = pdfService.processPdfFile(tempFile.toFile(), file.getOriginalFilename());

            Map<String, Object> response = new HashMap<>();
            response.put("fileId", document.getFileId());
            response.put("fileName", document.getFileName());
            response.put("pageCount", document.getPageCount());
            response.put("fileSize", document.getFileSize());

            // Add a flag to indicate if this was a reused document
            response.put("reused", document.getUploadDate().isBefore(LocalDateTime.now().minusMinutes(1)));

            // Clean up the temp file
            Files.deleteIfExists(tempFile);

            return ResponseEntity.ok().body(response);
        } catch (IOException e) {
            log.error("Failed to upload PDF", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to upload PDF: " + e.getMessage()));
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
     * @param fileId        The file ID
     * @param pageNumber    The page number (1-based)
     * @param forceRerender Whether to force re-rendering of the page
     * @return The page as an image
     */
    @GetMapping("/document/{fileId}/page/{pageNumber}")
    public ResponseEntity<?> getDocumentPage(
            @PathVariable String fileId,
            @PathVariable int pageNumber,
            @RequestParam(required = false, defaultValue = "false") boolean forceRerender) {

        try {
            // Get the document
            PdfDocument document = pdfService.getDocumentById(fileId);

            // Validate page number
            if (pageNumber < 1 || pageNumber > document.getPageCount()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Invalid page number: " + pageNumber,
                        "maxPage", document.getPageCount()
                ));
            }

            // If forcing re-render, delete existing rendered page
            if (forceRerender) {
                try {
                    File renderedPage = new File(document.getRenderedPagePath(pageNumber));
                    if (renderedPage.exists()) {
                        renderedPage.delete();
                    }
                } catch (Exception e) {
                    log.warn("Failed to delete existing page for re-render: {}", e.getMessage());
                }
            }

            // Get the rendered page image using the rendering service
            FileSystemResource pageImage = pdfRenderingService.getRenderedPage(document, pageNumber);

            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .body(pageImage);
        } catch (Exception e) {
            log.error("Failed to get document page: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to render page: " + e.getMessage()));
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
                return ResponseEntity.badRequest().body(Map.of("error", "Expected multipart request"));
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
                    return ResponseEntity.badRequest().body(Map.of("error", "Only PDF files are allowed"));
                }

                // Process the PDF
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
                try {
                    tempFile.delete();
                    Files.deleteIfExists(tempDir);
                } catch (Exception e) {
                    log.warn("Failed to delete temporary files: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to upload PDF via streaming", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to upload PDF: " + e.getMessage()));
        }
    }



    /**
     * Get a thumbnail for a specific page from a PDF document.
     *
     * @param fileId     The file ID
     * @param pageNumber The page number (1-based)
     * @return The thumbnail as an image
     */
    @GetMapping("/document/{fileId}/thumbnail/{pageNumber}")
    public ResponseEntity<?> getDocumentThumbnail(
            @PathVariable String fileId,
            @PathVariable int pageNumber,
            @RequestParam(required = false, defaultValue = "false") boolean forceRegenerate) {

        try {
            // Get the document
            PdfDocument document = pdfService.getDocumentById(fileId);

            // Validate page number
            if (pageNumber < 1 || pageNumber > document.getPageCount()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Invalid page number: " + pageNumber,
                        "maxPage", document.getPageCount()
                ));
            }

            // If forcing regeneration, delete existing thumbnail
            if (forceRegenerate) {
                try {
                    File thumbnailFile = new File(document.getThumbnailPath(pageNumber));
                    if (thumbnailFile.exists()) {
                        thumbnailFile.delete();
                    }
                } catch (Exception e) {
                    log.warn("Failed to delete existing thumbnail for regeneration: {}", e.getMessage());
                }
            }

            // Get the thumbnail using the rendering service
            FileSystemResource thumbnailImage = pdfRenderingService.getThumbnail(document, pageNumber);

            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .body(thumbnailImage);
        } catch (Exception e) {
            log.error("Failed to get document thumbnail: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to render thumbnail: " + e.getMessage()));
        }
    }

}