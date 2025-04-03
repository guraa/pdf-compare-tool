package guraa.pdfcompare.controller;

import guraa.pdfcompare.model.PdfDocument;
import guraa.pdfcompare.service.PdfService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/pdfs")
@RequiredArgsConstructor
public class PdfController {

    private final PdfService pdfService;

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