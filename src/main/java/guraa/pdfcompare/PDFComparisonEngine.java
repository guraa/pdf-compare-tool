package guraa.pdfcompare;

import guraa.pdfcompare.comparison.*;
import guraa.pdfcompare.core.ImageElement;
import guraa.pdfcompare.core.PDFDocumentModel;
import guraa.pdfcompare.core.PDFPageModel;
import guraa.pdfcompare.core.TextElement;

import java.util.*;

/**
 * Main class for comparing two PDF documents
 */
public class PDFComparisonEngine {

    /**
     * Compare two PDF document models and generate comparison results
     * @param baseDocument The base document model
     * @param compareDocument The document to compare against the base
     * @return A comparison result containing all differences
     */
    public PDFComparisonResult compareDocuments(PDFDocumentModel baseDocument, PDFDocumentModel compareDocument) {
        PDFComparisonResult result = new PDFComparisonResult();

        // Compare metadata
        Map<String, MetadataDifference> metadataDiffs = compareMetadata(baseDocument.getMetadata(), compareDocument.getMetadata());
        result.setMetadataDifferences(metadataDiffs);

        // Compare page count
        boolean pageCountDiff = baseDocument.getPageCount() != compareDocument.getPageCount();
        result.setPageCountDifferent(pageCountDiff);
        result.setBasePageCount(baseDocument.getPageCount());
        result.setComparePageCount(compareDocument.getPageCount());

        // Compare pages
        List<PageComparisonResult> pageDiffs = new ArrayList<>();
        int maxPages = Math.max(baseDocument.getPageCount(), compareDocument.getPageCount());

        for (int i = 0; i < maxPages; i++) {
            PageComparisonResult pageResult;

            if (i < baseDocument.getPageCount() && i < compareDocument.getPageCount()) {
                // Compare existing pages
                pageResult = comparePage(baseDocument.getPages().get(i), compareDocument.getPages().get(i));
            } else if (i < baseDocument.getPageCount()) {
                // Page exists only in base document
                pageResult = new PageComparisonResult();
                pageResult.setPageNumber(i + 1);
                pageResult.setOnlyInBase(true);
            } else {
                // Page exists only in compare document
                pageResult = new PageComparisonResult();
                pageResult.setPageNumber(i + 1);
                pageResult.setOnlyInCompare(true);
            }

            pageDiffs.add(pageResult);
        }

        result.setPageDifferences(pageDiffs);

        // Calculate summary statistics
        calculateSummaryStatistics(result);

        return result;
    }

    /**
     * Compare metadata between two documents
     * @param baseMetadata The base document metadata
     * @param compareMetadata The comparison document metadata
     * @return Map of metadata differences
     */
    private Map<String, MetadataDifference> compareMetadata(Map<String, String> baseMetadata, Map<String, String> compareMetadata) {
        Map<String, MetadataDifference> differences = new HashMap<>();

        // Check for all keys in base document
        for (String key : baseMetadata.keySet()) {
            String baseValue = baseMetadata.get(key);
            String compareValue = compareMetadata.get(key);

            if (compareValue == null) {
                // Key exists only in base document
                MetadataDifference diff = new MetadataDifference();
                diff.setKey(key);
                diff.setBaseValue(baseValue);
                diff.setCompareValue(null);
                diff.setOnlyInBase(true);
                differences.put(key, diff);
            } else if (!baseValue.equals(compareValue)) {
                // Key exists in both but values differ
                MetadataDifference diff = new MetadataDifference();
                diff.setKey(key);
                diff.setBaseValue(baseValue);
                diff.setCompareValue(compareValue);
                diff.setValueDifferent(true);
                differences.put(key, diff);
            }
        }

        // Check for keys only in compare document
        for (String key : compareMetadata.keySet()) {
            if (!baseMetadata.containsKey(key)) {
                // Key exists only in compare document
                MetadataDifference diff = new MetadataDifference();
                diff.setKey(key);
                diff.setBaseValue(null);
                diff.setCompareValue(compareMetadata.get(key));
                diff.setOnlyInCompare(true);
                differences.put(key, diff);
            }
        }

        return differences;
    }

