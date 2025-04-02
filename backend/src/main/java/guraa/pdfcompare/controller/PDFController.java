package guraa.pdfcompare.controller;

import guraa.pdfcompare.PDFComparisonService;
import guraa.pdfcompare.comparison.PDFComparisonResult;
import guraa.pdfcompare.core.SmartDocumentMatcher;
import guraa.pdfcompare.model.ComparisonRequest;
import guraa.pdfcompare.model.FileUploadResponse;
import guraa.pdfcompare.service.FileStorageService;
import guraa.pdfcompare.service.PageLevelComparisonIntegrationService;
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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Controller for PDF comparison operations with smart document matching
 */
@RestController
@RequestMapping("/api/pdfs")
public class PDFController {

    private static final Logger logger = LoggerFactory.getLogger(PDFController.class);
    private static final long MAX_WAIT_TIME_MS = 300000; // 5 minutes max wait time for comparison

    private final PDFComparisonService comparisonService;
    private final SmartDocumentComparisonService smartComparisonService;
    private final FileStorageService storageService;
    private final PageLevelComparisonIntegrationService pageLevelComparisonService;

    // Track when comparison requests were started
    private final Map<String, Long> comparisonStartTimes = new ConcurrentHashMap<>();

    @Autowired
    public PDFController(
            PDFComparisonService comparisonService,
            SmartDocumentComparisonService smartComparisonService,
            FileStorageService storageService,
            PageLevelComparisonIntegrationService pageLevelComparisonService) {
        this.comparisonService = comparisonService;
        this.smartComparisonService = smartComparisonService;
        this.storageService = storageService;
        this.pageLevelComparisonService = pageLevelComparisonService;
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
            try {
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

                // Track start time
                comparisonStartTimes.put(comparisonId, System.currentTimeMillis());

                // Create response
                Map<String, String> response = new HashMap<>();
                response.put("comparisonId", comparisonId);
                response.put("mode", useSmartMatching ? "smart" : "standard");

                return ResponseEntity.ok(response);
            } catch (Exception e) {
                logger.error("Error starting comparison: {}", e.getMessage(), e);
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "Error starting comparison: " + e.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
            }
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

        // Special handling for old comparison IDs from previous runs
        if (!comparisonStartTimes.containsKey(comparisonId)) {
            // If this is an old ID from a previous server run, and neither service has results
            if (comparisonService.getComparisonResult(comparisonId) == null &&
                    !smartComparisonService.isComparisonReady(comparisonId)) {

                Map<String, Object> oldIdResponse = new HashMap<>();
                oldIdResponse.put("status", "not_found");
                oldIdResponse.put("message", "This comparison ID is no longer valid. Please start a new comparison.");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(oldIdResponse);
            }
        }

        // Check if comparison has timed out
        Long startTime = comparisonStartTimes.get(comparisonId);
        if (startTime != null && System.currentTimeMillis() - startTime > MAX_WAIT_TIME_MS) {
            logger.warn("Comparison {} has exceeded maximum wait time of {} ms", comparisonId, MAX_WAIT_TIME_MS);
            comparisonStartTimes.remove(comparisonId);
            Map<String, Object> timeoutResponse = new HashMap<>();
            timeoutResponse.put("status", "timeout");
            timeoutResponse.put("message", "Comparison is taking too long to complete. Please try again with smaller files.");
            return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(timeoutResponse);
        }

        // Try standard comparison first
        PDFComparisonResult standardResult = comparisonService.getComparisonResult(comparisonId);

        if (standardResult != null) {
            logger.info("Returning standard comparison result for ID: {}", comparisonId);
            comparisonStartTimes.remove(comparisonId); // Clean up tracking
            return ResponseEntity.ok(standardResult);
        }

        // Check for smart comparison result
        if (smartComparisonService.isComparisonReady(comparisonId)) {
            logger.info("Returning smart comparison summary for ID: {}", comparisonId);
            Map<String, Object> summary = smartComparisonService.getComparisonSummary(comparisonId);

            // Check if comparison failed
            if ("failed".equals(summary.get("status"))) {
                logger.error("Comparison {} failed: {}", comparisonId, summary.get("error"));
                comparisonStartTimes.remove(comparisonId); // Clean up tracking
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(summary);
            }

            comparisonStartTimes.remove(comparisonId); // Clean up tracking
            return ResponseEntity.ok(summary);
        }

        // Get a progress update for better user feedback
        Map<String, Object> summary = smartComparisonService.getComparisonSummary(comparisonId);

        // If we have progress information, return it with ACCEPTED status
        if (summary.containsKey("percentComplete")) {
            logger.info("Comparison {} is in progress ({}% complete)",
                    comparisonId, summary.get("percentComplete"));
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .header("X-Comparison-Status", "processing")
                    .header("X-Comparison-Progress", summary.get("percentComplete").toString())
                    .body(summary);
        }

        // Otherwise, just indicate processing
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
        // Check for old comparison IDs
        if (!comparisonStartTimes.containsKey(comparisonId) &&
                !smartComparisonService.isComparisonReady(comparisonId)) {

            Map<String, Object> oldIdResponse = new HashMap<>();
            oldIdResponse.put("status", "not_found");
            oldIdResponse.put("message", "This comparison ID is no longer valid. Please start a new comparison.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(oldIdResponse);
        }

        List<SmartDocumentMatcher.DocumentPair> pairs = smartComparisonService.getDocumentPairs(comparisonId);

        if (pairs == null || pairs.isEmpty()) {
            // Check if comparison has timed out
            Long startTime = comparisonStartTimes.get(comparisonId);
            if (startTime != null && System.currentTimeMillis() - startTime > MAX_WAIT_TIME_MS) {
                logger.warn("Document pairs request for comparison {} has timed out", comparisonId);
                comparisonStartTimes.remove(comparisonId);
                Map<String, Object> timeoutResponse = new HashMap<>();
                timeoutResponse.put("status", "timeout");
                timeoutResponse.put("message", "Document pair identification is taking too long. Please try again with smaller files.");
                return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(timeoutResponse);
            }

            // Get progress information if available
            Map<String, Object> summary = smartComparisonService.getComparisonSummary(comparisonId);
            if (summary.containsKey("status") && "failed".equals(summary.get("status"))) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(summary);
            }

            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .header("X-Comparison-Status", "processing")
                    .build();
        }

        // OVERRIDE: Force documents to be treated as completely different
        // This is a temporary fix to prevent incorrect matching of documents
        List<SmartDocumentMatcher.DocumentPair> newPairs = new ArrayList<>();
        
        // Find the base document pages
        SmartDocumentMatcher.DocumentPair baseDocPair = null;
        for (SmartDocumentMatcher.DocumentPair pair : pairs) {
            if (pair.hasBaseDocument() && pair.hasCompareDocument()) {
                // Create two separate pairs instead of a matched pair
                newPairs.add(new SmartDocumentMatcher.DocumentPair(
                    pair.getBaseStartPage(),
                    pair.getBaseEndPage(),
                    -1, // No compare document
                    -1,
                    0.0 // Zero similarity
                ));
                
                newPairs.add(new SmartDocumentMatcher.DocumentPair(
                    -1, // No base document
                    -1,
                    pair.getCompareStartPage(),
                    pair.getCompareEndPage(),
                    0.0 // Zero similarity
                ));
            } else {
                // Keep unmatched pairs as they are
                newPairs.add(pair);
            }
        }
        
        // Replace the original pairs with our modified pairs
        pairs = newPairs;

        // Create a response with more details
        List<Map<String, Object>> pairDetails = new ArrayList<>();
        for (int i = 0; i < pairs.size(); i++) {
            SmartDocumentMatcher.DocumentPair pair = pairs.get(i);
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

        // Check for old comparison IDs
        if (!comparisonStartTimes.containsKey(comparisonId) &&
                !smartComparisonService.isComparisonReady(comparisonId)) {

            Map<String, Object> oldIdResponse = new HashMap<>();
            oldIdResponse.put("status", "not_found");
            oldIdResponse.put("message", "This comparison ID is no longer valid. Please start a new comparison.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(oldIdResponse);
        }

        PDFComparisonResult result = smartComparisonService.getDocumentPairResult(comparisonId, pairIndex);

        if (result == null) {
            // Check if the pair exists but comparison is not ready
            List<SmartDocumentMatcher.DocumentPair> pairs = smartComparisonService.getDocumentPairs(comparisonId);
            if (pairs != null && pairIndex < pairs.size()) {
                // Check for timeout
                Long startTime = comparisonStartTimes.get(comparisonId);
                if (startTime != null && System.currentTimeMillis() - startTime > MAX_WAIT_TIME_MS) {
                    logger.warn("Document pair result request for comparison {} has timed out", comparisonId);
                    comparisonStartTimes.remove(comparisonId);
                    Map<String, Object> timeoutResponse = new HashMap<>();
                    timeoutResponse.put("status", "timeout");
                    timeoutResponse.put("message", "Document pair comparison is taking too long. Please try again with smaller files.");
                    return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(timeoutResponse);
                }

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

        // Check for old comparison IDs
        if (!comparisonStartTimes.containsKey(comparisonId) &&
                !smartComparisonService.isComparisonReady(comparisonId)) {

            Map<String, Object> oldIdResponse = new HashMap<>();
            oldIdResponse.put("status", "not_found");
            oldIdResponse.put("message", "This comparison ID is no longer valid. Please start a new comparison.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(oldIdResponse);
        }

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
            List<SmartDocumentMatcher.DocumentPair> pairs = smartComparisonService.getDocumentPairs(comparisonId);
            if (pairs != null && pairIndex < pairs.size()) {
                // Check for timeout
                Long startTime = comparisonStartTimes.get(comparisonId);
                if (startTime != null && System.currentTimeMillis() - startTime > MAX_WAIT_TIME_MS) {
                    logger.warn("Page details request for comparison {} has timed out", comparisonId);
                    Map<String, Object> timeoutResponse = new HashMap<>();
                    timeoutResponse.put("status", "timeout");
                    timeoutResponse.put("message", "Page comparison is taking too long. Please try again with smaller files.");
                    return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(timeoutResponse);
                }

                return ResponseEntity.status(HttpStatus.ACCEPTED)
                        .header("X-Comparison-Status", "processing")
                        .body(Map.of("status", "processing", "message", "Page comparison is still processing"));
            }

            logger.warn("Document pair {} not found in comparison {}", pairIndex, comparisonId);
            return ResponseEntity.notFound().build();
        }

        // Find the page
        if (result.getPageDifferences() == null || pageNumber < 1 ||
                pageNumber > result.getPageDifferences().size()) {
            logger.warn("Page {} not found in document pair {} of comparison {}",
                    pageNumber, pairIndex, comparisonId);
            
            // Return a 200 response with a message instead of 404
            Map<String, Object> response = new HashMap<>();
            response.put("baseDifferences", new ArrayList<>());
            response.put("compareDifferences", new ArrayList<>());
            response.put("message", "Page not found in document pair");
            return ResponseEntity.ok(response);
        }

        // Get the page comparison result
        var pageResult = result.getPageDifferences().stream()
                .filter(page -> page.getPageNumber() == pageNumber)
                .findFirst()
                .orElse(null);

        if (pageResult == null) {
            logger.warn("Page {} details not found in document pair {} of comparison {}",
                    pageNumber, pairIndex, comparisonId);
            
            // Return a 200 response with a message instead of 404
            Map<String, Object> response = new HashMap<>();
            response.put("baseDifferences", new ArrayList<>());
            response.put("compareDifferences", new ArrayList<>());
            response.put("message", "Page not found in document pair");
            return ResponseEntity.ok(response);
        }

        // Extract differences
        var baseDifferences = pageResult.extractPageDifferences(true);
        var compareDifferences = pageResult.extractPageDifferences(false);
        
        // Create detailed response
        Map<String, Object> response = new HashMap<>();
        response.put("baseDifferences", baseDifferences);
        response.put("compareDifferences", compareDifferences);
        
        // Add a specific message if the page exists but has no differences
        if ((baseDifferences == null || baseDifferences.isEmpty()) && 
            (compareDifferences == null || compareDifferences.isEmpty())) {
            response.put("message", "Page exists but has no differences");
            logger.info("Page {} exists in document pair {} of comparison {} but has no differences",
                    pageNumber, pairIndex, comparisonId);
        }

        logger.info("Returning details for page {} of document pair {} in comparison {}",
                pageNumber, pairIndex, comparisonId);
        return ResponseEntity.ok(response);
    }

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
            // Log the request
            logger.info("Received report generation request for comparison ID: {}", comparisonId);

            // Check if the standard comparison service has the result
            PDFComparisonResult standardResult = comparisonService.getComparisonResult(comparisonId);

            if (standardResult == null) {
                // If the standard service doesn't have it, check if the smart service is ready
                if (smartComparisonService != null && smartComparisonService.isComparisonReady(comparisonId)) {
                    // Create a simplified result based on the smart service summary
                    Map<String, Object> summary = smartComparisonService.getComparisonSummary(comparisonId);

                    PDFComparisonResult simplifiedResult = new PDFComparisonResult();
                    simplifiedResult.setMatchingId(comparisonId);

                    // Set base info from summary
                    simplifiedResult.setBasePageCount((int)summary.getOrDefault("basePageCount", 0));
                    simplifiedResult.setComparePageCount((int)summary.getOrDefault("comparePageCount", 0));
                    simplifiedResult.setPageCountDifferent(simplifiedResult.getBasePageCount() != simplifiedResult.getComparePageCount());

                    // Set difference counts
                    simplifiedResult.setTotalDifferences((int)summary.getOrDefault("totalDifferences", 0));
                    simplifiedResult.setTotalTextDifferences((int)summary.getOrDefault("totalTextDifferences", 0));
                    simplifiedResult.setTotalImageDifferences((int)summary.getOrDefault("totalImageDifferences", 0));
                    simplifiedResult.setTotalFontDifferences((int)summary.getOrDefault("totalFontDifferences", 0));
                    simplifiedResult.setTotalStyleDifferences((int)summary.getOrDefault("totalStyleDifferences", 0));

                    // Set empty pages list to avoid null pointer exceptions
                    simplifiedResult.setPageDifferences(new ArrayList<>());

                    // Store in standard service
                    comparisonService.storeTemporaryResult(comparisonId, simplifiedResult);
                    logger.info("Created and stored simplified result for report generation");

                    // Get it from the standard service
                    standardResult = comparisonService.getComparisonResult(comparisonId);
                }

                // If still null, check page-level service
                if (standardResult == null && pageLevelComparisonService != null &&
                        pageLevelComparisonService.isComparisonReady(comparisonId)) {
                    // Create a simplified result based on the page-level service summary
                    Map<String, Object> summary = pageLevelComparisonService.getComparisonSummary(comparisonId);

                    PDFComparisonResult simplifiedResult = new PDFComparisonResult();
                    simplifiedResult.setMatchingId(comparisonId);

                    // Set basic info
                    simplifiedResult.setBasePageCount((int)summary.getOrDefault("totalBasePages", 0));
                    simplifiedResult.setComparePageCount((int)summary.getOrDefault("totalComparePages", 0));
                    simplifiedResult.setPageCountDifferent(simplifiedResult.getBasePageCount() != simplifiedResult.getComparePageCount());

                    // Set difference counts
                    simplifiedResult.setTotalDifferences((int)summary.getOrDefault("totalDifferences", 0));
                    simplifiedResult.setTotalTextDifferences((int)summary.getOrDefault("totalTextDifferences", 0));
                    simplifiedResult.setTotalImageDifferences((int)summary.getOrDefault("totalImageDifferences", 0));
                    simplifiedResult.setTotalFontDifferences((int)summary.getOrDefault("totalFontDifferences", 0));
                    simplifiedResult.setTotalStyleDifferences((int)summary.getOrDefault("totalStyleDifferences", 0));

                    // Set empty pages list to avoid null pointer exceptions
                    simplifiedResult.setPageDifferences(new ArrayList<>());

                    // Store in standard service
                    comparisonService.storeTemporaryResult(comparisonId, simplifiedResult);
                    logger.info("Created and stored simplified result from page-level service for report generation");

                    // Get it from the standard service
                    standardResult = comparisonService.getComparisonResult(comparisonId);
                }
            }

            // If we still don't have a result, return not found
            if (standardResult == null) {
                logger.warn("No comparison result found in any service for ID: {}", comparisonId);
                return ResponseEntity.notFound().build();
            }

            // Generate the report
            String format = (String) request.getOrDefault("format", "pdf");
            logger.info("Generating {} report for comparison {}", format, comparisonId);

            Resource report = comparisonService.generateReport(comparisonId, format);

            // Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentDispositionFormData("attachment", "comparison-report." + format);

            // Set content type based on format
            MediaType mediaType;
            switch (format.toLowerCase()) {
                case "html":
                    mediaType = MediaType.TEXT_HTML;
                    headers.set(HttpHeaders.CONTENT_TYPE, "text/html; charset=UTF-8");
                    break;
                case "json":
                    mediaType = MediaType.APPLICATION_JSON;
                    headers.set(HttpHeaders.CONTENT_TYPE, "application/json; charset=UTF-8");
                    break;
                default: // pdf
                    mediaType = MediaType.APPLICATION_PDF;
                    headers.set(HttpHeaders.CONTENT_TYPE, "application/pdf");
            }

            headers.setContentType(mediaType);

            if (report != null) {
                headers.setContentLength(report.contentLength());
                logger.info("Report generated successfully with size: {} bytes", report.contentLength());
            } else {
                logger.error("Generated report is null");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(report);
        } catch (Exception e) {
            logger.error("Error generating report: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
