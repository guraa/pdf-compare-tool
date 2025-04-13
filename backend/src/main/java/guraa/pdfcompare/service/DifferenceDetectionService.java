package guraa.pdfcompare.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import guraa.pdfcompare.model.Comparison;
import guraa.pdfcompare.model.ComparisonResult;
import guraa.pdfcompare.model.PageDetails;
import guraa.pdfcompare.model.PdfDocument;
import guraa.pdfcompare.model.difference.Difference;
import guraa.pdfcompare.model.difference.TextDifference;
import guraa.pdfcompare.util.DifferenceCalculator;
import guraa.pdfcompare.util.TextElement;
import guraa.pdfcompare.util.TextExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for detecting differences between PDF documents.
 * This main class coordinates the overall comparison process.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DifferenceDetectionService {

    private final TextExtractor textExtractor;
    private final ImageComparisonService imageComparisonService;
    private final FontComparisonService fontComparisonService;
    private final DifferenceCalculator differenceCalculator;
    private final ObjectMapper objectMapper;
    private final DifferenceDetectionServiceOptimizer optimizer;

    /**
     * Compare all pages between two PDF documents.
     *
     * @param comparison      The comparison entity
     * @param baseDocument    The base document
     * @param compareDocument The comparison document
     * @return List of page differences
     * @throws IOException If there's an error processing the documents
     */
    public List<ComparisonResult.PageDifference> compareAllPages(
            Comparison comparison,
            PdfDocument baseDocument,
            PdfDocument compareDocument) throws IOException {

        // Log memory usage at the start
        optimizer.logMemoryUsage("Start of compareAllPages");

        // Load PDF documents
        PDDocument basePdf = PDDocument.load(new File(baseDocument.getFilePath()));
        PDDocument comparePdf = PDDocument.load(new File(compareDocument.getFilePath()));

        try {
            List<ComparisonResult.PageDifference> pageDifferences = new ArrayList<>();

            // Get page counts
            int basePageCount = basePdf.getNumberOfPages();
            int comparePageCount = comparePdf.getNumberOfPages();

            // Create directory for comparison results
            Path comparisonDir = Paths.get("uploads", "comparisons", comparison.getComparisonId());
            Files.createDirectories(comparisonDir);

            // Compare each page (considering both documents may have different page counts)
            int maxPages = Math.max(basePageCount, comparePageCount);

            for (int i = 0; i < maxPages; i++) {
                // Use memory optimization for page processing
                final int pageIndex = i;
                ComparisonResult.PageDifference pageDiff = optimizer.processPageWithMemoryOptimization(
                        i + 1, // Log with 1-based page number
                        () -> processPage(
                                comparison,
                                basePdf,
                                comparePdf,
                                baseDocument,
                                compareDocument,
                                pageIndex,
                                basePageCount,
                                comparePageCount,
                                comparisonDir
                        )
                );

                pageDifferences.add(pageDiff);
            }

            return pageDifferences;
        } finally {
            // Close PDFs
            basePdf.close();
            comparePdf.close();

            // Log memory usage at the end
            optimizer.logMemoryUsage("End of compareAllPages");

            // Suggest garbage collection
            optimizer.checkAndOptimizeMemory();
        }
    }

    /**
     * Process a single page for comparison.
     */
    private ComparisonResult.PageDifference processPage(
            Comparison comparison,
            PDDocument basePdf,
            PDDocument comparePdf,
            PdfDocument baseDocument,
            PdfDocument compareDocument,
            int pageIndex,
            int basePageCount,
            int comparePageCount,
            Path comparisonDir) {

        // Check if page exists in both documents
        boolean pageInBase = pageIndex < basePageCount;
        boolean pageInCompare = pageIndex < comparePageCount;

        // Create page difference object
        ComparisonResult.PageDifference pageDiff = new ComparisonResult.PageDifference();
        pageDiff.setPageNumber(pageIndex + 1); // 1-based page numbers for the API
        pageDiff.setOnlyInBase(pageInBase && !pageInCompare);
        pageDiff.setOnlyInCompare(!pageInBase && pageInCompare);

        // If page exists in only one document, add minimal information
        if (!pageInBase || !pageInCompare) {
            return pageDiff;
        }

        // Get pages from both documents
        PDPage basePage = basePdf.getPage(pageIndex);
        PDPage comparePage = comparePdf.getPage(pageIndex);

        // Check dimensions
        PDRectangle baseSize = basePage.getMediaBox();
        PDRectangle compareSize = comparePage.getMediaBox();

        pageDiff.setDimensionsDifferent(!baseSize.equals(compareSize));

        float basePageHeight = baseSize.getHeight();
        float comparePageHeight = compareSize.getHeight();

        try {
            // Extract text from both pages
            String baseText = textExtractor.extractTextFromPage(basePdf, pageIndex);
            String compareText = textExtractor.extractTextFromPage(comparePdf, pageIndex);

            // Compare text content
            List<TextDifference> textDiffs = differenceCalculator.compareText(
                    baseText, compareText, comparison.getTextComparisonMethod());

            // Ensure all text differences have coordinates
            ensureTextDifferencesHaveCoordinates(textDiffs, baseText, baseSize.getWidth(), baseSize.getHeight(),
                    basePdf, pageIndex);

            if (!textDiffs.isEmpty()) {
                ComparisonResult.TextDifferences textDifferences = new ComparisonResult.TextDifferences();
                textDifferences.setBaseText(baseText);
                textDifferences.setCompareText(compareText);
                textDifferences.setDifferences(new ArrayList<>(textDiffs));

                pageDiff.setTextDifferences(textDifferences);
            }

            // Compare text elements (style, fonts, positioning)
            List<guraa.pdfcompare.util.TextElement> baseElements = textExtractor.extractTextElementsFromPage(basePdf, pageIndex);
            List<guraa.pdfcompare.util.TextElement> compareElements = textExtractor.extractTextElementsFromPage(comparePdf, pageIndex);

            List<Difference> textElementDiffs = TextElementComparisonService.compareTextElements(
                    baseElements, compareElements, differenceCalculator);

            // Make sure all text element differences have coordinates
            ensureCoordinatesForDifferences(textElementDiffs, baseSize.getWidth(), baseSize.getHeight(), 0.3);

            if (!textElementDiffs.isEmpty()) {
                pageDiff.setTextElementDifferences(textElementDiffs);
            }

            // Compare images
            List<Difference> imageDiffs = imageComparisonService.compareImagesOnPage(
                    basePdf, comparePdf, pageIndex, comparisonDir);

            // Ensure all image differences have coordinates
            ensureCoordinatesForDifferences(imageDiffs, baseSize.getWidth(), baseSize.getHeight(), 0.4);

            if (!imageDiffs.isEmpty()) {
                pageDiff.setImageDifferences(imageDiffs);
            }

            // Compare fonts
            List<Difference> fontDiffs = fontComparisonService.compareFontsOnPage(
                    basePdf, comparePdf, pageIndex, baseDocument, compareDocument, comparisonDir);

            // Ensure all font differences have coordinates
            ensureCoordinatesForDifferences(fontDiffs, baseSize.getWidth(), baseSize.getHeight(), 0.2);

            if (!fontDiffs.isEmpty()) {
                pageDiff.setFontDifferences(fontDiffs);
            }

            // Also create detailed page analysis for the API to serve
            createPageDetails(
                    comparison.getComparisonId(),
                    pageIndex + 1, // 1-based page number
                    baseDocument,
                    compareDocument,
                    baseText,
                    compareText,
                    textDiffs,
                    textElementDiffs,
                    imageDiffs,
                    fontDiffs,
                    baseSize,
                    compareSize);

        } catch (Exception e) {
            log.error("Error processing page {}: {}", pageIndex + 1, e.getMessage(), e);
        }

        return pageDiff;
    }

    /**
     * Ensure text differences have coordinates with proper coordinate transformation.
     * This method extracts text positions and assigns proper coordinates to differences.
     *
     * @param textDiffs List of text differences to update with coordinates
     * @param fullText Full text content of the page
     * @param pageWidth Width of the page
     * @param pageHeight Height of the page
     * @param pdf PDF document
     * @param pageIndex Page index
     */
    private void ensureTextDifferencesHaveCoordinates(
            List<TextDifference> textDiffs,
            String fullText,
            double pageWidth,
            double pageHeight,
            PDDocument pdf,
            int pageIndex) {

        try {
            // Extract text elements with correct coordinate transformation
            List<TextElement> textElements = textExtractor.extractTextElementsFromPage(pdf, pageIndex);

            // Create a map to quickly find text elements that might match each difference
            Map<String, List<TextElement>> textElementMap = new HashMap<>();
            for (TextElement element : textElements) {
                String text = element.getText().trim();
                if (!text.isEmpty()) {
                    textElementMap.computeIfAbsent(text, k -> new ArrayList<>()).add(element);
                }
            }

            // Assign coordinates to each text difference
            for (TextDifference diff : textDiffs) {
                // Get the text to search for
                String diffText = diff.getBaseText() != null ? diff.getBaseText() : diff.getCompareText();
                if (diffText == null || diffText.trim().isEmpty()) {
                    continue;
                }

                // Try to find exact matches first
                boolean coordsFound = findAndAssignCoordinates(diff, diffText, textElementMap);

                // If no exact match, try to find partial matches
                if (!coordsFound && diffText.length() > 5) {
                    // Try to match based on context - find a portion of the text
                    String context = findTextContext(fullText, diffText);
                    if (context != null && !context.equals(diffText)) {
                        coordsFound = findAndAssignCoordinates(diff, context, textElementMap);
                    }
                }

                // If still no coordinates, use estimated position
                if (!coordsFound) {
                    // Estimate position based on change type and other heuristics
                    double relativeY = estimateRelativeYPosition(diff, pageIndex);
                    differenceCalculator.estimatePositionForDifference(diff, pageWidth, pageHeight, relativeY);
                }
            }
        } catch (Exception e) {
            log.warn("Error assigning coordinates to text differences: {}", e.getMessage(), e);

            // Fallback: ensure all differences have at least estimated coordinates
            for (TextDifference diff : textDiffs) {
                if (diff.getX() == 0 && diff.getY() == 0) {
                    double relativeY = estimateRelativeYPosition(diff, pageIndex);
                    differenceCalculator.estimatePositionForDifference(diff, pageWidth, pageHeight, relativeY);
                }
            }
        }
    }

    private String findTextContext(String fullText, String diffText) {
        if (fullText == null || diffText == null) {
            return null;
        }

        int index = fullText.indexOf(diffText);
        if (index >= 0) {
            // Get some context around the difference
            int start = Math.max(0, index - 20);
            int end = Math.min(fullText.length(), index + diffText.length() + 20);

            // Try to expand to whole words
            while (start > 0 && !Character.isWhitespace(fullText.charAt(start))) {
                start--;
            }

            while (end < fullText.length() && !Character.isWhitespace(fullText.charAt(end))) {
                end++;
            }

            return fullText.substring(start, end).trim();
        }

        return null;
    }

    private boolean findAndAssignCoordinates(
            TextDifference diff,
            String searchText,
            Map<String, List<TextElement>> textElementMap) {

        // Try to find exact matches first
        List<TextElement> matches = textElementMap.getOrDefault(searchText.trim(), Collections.emptyList());

        // If no exact matches, search for partial matches
        if (matches.isEmpty() && searchText.length() > 10) {
            List<TextElement> candidates = new ArrayList<>();
            for (Map.Entry<String, List<TextElement>> entry : textElementMap.entrySet()) {
                if (entry.getKey().contains(searchText) || searchText.contains(entry.getKey())) {
                    candidates.addAll(entry.getValue());
                }
            }
            matches = candidates;
        }

        if (!matches.isEmpty()) {
            // Calculate a bounding box that includes all matching elements
            double minX = Double.MAX_VALUE;
            double minY = Double.MAX_VALUE;
            double maxX = 0;
            double maxY = 0;

            for (TextElement element : matches) {
                minX = Math.min(minX, element.getX());
                minY = Math.min(minY, element.getY());
                maxX = Math.max(maxX, element.getX() + element.getWidth());
                maxY = Math.max(maxY, element.getY() + element.getHeight());
            }

            // Set the coordinates on the difference
            // No need for additional transformation since the TextElements already have
            // coordinates in display space (origin at top-left)
            differenceCalculator.setPositionAndBounds(
                    diff,
                    minX,
                    minY,
                    maxX - minX,
                    maxY - minY);

            return true;
        }

        return false;
    }





    /**
     * Create detailed page analysis for a standard comparison with proper coordinate handling.
     * This method ensures consistent coordinate transformation throughout the page analysis.
     *
     * @param comparisonId     The comparison ID
     * @param pageNumber       The page number (1-based)
     * @param baseDocument     The base document
     * @param compareDocument  The comparison document
     * @param baseText         Text content from base page
     * @param compareText      Text content from comparison page
     * @param textDiffs        Text differences
     * @param textElementDiffs Text element differences
     * @param imageDiffs       Image differences
     * @param fontDiffs        Font differences
     * @param baseSize         Base page dimensions
     * @param compareSize      Comparison page dimensions
     * @throws IOException If there's an error saving the page details
     */
    private void createPageDetails(
            String comparisonId,
            int pageNumber,
            PdfDocument baseDocument,
            PdfDocument compareDocument,
            String baseText,
            String compareText,
            List<TextDifference> textDiffs,
            List<Difference> textElementDiffs,
            List<Difference> imageDiffs,
            List<Difference> fontDiffs,
            PDRectangle baseSize,
            PDRectangle compareSize) throws IOException {

        PageDetails pageDetails = new PageDetails();
        pageDetails.setPageNumber(pageNumber);
        pageDetails.setPageId(UUID.randomUUID().toString());

        // Store page dimensions
        if (baseSize != null) {
            pageDetails.setBaseWidth(baseSize.getWidth());
            pageDetails.setBaseHeight(baseSize.getHeight());
        }

        if (compareSize != null) {
            pageDetails.setCompareWidth(compareSize.getWidth());
            pageDetails.setCompareHeight(compareSize.getHeight());
        }

        // Store page existence flags
        pageDetails.setPageExistsInBase(baseText != null);
        pageDetails.setPageExistsInCompare(compareText != null);

        // Store extracted text
        pageDetails.setBaseExtractedText(baseText);
        pageDetails.setCompareExtractedText(compareText);

        // Get page heights for coordinate transformations
        double basePageHeight = baseSize != null ? baseSize.getHeight() : 0;
        double comparePageHeight = compareSize != null ? compareSize.getHeight() : 0;

        // Organize differences by source document
        List<Difference> baseDifferences = new ArrayList<>();
        List<Difference> compareDifferences = new ArrayList<>();

        // Add text differences - ensure all have coordinates
        for (TextDifference diff : textDiffs) {
            // Make sure all text differences have coordinates
            if (diff.getX() == 0 && diff.getY() == 0 && diff.getWidth() == 0 && diff.getHeight() == 0) {
                double relativeY = estimateRelativeYPosition(diff, pageNumber);
                differenceCalculator.estimatePositionForDifference(diff,
                        baseSize.getWidth(), baseSize.getHeight(), relativeY);
            }

            if (!"added".equals(diff.getChangeType())) {
                baseDifferences.add(diff);
            }

            if (!"deleted".equals(diff.getChangeType())) {
                compareDifferences.add(diff);
            }
        }

        // Add text element differences - ensure all have coordinates
        for (Difference diff : textElementDiffs) {
            // Make sure all differences have coordinates
            if (diff.getX() == 0 && diff.getY() == 0 && diff.getWidth() == 0 && diff.getHeight() == 0) {
                differenceCalculator.estimatePositionForDifference(diff,
                        baseSize.getWidth(), baseSize.getHeight(), 0.3);
            }

            if (!"added".equals(diff.getChangeType())) {
                baseDifferences.add(diff);
            }

            if (!"deleted".equals(diff.getChangeType())) {
                compareDifferences.add(diff);
            }
        }

        // Add image differences - ensure all have coordinates
        for (Difference diff : imageDiffs) {
            // Make sure all image differences have coordinates
            if (diff.getX() == 0 && diff.getY() == 0 && diff.getWidth() == 0 && diff.getHeight() == 0) {
                differenceCalculator.estimatePositionForDifference(diff,
                        baseSize.getWidth(), baseSize.getHeight(), 0.4);
            }

            if (!"added".equals(diff.getChangeType())) {
                baseDifferences.add(diff);
            }

            if (!"deleted".equals(diff.getChangeType())) {
                compareDifferences.add(diff);
            }
        }

        // Add font differences - ensure all have coordinates
        for (Difference diff : fontDiffs) {
            // Make sure all font differences have coordinates
            if (diff.getX() == 0 && diff.getY() == 0 && diff.getWidth() == 0 && diff.getHeight() == 0) {
                differenceCalculator.estimatePositionForDifference(diff,
                        baseSize.getWidth(), baseSize.getHeight(), 0.2);
            }

            if (!"added".equals(diff.getChangeType())) {
                baseDifferences.add(diff);
            }

            if (!"deleted".equals(diff.getChangeType())) {
                compareDifferences.add(diff);
            }
        }

        pageDetails.setBaseDifferences(baseDifferences);
        pageDetails.setCompareDifferences(compareDifferences);

        // Set difference counts
        pageDetails.setTextDifferenceCount((int) baseDifferences.stream()
                .filter(diff -> "text".equals(diff.getType())).count());
        pageDetails.setImageDifferenceCount((int) baseDifferences.stream()
                .filter(diff -> "image".equals(diff.getType())).count());
        pageDetails.setFontDifferenceCount((int) baseDifferences.stream()
                .filter(diff -> "font".equals(diff.getType())).count());
        pageDetails.setStyleDifferenceCount((int) baseDifferences.stream()
                .filter(diff -> "style".equals(diff.getType())).count());

        // Set rendered page image paths
        String baseImagePath = String.format("/api/pdfs/document/%s/page/%d",
                baseDocument.getFileId(), pageNumber);
        String compareImagePath = String.format("/api/pdfs/document/%s/page/%d",
                compareDocument.getFileId(), pageNumber);

        pageDetails.setBaseRenderedImagePath(baseImagePath);
        pageDetails.setCompareRenderedImagePath(compareImagePath);

        // Save page details to a file
        Path detailsPath = Paths.get("uploads", "comparisons", comparisonId,
                "page_" + pageNumber + "_details.json");
        objectMapper.writeValue(detailsPath.toFile(), pageDetails);
    }

    /**
     * Estimate relative Y position for a difference on the page.
     * Used when exact coordinates cannot be determined.
     *
     * @param diff The difference
     * @param pageNumber The page number
     * @return Relative Y position (0.0-1.0)
     */
    private double estimateRelativeYPosition(TextDifference diff, int pageNumber) {
        // The idea is to distribute differences across the page for better visualization
        // Based on page number and other factors to make it predictable and consistent

        double baseOffset = (pageNumber % 10) * 0.05;

        // Use hash code of the text for deterministic but distributed placement
        int textHash = diff.getText() != null ? diff.getText().hashCode() : 0;
        double hashOffset = Math.abs(textHash % 100) / 100.0 * 0.4; // 0-0.4 range

        double position = 0.1 + baseOffset + hashOffset; // Start at 10% from the top, then add offsets

        // Cap at reasonable range
        return Math.min(0.9, Math.max(0.1, position));
    }
    /**
     * Ensure all differences in a list have coordinates.
     * Fills in missing coordinates for any difference in the list.
     *
     * @param differences List of differences to check and update
     * @param pageWidth Width of the page
     * @param pageHeight Height of the page
     * @param defaultRelativeY Default relative Y position (0.0-1.0) to use if no other reference is available
     */
    private void ensureCoordinatesForDifferences(
            List<Difference> differences,
            double pageWidth,
            double pageHeight,
            double defaultRelativeY) {

        if (differences == null) {
            return;
        }

        // Map to track how many differences of each type we've seen
        Map<String, Integer> typeCounts = new HashMap<>();

        for (Difference diff : differences) {
            // Skip if already has coordinates
            if (diff.getX() != 0 || diff.getY() != 0) {
                continue;
            }

            // Get count for this type
            int count = typeCounts.getOrDefault(diff.getType(), 0);
            typeCounts.put(diff.getType(), count + 1);

            // Calculate a relative Y offset based on the count to spread differences vertically
            double relativeYOffset = count * 0.05;
            double relativeY = defaultRelativeY + relativeYOffset;

            // Ensure we stay within bounds
            relativeY = Math.min(0.9, relativeY);

            // Set position and bounds using the estimatePositionForDifference method
            // which handles coordinates correctly for display space
            differenceCalculator.estimatePositionForDifference(diff, pageWidth, pageHeight, relativeY);
        }
    }



    /**
     * Compare pages for a specific document pair.
     *
     * @param comparison       The comparison entity
     * @param baseDocument     The base document
     * @param compareDocument  The comparison document
     * @param baseStartPage    Start page in base document
     * @param baseEndPage      End page in base document
     * @param compareStartPage Start page in comparison document
     * @param compareEndPage   End page in comparison document
     * @param pairIndex        Index of the document pair
     * @throws IOException If there's an error processing the documents
     */
    public void comparePages(
            Comparison comparison,
            PdfDocument baseDocument,
            PdfDocument compareDocument,
            int baseStartPage,
            int baseEndPage,
            int compareStartPage,
            int compareEndPage,
            int pairIndex) throws IOException {

        // Log memory at start
        optimizer.logMemoryUsage("START of comparePages for pair " + pairIndex);

        // Load PDF documents
        PDDocument basePdf = PDDocument.load(new File(baseDocument.getFilePath()));
        PDDocument comparePdf = PDDocument.load(new File(compareDocument.getFilePath()));

        // Log memory after loading PDFs
        optimizer.logMemoryUsage("AFTER loading PDFs for pair " + pairIndex);

        try {
            // Create directory for comparison results
            Path comparisonDir = Paths.get("uploads", "comparisons", comparison.getComparisonId());
            Files.createDirectories(comparisonDir);

            // Calculate relative page count (1-based for API)
            int basePageCount = baseEndPage - baseStartPage + 1;
            int comparePageCount = compareEndPage - compareStartPage + 1;

            // Map relative page numbers to absolute page numbers
            for (int relPage = 1; relPage <= Math.max(basePageCount, comparePageCount); relPage++) {
                int basePageIdx = baseStartPage - 1 + (relPage - 1);
                int comparePageIdx = compareStartPage - 1 + (relPage - 1);

                // Check if page exists in both documents
                boolean pageInBase = basePageIdx >= baseStartPage - 1 && basePageIdx <= baseEndPage - 1 &&
                        basePageIdx < basePdf.getNumberOfPages();
                boolean pageInCompare = comparePageIdx >= compareStartPage - 1 && comparePageIdx <= compareEndPage - 1 &&
                        comparePageIdx < comparePdf.getNumberOfPages();

                // Skip if page doesn't exist in either document
                if (!pageInBase && !pageInCompare) {
                    continue;
                }

                List<Difference> baseDifferences = new ArrayList<>();
                List<Difference> compareDifferences = new ArrayList<>();

                // Extract and compare text content
                String baseText = pageInBase ? textExtractor.extractTextFromPage(basePdf, basePageIdx) : "";
                String compareText = pageInCompare ? textExtractor.extractTextFromPage(comparePdf, comparePageIdx) : "";

                List<TextDifference> textDiffs = differenceCalculator.compareText(
                        baseText, compareText, comparison.getTextComparisonMethod());

                // Get page dimensions for coordinate assignment
                PDRectangle baseSize = pageInBase ? basePdf.getPage(basePageIdx).getMediaBox() : null;
                PDRectangle compareSize = pageInCompare ? comparePdf.getPage(comparePageIdx).getMediaBox() : null;

                // Ensure all text differences have coordinates
                if (pageInBase) {
                    ensureTextDifferencesHaveCoordinates(textDiffs, baseText, baseSize.getWidth(), baseSize.getHeight(),
                            basePdf, basePageIdx);
                } else if (pageInCompare) {
                    ensureTextDifferencesHaveCoordinates(textDiffs, compareText, compareSize.getWidth(), compareSize.getHeight(),
                            comparePdf, comparePageIdx);
                }

                // Split differences by source document
                List<Difference> baseTextDiffs = textDiffs.stream()
                        .filter(diff -> !"added".equals(diff.getChangeType()))
                        .collect(Collectors.toList());

                List<Difference> compareTextDiffs = textDiffs.stream()
                        .filter(diff -> !"deleted".equals(diff.getChangeType()))
                        .collect(Collectors.toList());

                baseDifferences.addAll(baseTextDiffs);
                compareDifferences.addAll(compareTextDiffs);

                // Process other difference types if both pages exist
                if (pageInBase && pageInCompare) {
                    // Delegate to specialized services for each difference type
                    processPairPageWithBothDocuments(
                            basePdf, comparePdf, basePageIdx, comparePageIdx,
                            baseDocument, compareDocument, comparisonDir,
                            baseDifferences, compareDifferences);
                }

                // Create detailed page analysis for the API to serve
                createPairPageDetails(
                        comparison.getComparisonId(),
                        pairIndex,
                        relPage, // Relative 1-based page number within the pair
                        baseDocument,
                        compareDocument,
                        baseText,
                        compareText,
                        baseDifferences,
                        compareDifferences,
                        baseSize,
                        compareSize,
                        pageInBase,
                        pageInCompare);
            }
        } finally {
            // Close PDFs
            basePdf.close();
            comparePdf.close();

            // Log memory after processing
            optimizer.logMemoryUsage("END of comparePages for pair " + pairIndex);

            // Force garbage collection to free memory
            optimizer.checkAndOptimizeMemory();
        }
    }

    /**
     * Process a pair page when both documents exist.
     *
     * @param basePdf            Base PDF document
     * @param comparePdf         Compare PDF document
     * @param basePageIdx        Base page index
     * @param comparePageIdx     Compare page index
     * @param baseDocument       Base document info
     * @param compareDocument    Compare document info
     * @param comparisonDir      Directory for comparison output
     * @param baseDifferences    List to add base differences to
     * @param compareDifferences List to add compare differences to
     * @throws IOException If there's an error processing the pages
     */
    private void processPairPageWithBothDocuments(
            PDDocument basePdf, PDDocument comparePdf,
            int basePageIdx, int comparePageIdx,
            PdfDocument baseDocument, PdfDocument compareDocument,
            Path comparisonDir,
            List<Difference> baseDifferences,
            List<Difference> compareDifferences) throws IOException {

        // Compare text elements
        List<guraa.pdfcompare.util.TextElement> baseElements =
                textExtractor.extractTextElementsFromPage(basePdf, basePageIdx);
        List<guraa.pdfcompare.util.TextElement> compareElements =
                textExtractor.extractTextElementsFromPage(comparePdf, comparePageIdx);

        List<Difference> textElementDiffs = TextElementComparisonService.compareTextElements(
                baseElements, compareElements, differenceCalculator);

        // Split element differences by source document
        List<Difference> baseElementDiffs = textElementDiffs.stream()
                .filter(diff -> !"added".equals(diff.getChangeType()))
                .collect(Collectors.toList());

        List<Difference> compareElementDiffs = textElementDiffs.stream()
                .filter(diff -> !"deleted".equals(diff.getChangeType()))
                .collect(Collectors.toList());

        baseDifferences.addAll(baseElementDiffs);
        compareDifferences.addAll(compareElementDiffs);

        // Compare images
        List<Difference> imageDiffs = imageComparisonService.compareImagesOnPage(
                basePdf, comparePdf, basePageIdx, comparePageIdx, comparisonDir);

        // Split image differences by source document
        List<Difference> baseImageDiffs = imageDiffs.stream()
                .filter(diff -> !"added".equals(diff.getChangeType()))
                .collect(Collectors.toList());

        List<Difference> compareImageDiffs = imageDiffs.stream()
                .filter(diff -> !"deleted".equals(diff.getChangeType()))
                .collect(Collectors.toList());

        baseDifferences.addAll(baseImageDiffs);
        compareDifferences.addAll(compareImageDiffs);

        // Compare fonts
        List<Difference> fontDiffs = fontComparisonService.compareFontsOnPage(
                basePdf, comparePdf, basePageIdx, comparePageIdx,
                baseDocument, compareDocument, comparisonDir);

        // Split font differences by source document
        List<Difference> baseFontDiffs = fontDiffs.stream()
                .filter(diff -> !"added".equals(diff.getChangeType()))
                .collect(Collectors.toList());

        List<Difference> compareFontDiffs = fontDiffs.stream()
                .filter(diff -> !"deleted".equals(diff.getChangeType()))
                .collect(Collectors.toList());

        baseDifferences.addAll(baseFontDiffs);
        compareDifferences.addAll(compareFontDiffs);
    }

    /**
     * Create detailed page analysis for a document pair in smart comparison mode.
     * Ensures all differences include coordinate information.
     *
     * @param comparisonId        The comparison ID
     * @param pairIndex           The pair index
     * @param relPageNumber       The relative page number (1-based) within the pair
     * @param baseDocument        The base document
     * @param compareDocument     The comparison document
     * @param baseText            Text content from base page
     * @param compareText         Text content from comparison page
     * @param baseDifferences     Differences in base document
     * @param compareDifferences  Differences in comparison document
     * @param baseSize            Base page dimensions
     * @param compareSize         Comparison page dimensions
     * @param pageExistsInBase    Whether the page exists in base document
     * @param pageExistsInCompare Whether the page exists in comparison document
     * @throws IOException If there's an error saving the page details
     */
    private void createPairPageDetails(
            String comparisonId,
            int pairIndex,
            int relPageNumber,
            PdfDocument baseDocument,
            PdfDocument compareDocument,
            String baseText,
            String compareText,
            List<Difference> baseDifferences,
            List<Difference> compareDifferences,
            PDRectangle baseSize,
            PDRectangle compareSize,
            boolean pageExistsInBase,
            boolean pageExistsInCompare) throws IOException {

        PageDetails pageDetails = new PageDetails();
        pageDetails.setPageNumber(relPageNumber);
        pageDetails.setPageId(UUID.randomUUID().toString());

        // Store page dimensions
        if (baseSize != null) {
            pageDetails.setBaseWidth(baseSize.getWidth());
            pageDetails.setBaseHeight(baseSize.getHeight());
        }

        if (compareSize != null) {
            pageDetails.setCompareWidth(compareSize.getWidth());
            pageDetails.setCompareHeight(compareSize.getHeight());
        }

        // Store page existence flags
        pageDetails.setPageExistsInBase(pageExistsInBase);
        pageDetails.setPageExistsInCompare(pageExistsInCompare);

        // Store extracted text
        pageDetails.setBaseExtractedText(baseText);
        pageDetails.setCompareExtractedText(compareText);

        // Ensure all differences have coordinates before storing them
        if (baseSize != null && pageExistsInBase) {
            ensureCoordinatesForDifferences(baseDifferences, baseSize.getWidth(), baseSize.getHeight(), 0.3);
        } else if (compareSize != null && pageExistsInCompare) {
            ensureCoordinatesForDifferences(baseDifferences, compareSize.getWidth(), compareSize.getHeight(), 0.3);
        }

        if (compareSize != null && pageExistsInCompare) {
            ensureCoordinatesForDifferences(compareDifferences, compareSize.getWidth(), compareSize.getHeight(), 0.3);
        } else if (baseSize != null && pageExistsInBase) {
            ensureCoordinatesForDifferences(compareDifferences, baseSize.getWidth(), baseSize.getHeight(), 0.3);
        }

        // Store differences
        pageDetails.setBaseDifferences(baseDifferences);
        pageDetails.setCompareDifferences(compareDifferences);

        // Set difference counts
        pageDetails.setTextDifferenceCount((int) baseDifferences.stream()
                .filter(diff -> "text".equals(diff.getType())).count());
        pageDetails.setImageDifferenceCount((int) baseDifferences.stream()
                .filter(diff -> "image".equals(diff.getType())).count());
        pageDetails.setFontDifferenceCount((int) baseDifferences.stream()
                .filter(diff -> "font".equals(diff.getType())).count());
        pageDetails.setStyleDifferenceCount((int) baseDifferences.stream()
                .filter(diff -> "style".equals(diff.getType())).count());

        // Set rendered page image paths if the pages exist
        if (pageExistsInBase) {
            String baseImagePath = String.format("/api/pdfs/document/%s/page/%d",
                    baseDocument.getFileId(), relPageNumber);
            pageDetails.setBaseRenderedImagePath(baseImagePath);
        }

        if (pageExistsInCompare) {
            String compareImagePath = String.format("/api/pdfs/document/%s/page/%d",
                    compareDocument.getFileId(), relPageNumber);
            pageDetails.setCompareRenderedImagePath(compareImagePath);
        }

        // Save page details to a file
        Path detailsPath = Paths.get("uploads", "comparisons", comparisonId,
                "pair_" + pairIndex + "_page_" + relPageNumber + "_details.json");
        objectMapper.writeValue(detailsPath.toFile(), pageDetails);
    }
}