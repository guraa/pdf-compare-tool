package guraa.pdfcompare.controller;

import guraa.pdfcompare.model.Comparison;
import guraa.pdfcompare.model.ComparisonResult;
import guraa.pdfcompare.model.DocumentPair;
import guraa.pdfcompare.model.PdfDocument;
import guraa.pdfcompare.repository.ComparisonRepository;
import guraa.pdfcompare.repository.PdfRepository;
import guraa.pdfcompare.service.ComparisonResultStorage;
import guraa.pdfcompare.service.ComparisonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/pdfs/")
@Slf4j
@RequiredArgsConstructor
public class ComparisonController {
    private final ComparisonService comparisonService;
    private final ComparisonResultStorage resultStorage;
    private final ComparisonRepository comparisonRepository;
    private final PdfRepository pdfRepository;

    // Custom exception for resource not found
    private static class ResourceNotFoundException extends RuntimeException {
        public ResourceNotFoundException(String message) {
            super(message);
        }
    }

    @PostConstruct
    public void init() {
        log.info("ComparisonController initialized with base paths: /api/pdfs and /api/pdfs/comparison");
    }


    @PostMapping("/compare")
    public ResponseEntity<?> compareDocuments(@RequestBody CompareRequest request) {
        try {
            log.info("Received comparison request: baseFileId={}, compareFileId={}",
                    request.getBaseFileId(), request.getCompareFileId());

            // Validate input
            if (request.getBaseFileId() == null || request.getCompareFileId() == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Both baseFileId and compareFileId are required"));
            }

            // Create the comparison
            Comparison comparison = comparisonService.createComparison(
                    request.getBaseFileId(),
                    request.getCompareFileId()
            );

            // Prepare response
            Map<String, Object> response = new HashMap<>();
            response.put("comparisonId", comparison.getId());
            response.put("status", comparison.getStatus().name());
            response.put("message", "Comparison initiated successfully");

            log.info("Comparison initiated with ID: {}", comparison.getId());
            return ResponseEntity.accepted().body(response);
        } catch (Exception e) {
            log.error("Failed to initiate comparison: {}", e.getMessage(), e);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "Failed to compare PDFs",
                            "message", e.getMessage()
                    ));
        }
    }

    /**
     * Check if a comparison is ready using a HEAD request.
     *
     * @param comparisonId The comparison ID
     * @return Appropriate status code based on comparison state
     */
    @RequestMapping(value = "/comparison/{comparisonId}", method = RequestMethod.HEAD)
    public ResponseEntity<?> checkComparisonStatus(@PathVariable String comparisonId) {
        log.debug("HEAD request for comparison status: {}", comparisonId);

        try {
            // First check if result exists, which is the most reliable indicator
            if (resultStorage.resultExists(comparisonId)) {
                log.debug("Result exists for comparison {}", comparisonId);

                // Ensure database status is consistent
                Optional<Comparison> comparisonOpt = comparisonRepository.findById(comparisonId);
                if (comparisonOpt.isPresent() &&
                        comparisonOpt.get().getStatus() != Comparison.ComparisonStatus.COMPLETED) {

                    log.info("Fixing inconsistent status for comparison {}", comparisonId);
                    comparisonService.updateComparisonStatus(comparisonId,
                            Comparison.ComparisonStatus.COMPLETED, null);
                }

                return ResponseEntity.ok().build();
            }

            // Check database status
            Optional<Comparison> comparisonOpt = comparisonRepository.findById(comparisonId);
            if (comparisonOpt.isEmpty()) {
                log.debug("Comparison {} not found", comparisonId);
                return ResponseEntity.notFound().build();
            }

            Comparison comparison = comparisonOpt.get();
            Comparison.ComparisonStatus status = comparison.getStatus();

            // Return appropriate status code
            if (status == Comparison.ComparisonStatus.COMPLETED) {
                return ResponseEntity.ok().build();
            } else if (status == Comparison.ComparisonStatus.PROCESSING ||
                    status == Comparison.ComparisonStatus.PENDING) {
                log.debug("Comparison {} is in progress ({})", comparisonId, status);
                return ResponseEntity.accepted().build();
            } else if (status == Comparison.ComparisonStatus.FAILED) {
                log.debug("Comparison {} failed", comparisonId);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            } else {
                log.debug("Comparison {} is in an unexpected state: {}", comparisonId, status);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (Exception e) {
            log.error("Error checking comparison status for {}: {}", comparisonId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get the status of a comparison.
     *
     * @param comparisonId The comparison ID
     * @return The comparison status
     */
    @GetMapping("/comparison/{comparisonId}/status")
    public ResponseEntity<?> getComparisonStatus(@PathVariable String comparisonId) {
        log.debug("Getting status for comparison: {}", comparisonId);

        try {
            // Check if result exists first (most reliable indicator)
            boolean resultExists = resultStorage.resultExists(comparisonId);

            // Check if comparison exists in database
            Optional<Comparison> comparisonOpt = comparisonRepository.findById(comparisonId);
            if (comparisonOpt.isEmpty()) {
                log.warn("Comparison not found: {}", comparisonId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("status", "NOT_FOUND", "message", "Comparison not found"));
            }

            Comparison comparison = comparisonOpt.get();

            // If result exists but status isn't COMPLETED, fix it
            if (resultExists && comparison.getStatus() != Comparison.ComparisonStatus.COMPLETED) {
                log.info("Result exists but comparison {} status is {}. Fixing to COMPLETED.",
                        comparisonId, comparison.getStatus());
                comparisonService.updateComparisonStatus(comparisonId,
                        Comparison.ComparisonStatus.COMPLETED, null);
                comparison.setStatus(Comparison.ComparisonStatus.COMPLETED);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("status", comparison.getStatus().name());
            response.put("comparisonId", comparisonId);
            response.put("resultReady", resultExists || comparison.getStatus() == Comparison.ComparisonStatus.COMPLETED);

            if (comparison.getErrorMessage() != null) {
                response.put("errorMessage", comparison.getErrorMessage());
            }

            // Add timing information for PROCESSING status
            if (comparison.getStatus() == Comparison.ComparisonStatus.PROCESSING ||
                    comparison.getStatus() == Comparison.ComparisonStatus.PENDING) {

                LocalDateTime now = LocalDateTime.now();
                Duration duration = Duration.between(comparison.getCreatedAt(), now);
                response.put("elapsedTimeSeconds", duration.getSeconds());
                response.put("startedAt", comparison.getCreatedAt());

                // Flag as potentially stuck if running for too long
                boolean potentiallyStuck = duration.toMinutes() > 5; // 5 minutes
                response.put("potentiallyStuck", potentiallyStuck);
            }

            HttpStatus httpStatus;
            if (comparison.getStatus() == Comparison.ComparisonStatus.COMPLETED) {
                httpStatus = HttpStatus.OK;
            } else if (comparison.getStatus() == Comparison.ComparisonStatus.PROCESSING ||
                    comparison.getStatus() == Comparison.ComparisonStatus.PENDING) {
                httpStatus = HttpStatus.ACCEPTED;
            } else if (comparison.getStatus() == Comparison.ComparisonStatus.FAILED) {
                httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
            } else {
                httpStatus = HttpStatus.OK;
            }

            return ResponseEntity.status(httpStatus).body(response);
        } catch (Exception e) {
            log.error("Error getting comparison status for {}: {}", comparisonId, e.getMessage(), e);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "ERROR");
            response.put("message", "Failed to get comparison status: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }


    @GetMapping("/{comparisonId}/progress")
    public ResponseEntity<?> getComparisonProgress(@PathVariable String comparisonId) {
        try {
            Comparison comparison = comparisonRepository.findById(comparisonId)
                    .orElseThrow(() -> new ResourceNotFoundException("Comparison not found"));

            Map<String, Object> progressResponse = new HashMap<>();
            progressResponse.put("comparisonId", comparisonId);
            progressResponse.put("status", comparison.getStatus().name());
            progressResponse.put("progress", comparison.getProgress());
            progressResponse.put("completedOperations", comparison.getCompletedOperations());
            progressResponse.put("totalOperations", comparison.getTotalOperations());
            progressResponse.put("currentPhase", comparison.getCurrentPhase());

            return ResponseEntity.ok(progressResponse);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{comparisonId}/documents")
    public ResponseEntity<?> getDocumentPairs(@PathVariable String comparisonId) {
        try {
            Comparison comparison = comparisonRepository.findById(comparisonId)
                    .orElseThrow(() -> new ResourceNotFoundException("Comparison not found"));

            // Fetch result to get additional details
            ComparisonResult result = comparisonService.getComparisonResult(comparisonId);

            if (result == null) {
                return ResponseEntity.status(HttpStatus.ACCEPTED)
                        .body(createProcessingResponse(comparison));
            }

            // Create document pairs
            List<DocumentPair> documentPairs = createDocumentPairs(comparison, result);

            return ResponseEntity.ok(documentPairs);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error retrieving document pairs", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to retrieve document pairs"));
        }
    }

    @GetMapping("/{comparisonId}")
    public ResponseEntity<?> getComparisonResult(@PathVariable String comparisonId) {
        try {
            Comparison comparison = comparisonRepository.findById(comparisonId)
                    .orElseThrow(() -> new ResourceNotFoundException("Comparison not found"));

            // Check comparison status
            if (comparison.getStatus() == Comparison.ComparisonStatus.PROCESSING) {
                return ResponseEntity.accepted()
                        .body(createProcessingResponse(comparison));
            }

            ComparisonResult result = comparisonService.getComparisonResult(comparisonId);

            if (result == null) {
                return ResponseEntity.notFound().build();
            }

            // Enhance result with additional metadata
            Map<String, Object> enhancedResult = new HashMap<>();
            enhancedResult.put("id", result.getId());
            enhancedResult.put("baseDocumentId", result.getBaseDocumentId());
            enhancedResult.put("compareDocumentId", result.getCompareDocumentId());

            // Total differences
            enhancedResult.put("totalDifferences", result.getTotalDifferences());

            // Page pair information
            enhancedResult.put("pagePairs", result.getPagePairs());

            // Differences by page
            enhancedResult.put("differencesByPage", result.getDifferencesByPage());

            // Summary information
            enhancedResult.put("summary", result.getSummary());

            // Overall similarity score
            enhancedResult.put("overallSimilarityScore",
                    result.getSummary() != null ? result.getSummary().getOverallSimilarityScore() : null);

            return ResponseEntity.ok(enhancedResult);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error retrieving comparison result", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to retrieve comparison result"));
        }
    }
    private Map<String, Object> createProcessingResponse(Comparison comparison) {
        Map<String, Object> processingResponse = new HashMap<>();
        processingResponse.put("status", "PROCESSING");
        processingResponse.put("progress", comparison.getProgress());
        processingResponse.put("currentPhase", comparison.getCurrentPhase());
        processingResponse.put("completedOperations", comparison.getCompletedOperations());
        processingResponse.put("totalOperations", comparison.getTotalOperations());
        return processingResponse;
    }

    private List<DocumentPair> createDocumentPairs(Comparison comparison, ComparisonResult result) {
        PdfDocument baseDocument = pdfRepository.findById(comparison.getBaseDocumentId())
                .orElseThrow(() -> new ResourceNotFoundException("Base document not found"));

        PdfDocument compareDocument = pdfRepository.findById(comparison.getCompareDocumentId())
                .orElseThrow(() -> new ResourceNotFoundException("Compare document not found"));

        DocumentPair pair = DocumentPair.builder()
                .pairIndex(0)
                .matched(true)
                .baseStartPage(1)
                .baseEndPage(baseDocument.getPageCount())
                .basePageCount(baseDocument.getPageCount())
                .compareStartPage(1)
                .compareEndPage(compareDocument.getPageCount())
                .comparePageCount(compareDocument.getPageCount())
                .hasBaseDocument(true)
                .hasCompareDocument(true)
                .baseDocumentId(baseDocument.getFileId())
                .compareDocumentId(compareDocument.getFileId())
                .similarityScore(result.getOverallSimilarityScore())
                .totalDifferences(result.getTotalDifferences())
                .build();

        return Collections.singletonList(pair);
    }

}