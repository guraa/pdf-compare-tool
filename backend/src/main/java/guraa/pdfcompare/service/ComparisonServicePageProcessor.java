package guraa.pdfcompare.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import guraa.pdfcompare.model.Comparison;
import guraa.pdfcompare.model.ComparisonResult;
import guraa.pdfcompare.model.DocumentPair;
import guraa.pdfcompare.model.PageDetails;
import guraa.pdfcompare.model.difference.Difference;
import guraa.pdfcompare.model.difference.TextDifference;
import guraa.pdfcompare.repository.ComparisonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Handles page-level processing for the ComparisonService.
 * Responsible for page details retrieval, filtering, and difference calculation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComparisonServicePageProcessor {
    // Define logger explicitly as fallback if Lombok annotation doesn't work
    private static final Logger logger = LoggerFactory.getLogger(ComparisonServicePageProcessor.class);

    private final ComparisonServiceCache cacheService;
    private final ComparisonRepository comparisonRepository;
    private final ObjectMapper objectMapper;

    /**
     * Calculate total differences for a document pair by analyzing page detail files.
     *
     * @param comparisonId The comparison ID
     * @param pairIndex The pair index
     * @return Total number of differences
     */
    public int calculateTotalDifferences(String comparisonId, int pairIndex, ObjectMapper objectMapper) {
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
            logger.warn("Error calculating total differences for pair {}: {}", pairIndex, e.getMessage());
            return 0;
        }
    }

    /**
     * Get the page details for a comparison with LRU caching and memory management.
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
        PageDetails details = cacheService.getPageDetailsFromCache(cacheKey);
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

            // Cache the result
            cacheService.cachePageDetails(cacheKey, details);
            return details;
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

        // Generate cache key based on the filters
        String cacheKey = getCacheKey(comparisonId, pairIndex, pageNumber, filters);

        // Check cache first
        PageDetails details = cacheService.getPageDetailsFromCache(cacheKey);
        if (details != null) {
            return details;
        }

        // Load from file
        Path detailsPath = Paths.get("uploads", "comparisons", comparisonId,
                "pair_" + pairIndex + "_page_" + pageNumber + "_details.json");

        // Try to get the base page number from the DocumentPair
        int basePageNumber = -1;
        try {
            ComparisonResult result = getComparisonResult(comparisonId);
            if (result != null && result.getDocumentPairs() != null && pairIndex < result.getDocumentPairs().size()) {
                DocumentPair pair = result.getDocumentPairs().get(pairIndex);
                basePageNumber = pair.getBaseStartPage() + pageNumber - 1;
            }
        } catch (Exception e) {
            logger.warn("Error determining base page number: {}", e.getMessage());
        }

        // If pair-specific page doesn't exist, try the global page
        if (!Files.exists(detailsPath) && basePageNumber >= 0) {
            detailsPath = Paths.get("uploads", "comparisons", comparisonId,
                    "page_" + basePageNumber + "_details.json");
        }

        if (Files.exists(detailsPath)) {
            details = objectMapper.readValue(detailsPath.toFile(), PageDetails.class);

            // Apply filters if provided
            if (filters != null && !filters.isEmpty()) {
                details = applyFilters(details, filters);
            }

            // Cache the result
            cacheService.cachePageDetails(cacheKey, details);
            return details;
        }

        return null;
    }

    /**
     * Get the comparison result from the cache or file system.
     */
    private ComparisonResult getComparisonResult(String comparisonId) throws IOException {
        return cacheService.getComparisonResult(comparisonId, comparisonRepository, objectMapper);
    }

    // Rest of the class remains the same
    // ...

    /**
     * Apply filters to page details with optimized memory usage.
     *
     * @param details The page details to filter
     * @param filters The filters to apply
     * @return Filtered page details
     */
    public PageDetails applyFilters(PageDetails details, Map<String, Object> filters) {
        if (details == null) {
            return null;
        }

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

            // Filtering with optimized collection approach rather than stream for better memory usage
            List<Difference> tempList = new ArrayList<>();
            for (Difference diff : filteredBaseDiffs) {
                if (typesList.contains(diff.getType())) {
                    tempList.add(diff);
                }
            }
            filteredBaseDiffs = tempList;
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
                // Filtering with optimized collection approach
                List<Difference> tempList = new ArrayList<>();
                for (Difference diff : filteredBaseDiffs) {
                    if (allowedSeverities.contains(diff.getSeverity())) {
                        tempList.add(diff);
                    }
                }
                filteredBaseDiffs = tempList;
            }
        }

        // Filter by search term
        if (filters.containsKey("search")) {
            String search = ((String) filters.get("search")).toLowerCase();

            // Optimized search filtering without streams
            List<Difference> tempList = new ArrayList<>();
            for (Difference diff : filteredBaseDiffs) {
                boolean matches = false;

                // Check description
                if (diff.getDescription() != null &&
                        diff.getDescription().toLowerCase().contains(search)) {
                    matches = true;
                }

                // Check for text content if it's a TextDifference
                if (!matches && diff instanceof TextDifference) {
                    TextDifference textDiff = (TextDifference) diff;

                    if ((textDiff.getBaseText() != null &&
                            textDiff.getBaseText().toLowerCase().contains(search)) ||
                            (textDiff.getCompareText() != null &&
                                    textDiff.getCompareText().toLowerCase().contains(search)) ||
                            (textDiff.getText() != null &&
                                    textDiff.getText().toLowerCase().contains(search))) {
                        matches = true;
                    }
                }

                if (matches) {
                    tempList.add(diff);
                }
            }
            filteredBaseDiffs = tempList;
        }

        filteredDetails.setBaseDifferences(filteredBaseDiffs);

        // Apply the same filters to compare differences
        List<Difference> filteredCompareDiffs = new ArrayList<>(details.getCompareDifferences());

        // Filter by type
        if (filters.containsKey("types")) {
            String[] types = (String[]) filters.get("types");
            List<String> typesList = List.of(types);

            // Optimized filtering for compare differences
            List<Difference> tempList = new ArrayList<>();
            for (Difference diff : filteredCompareDiffs) {
                if (typesList.contains(diff.getType())) {
                    tempList.add(diff);
                }
            }
            filteredCompareDiffs = tempList;
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
                // Optimized severity filtering
                List<Difference> tempList = new ArrayList<>();
                for (Difference diff : filteredCompareDiffs) {
                    if (allowedSeverities.contains(diff.getSeverity())) {
                        tempList.add(diff);
                    }
                }
                filteredCompareDiffs = tempList;
            }
        }

        // Filter by search term
        if (filters.containsKey("search")) {
            String search = ((String) filters.get("search")).toLowerCase();

            // Optimized search filtering for compare differences
            List<Difference> tempList = new ArrayList<>();
            for (Difference diff : filteredCompareDiffs) {
                boolean matches = false;

                // Check description
                if (diff.getDescription() != null &&
                        diff.getDescription().toLowerCase().contains(search)) {
                    matches = true;
                }

                // Check for text content if it's a TextDifference
                if (!matches && diff instanceof TextDifference) {
                    TextDifference textDiff = (TextDifference) diff;

                    if ((textDiff.getBaseText() != null &&
                            textDiff.getBaseText().toLowerCase().contains(search)) ||
                            (textDiff.getCompareText() != null &&
                                    textDiff.getCompareText().toLowerCase().contains(search)) ||
                            (textDiff.getText() != null &&
                                    textDiff.getText().toLowerCase().contains(search))) {
                        matches = true;
                    }
                }

                if (matches) {
                    tempList.add(diff);
                }
            }
            filteredCompareDiffs = tempList;
        }

        filteredDetails.setCompareDifferences(filteredCompareDiffs);

        // Update difference counts using optimized counting approach
        int textCount = 0;
        int imageCount = 0;
        int fontCount = 0;
        int styleCount = 0;

        for (Difference diff : filteredDetails.getBaseDifferences()) {
            String type = diff.getType();
            if ("text".equals(type)) {
                textCount++;
            } else if ("image".equals(type)) {
                imageCount++;
            } else if ("font".equals(type)) {
                fontCount++;
            } else if ("style".equals(type)) {
                styleCount++;
            }
        }

        filteredDetails.setTextDifferenceCount(textCount);
        filteredDetails.setImageDifferenceCount(imageCount);
        filteredDetails.setFontDifferenceCount(fontCount);
        filteredDetails.setStyleDifferenceCount(styleCount);

        return filteredDetails;
    }

    /**
     * Generate a cache key for page details with optimized string handling.
     *
     * @param comparisonId The comparison ID
     * @param pageNumber The page number
     * @param filters Filters to apply
     * @return Cache key
     */
    public String getCacheKey(String comparisonId, int pageNumber, Map<String, Object> filters) {
        StringBuilder key = new StringBuilder(comparisonId.length() + 20);
        key.append(comparisonId).append("_").append(pageNumber);

        if (filters != null && !filters.isEmpty()) {
            key.append("_");

            if (filters.containsKey("types")) {
                key.append("t_");
                String[] types = (String[]) filters.get("types");
                if (types.length > 0) {
                    key.append(types[0]);
                    for (int i = 1; i < types.length; i++) {
                        key.append(",").append(types[i]);
                    }
                }
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
     * Generate a cache key for document pair page details with optimized string handling.
     *
     * @param comparisonId The comparison ID
     * @param pairIndex The pair index
     * @param pageNumber The page number
     * @param filters Filters to apply
     * @return Cache key
     */
    public String getCacheKey(String comparisonId, int pairIndex, int pageNumber, Map<String, Object> filters) {
        StringBuilder key = new StringBuilder(comparisonId.length() + 30);
        key.append(comparisonId)
                .append("_pair_").append(pairIndex)
                .append("_page_").append(pageNumber);

        if (filters != null && !filters.isEmpty()) {
            key.append("_");

            if (filters.containsKey("types")) {
                key.append("t_");
                String[] types = (String[]) filters.get("types");
                if (types.length > 0) {
                    key.append(types[0]);
                    for (int i = 1; i < types.length; i++) {
                        key.append(",").append(types[i]);
                    }
                }
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
}