    /**
     * Compare two pages and identify differences
     * @param basePage The base page model
     * @param comparePage The page to compare against the base
     * @return A page comparison result containing all differences
     */
    private PageComparisonResult comparePage(PDFPageModel basePage, PDFPageModel comparePage) {
        PageComparisonResult result = new PageComparisonResult();
        result.setPageNumber(basePage.getPageNumber());

        // Compare page dimensions
        boolean dimensionsDifferent = Math.abs(basePage.getWidth() - comparePage.getWidth()) > 0.1 ||
                Math.abs(basePage.getHeight() - comparePage.getHeight()) > 0.1;
        result.setDimensionsDifferent(dimensionsDifferent);
        result.setBaseDimensions(new float[]{basePage.getWidth(), basePage.getHeight()});
        result.setCompareDimensions(new float[]{comparePage.getWidth(), comparePage.getHeight()});

        // Compare text content using diff algorithm
        TextComparisonResult textDiff = compareText(basePage.getText(), comparePage.getText());
        result.setTextDifferences(textDiff);

        // Compare text elements (with style information)
        List<TextElementDifference> textElementDiffs = compareTextElements(basePage.getTextElements(), comparePage.getTextElements());
        result.setTextElementDifferences(textElementDiffs);

        // Compare images
        List<ImageDifference> imageDiffs = compareImages(basePage.getImages(), comparePage.getImages());
        result.setImageDifferences(imageDiffs);

        // Compare fonts
        List<FontDifference> fontDiffs = compareFonts(basePage.getFonts(), comparePage.getFonts());
        result.setFontDifferences(fontDiffs);

        return result;
    }

    /**
     * Compare text content using a diff algorithm
     * @param baseText The base text content
     * @param compareText The text to compare against the base
     * @return A text comparison result
     */
    private TextComparisonResult compareText(String baseText, String compareText) {
        TextComparisonResult result = new TextComparisonResult();

        // Split texts into lines for line-by-line comparison
        String[] baseLines = baseText.split("\n");
        String[] compareLines = compareText.split("\n");

        // Use diff_match_patch or another diff algorithm for detailed text comparison
        // This is a simplified implementation, in a real application you would use
        // a proper diff algorithm like Myers diff, diff_match_patch, or similar

        List<TextDifferenceItem> differences = new ArrayList<>();
        int maxLines = Math.max(baseLines.length, compareLines.length);

        for (int i = 0; i < maxLines; i++) {
            if (i < baseLines.length && i < compareLines.length) {
                if (!baseLines[i].equals(compareLines[i])) {
                    TextDifferenceItem diff = new TextDifferenceItem();
                    diff.setLineNumber(i + 1);
                    diff.setBaseText(baseLines[i]);
                    diff.setCompareText(compareLines[i]);
                    diff.setDifferenceType(TextDifferenceType.MODIFIED);
                    differences.add(diff);
                }
            } else if (i < baseLines.length) {
                TextDifferenceItem diff = new TextDifferenceItem();
                diff.setLineNumber(i + 1);
                diff.setBaseText(baseLines[i]);
                diff.setCompareText("");
                diff.setDifferenceType(TextDifferenceType.DELETED);
                differences.add(diff);
            } else {
                TextDifferenceItem diff = new TextDifferenceItem();
                diff.setLineNumber(i + 1);
                diff.setBaseText("");
                diff.setCompareText(compareLines[i]);
                diff.setDifferenceType(TextDifferenceType.ADDED);
                differences.add(diff);
            }
        }

        result.setDifferences(differences);
        result.setDifferenceCount(differences.size());

        return result;
    }

    /**
     * Compare text elements with style information
     * @param baseElements The base text elements
     * @param compareElements The text elements to compare against the base
     * @return List of text element differences
     */
    private List<TextElementDifference> compareTextElements(List<TextElement> baseElements, List<TextElement> compareElements) {
        List<TextElementDifference> differences = new ArrayList<>();

        // This is a simplified implementation. A more advanced implementation would
        // use positional matching and fuzzy text matching to identify corresponding
        // text elements between documents

        // For now, match elements by their text content and position
        Map<String, TextElement> baseElementMap = new HashMap<>();
        for (TextElement element : baseElements) {
            String key = element.getText() + "_" + Math.round(element.getX()) + "_" + Math.round(element.getY());
            baseElementMap.put(key, element);
        }

        Map<String, TextElement> compareElementMap = new HashMap<>();
        for (TextElement element : compareElements) {
            String key = element.getText() + "_" + Math.round(element.getX()) + "_" + Math.round(element.getY());
            compareElementMap.put(key, element);
        }

        // Find differences in base elements
        for (String key : baseElementMap.keySet()) {
            TextElement baseElement = baseElementMap.get(key);
            TextElement compareElement = compareElementMap.get(key);

            if (compareElement == null) {
                // Element exists only in base document
                TextElementDifference diff = new TextElementDifference();
                diff.setBaseElement(baseElement);
                diff.setCompareElement(null);
                diff.setOnlyInBase(true);
                differences.add(diff);
            } else {
                // Element exists in both, check for style differences
                boolean stylesDiffer = !compareTextElementStyles(baseElement, compareElement);
                if (stylesDiffer) {
                    TextElementDifference diff = new TextElementDifference();
                    diff.setBaseElement(baseElement);
                    diff.setCompareElement(compareElement);
                    diff.setStyleDifferent(true);
                    differences.add(diff);
                }

                // Remove from compare map to track processed elements
                compareElementMap.remove(key);
            }
        }

        // Add elements that exist only in compare document
        for (TextElement element : compareElementMap.values()) {
            TextElementDifference diff = new TextElementDifference();
            diff.setBaseElement(null);
            diff.setCompareElement(element);
            diff.setOnlyInCompare(true);
            differences.add(diff);
        }

        return differences;
    }

