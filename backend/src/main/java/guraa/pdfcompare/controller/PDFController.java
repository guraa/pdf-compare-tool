package guraa.pdfcompare.controller;

import guraa.pdfcompare.PDFComparisonService;
import guraa.pdfcompare.comparison.PDFComparisonResult;
import guraa.pdfcompare.comparison.PageComparisonResult;
import guraa.pdfcompare.model.ComparisonRequest;
import guraa.pdfcompare.model.FileUploadResponse;
import guraa.pdfcompare.service.FileStorageService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Controller for PDF comparison operations
 */
@RestController
@RequestMapping("/api/pdfs")
public class PDFController {

    private final PDFComparisonService comparisonService;
    private final FileStorageService storageService;

    @Autowired
    public PDFController(PDFComparisonService comparisonService, FileStorageService storageService) {
        this.comparisonService = comparisonService;
        this.storageService = storageService;
    }

    /**
     * Upload a PDF file
     * @param file The PDF file to upload
     * @return Response with file ID and metadata
     */
    @PostMapping("/upload")
    public ResponseEntity<FileUploadResponse> uploadPDF(@RequestParam("file") MultipartFile file) {
        try {
            // Validate file
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(new FileUploadResponse(null, "File is empty"));
            }

            if (!file.getContentType().equals("application/pdf")) {
                return ResponseEntity.badRequest().body(new FileUploadResponse(null, "Only PDF files are allowed"));
            }

            // Store the file
            String fileId = storageService.storeFile(file);

            // Create response
            FileUploadResponse response = new FileUploadResponse();
            response.setFileId(fileId);
            response.setSuccess(true);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new FileUploadResponse(null, "Failed to upload file: " + e.getMessage()));
        }
    }

    /**
     * Compare two PDF documents
     * @param request The comparison request
     * @return Response with comparison ID
     */
    @PostMapping("/compare")
    public ResponseEntity<Map<String, String>> compareDocuments(@RequestBody ComparisonRequest request) {
        try {
            // Validate request
            if (request.getBaseFileId() == null || request.getCompareFileId() == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Both base and compare file IDs are required"));
            }

            // Get file paths
            String baseFilePath = storageService.getFilePath(request.getBaseFileId());
            String compareFilePath = storageService.getFilePath(request.getCompareFileId());

            // Compare files
            String comparisonId = comparisonService.compareFiles(baseFilePath, compareFilePath);

            // Create response
            Map<String, String> response = new HashMap<>();
            response.put("comparisonId", comparisonId);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to compare documents: " + e.getMessage()));
        }
    }

    /**
     * Get comparison result
     * @param comparisonId The comparison ID
     * @return The comparison result
     */
    @GetMapping("/comparison/{comparisonId}")
    public ResponseEntity<PDFComparisonResult> getComparisonResult(@PathVariable String comparisonId) {
        PDFComparisonResult result = comparisonService.getComparisonResult(comparisonId);

        if (result == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Get detailed comparison for a specific page
     * @param comparisonId The comparison ID
     * @param pageNumber The page number
     * @return The page comparison details
     */
    @GetMapping("/comparison/{comparisonId}/page/{pageNumber}")
    public ResponseEntity<Map<String, Object>> getComparisonDetails(
            @PathVariable String comparisonId,
            @PathVariable int pageNumber) {
        PDFComparisonResult result = comparisonService.getComparisonResult(comparisonId);

        if (result == null) {
            return ResponseEntity.notFound().build();
        }

        // Find the page
        PageComparisonResult pageResult = result.getPageDifferences().stream()
                .filter(page -> page.getPageNumber() == pageNumber)
                .findFirst()
                .orElse(null);

        if (pageResult == null) {
            return ResponseEntity.notFound().build();
        }

        // Create detailed response
        Map<String, Object> response = new HashMap<>();
        response.put("baseDifferences", pageResult.extractPageDifferences(true));
        response.put("compareDifferences", pageResult.extractPageDifferences(false));

        return ResponseEntity.ok(response);
    }

    /**
     * Get document page as image
     * @param fileId The file ID
     * @param page The page number
     * @return The page as an image
     */
    @GetMapping("/document/{fileId}/page/{page}")
    public ResponseEntity<Resource> getDocumentPage(
            @PathVariable String fileId,
            @PathVariable int page) {
        try {
            // Get the rendered page
            Resource resource = storageService.getPageAsImage(fileId, page);

            // Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Generate comparison report
     * @param comparisonId The comparison ID
     * @param request The report generation request
     * @return The generated report
     */
    @PostMapping("/comparison/{comparisonId}/report")
    public ResponseEntity<Resource> generateReport(
            @PathVariable String comparisonId,
            @RequestBody Map<String, Object> request) {
        try {
            String format = (String) request.getOrDefault("format", "pdf");

            // Generate report
            Resource report = comparisonService.generateReport(comparisonId, format);

            // Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentDispositionFormData("attachment", "comparison-report." + format);

            // Set content type based on format
            MediaType mediaType;
            switch (format) {
                case "html":
                    mediaType = MediaType.TEXT_HTML;
                    break;
                case "json":
                    mediaType = MediaType.APPLICATION_JSON;
                    break;
                default: // pdf
                    mediaType = MediaType.APPLICATION_PDF;
            }

            headers.setContentType(mediaType);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(report);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}