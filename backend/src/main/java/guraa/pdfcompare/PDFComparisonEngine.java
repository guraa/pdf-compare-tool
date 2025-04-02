package guraa.pdfcompare;

import guraa.pdfcompare.comparison.*;
import guraa.pdfcompare.core.*;

import guraa.pdfcompare.util.PDFComparisonUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

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
        result.setMetadataDifferences(compareMetadata(baseDocument, compareDocument));

        // Compare document structure
        DocumentStructureAnalyzer structureAnalyzer = new DocumentStructureAnalyzer();
        result.setStructureDifferences(structureAnalyzer.compareDocumentStructure(baseDocument, compareDocument));

        // Compare each page
        List<PageComparisonResult> pageDifferences = new ArrayList<>();
        if (baseDocument.getPages() != null && compareDocument.getPages() != null) {
            int maxPages = Math.max(baseDocument.getPages().size(), compareDocument.getPages().size());
            for (int i = 0; i < maxPages; i++) {
                PDFPageModel basePage = i < baseDocument.getPages().size() ? baseDocument.getPages().get(i) : null;
                PDFPageModel comparePage = i < compareDocument.getPages().size() ? compareDocument.get(i) : null;
                pageDifferences.add(comparePage(basePage, comparePage));
            }
        }
        result.setPageDifferences(pageDifferences);

        // Calculate total differences
        int totalDifferences = 0;
        for (PageComparisonResult pageResult : pageDifferences) {
            totalDifferences += pageResult.getTotalDifferences();
        }
        result.setTotalDifferences(totalDifferences);

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

        if (basePage == null || comparePage == null) {
            // One of the pages is null, mark as different
            result.setHasDifferences(true);
            result.setTotalDifferences(1); // Consider a missing page as 1 difference
            return result;
        }

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

        // Calculate total differences for this page
        int totalDiffs = 0;
        if (result.getTextDifferences() != null) {
            totalDiffs += result.getTextDifferences().getDifferences().size();
        }
        if (result.getTextElementDifferences() != null) {
            totalDiffs += result.getTextElementDifferences().size();
        }
        if (result.getImageDifferences() != null) {
            totalDiffs += result.getImageDifferences().size();
        }
        if (result.getFontDifferences() != null) {
            totalDiffs += result.getFontDifferences().size();
        }
        result.setTotalDifferences(totalDiffs);

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
        result.setDifferences(new ArrayList<>());

        if (basePage.getText() == null && comparePage.getText() == null) {
            return result; // Both texts are null, return empty result
        }

        String baseText = basePage.getText() != null ? basePage.getText() : "";
        String compareText = comparePage.getText() != null ? comparePage.getText() : "";

        // Calculate Levenshtein distance
        int levenshteinDistance = PDFComparisonUtility.calculateLevenshteinDistance(baseText, compareText);
        result.setLevenshteinDistance(levenshteinDistance);

        // Simple text difference detection (can be improved)
        if (!baseText.equals(compareText)) {
            TextDifferenceItem diff = new TextDifferenceItem();
            diff.setType(TextDifferenceType.CONTENT_CHANGE);
            diff.setBaseText(baseText);
            diff.setCompareText(compareText);
            result.getDifferences().add(diff);
        }

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
            TextElementDifference diff = new TextElementDifference();

            diff.setTextDifferent(!baseElement.getText().equals(compareElement.getText()));
            diff.setPositionDifferent(baseElement.getX() != compareElement.getX() || baseElement.getY() != compareElement.getY());
            diff.setWidthDifferent(baseElement.getWidth() != compareElement.getWidth());
            diff.setHeightDifferent(baseElement.getHeight() != compareElement.getHeight());
            diff.setFontDifferent(baseElement.getFontName().equals(compareElement.getFontName()));
            diff.setFontSizeDifferent(baseElement.getFontSize() != compareElement.getFontSize());
            diff.setFontStyleDifferent(baseElement.getFontStyle().equals(compareElement.getFontStyle()));

            // Enhanced Style Comparison
            diff.setStyleDifferent(isStyleDifferent(baseElement, compareElement));

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

        // Simple comparison (can be improved with image hashing or other techniques)
        if (baseImages.size() != compareImages.size()) {
            ImageDifference diff = new ImageDifference();
            diff.setDifferenceType("Image count difference");
            diff.setBaseImageCount(baseImages.size());
            diff.setCompareImageCount(compareImages.size());
            differences.add(diff);
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

        // Simple comparison (can be improved with font properties comparison)
        if (baseFonts.size() != compareFonts.size()) {
            FontDifference diff = new FontDifference();
            diff.setDifferenceType("Font count difference");
            diff.setBaseFontCount(baseFonts.size());
            diff.setCompareFontCount(compareFonts.size());
            differences.add(diff);
        }

        return differences;
    }

    /**
     * Compare metadata of two documents
     *
     * @param baseDocument    The base PDF document model
     * @param compareDocument The PDF document model to compare against the base
     * @return List<MetadataDifference> The list of metadata differences
     */
    private List<MetadataDifference> compareMetadata(PDFDocumentModel baseDocument, PDFDocumentModel compareDocument) {
        List<MetadataDifference> differences = new ArrayList<>();

        // Placeholder for metadata comparison logic
        // This is a simplified implementation. A real-world implementation would
        // compare individual metadata fields (author, title, etc.) and identify differences.

        return differences;
    }
}