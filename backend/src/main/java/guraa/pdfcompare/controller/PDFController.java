package guraa.pdfcompare.controller;

import guraa.pdfcompare.PDFComparisonService;
import guraa.pdfcompare.comparison.PDFComparisonResult;
import guraa.pdfcompare.comparison.PageComparisonResult;
import guraa.pdfcompare.model.ComparisonRequest;
import guraa.pdfcompare.model.FileUploadResponse;
import guraa.pdfcompare.service.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller for PDF comparison operations
 */
@RestController
@RequestMapping("/api/pdfs")
public class PDFController {

    private static final Logger logger = LoggerFactory.getLogger(PDFController.class);

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
            logger.info("File uploaded successfully: ID={}, Name={}, Size={}",
                    fileId, file.getOriginalFilename(), file.getSize());

            // Create response
            FileUploadResponse response = new FileUploadResponse();
            response.setFileId(fileId);
            response.setSuccess(true);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to upload file: {}", e.getMessage(), e);
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

            logger.info("Starting comparison between files: base={}, compare={}",
                    request.getBaseFileId(), request.getCompareFileId());

            // Compare files
            String comparisonId = comparisonService.compareFiles(baseFilePath, compareFilePath);
            logger.info("Comparison started with ID: {}", comparisonId);

            // Create response
            Map<String, String> response = new HashMap<>();
            response.put("comparisonId", comparisonId);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to compare documents: {}", e.getMessage(), e);
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
        logger.info("Received request for comparison result: {}", comparisonId);

        PDFComparisonResult result = comparisonService.getComparisonResult(comparisonId);

        if (result == null) {
            // Instead of returning 404, return 202 Accepted to indicate processing
            logger.info("Comparison result not found or still processing: {}", comparisonId);
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .header("X-Comparison-Status", "processing")
                    .build();
        }

        logger.info("Returning comparison result for ID: {}", comparisonId);
        return ResponseEntity.ok(result);
    }

    /**
     * Get detailed comparison for a specific page
     * @param comparisonId The comparison ID
     * @param pageNumber The page number
     * @param types Filter by difference types (comma separated)
     * @param severity Filter by minimum severity
     * @param search Search term
     * @return The page comparison details
     */
    @GetMapping("/comparison/{comparisonId}/page/{pageNumber}")
    public ResponseEntity<Map<String, Object>> getComparisonDetails(
            @PathVariable String comparisonId,
            @PathVariable int pageNumber,
            @RequestParam(required = false) String types,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String search) {

        // Convert filter parameters to a map
        Map<String, Object> filters = new HashMap<>();

        if (types != null) {
            filters.put("differenceTypes", Arrays.asList(types.split(",")));
            logger.info("Filtering by types: {}", types);
        }

        if (severity != null) {
            filters.put("minSeverity", severity);
            logger.info("Filtering by severity: {}", severity);
        }

        if (search != null) {
            filters.put("searchTerm", search);
            logger.info("Filtering by search term: {}", search);
        }

        logger.info("Received request for page {} of comparison {} with filters: {}",
                pageNumber, comparisonId, filters);

        PDFComparisonResult result = comparisonService.getComparisonResult(comparisonId);

        if (result == null) {
            // Instead of returning 404, return 202 Accepted to indicate processing
            logger.info("Comparison result not found or still processing: {}", comparisonId);
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .header("X-Comparison-Status", "processing")
                    .build();
        }

        // Find the page
        PageComparisonResult pageResult = result.getPageDifferences().stream()
                .filter(page -> page.getPageNumber() == pageNumber)
                .findFirst()
                .orElse(null);

        if (pageResult == null) {
            logger.warn("Page {} not found in comparison {}", pageNumber, comparisonId);
            return ResponseEntity.notFound().build();
        }

        // Create detailed response
        Map<String, Object> response = new HashMap<>();
        response.put("baseDifferences", pageResult.extractPageDifferences(true));
        response.put("compareDifferences", pageResult.extractPageDifferences(false));

        logger.info("Returning details for page {} of comparison {}", pageNumber, comparisonId);
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
            logger.info("Received request for page {} of document {}", page, fileId);

            // Get the rendered page
            Resource resource = storageService.getPageAsImage(fileId, page);

            // Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);

            logger.info("Returning image for page {} of document {}", page, fileId);
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(resource);
        } catch (Exception e) {
            logger.error("Error getting document page: {}", e.getMessage(), e);
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

            logger.info("Generating {} report for comparison {}", format, comparisonId);

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

            logger.info("Report generated successfully for comparison {}", comparisonId);
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(report);
        } catch (Exception e) {
            logger.error("Error generating report: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}