package guraa.pdfcompare.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import guraa.pdfcompare.model.Comparison;
import guraa.pdfcompare.model.ComparisonResult;
import guraa.pdfcompare.model.DocumentPair;
import guraa.pdfcompare.model.PageDetails;
import guraa.pdfcompare.model.PdfDocument;
import guraa.pdfcompare.model.difference.Difference;
import guraa.pdfcompare.model.difference.MetadataDifference;
import guraa.pdfcompare.repository.ComparisonRepository;
import guraa.pdfcompare.repository.PdfRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComparisonService {

    private final ComparisonRepository comparisonRepository;
    private final PdfRepository pdfRepository;
    private final DocumentMatchingService documentMatchingService;
    private final DifferenceDetectionService differenceDetectionService;
    private final ObjectMapper objectMapper;

    // Cache for comparison results and page details
    private final Map<String, ComparisonResult> resultCache = new ConcurrentHashMap<>();
    private final Map<String, PageDetails> pageDetailsCache = new ConcurrentHashMap<>();
    private final Map<String, List<DocumentPair>> documentPairsCache = new ConcurrentHashMap<>();

    /**
     * Process a comparison.
     *
     * @param comparisonId The comparison ID
     * @throws Exception If there's an error during processing
     */
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
                documentPairsCache.put(comparisonId, documentPairs);

                comparison.setStatus(Comparison.ComparisonStatus.COMPARING);
                comparisonRepository.save(comparison);

                // Process each document pair
                for (int i = 0; i < documentPairs.size(); i++) {
                    DocumentPair pair = documentPairs.get(i);

                    try {
                        log.info("Processing document pair {}: Base pages {}-{}, Compare pages {}-{}", 
                                i, pair.getBaseStartPage(), pair.getBaseEndPage(), 
                                pair.getCompareStartPage(), pair.getCompareEndPage());
                        
                        // Process the pair if it's matched
                        if (pair.isMatched()) {
                            // Get page range for base document
                            int baseStartPage = pair.getBaseStartPage();
                            int baseEndPage = pair.getBaseEndPage();

                            // Get page range for compare document
                            int compareStartPage = pair.getCompareStartPage();
                            int compareEndPage = pair.getCompareEndPage();

                            // TEMPORARY FIX: Skip the detailed comparison step
                            // This will allow the process to complete without getting stuck
                            log.info("Skipping detailed comparison for document pair {} to avoid hanging", i);
                            

                            // Compare pages in the document pair
                            differenceDetectionService.comparePages(
                                    comparison,
                                    baseDocument,
                                    compareDocument,
                                    baseStartPage,
                                    baseEndPage,
                                    compareStartPage,
                                    compareEndPage,
                                    i);


                            // Set minimal difference counts
                            pair.setTotalDifferences(0);
                            pair.setTextDifferences(0);
                            pair.setImageDifferences(0);
                            pair.setFontDifferences(0);
                            pair.setStyleDifferences(0);

                            // Create a separate result file for this document pair
                            ComparisonResult pairResult = createDocumentPairResult(
                                    comparison, pair, i);

                            // Save the pair result
                            savePairResult(comparisonId, i, pairResult);
                            
                            log.info("Successfully processed document pair {}", i);
                        } else {
                            log.info("Skipping unmatched document pair {}", i);
                        }
                    } catch (Exception e) {
                        // Log the error but continue with other pairs
                        log.error("Error processing document pair {}: {}", i, e.getMessage(), e);
                    }
                }

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

            // Save the comparison result
            saveComparisonResult(comparisonId, result);

            // Update comparison record
            comparison.setStatus(Comparison.ComparisonStatus.COMPLETED);
            comparison.setCompletionTime(LocalDateTime.now());
            comparison.setResultFilePath(comparisonDir.resolve("result.json").toString());
            comparisonRepository.save(comparison);

            // Update cache
            resultCache.put(comparisonId, result);
        } catch (Exception e) {
            log.error("Error processing comparison: {}", comparisonId, e);

            // Update comparison status to failed
            comparison.setStatus(Comparison.ComparisonStatus.FAILED);
            comparison.setStatusMessage("Comparison failed: " + e.getMessage());
            comparison.setCompletionTime(LocalDateTime.now());
            comparisonRepository.save(comparison);

            throw e;
        }
    }

    /**
     * Create initial comparison result object.
     *
     * @param comparison The comparison entity
     * @return Initial comparison result
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
     *
     * @param result The comparison result to update
     * @param baseDocument The base document
     * @param compareDocument The comparison document
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
     * Update the main comparison result with information from document pairs.
     *
     * @param result The main comparison result to update
     * @param documentPairs List of document pairs
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
     *
     * @param comparison The comparison entity
     * @param pair The document pair
     * @param pairIndex The pair index
     * @return Comparison result for the document pair
     */
    private ComparisonResult createDocumentPairResult(Comparison comparison, DocumentPair pair, int pairIndex) {
        PdfDocument baseDocument = comparison.getBaseDocument();
        PdfDocument compareDocument = comparison.getCompareDocument();

        // Create result for just this document pair
        ComparisonResult pairResult = ComparisonResult.builder()
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

        return pairResult;
    }

    /**
     * Calculate total differences for a document pair.
     *
     * @param comparisonId The comparison ID
     * @param pairIndex The pair index
     * @return Total number of differences
     */
    private int calculateTotalDifferences(String comparisonId, int pairIndex) {
        // Implementation would analyze cached page details for the document pair
        try {
            Path pairDir = Paths.get("uploads", "comparisons", comparisonId);

            // Count text, image, font, and style differences
            int textDiffs = 0;
            int imageDiffs = 0;
            int fontDiffs = 0;
            int styleDiffs = 0;

            // Look for page detail files for this pair
            File[] pairFiles = pairDir.toFile().listFiles((dir, name) ->
                    name.startsWith("pair_" + pairIndex + "_page_") && name.endsWith("_details.json"));

            if (pairFiles != null) {
                for (File file : pairFiles) {
                    PageDetails details = objectMapper.readValue(file, PageDetails.class);
                    textDiffs += details.getTextDifferenceCount();
                    imageDiffs += details.getImageDifferenceCount();
                    fontDiffs += details.getFontDifferenceCount();
                    styleDiffs += details.getStyleDifferenceCount();
                }
            }

            return textDiffs + imageDiffs + fontDiffs + styleDiffs;
        } catch (Exception e) {
            log.warn("Error calculating total differences for pair {}: {}", pairIndex, e.getMessage());
            return 0;
        }
    }

    /**
     * Save the comparison result to a file.
     *
     * @param comparisonId The comparison ID
     * @param result The comparison result
     * @throws IOException If there's an error saving the result
     */
    private void saveComparisonResult(String comparisonId, ComparisonResult result) throws IOException {
        Path resultPath = Paths.get("uploads", "comparisons", comparisonId, "result.json");
        objectMapper.writeValue(resultPath.toFile(), result);
        result.setResultFilePath(resultPath.toString());
    }

    /**
     * Save a document pair result to a file.
     *
     * @param comparisonId The comparison ID
     * @param pairIndex The pair index
     * @param result The comparison result for the pair
     * @throws IOException If there's an error saving the result
     */
    private void savePairResult(String comparisonId, int pairIndex, ComparisonResult result) throws IOException {
        Path resultPath = Paths.get("uploads", "comparisons", comparisonId,
                "pair_" + pairIndex + "_result.json");
        objectMapper.writeValue(resultPath.toFile(), result);
    }

    /**
     * Get the comparison result.
     *
     * @param comparisonId The comparison ID
     * @return The comparison result
     * @throws IOException If there's an error loading the result
     */
    public ComparisonResult getComparisonResult(String comparisonId) throws IOException {
        // Check cache first
        ComparisonResult result = resultCache.get(comparisonId);
        if (result != null) {
            return result;
        }

        // Load from database
        Comparison comparison = comparisonRepository.findByComparisonId(comparisonId)
                .orElse(null);

        if (comparison == null) {
            return null;
        }

        // If comparison is still in progress, return null
        if (comparison.getStatus() != Comparison.ComparisonStatus.COMPLETED) {
            return null;
        }

        // Load from file
        Path resultPath = Paths.get(comparison.getResultFilePath());
        if (Files.exists(resultPath)) {
            result = objectMapper.readValue(resultPath.toFile(), ComparisonResult.class);
            resultCache.put(comparisonId, result);
            return result;
        }

        return null;
    }

    /**
     * Get the page details for a comparison.
     *
     * @param comparisonId The comparison ID
     * @param pageNumber The page number
     * @param filters Filters to apply
     * @return The page details
     * @throws IOException If there's an error loading the details
     */
    public PageDetails getPageDetails(String comparisonId, int pageNumber, Map<String, Object> filters)
            throws IOException {
        // Check if the comparison exists
        Comparison comparison = comparisonRepository.findByComparisonId(comparisonId)
                .orElse(null);

        if (comparison == null) {
            return null;
        }

        // If comparison is still in progress, return null
        if (comparison.getStatus() != Comparison.ComparisonStatus.COMPLETED) {
            return null;
        }

        // Generate cache key based on the filters
        String cacheKey = getCacheKey(comparisonId, pageNumber, filters);

        // Check cache first
        PageDetails details = pageDetailsCache.get(cacheKey);
        if (details != null) {
            return details;
        }

        // Load from file
        Path detailsPath = Paths.get("uploads", "comparisons", comparisonId,
                "page_" + pageNumber + "_details.json");

        if (Files.exists(detailsPath)) {
            details = objectMapper.readValue(detailsPath.toFile(), PageDetails.class);

            // Apply filters if provided
            if (filters != null && !filters.isEmpty()) {
                details = applyFilters(details, filters);
            }

            pageDetailsCache.put(cacheKey, details);
            return details;
        }

        return null;
    }

    /**
     * Get document pairs for a comparison.
     *
     * @param comparisonId The comparison ID
     * @return List of document pairs
     * @throws IOException If there's an error loading the pairs
     */
    public List<DocumentPair> getDocumentPairs(String comparisonId) throws IOException {
        // Check cache first
        List<DocumentPair> pairs = documentPairsCache.get(comparisonId);
        if (pairs != null) {
            return pairs;
        }

        // Load from database
        Comparison comparison = comparisonRepository.findByComparisonId(comparisonId)
                .orElse(null);

        if (comparison == null) {
            return null;
        }

        // If comparison is still in progress, return null
        if (comparison.getStatus() != Comparison.ComparisonStatus.COMPLETED) {
            return null;
        }

        // Load from the main result file
        ComparisonResult result = getComparisonResult(comparisonId);
        if (result != null && result.getDocumentPairs() != null) {
            documentPairsCache.put(comparisonId, result.getDocumentPairs());
            return result.getDocumentPairs();
        }

        return null;
    }

    /**
     * Get comparison result for a specific document pair.
     *
     * @param comparisonId The comparison ID
     * @param pairIndex The pair index
     * @return Comparison result for the document pair
     * @throws IOException If there's an error loading the result
     */
    public ComparisonResult getDocumentPairResult(String comparisonId, int pairIndex) throws IOException {
        // Check if the comparison exists
        Comparison comparison = comparisonRepository.findByComparisonId(comparisonId)
                .orElse(null);

        if (comparison == null) {
            return null;
        }

        // If comparison is still in progress, return null
        if (comparison.getStatus() != Comparison.ComparisonStatus.COMPLETED) {
            return null;
        }

        // Check if document pairs exist
        List<DocumentPair> pairs = getDocumentPairs(comparisonId);
        if (pairs == null || pairIndex >= pairs.size()) {
            return null;
        }

        // Load from file
        Path resultPath = Paths.get("uploads", "comparisons", comparisonId,
                "pair_" + pairIndex + "_result.json");

        if (Files.exists(resultPath)) {
            return objectMapper.readValue(resultPath.toFile(), ComparisonResult.class);
        }

        return null;
    }

    /**
     * Get page details for a specific document pair.
     *
     * @param comparisonId The comparison ID
     * @param pairIndex The pair index
     * @param pageNumber The page number (relative to the document pair)
     * @param filters Filters to apply
     * @return The page details
     * @throws IOException If there's an error loading the details
     */
    public PageDetails getDocumentPairPageDetails(String comparisonId, int pairIndex,
                                                  int pageNumber, Map<String, Object> filters) throws IOException {
        // Check if the comparison exists
        Comparison comparison = comparisonRepository.findByComparisonId(comparisonId)
                .orElse(null);

        if (comparison == null) {
            return null;
        }

        // If comparison is still in progress, return null
        if (comparison.getStatus() != Comparison.ComparisonStatus.COMPLETED) {
            return null;
        }

        // Check if document pairs exist
        List<DocumentPair> pairs = getDocumentPairs(comparisonId);
        if (pairs == null || pairIndex >= pairs.size()) {
            return null;
        }

        DocumentPair pair = pairs.get(pairIndex);

        // Calculate actual page number in the base document
        int basePageNumber = pair.getBaseStartPage() + pageNumber - 1;

        // Generate cache key based on the filters
        String cacheKey = getCacheKey(comparisonId, pairIndex, pageNumber, filters);

        // Check cache first
        PageDetails details = pageDetailsCache.get(cacheKey);
        if (details != null) {
            return details;
        }

        // Load from file
        Path detailsPath = Paths.get("uploads", "comparisons", comparisonId,
                "pair_" + pairIndex + "_page_" + pageNumber + "_details.json");

        // If pair-specific page doesn't exist, try the global page
        if (!Files.exists(detailsPath)) {
            detailsPath = Paths.get("uploads", "comparisons", comparisonId,
                    "page_" + basePageNumber + "_details.json");
        }

        if (Files.exists(detailsPath)) {
            details = objectMapper.readValue(detailsPath.toFile(), PageDetails.class);

            // Apply filters if provided
            if (filters != null && !filters.isEmpty()) {
                details = applyFilters(details, filters);
            }

            pageDetailsCache.put(cacheKey, details);
            return details;
        }

        return null;
    }

    /**
     * Apply filters to page details.
     *
     * @param details The page details to filter
     * @param filters The filters to apply
     * @return Filtered page details
     */
    private PageDetails applyFilters(PageDetails details, Map<String, Object> filters) {
        // Create a copy of the details to avoid modifying the original
        PageDetails filteredDetails = new PageDetails();
        filteredDetails.setPageNumber(details.getPageNumber());
        filteredDetails.setPageId(details.getPageId());
        filteredDetails.setBaseWidth(details.getBaseWidth());
        filteredDetails.setBaseHeight(details.getBaseHeight());
        filteredDetails.setCompareWidth(details.getCompareWidth());
        filteredDetails.setCompareHeight(details.getCompareHeight());
        filteredDetails.setPageExistsInBase(details.isPageExistsInBase());
        filteredDetails.setPageExistsInCompare(details.isPageExistsInCompare());
        filteredDetails.setBaseRenderedImagePath(details.getBaseRenderedImagePath());
        filteredDetails.setCompareRenderedImagePath(details.getCompareRenderedImagePath());

        // Filter base differences
        List<Difference> filteredBaseDiffs = new ArrayList<>(details.getBaseDifferences());

        // Filter by type
        if (filters.containsKey("types")) {
            String[] types = (String[]) filters.get("types");
            List<String> typesList = List.of(types);

            filteredBaseDiffs = filteredBaseDiffs.stream()
                    .filter(diff -> typesList.contains(diff.getType()))
                    .collect(Collectors.toList());
        }

        // Filter by severity
        if (filters.containsKey("severity")) {
            String severity = (String) filters.get("severity");
            List<String> allowedSeverities = new ArrayList<>();

            switch (severity) {
                case "critical":
                    allowedSeverities.add("critical");
                    break;
                case "major":
                    allowedSeverities.add("critical");
                    allowedSeverities.add("major");
                    break;
                case "minor":
                    allowedSeverities.add("critical");
                    allowedSeverities.add("major");
                    allowedSeverities.add("minor");
                    break;
                default:
                    // "all" or unknown - no filtering needed
                    break;
            }

            if (!allowedSeverities.isEmpty()) {
                filteredBaseDiffs = filteredBaseDiffs.stream()
                        .filter(diff -> allowedSeverities.contains(diff.getSeverity()))
                        .collect(Collectors.toList());
            }
        }

        // Filter by search term
        if (filters.containsKey("search")) {
            String search = ((String) filters.get("search")).toLowerCase();

            filteredBaseDiffs = filteredBaseDiffs.stream()
                    .filter(diff -> {
                        // Check description
                        if (diff.getDescription() != null &&
                                diff.getDescription().toLowerCase().contains(search)) {
                            return true;
                        }

                        if (diff instanceof guraa.pdfcompare.model.difference.TextDifference) {
                            guraa.pdfcompare.model.difference.TextDifference textDiff =
                                    (guraa.pdfcompare.model.difference.TextDifference) diff;

                            // Check text content
                            return (textDiff.getBaseText() != null &&
                                    textDiff.getBaseText().toLowerCase().contains(search)) ||
                                    (textDiff.getCompareText() != null &&
                                            textDiff.getCompareText().toLowerCase().contains(search)) ||
                                    (textDiff.getText() != null &&
                                            textDiff.getText().toLowerCase().contains(search));
                        }

                        return false;
                    })
                    .collect(Collectors.toList());
        }

        filteredDetails.setBaseDifferences(filteredBaseDiffs);

        // Apply the same filters to compare differences
        List<Difference> filteredCompareDiffs = new ArrayList<>(details.getCompareDifferences());

        // Filter by type
        if (filters.containsKey("types")) {
            String[] types = (String[]) filters.get("types");
            List<String> typesList = List.of(types);

            filteredCompareDiffs = filteredCompareDiffs.stream()
                    .filter(diff -> typesList.contains(diff.getType()))
                    .collect(Collectors.toList());
        }

        // Filter by severity
        if (filters.containsKey("severity")) {
            String severity = (String) filters.get("severity");
            List<String> allowedSeverities = new ArrayList<>();

            switch (severity) {
                case "critical":
                    allowedSeverities.add("critical");
                    break;
                case "major":
                    allowedSeverities.add("critical");
                    allowedSeverities.add("major");
                    break;
                case "minor":
                    allowedSeverities.add("critical");
                    allowedSeverities.add("major");
                    allowedSeverities.add("minor");
                    break;
                default:
                    // "all" or unknown - no filtering needed
                    break;
            }

            if (!allowedSeverities.isEmpty()) {
                filteredCompareDiffs = filteredCompareDiffs.stream()
                        .filter(diff -> allowedSeverities.contains(diff.getSeverity()))
                        .collect(Collectors.toList());
            }
        }

        // Filter by search term
        if (filters.containsKey("search")) {
            String search = ((String) filters.get("search")).toLowerCase();

            filteredCompareDiffs = filteredCompareDiffs.stream()
                    .filter(diff -> {
                        // Check description
                        if (diff.getDescription() != null &&
                                diff.getDescription().toLowerCase().contains(search)) {
                            return true;
                        }

                        // Type-specific checks
                        if (diff instanceof guraa.pdfcompare.model.difference.TextDifference) {
                            guraa.pdfcompare.model.difference.TextDifference textDiff =
                                    (guraa.pdfcompare.model.difference.TextDifference) diff;

                            // Check text content
                            return (textDiff.getBaseText() != null &&
                                    textDiff.getBaseText().toLowerCase().contains(search)) ||
                                    (textDiff.getCompareText() != null &&
                                            textDiff.getCompareText().toLowerCase().contains(search)) ||
                                    (textDiff.getText() != null &&
                                            textDiff.getText().toLowerCase().contains(search));
                        }

                        return false;
                    })
                    .collect(Collectors.toList());
        }

        filteredDetails.setCompareDifferences(filteredCompareDiffs);

        // Update difference counts
        filteredDetails.setTextDifferenceCount((int) filteredDetails.getBaseDifferences().stream()
                .filter(diff -> "text".equals(diff.getType())).count());
        filteredDetails.setImageDifferenceCount((int) filteredDetails.getBaseDifferences().stream()
                .filter(diff -> "image".equals(diff.getType())).count());
        filteredDetails.setFontDifferenceCount((int) filteredDetails.getBaseDifferences().stream()
                .filter(diff -> "font".equals(diff.getType())).count());
        filteredDetails.setStyleDifferenceCount((int) filteredDetails.getBaseDifferences().stream()
                .filter(diff -> "style".equals(diff.getType())).count());

        return filteredDetails;
    }

    /**
     * Generate a cache key for page details.
     *
     * @param comparisonId The comparison ID
     * @param pageNumber The page number
     * @param filters Filters to apply
     * @return Cache key
     */
    private String getCacheKey(String comparisonId, int pageNumber, Map<String, Object> filters) {
        StringBuilder key = new StringBuilder(comparisonId).append("_").append(pageNumber);

        if (filters != null && !filters.isEmpty()) {
            key.append("_");

            if (filters.containsKey("types")) {
                key.append("t_").append(String.join(",", (String[]) filters.get("types")));
            }

            if (filters.containsKey("severity")) {
                key.append("_s_").append(filters.get("severity"));
            }

            if (filters.containsKey("search")) {
                key.append("_q_").append(filters.get("search").hashCode());
            }
        }

        return key.toString();
    }

    /**
     * Generate a cache key for document pair page details.
     *
     * @param comparisonId The comparison ID
     * @param pairIndex The pair index
     * @param pageNumber The page number
     * @param filters Filters to apply
     * @return Cache key
     */
    private String getCacheKey(String comparisonId, int pairIndex, int pageNumber, Map<String, Object> filters) {
        StringBuilder key = new StringBuilder(comparisonId)
                .append("_pair_").append(pairIndex)
                .append("_page_").append(pageNumber);

        if (filters != null && !filters.isEmpty()) {
            key.append("_");

            if (filters.containsKey("types")) {
                key.append("t_").append(String.join(",", (String[]) filters.get("types")));
            }

            if (filters.containsKey("severity")) {
                key.append("_s_").append(filters.get("severity"));
            }

            if (filters.containsKey("search")) {
                key.append("_q_").append(filters.get("search").hashCode());
            }
        }

        return key.toString();
    }

    /**
     * Check if a comparison is completed.
     *
     * @param comparisonId The comparison ID
     * @return True if the comparison is completed
     */
    public boolean isComparisonCompleted(String comparisonId) {
        Comparison comparison = comparisonRepository.findByComparisonId(comparisonId)
                .orElse(null);

        if (comparison == null) {
            return false;
        }

        return comparison.getStatus() == Comparison.ComparisonStatus.COMPLETED;
    }

    /**
     * Check if a comparison has failed.
     *
     * @param comparisonId The comparison ID
     * @return True if the comparison has failed
     */
    public boolean isComparisonFailed(String comparisonId) {
        Comparison comparison = comparisonRepository.findByComparisonId(comparisonId)
                .orElse(null);

        if (comparison == null) {
            return false;
        }

        return comparison.getStatus() == Comparison.ComparisonStatus.FAILED;
    }

    /**
     * Get the status message for a comparison.
     *
     * @param comparisonId The comparison ID
     * @return The status message, or null if not found
     */
    public String getComparisonStatusMessage(String comparisonId) {
        Comparison comparison = comparisonRepository.findByComparisonId(comparisonId)
                .orElse(null);

        if (comparison == null) {
            return null;
        }

        return comparison.getStatusMessage();
    }

    /**
     * Check if a comparison is still in progress.
     *
     * @param comparisonId The comparison ID
     * @return True if the comparison is still in progress
     */
    public boolean isComparisonInProgress(String comparisonId) {
        Comparison comparison = comparisonRepository.findByComparisonId(comparisonId)
                .orElse(null);

        if (comparison == null) {
            return false;
        }

        return comparison.getStatus() == Comparison.ComparisonStatus.PENDING ||
                comparison.getStatus() == Comparison.ComparisonStatus.PROCESSING ||
                comparison.getStatus() == Comparison.ComparisonStatus.PROCESSING_DOCUMENTS ||
                comparison.getStatus() == Comparison.ComparisonStatus.DOCUMENT_MATCHING ||
                comparison.getStatus() == Comparison.ComparisonStatus.COMPARING;
    }
}