    /**
     * Compare styles of two text elements
     * @param baseElement The base text element
     * @param compareElement The text element to compare against the base
     * @return True if styles match, false otherwise
     */
    private boolean compareTextElementStyles(TextElement baseElement, TextElement compareElement) {
        // Compare font name
        if (!Objects.equals(baseElement.getFontName(), compareElement.getFontName())) {
            return false;
        }

        // Compare font size (with small tolerance for floating point differences)
        if (Math.abs(baseElement.getFontSize() - compareElement.getFontSize()) > 0.1) {
            return false;
        }

        // Compare font style
        if (!Objects.equals(baseElement.getFontStyle(), compareElement.getFontStyle())) {
            return false;
        }

        // Compare color
        if (!Arrays.equals(baseElement.getColor(), compareElement.getColor())) {
            return false;
        }

        return true;
    }

    /**
     * Compare images between two pages
     * @param baseImages The base page images
     * @param compareImages The images to compare against the base
     * @return List of image differences
     */
    private List<ImageDifference> compareImages(List<ImageElement> baseImages, List<ImageElement> compareImages) {
        List<ImageDifference> differences = new ArrayList<>();

        // Match images by name if available, otherwise by position and size
        Map<String, ImageElement> baseImageMap = new HashMap<>();
        for (ImageElement image : baseImages) {
            String key = image.getName() != null ? image.getName() :
                    Math.round(image.getX()) + "_" + Math.round(image.getY()) + "_" +
                            Math.round(image.getWidth()) + "_" + Math.round(image.getHeight());
            baseImageMap.put(key, image);
        }

        Map<String, ImageElement> compareImageMap = new HashMap<>();
        for (ImageElement image : compareImages) {
            String key = image.getName() != null ? image.getName() :
                    Math.round(image.getX()) + "_" + Math.round(image.getY()) + "_" +
                            Math.round(image.getWidth()) + "_" + Math.round(image.getHeight());
            compareImageMap.put(key, image);
        }

        // Find differences in base images
        for (String key : baseImageMap.keySet()) {
            ImageElement baseImage = baseImageMap.get(key);
            ImageElement compareImage = compareImageMap.get(key);

            if (compareImage == null) {
                // Image exists only in base document
                ImageDifference diff = new ImageDifference();
                diff.setBaseImage(baseImage);
                diff.setCompareImage(null);
                diff.setOnlyInBase(true);
                differences.add(diff);
            } else {
                // Image exists in both, check for differences
                boolean dimensionsDiffer = Math.abs(baseImage.getWidth() - compareImage.getWidth()) > 0.1 ||
                        Math.abs(baseImage.getHeight() - compareImage.getHeight()) > 0.1;
                boolean positionDiffers = Math.abs(baseImage.getX() - compareImage.getX()) > 0.1 ||
                        Math.abs(baseImage.getY() - compareImage.getY()) > 0.1;
                boolean formatDiffers = !Objects.equals(baseImage.getFormat(), compareImage.getFormat());

                if (dimensionsDiffer || positionDiffers || formatDiffers) {
                    ImageDifference diff = new ImageDifference();
                    diff.setBaseImage(baseImage);
                    diff.setCompareImage(compareImage);
                    diff.setDimensionsDifferent(dimensionsDiffer);
                    diff.setPositionDifferent(positionDiffers);
                    diff.setFormatDifferent(formatDiffers);
                    differences.add(diff);
                }

                // Remove from compare map to track processed elements
                compareImageMap.remove(key);
            }
        }

        // Add images that exist only in compare document
        for (ImageElement image : compareImageMap.values()) {
            ImageDifference diff = new ImageDifference();
            diff.setBaseImage(null);
            diff.setCompareImage(image);
            diff.setOnlyInCompare(true);
            differences.add(diff);
        }

        return differences;