package guraa.pdfcompare;

import guraa.pdfcompare.comparison.*;
import guraa.pdfcompare.core.*;

import guraa.pdfcompare.util.PDFComparisonUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PDFComparisonEngine {

    private static final Logger logger = LoggerFactory.getLogger(PDFComparisonEngine.class);

    /**
     * Compare two PDF documents and return the comparison result
     *
     * @param baseDocument    The base PDF document model
     * @param compareDocument The PDF document model to compare against the base
     * @return PDFComparisonResult  The result of the comparison
     */
    public PDFComparisonResult compareDocuments(PDFDocumentModel baseDocument, PDFDocumentModel compareDocument) {
        PDFComparisonResult result = new PDFComparisonResult();

        // Compare document metadata
        Map<String, MetadataDifference> metadataDiffs = compareMetadata(baseDocument.getMetadata(), compareDocument.getMetadata());
        result.setMetadataDifferences(metadataDiffs);

        // Compare each page
        List<PageComparisonResult> pageDifferences = new ArrayList<>();
        if (baseDocument.getPages() != null && compareDocument.getPages() != null) {
            int maxPages = Math.max(baseDocument.getPages().size(), compareDocument.getPages().size());
            for (int i = 0; i < maxPages; i++) {
                PDFPageModel basePage = i < baseDocument.getPages().size() ? baseDocument.getPages().get(i) : null;
                PDFPageModel comparePage = i < compareDocument.getPages().size() ? compareDocument.getPages().get(i) : null;
                pageDifferences.add(comparePage(basePage, comparePage));
            }
        }
        result.setPageDifferences(pageDifferences);

        // Calculate total differences
        int totalDifferences = 0;
        int totalTextDifferences = 0;
        int totalImageDifferences = 0;
        int totalFontDifferences = 0;
        int totalStyleDifferences = 0;
        int totalStructuralDifferences = 0;

        for (PageComparisonResult pageResult : pageDifferences) {
            // Count text differences
            if (pageResult.getTextDifferences() != null && pageResult.getTextDifferences().getDifferences() != null) {
                totalTextDifferences += pageResult.getTextDifferences().getDifferences().size();
            }

            // Count text element differences
            if (pageResult.getTextElementDifferences() != null) {
                totalStyleDifferences += pageResult.getTextElementDifferences().size();
            }

            // Count image differences
            if (pageResult.getImageDifferences() != null) {
                totalImageDifferences += pageResult.getImageDifferences().size();
            }

            // Count font differences
            if (pageResult.getFontDifferences() != null) {
                totalFontDifferences += pageResult.getFontDifferences().size();
            }
            
            // Count structural differences (pages that only exist in one document)
            if (pageResult.isOnlyInBase() || pageResult.isOnlyInCompare()) {
                totalStructuralDifferences++;
            }
            
            // If the page exists in both documents but has no specific differences detected,
            // and the documents are clearly different (e.g., completely different content),
            // count it as a text difference
            if (!pageResult.isOnlyInBase() && !pageResult.isOnlyInCompare() && 
                pageResult.getTextDifferences() != null &&
                (pageResult.getTextDifferences().getDifferences() == null || pageResult.getTextDifferences().getDifferences().isEmpty()) &&
                (pageResult.getTextElementDifferences() == null || pageResult.getTextElementDifferences().isEmpty()) &&
                (pageResult.getImageDifferences() == null || pageResult.getImageDifferences().isEmpty()) &&
                (pageResult.getFontDifferences() == null || pageResult.getFontDifferences().isEmpty())) {
                
                // Check if the page text is completely different
                String baseText = null;
                String compareText = null;
                
                if (pageResult.getTextDifferences() != null) {
                    baseText = pageResult.getTextDifferences().getBaseText();
                    compareText = pageResult.getTextDifferences().getCompareText();
                }
                
                if ((baseText != null && !baseText.isEmpty()) && 
                    (compareText != null && !compareText.isEmpty()) &&
                    !baseText.equals(compareText)) {
                    // The pages have different text but no specific differences were detected
                    // This is likely a case where the documents are completely different
                    
                    // Create a new TextDifference and add it to the TextComparisonResult
                    TextDifference textDiff = new TextDifference();
                    textDiff.setBaseText(baseText);
                    textDiff.setCompareText(compareText);
                    textDiff.setBaseStartIndex(0);
                    textDiff.setBaseEndIndex(baseText.length());
                    textDiff.setCompareStartIndex(0);
                    textDiff.setCompareEndIndex(compareText.length());
                    textDiff.setDifferenceType("MODIFICATION");
                    
                    if (pageResult.getTextDifferences().getDifferences() == null) {
                        pageResult.getTextDifferences().setDifferences(new ArrayList<>());
                    }
                    
                    pageResult.getTextDifferences().getDifferences().add(textDiff);
                    totalTextDifferences++; // Count as a text difference
                }
            }
        }

        totalDifferences = totalTextDifferences + totalStyleDifferences + totalImageDifferences + totalFontDifferences + totalStructuralDifferences;

        result.setTotalDifferences(totalDifferences);
        result.setTotalTextDifferences(totalTextDifferences);
        result.setTotalImageDifferences(totalImageDifferences);
        result.setTotalFontDifferences(totalFontDifferences);
        result.setTotalStyleDifferences(totalStyleDifferences);

        // Set page counts
        result.setBasePageCount(baseDocument.getPageCount());
        result.setComparePageCount(compareDocument.getPageCount());
        result.setPageCountDifferent(baseDocument.getPageCount() != compareDocument.getPageCount());

        return result;
    }

    /**
     * Compare two PDF pages and return the comparison result
     *
     * @param basePage    The base PDF page model
     * @param comparePage The PDF page model to compare against the base
     * @return PageComparisonResult The result of the page comparison
     */
    public PageComparisonResult comparePage(PDFPageModel basePage, PDFPageModel comparePage) {
        PageComparisonResult result = new PageComparisonResult();

        if (basePage == null && comparePage == null) {
            return result; // Both pages are null, return empty result
        }

        if (basePage == null) {
            // Base page is null
            result.setOnlyInCompare(true);
            result.setPageNumber(comparePage.getPageNumber());
            return result;
        }

        if (comparePage == null) {
            // Compare page is null
            result.setOnlyInBase(true);
            result.setPageNumber(basePage.getPageNumber());
            return result;
        }

        // Set page number
        result.setPageNumber(basePage.getPageNumber());

        // Compare page dimensions
        result.setDimensionsDifferent(basePage.getWidth() != comparePage.getWidth() ||
                basePage.getHeight() != comparePage.getHeight());
        result.setBaseDimensions(new float[]{basePage.getWidth(), basePage.getHeight()});
        result.setCompareDimensions(new float[]{comparePage.getWidth(), comparePage.getHeight()});

        // Compare page text
        TextComparisonResult textComparisonResult = compareText(basePage, comparePage);
        result.setTextDifferences(textComparisonResult);

        // Compare text elements (including style)
        List<TextElementDifference> textElementDifferences = compareTextElements(basePage, comparePage);
        result.setTextElementDifferences(textElementDifferences);

        // Compare images
        List<ImageDifference> imageDifferences = compareImages(basePage, comparePage);
        result.setImageDifferences(imageDifferences);

        // Compare fonts
        List<FontDifference> fontDifferences = compareFonts(basePage, comparePage);
        result.setFontDifferences(fontDifferences);

        return result;
    }

    /**
     * Compare text content of two pages
     *
     * @param basePage    The base PDF page model
     * @param comparePage The PDF page model to compare against the base
     * @return TextComparisonResult The result of the text comparison
     */
    private TextComparisonResult compareText(PDFPageModel basePage, PDFPageModel comparePage) {
        TextComparisonResult result = new TextComparisonResult();
        List<TextDifferenceItem> differenceItems = new ArrayList<>();
        result.setDifferenceItems(differenceItems);

        if (basePage.getText() == null && comparePage.getText() == null) {
            return result; // Both texts are null, return empty result
        }

        String baseText = basePage.getText() != null ? basePage.getText() : "";
        String compareText = comparePage.getText() != null ? comparePage.getText() : "";

        // Calculate Levenshtein distance
        int levenshteinDistance = PDFComparisonUtility.calculateLevenshteinDistance(baseText, compareText);

        // Simple text difference detection (can be improved)
        if (!baseText.equals(compareText)) {
            TextDifferenceItem diff = new TextDifferenceItem();
            diff.setLineNumber(1); // Default to first line
            diff.setBaseText(baseText);
            diff.setCompareText(compareText);
            diff.setDifferenceType(TextDifferenceType.MODIFIED);
            differenceItems.add(diff);
            
            // Also create a TextDifference for compatibility
            TextDifference textDiff = new TextDifference();
            textDiff.setBaseText(baseText);
            textDiff.setCompareText(compareText);
            textDiff.setBaseStartIndex(0);
            textDiff.setBaseEndIndex(baseText.length());
            textDiff.setCompareStartIndex(0);
            textDiff.setCompareEndIndex(compareText.length());
            textDiff.setDifferenceType("MODIFICATION");
            result.addDifference(textDiff);
        }

        result.setDifferenceCount(differenceItems.size());
        return result;
    }

    /**
     * Compare text elements (including style) of two pages
     *
     * @param basePage    The base PDF page model
     * @param comparePage The PDF page model to compare against the base
     * @return List<TextElementDifference> The list of text element differences
     */
    private List<TextElementDifference> compareTextElements(PDFPageModel basePage, PDFPageModel comparePage) {
        List<TextElementDifference> differences = new ArrayList<>();

        if (basePage.getTextElements() == null && comparePage.getTextElements() == null) {
            return differences; // Both are null, return empty list
        }

        List<TextElement> baseElements = basePage.getTextElements() != null ? basePage.getTextElements() : new ArrayList<>();
        List<TextElement> compareElements = comparePage.getTextElements() != null ? comparePage.getTextElements() : new ArrayList<>();

        // Simple comparison (can be improved with more sophisticated matching)
        int minElements = Math.min(baseElements.size(), compareElements.size());
        for (int i = 0; i < minElements; i++) {
            TextElement baseElement = baseElements.get(i);
            TextElement compareElement = compareElements.get(i);

            // Check for differences
            boolean textDifferent = !baseElement.getText().equals(compareElement.getText());
            boolean positionDifferent = baseElement.getX() != compareElement.getX() || baseElement.getY() != compareElement.getY();
            boolean widthDifferent = baseElement.getWidth() != compareElement.getWidth();
            boolean heightDifferent = baseElement.getHeight() != compareElement.getHeight();
            boolean fontNameDifferent = !baseElement.getFontName().equals(compareElement.getFontName());
            boolean fontSizeDifferent = baseElement.getFontSize() != compareElement.getFontSize();
            boolean fontStyleDifferent = !baseElement.getFontStyle().equals(compareElement.getFontStyle());

            // Enhanced Style Comparison
            boolean styleDifferent = isStyleDifferent(baseElement, compareElement);

            if (textDifferent || positionDifferent || widthDifferent || heightDifferent ||
                    fontNameDifferent || fontSizeDifferent || fontStyleDifferent || styleDifferent) {

                TextElementDifference diff = new TextElementDifference();
                diff.setBaseElement(baseElement);
                diff.setCompareElement(compareElement);
                diff.setStyleDifferent(styleDifferent);
                differences.add(diff);
            }
        }

        // Add elements that only exist in the base document
        for (int i = minElements; i < baseElements.size(); i++) {
            TextElementDifference diff = new TextElementDifference();
            diff.setBaseElement(baseElements.get(i));
            diff.setOnlyInBase(true);
            differences.add(diff);
        }

        // Add elements that only exist in the compare document
        for (int i = minElements; i < compareElements.size(); i++) {
            TextElementDifference diff = new TextElementDifference();
            diff.setCompareElement(compareElements.get(i));
            diff.setOnlyInCompare(true);
            differences.add(diff);
        }

        return differences;
    }

    /**
     * Enhanced Style Comparison Logic
     *
     * @param baseElement    The base text element
     * @param compareElement The text element to compare against the base
     * @return boolean  True if style is different, false otherwise
     */
    private boolean isStyleDifferent(TextElement baseElement, TextElement compareElement) {
        boolean styleDifferent = false;

        // Check font weight (bold)
        boolean fontWeightDifferent = !baseElement.getFontStyle().equals(compareElement.getFontStyle());

        // Check font style (italic)
        boolean fontStyleDifferent = !baseElement.getFontStyle().equals(compareElement.getFontStyle());

        // Check font size
        boolean fontSizeDifferent = baseElement.getFontSize() != compareElement.getFontSize();

        // Heuristics to detect significant style changes
        if (fontWeightDifferent || fontStyleDifferent || fontSizeDifferent) {
            styleDifferent = true; // Consider any of these differences as style change
        }

        return styleDifferent;
    }

    /**
     * Compare images of two pages
     *
     * @param basePage    The base PDF page model
     * @param comparePage The PDF page model to compare against the base
     * @return List<ImageDifference> The list of image differences
     */
    private List<ImageDifference> compareImages(PDFPageModel basePage, PDFPageModel comparePage) {
        List<ImageDifference> differences = new ArrayList<>();

        if (basePage.getImages() == null && comparePage.getImages() == null) {
            return differences; // Both are null, return empty list
        }

        List<ImageElement> baseImages = basePage.getImages() != null ? basePage.getImages() : new ArrayList<>();
        List<ImageElement> compareImages = comparePage.getImages() != null ? comparePage.getImages() : new ArrayList<>();

        // Check for difference in number of images
        if (baseImages.size() != compareImages.size()) {
            // For each image in the base document not matched in compare
            for (int i = compareImages.size(); i < baseImages.size(); i++) {
                ImageDifference diff = new ImageDifference();
                diff.setBaseImage(baseImages.get(i));
                diff.setOnlyInBase(true);
                differences.add(diff);
            }

            // For each image in the compare document not matched in base
            for (int i = baseImages.size(); i < compareImages.size(); i++) {
                ImageDifference diff = new ImageDifference();
                diff.setCompareImage(compareImages.get(i));
                diff.setOnlyInCompare(true);
                differences.add(diff);
            }
        }

        // Compare matching images
        int minImages = Math.min(baseImages.size(), compareImages.size());
        for (int i = 0; i < minImages; i++) {
            ImageElement baseImage = baseImages.get(i);
            ImageElement compareImage = compareImages.get(i);

            boolean dimensionsDifferent = baseImage.getWidth() != compareImage.getWidth() ||
                    baseImage.getHeight() != compareImage.getHeight();
            boolean positionDifferent = baseImage.getX() != compareImage.getX() ||
                    baseImage.getY() != compareImage.getY();
            boolean formatDifferent = !baseImage.getFormat().equals(compareImage.getFormat());

            if (dimensionsDifferent || positionDifferent || formatDifferent) {
                ImageDifference diff = new ImageDifference();
                diff.setBaseImage(baseImage);
                diff.setCompareImage(compareImage);
                diff.setDimensionsDifferent(dimensionsDifferent);
                diff.setPositionDifferent(positionDifferent);
                diff.setFormatDifferent(formatDifferent);
                differences.add(diff);
            }
        }

        return differences;
    }

    /**
     * Compare fonts of two pages
     *
     * @param basePage    The base PDF page model
     * @param comparePage The PDF page model to compare against the base
     * @return List<FontDifference> The list of font differences
     */
    private List<FontDifference> compareFonts(PDFPageModel basePage, PDFPageModel comparePage) {
        List<FontDifference> differences = new ArrayList<>();

        if (basePage.getFonts() == null && comparePage.getFonts() == null) {
            return differences; // Both are null, return empty list
        }

        List<FontInfo> baseFonts = basePage.getFonts() != null ? basePage.getFonts() : new ArrayList<>();
        List<FontInfo> compareFonts = comparePage.getFonts() != null ? comparePage.getFonts() : new ArrayList<>();

        // Create a map of font names to font info for easier lookup
        Map<String, FontInfo> baseMap = new HashMap<>();
        for (FontInfo font : baseFonts) {
            baseMap.put(font.getName(), font);
        }

        Map<String, FontInfo> compareMap = new HashMap<>();
        for (FontInfo font : compareFonts) {
            compareMap.put(font.getName(), font);
        }

        // Find fonts in base not in compare
        for (FontInfo baseFont : baseFonts) {
            if (!compareMap.containsKey(baseFont.getName())) {
                FontDifference diff = new FontDifference();
                diff.setBaseFont(baseFont);
                diff.setOnlyInBase(true);
                differences.add(diff);
            }
        }

        // Find fonts in compare not in base
        for (FontInfo compareFont : compareFonts) {
            if (!baseMap.containsKey(compareFont.getName())) {
                FontDifference diff = new FontDifference();
                diff.setCompareFont(compareFont);
                diff.setOnlyInCompare(true);
                differences.add(diff);
            }
        }

        // Compare fonts that exist in both documents
        for (String fontName : baseMap.keySet()) {
            if (compareMap.containsKey(fontName)) {
                FontInfo baseFont = baseMap.get(fontName);
                FontInfo compareFont = compareMap.get(fontName);

                boolean embeddingDifferent = baseFont.isEmbedded() != compareFont.isEmbedded();
                boolean subsetDifferent = baseFont.isSubset() != compareFont.isSubset();

                if (embeddingDifferent || subsetDifferent) {
                    FontDifference diff = new FontDifference();
                    diff.setBaseFont(baseFont);
                    diff.setCompareFont(compareFont);
                    diff.setEmbeddingDifferent(embeddingDifferent);
                    diff.setSubsetDifferent(subsetDifferent);
                    differences.add(diff);
                }
            }
        }

        return differences;
    }

    /**
     * Compare metadata of two documents
     *
     * @param baseMetadata    The base document metadata
     * @param compareMetadata The compare document metadata
     * @return Map<String, MetadataDifference> Map of metadata key to difference
     */
    public Map<String, MetadataDifference> compareMetadata(Map<String, String> baseMetadata, Map<String, String> compareMetadata) {
        Map<String, MetadataDifference> differences = new HashMap<>();

        // Handle null metadata
        if (baseMetadata == null) baseMetadata = new HashMap<>();
        if (compareMetadata == null) compareMetadata = new HashMap<>();

        // Check for keys in base not in compare
        for (String key : baseMetadata.keySet()) {
            if (!compareMetadata.containsKey(key)) {
                MetadataDifference diff = new MetadataDifference();
                diff.setKey(key);
                diff.setBaseValue(baseMetadata.get(key));
                diff.setOnlyInBase(true);
                differences.put(key, diff);
            }
        }

        // Check for keys in compare not in base
        for (String key : compareMetadata.keySet()) {
            if (!baseMetadata.containsKey(key)) {
                MetadataDifference diff = new MetadataDifference();
                diff.setKey(key);
                diff.setCompareValue(compareMetadata.get(key));
                diff.setOnlyInCompare(true);
                differences.put(key, diff);
            }
        }

        // Check for differences in values for keys in both documents
        for (String key : baseMetadata.keySet()) {
            if (compareMetadata.containsKey(key)) {
                String baseValue = baseMetadata.get(key);
                String compareValue = compareMetadata.get(key);

                if (!baseValue.equals(compareValue)) {
                    MetadataDifference diff = new MetadataDifference();
                    diff.setKey(key);
                    diff.setBaseValue(baseValue);
                    diff.setCompareValue(compareValue);
                    diff.setValueDifferent(true);
                    differences.put(key, diff);
                }
            }
        }

        return differences;
    }
}
