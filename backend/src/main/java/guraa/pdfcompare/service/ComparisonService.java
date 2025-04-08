package guraa.pdfcompare.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import guraa.pdfcompare.model.*;
import guraa.pdfcompare.model.difference.MetadataDifference;
import guraa.pdfcompare.repository.ComparisonRepository;
import guraa.pdfcompare.repository.PdfRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service for handling PDF comparisons with improved memory management
 * and parallel processing capabilities.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComparisonService {
    // Define logger explicitly as fallback if Lombok annotation doesn't work
    private static final Logger logger = LoggerFactory.getLogger(ComparisonService.class);

    private final ComparisonRepository comparisonRepository;
    private final PdfRepository pdfRepository;
    private final DocumentMatchingService documentMatchingService;
    private final DifferenceDetectionService differenceDetectionService;
    private final ObjectMapper objectMapper;
    private final ComparisonServiceCache cacheService;
    private final ComparisonServiceMemoryManager memoryManager;
    private final ComparisonServicePageProcessor pageProcessor;

    // Thread pool for parallel processing
    private final ExecutorService processingExecutor = Executors.newWorkStealingPool();

    /**
     * Process a comparison with improved memory management and parallel processing.
     *
     * @param comparisonId The comparison ID
     * @throws Exception If there's an error during processing
     */
    @Async
    @Transactional
    public void processComparison(String comparisonId) throws Exception {
        Comparison comparison = comparisonRepository.findByComparisonId(comparisonId)
                .orElseThrow(() -> new IllegalArgumentException("Comparison not found: " + comparisonId));

        PdfDocument baseDocument = comparison.getBaseDocument();
        PdfDocument compareDocument = comparison.getCompareDocument();

        try {
            // Update status to processing
            comparison.setStatus(Comparison.ComparisonStatus.PROCESSING);
            comparisonRepository.save(comparison);

            // Create directory for comparison results
            Path comparisonDir = Paths.get("uploads", "comparisons", comparisonId);
            Files.createDirectories(comparisonDir);

            // Initialize comparison result
            ComparisonResult result = createInitialComparisonResult(comparison);

            // Create smart comparison if requested
            if (comparison.isSmartMatching()) {
                comparison.setStatus(Comparison.ComparisonStatus.DOCUMENT_MATCHING);
                comparisonRepository.save(comparison);

                result.setMode("smart");

                // Perform document matching
                List<DocumentPair> documentPairs = documentMatchingService.matchDocuments(
                        baseDocument, compareDocument);

                result.setDocumentPairs(documentPairs);
                cacheService.cacheDocumentPairs(comparisonId, documentPairs);

                comparison.setStatus(Comparison.ComparisonStatus.COMPARING);
                comparisonRepository.save(comparison);

                // Process document pairs in parallel with optimal batch size
                processDocumentPairsInParallel(comparison, baseDocument, compareDocument, documentPairs);

                // After processing all pairs, update the main result
                updateResultFromDocumentPairs(result, documentPairs);
            } else {
                // Standard comparison mode
                result.setMode("standard");

                comparison.setStatus(Comparison.ComparisonStatus.COMPARING);
                comparisonRepository.save(comparison);

                // Compare metadata
                compareMetadata(result, baseDocument, compareDocument);

                // Compare all pages
                List<ComparisonResult.PageDifference> pageDifferences =
                        differenceDetectionService.compareAllPages(
                                comparison,
                                baseDocument,
                                compareDocument);

                result.setPageDifferences(pageDifferences);

                // Calculate total differences
                calculateTotalDifferencesForStandardMode(result, pageDifferences);
            }

            // Save the comparison result
            saveComparisonResult(comparisonId, result);

            // Update comparison record
            comparison.setStatus(Comparison.ComparisonStatus.COMPLETED);
            comparison.setCompletionTime(LocalDateTime.now());
            comparison.setResultFilePath(comparisonDir.resolve("result.json").toString());
            comparisonRepository.save(comparison);

            // Update cache
            cacheService.cacheComparisonResult(comparisonId, result);

            // Suggest garbage collection after completion
            memoryManager.suggestGarbageCollection();
        } catch (Exception e) {
            logger.error("Error processing comparison: {}", comparisonId, e);

            // Update comparison status to failed
            comparison.setStatus(Comparison.ComparisonStatus.FAILED);
            comparison.setStatusMessage("Comparison failed: " + e.getMessage());
            comparison.setCompletionTime(LocalDateTime.now());
            comparisonRepository.save(comparison);

            throw e;
        }
    }

    /**
     * Process document pairs in parallel with optimal batch size and memory management.
     */
    private void processDocumentPairsInParallel(Comparison comparison, PdfDocument baseDocument,
                                                PdfDocument compareDocument, List<DocumentPair> documentPairs) throws Exception {
        // Determine optimal batch size based on document size and available memory
        int batchSize = memoryManager.calculateOptimalBatchSize(baseDocument, compareDocument);
        logger.info("Processing document pairs in batches of {}", batchSize);

        List<List<DocumentPair>> batches = memoryManager.partitionList(documentPairs, batchSize);

        for (List<DocumentPair> batch : batches) {
            // Process each batch in parallel
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (DocumentPair pair : batch) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        processDocumentPair(comparison, baseDocument, compareDocument, pair);
                    } catch (Exception e) {
                        logger.error("Error processing document pair {}: {}", pair.getPairIndex(), e.getMessage(), e);
                    }
                }, processingExecutor);

                futures.add(future);
            }

            // Wait for all pairs in the batch to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // Suggest garbage collection between batches
            memoryManager.suggestGarbageCollection();
        }
    }

    /**
     * Process a single document pair.
     */
    private void processDocumentPair(Comparison comparison, PdfDocument baseDocument,
                                     PdfDocument compareDocument, DocumentPair pair) {
        int pairIndex = pair.getPairIndex();
        try {
            logger.info("Processing document pair {}: Base pages {}-{}, Compare pages {}-{}",
                    pairIndex, pair.getBaseStartPage(), pair.getBaseEndPage(),
                    pair.getCompareStartPage(), pair.getCompareEndPage());

            // Process the pair if it's matched
            if (pair.isMatched()) {
                // Get page range for base document
                int baseStartPage = pair.getBaseStartPage();
                int baseEndPage = pair.getBaseEndPage();

                // Get page range for compare document
                int compareStartPage = pair.getCompareStartPage();
                int compareEndPage = pair.getCompareEndPage();

                // Compare pages in the document pair with optimized memory usage
                differenceDetectionService.comparePages(
                        comparison,
                        baseDocument,
                        compareDocument,
                        baseStartPage,
                        baseEndPage,
                        compareStartPage,
                        compareEndPage,
                        pairIndex);

                // Calculate and update difference counts for this pair
                int totalDifferences = pageProcessor.calculateTotalDifferences(comparison.getComparisonId(), pairIndex, objectMapper);
                memoryManager.updatePairDifferenceCounts(pair, totalDifferences);

                // Create a separate result file for this document pair
                ComparisonResult pairResult = createDocumentPairResult(
                        comparison, pair, pairIndex);

                // Save the pair result
                savePairResult(comparison.getComparisonId(), pairIndex, pairResult);

                logger.info("Successfully processed document pair {}", pairIndex);
            } else {
                logger.info("Skipping unmatched document pair {}", pairIndex);
            }
        } catch (Exception e) {
            logger.error("Error processing document pair {}: {}", pairIndex, e.getMessage(), e);
        }
    }

    /**
     * Create initial comparison result object.
     */
    private ComparisonResult createInitialComparisonResult(Comparison comparison) {
        PdfDocument baseDocument = comparison.getBaseDocument();
        PdfDocument compareDocument = comparison.getCompareDocument();

        return ComparisonResult.builder()
                .id(comparison.getComparisonId())
                .baseFileId(baseDocument.getFileId())
                .baseFileName(baseDocument.getFileName())
                .basePageCount(baseDocument.getPageCount())
                .compareFileId(compareDocument.getFileId())
                .compareFileName(compareDocument.getFileName())
                .comparePageCount(compareDocument.getPageCount())
                .pageCountDifferent(baseDocument.getPageCount() != compareDocument.getPageCount())
                .createdAt(comparison.getStartTime())
                .build();
    }

    /**
     * Compare metadata between two documents.
     */
    private void compareMetadata(ComparisonResult result, PdfDocument baseDocument, PdfDocument compareDocument) {
        Map<String, String> baseMetadata = baseDocument.getMetadata();
        Map<String, String> compareMetadata = compareDocument.getMetadata();

        // Combine all keys
        List<String> allKeys = new ArrayList<>();
        allKeys.addAll(baseMetadata.keySet());
        for (String key : compareMetadata.keySet()) {
            if (!allKeys.contains(key)) {
                allKeys.add(key);
            }
        }

        // Compare each metadata key
        for (String key : allKeys) {
            String baseValue = baseMetadata.get(key);
            String compareValue = compareMetadata.get(key);

            boolean onlyInBase = baseValue != null && compareValue == null;
            boolean onlyInCompare = baseValue == null && compareValue != null;
            boolean valueDifferent = baseValue != null && compareValue != null && !baseValue.equals(compareValue);

            if (onlyInBase || onlyInCompare || valueDifferent) {
                // Create metadata difference
                MetadataDifference diff = MetadataDifference.builder()
                        .id(UUID.randomUUID().toString())
                        .type("metadata")
                        .key(key)
                        .baseValue(baseValue)
                        .compareValue(compareValue)
                        .onlyInBase(onlyInBase)
                        .onlyInCompare(onlyInCompare)
                        .valueDifferent(valueDifferent)
                        .build();

                if (onlyInBase) {
                    diff.setChangeType("deleted");
                    diff.setSeverity("minor");
                    diff.setDescription("Metadata key '" + key + "' only exists in base document");
                } else if (onlyInCompare) {
                    diff.setChangeType("added");
                    diff.setSeverity("minor");
                    diff.setDescription("Metadata key '" + key + "' only exists in comparison document");
                } else {
                    diff.setChangeType("modified");
                    diff.setSeverity("minor");
                    diff.setDescription("Metadata key '" + key + "' has different values");
                }

                // Add to the result
                result.getMetadataDifferences().put(key, diff);
            }
        }
    }

    /**
     * Calculate total differences for standard comparison mode.
     */
    private void calculateTotalDifferencesForStandardMode(ComparisonResult result,
                                                          List<ComparisonResult.PageDifference> pageDifferences) {
        int totalText = 0;
        int totalImage = 0;
        int totalFont = 0;
        int totalStyle = 0;

        for (ComparisonResult.PageDifference page : pageDifferences) {
            if (page.getTextDifferences() != null && page.getTextDifferences().getDifferences() != null) {
                totalText += page.getTextDifferences().getDifferences().size();
            }

            if (page.getTextElementDifferences() != null) {
                totalStyle += page.getTextElementDifferences().size();
            }

            if (page.getImageDifferences() != null) {
                totalImage += page.getImageDifferences().size();
            }

            if (page.getFontDifferences() != null) {
                totalFont += page.getFontDifferences().size();
            }
        }

        result.setTotalTextDifferences(totalText);
        result.setTotalImageDifferences(totalImage);
        result.setTotalFontDifferences(totalFont);
        result.setTotalStyleDifferences(totalStyle);
        result.setTotalDifferences(totalText + totalImage + totalFont + totalStyle +
                result.getMetadataDifferences().size());
    }

    /**
     * Update the main comparison result with information from document pairs.
     */
    private void updateResultFromDocumentPairs(ComparisonResult result, List<DocumentPair> documentPairs) {
        int totalDifferences = 0;
        int totalTextDifferences = 0;
        int totalImageDifferences = 0;
        int totalFontDifferences = 0;
        int totalStyleDifferences = 0;

        for (DocumentPair pair : documentPairs) {
            totalDifferences += pair.getTotalDifferences();
            totalTextDifferences += pair.getTextDifferences();
            totalImageDifferences += pair.getImageDifferences();
            totalFontDifferences += pair.getFontDifferences();
            totalStyleDifferences += pair.getStyleDifferences();
        }

        result.setTotalDifferences(totalDifferences);
        result.setTotalTextDifferences(totalTextDifferences);
        result.setTotalImageDifferences(totalImageDifferences);
        result.setTotalFontDifferences(totalFontDifferences);
        result.setTotalStyleDifferences(totalStyleDifferences);
    }

    /**
     * Create a comparison result for a specific document pair.
     */
    private ComparisonResult createDocumentPairResult(Comparison comparison, DocumentPair pair, int pairIndex) {
        PdfDocument baseDocument = comparison.getBaseDocument();
        PdfDocument compareDocument = comparison.getCompareDocument();

        // Create result for just this document pair
        return ComparisonResult.builder()
                .id(comparison.getComparisonId())
                .mode("standard") // Within a pair, we use standard comparison
                .baseFileId(baseDocument.getFileId())
                .baseFileName(baseDocument.getFileName())
                .basePageCount(pair.getBasePageCount())
                .compareFileId(compareDocument.getFileId())
                .compareFileName(compareDocument.getFileName())
                .comparePageCount(pair.getComparePageCount())
                .pageCountDifferent(pair.getBasePageCount() != pair.getComparePageCount())
                .totalDifferences(pair.getTotalDifferences())
                .totalTextDifferences(pair.getTextDifferences())
                .totalImageDifferences(pair.getImageDifferences())
                .totalFontDifferences(pair.getFontDifferences())
                .totalStyleDifferences(pair.getStyleDifferences())
                .createdAt(comparison.getStartTime())
                .completedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Save the comparison result to a file.
     */
    private void saveComparisonResult(String comparisonId, ComparisonResult result) throws IOException {
        Path resultPath = Paths.get("uploads", "comparisons", comparisonId, "result.json");
        objectMapper.writeValue(resultPath.toFile(), result);
        result.setResultFilePath(resultPath.toString());
    }

    /**
     * Save a document pair result to a file.
     */
    private void savePairResult(String comparisonId, int pairIndex, ComparisonResult result) throws IOException {
        Path resultPath = Paths.get("uploads", "comparisons", comparisonId,
                "pair_" + pairIndex + "_result.json");
        objectMapper.writeValue(resultPath.toFile(), result);
    }

    // ========== Public Methods for API ==========

    /**
     * Get the comparison result.
     */
    public ComparisonResult getComparisonResult(String comparisonId) throws IOException {
        return cacheService.getComparisonResult(comparisonId, comparisonRepository, objectMapper);
    }

    /**
     * Get the page details for a comparison.
     */
    public PageDetails getPageDetails(String comparisonId, int pageNumber, Map<String, Object> filters)
            throws IOException {
        return pageProcessor.getPageDetails(comparisonId, pageNumber, filters);
    }

    /**
     * Get document pairs for a comparison.
     */
    public List<DocumentPair> getDocumentPairs(String comparisonId) throws IOException {
        return cacheService.getDocumentPairs(comparisonId, comparisonRepository, objectMapper, this);
    }

    /**
     * Get comparison result for a specific document pair.
     */
    public ComparisonResult getDocumentPairResult(String comparisonId, int pairIndex) throws IOException {
        return pageProcessor.getDocumentPairResult(comparisonId, pairIndex);
    }


    /**
     * Get page details for a specific document pair.
     */
    public PageDetails getDocumentPairPageDetails(String comparisonId, int pairIndex,
                                                  int pageNumber, Map<String, Object> filters) throws IOException {
        return pageProcessor.getDocumentPairPageDetails(comparisonId, pairIndex, pageNumber, filters);
    }

    /**
     * Check if a comparison is completed.
     */
    public boolean isComparisonCompleted(String comparisonId) {
        return cacheService.isComparisonCompleted(comparisonId, comparisonRepository);
    }

    /**
     * Check if a comparison has failed.
     */
    public boolean isComparisonFailed(String comparisonId) {
        return cacheService.isComparisonFailed(comparisonId, comparisonRepository);
    }

    /**
     * Get the status message for a comparison.
     */
    public String getComparisonStatusMessage(String comparisonId) {
        return cacheService.getComparisonStatusMessage(comparisonId, comparisonRepository);
    }

    /**
     * Check if a comparison is still in progress.
     */
    public boolean isComparisonInProgress(String comparisonId) {
        return cacheService.isComparisonInProgress(comparisonId, comparisonRepository);
    }

    /**
     * Cleanup method to free memory when comparison details are no longer needed.
     */
    public void cleanupComparisonMemory(String comparisonId) {
        cacheService.cleanupComparisonMemory(comparisonId);
        memoryManager.suggestGarbageCollection();
    }

    /**
     * Get the current memory usage status.
     */
    public Map<String, Object> getMemoryUsageStats() {
        return memoryManager.getMemoryUsageStats(cacheService);
    }
}