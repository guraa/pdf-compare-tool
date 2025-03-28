package guraa.pdfcompare.controller;

import guraa.pdfcompare.PDFComparisonService;
import guraa.pdfcompare.comparison.PDFComparisonResult;
import guraa.pdfcompare.core.SmartDocumentMatcher.DocumentPair;
import guraa.pdfcompare.model.ComparisonRequest;
import guraa.pdfcompare.model.FileUploadResponse;
import guraa.pdfcompare.service.FileStorageService;
import guraa.pdfcompare.service.SmartDocumentComparisonService;
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

import java.util.*;

/**
 * Controller for PDF comparison operations with smart document matching
 */
@RestController
@RequestMapping("/api/pdfs")
public class PDFController {

    private static final Logger logger = LoggerFactory.getLogger(PDFController.class);

    private final PDFComparisonService comparisonService;
    private final SmartDocumentComparisonService smartComparisonService;
    private final FileStorageService storageService;

    @Autowired
    public PDFController(
            PDFComparisonService comparisonService,
            SmartDocumentComparisonService smartComparisonService,
            FileStorageService storageService) {
        this.comparisonService = comparisonService;
        this.smartComparisonService = smartComparisonService;
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
            response.setFileName(file.getOriginalFilename());
            response.setFileSize(file.getSize());
            response.setSuccess(true);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to upload file: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new FileUploadResponse(null, "Failed to upload file: " + e.getMessage()));
        }
    }

    /**
     * Compare two PDF documents with optional smart matching
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

            // Check if smart matching is requested
            boolean useSmartMatching = request.getOptions() != null &&
                    Boolean.TRUE.equals(request.getOptions().get("smartMatching"));

            String comparisonId;
            if (useSmartMatching) {
                // Use smart comparison service
                comparisonId = smartComparisonService.compareFiles(
                        baseFilePath, compareFilePath, true);
                logger.info("Smart comparison started with ID: {}", comparisonId);
            } else {
                // Use standard comparison service
                comparisonId = comparisonService.compareFiles(baseFilePath, compareFilePath);
                logger.info("Standard comparison started with ID: {}", comparisonId);
            }

            // Create response
            Map<String, String> response = new HashMap<>();
            response.put("comparisonId", comparisonId);
            response.put("mode", useSmartMatching ? "smart" : "standard");

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
    public ResponseEntity<?> getComparisonResult(@PathVariable String comparisonId) {
        logger.info("Received request for comparison result: {}", comparisonId);

        // Try standard comparison first
        PDFComparisonResult standardResult = comparisonService.getComparisonResult(comparisonId);

        if (standardResult != null) {
            logger.info("Returning standard comparison result for ID: {}", comparisonId);
            return ResponseEntity.ok(standardResult);
        }

        // Check for smart comparison result
        if (smartComparisonService.isComparisonReady(comparisonId)) {
            logger.info("Returning smart comparison summary for ID: {}", comparisonId);
            Map<String, Object> summary = smartComparisonService.getComparisonSummary(comparisonId);
            return ResponseEntity.ok(summary);
        }

        // If neither is ready, return "processing" status
        logger.info("Comparison result not found or still processing: {}", comparisonId);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .header("X-Comparison-Status", "processing")
                .build();
    }

    /**
     * Get document pairs found by smart matching
     * @param comparisonId The comparison ID
     * @return List of document pairs
     */
    @GetMapping("/comparison/{comparisonId}/documents")
    public ResponseEntity<?> getDocumentPairs(@PathVariable String comparisonId) {
        List<DocumentPair> pairs = smartComparisonService.getDocumentPairs(comparisonId);

        if (pairs == null || pairs.isEmpty()) {
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .header("X-Comparison-Status", "processing")
                    .build();
        }

        // Create a response with more details
        List<Map<String, Object>> pairDetails = new ArrayList<>();
        for (int i = 0; i < pairs.size(); i++) {
            DocumentPair pair = pairs.get(i);
            Map<String, Object> details = new HashMap<>();

            details.put("pairIndex", i);
            details.put("matched", pair.isMatched());
            details.put("similarityScore", pair.getSimilarityScore());

            if (pair.hasBaseDocument()) {
                details.put("baseStartPage", pair.getBaseStartPage() + 1); // Convert to 1-based for API
                details.put("baseEndPage", pair.getBaseEndPage() + 1);
                details.put("basePageCount", pair.getBaseEndPage() - pair.getBaseStartPage() + 1);
            }

            if (pair.hasCompareDocument()) {
                details.put("compareStartPage", pair.getCompareStartPage() + 1); // Convert to 1-based for API
                details.put("compareEndPage", pair.getCompareEndPage() + 1);
                details.put("comparePageCount", pair.getCompareEndPage() - pair.getCompareStartPage() + 1);
            }

            pairDetails.add(details);
        }

        return ResponseEntity.ok(pairDetails);
    }

    /**
     * Get comparison result for a specific document pair
     * @param comparisonId The comparison ID
     * @param pairIndex The document pair index
     * @return The comparison result for the document pair
     */
    @GetMapping("/comparison/{comparisonId}/documents/{pairIndex}")
    public ResponseEntity<?> getDocumentPairResult(
            @PathVariable String comparisonId,
            @PathVariable int pairIndex) {

        PDFComparisonResult result = smartComparisonService.getDocumentPairResult(comparisonId, pairIndex);

        if (result == null) {
            // Check if the pair exists but comparison is not ready
            List<DocumentPair> pairs = smartComparisonService.getDocumentPairs(comparisonId);
            if (pairs != null && pairIndex < pairs.size()) {
                return ResponseEntity.status(HttpStatus.ACCEPTED)
                        .header("X-Comparison-Status", "processing")
                        .build();
            }

            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Get detailed comparison for a specific page
     * @param comparisonId The comparison ID
     * @param pairIndex The document pair index
     * @param pageNumber The page number
     * @return The page comparison details
     */
    @GetMapping("/comparison/{comparisonId}/documents/{pairIndex}/page/{pageNumber}")
    public ResponseEntity<Map<String, Object>> getDocumentPageDetails(
            @PathVariable String comparisonId,
            @PathVariable int pairIndex,
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

        logger.info("Received request for page {} of document pair {} in comparison {} with filters: {}",
                pageNumber, pairIndex, comparisonId, filters);

        // Get the document pair result
        PDFComparisonResult result = smartComparisonService.getDocumentPairResult(comparisonId, pairIndex);

        if (result == null) {
            // Check if the pair exists but comparison is not ready
            List<DocumentPair> pairs = smartComparisonService.getDocumentPairs(comparisonId);
            if (pairs != null && pairIndex < pairs.size()) {
                return ResponseEntity.status(HttpStatus.ACCEPTED)
                        .header("X-Comparison-Status", "processing")
                        .build();
            }

            logger.warn("Document pair {} not found in comparison {}", pairIndex, comparisonId);
            return ResponseEntity.notFound().build();
        }

        // Find the page
        if (result.getPageDifferences() == null || pageNumber < 1 ||
                pageNumber > result.getPageDifferences().size()) {
            logger.warn("Page {} not found in document pair {} of comparison {}",
                    pageNumber, pairIndex, comparisonId);
            return ResponseEntity.notFound().build();
        }

        // Get the page comparison result
        var pageResult = result.getPageDifferences().stream()
                .filter(page -> page.getPageNumber() == pageNumber)
                .findFirst()
                .orElse(null);

        if (pageResult == null) {
            logger.warn("Page {} details not found in document pair {} of comparison {}",
                    pageNumber, pairIndex, comparisonId);
            return ResponseEntity.notFound().build();
        }

        // Create detailed response
        Map<String, Object> response = new HashMap<>();
        response.put("baseDifferences", pageResult.extractPageDifferences(true));
        response.put("compareDifferences", pageResult.extractPageDifferences(false));

        logger.info("Returning details for page {} of document pair {} in comparison {}",
                pageNumber, pairIndex, comparisonId);
        return ResponseEntity.ok(response);
    }

    // Other existing controller methods remain the same...

    /**
     * Get document page as image
     * For multi-document PDFs, this method can handle page ranges
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
            Integer pairIndex = request.containsKey("documentPairIndex") ?
                    (Integer) request.get("documentPairIndex") : null;

            if (pairIndex != null) {
                logger.info("Generating {} report for document pair {} in comparison {}",
                        format, pairIndex, comparisonId);

                // TODO: Implement report generation for specific document pairs
                // This would need to be added to the service layer

                return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                        .body(null);
            } else {
                logger.info("Generating {} report for comparison {}", format, comparisonId);

                // Generate standard report
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
            }
        } catch (Exception e) {
            logger.error("Error generating report: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}