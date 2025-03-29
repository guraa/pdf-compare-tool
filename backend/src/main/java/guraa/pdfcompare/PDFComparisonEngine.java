package guraa.pdfcompare;

import guraa.pdfcompare.comparison.*;
import guraa.pdfcompare.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Main class for comparing two PDF documents
 */
public class PDFComparisonEngine {

    private static final Logger logger = LoggerFactory.getLogger(PDFComparisonEngine.class);

    /**
     * Compare two PDF document models and generate comparison results
     * @param baseDocument The base document model
     * @param compareDocument The document to compare against the base
     * @return A comparison result containing all differences
     */
    public PDFComparisonResult compareDocuments(PDFDocumentModel baseDocument, PDFDocumentModel compareDocument) {
        logger.info("Starting document comparison between {} and {}",
                baseDocument.getFileName(), compareDocument.getFileName());

        PDFComparisonResult result = new PDFComparisonResult();

        // Compare metadata
        logger.debug("Comparing document metadata");
        Map<String, MetadataDifference> metadataDiffs = compareMetadata(baseDocument.getMetadata(), compareDocument.getMetadata());
        result.setMetadataDifferences(metadataDiffs);

        // Compare page count
        boolean pageCountDiff = baseDocument.getPageCount() != compareDocument.getPageCount();
        result.setPageCountDifferent(pageCountDiff);
        result.setBasePageCount(baseDocument.getPageCount());
        result.setComparePageCount(compareDocument.getPageCount());

        logger.debug("Base document page count: {}, Compare document page count: {}",
                baseDocument.getPageCount(), compareDocument.getPageCount());

        // Compare pages
        List<PageComparisonResult> pageDiffs = new ArrayList<>();
        int maxPages = Math.max(baseDocument.getPageCount(), compareDocument.getPageCount());

        for (int i = 0; i < maxPages; i++) {
            PageComparisonResult pageResult;

            if (i < baseDocument.getPageCount() && i < compareDocument.getPageCount()) {
                // Compare existing pages
                logger.debug("Comparing page {}", i + 1);
                pageResult = comparePage(baseDocument.getPages().get(i), compareDocument.getPages().get(i));
            } else if (i < baseDocument.getPageCount()) {
                // Page exists only in base document
                logger.debug("Page {} exists only in base document", i + 1);
                pageResult = new PageComparisonResult();
                pageResult.setPageNumber(i + 1);
                pageResult.setOnlyInBase(true);
            } else {
                // Page exists only in compare document
                logger.debug("Page {} exists only in compare document", i + 1);
                pageResult = new PageComparisonResult();
                pageResult.setPageNumber(i + 1);
                pageResult.setOnlyInCompare(true);
            }

            pageDiffs.add(pageResult);
        }

        result.setPageDifferences(pageDiffs);

        // Calculate summary statistics
        calculateSummaryStatistics(result);

        logger.info("Document comparison completed. Found {} total differences",
                result.getTotalDifferences());

        return result;
    }

    /**
     * Compare metadata between two documents
     * @param baseMetadata The base document metadata
     * @param compareMetadata The comparison document metadata
     * @return Map of metadata differences
     */
    Map<String, MetadataDifference> compareMetadata(Map<String, String> baseMetadata, Map<String, String> compareMetadata) {
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
                logger.debug("Metadata key '{}' only exists in base document", key);
            } else if (!baseValue.equals(compareValue)) {
                // Key exists in both but values differ
                MetadataDifference diff = new MetadataDifference();
                diff.setKey(key);
                diff.setBaseValue(baseValue);
                diff.setCompareValue(compareValue);
                diff.setValueDifferent(true);
                differences.put(key, diff);
                logger.debug("Metadata key '{}' has different values: '{}' vs '{}'",
                        key, baseValue, compareValue);
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
                logger.debug("Metadata key '{}' only exists in compare document", key);
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
    PageComparisonResult comparePage(PDFPageModel basePage, PDFPageModel comparePage) {
        PageComparisonResult result = new PageComparisonResult();
        result.setPageNumber(basePage.getPageNumber());

        // Compare page dimensions
        boolean dimensionsDifferent = Math.abs(basePage.getWidth() - comparePage.getWidth()) > 0.1 ||
                Math.abs(basePage.getHeight() - comparePage.getHeight()) > 0.1;
        result.setDimensionsDifferent(dimensionsDifferent);
        result.setBaseDimensions(new float[]{basePage.getWidth(), basePage.getHeight()});
        result.setCompareDimensions(new float[]{comparePage.getWidth(), comparePage.getHeight()});

        if (dimensionsDifferent) {
            logger.debug("Page {} dimensions differ: base [{}x{}], compare [{}x{}]",
                    basePage.getPageNumber(),
                    basePage.getWidth(), basePage.getHeight(),
                    comparePage.getWidth(), comparePage.getHeight());
        }

        // Compare text content using diff algorithm
        TextComparisonResult textDiff = compareText(basePage.getText(), comparePage.getText());
        result.setTextDifferences(textDiff);

        if (textDiff.getDifferenceCount() > 0) {
            logger.debug("Page {} has {} text differences",
                    basePage.getPageNumber(), textDiff.getDifferenceCount());
        }

        // Compare text elements (with style information)
        List<TextElementDifference> textElementDiffs = compareTextElements(basePage.getTextElements(), comparePage.getTextElements());
        result.setTextElementDifferences(textElementDiffs);

        if (!textElementDiffs.isEmpty()) {
            logger.debug("Page {} has {} text element differences",
                    basePage.getPageNumber(), textElementDiffs.size());
        }

        // Compare images
        List<ImageDifference> imageDiffs = compareImages(basePage.getImages(), comparePage.getImages());
        result.setImageDifferences(imageDiffs);

        if (!imageDiffs.isEmpty()) {
            logger.debug("Page {} has {} image differences",
                    basePage.getPageNumber(), imageDiffs.size());
        }

        // Compare fonts
        List<FontDifference> fontDiffs = compareFonts(basePage.getFonts(), comparePage.getFonts());
        result.setFontDifferences(fontDiffs);

        if (!fontDiffs.isEmpty()) {
            logger.debug("Page {} has {} font differences",
                    basePage.getPageNumber(), fontDiffs.size());
        }

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

        // Special case - handle null or empty texts properly
        if ((baseText == null || baseText.trim().isEmpty()) &&
                (compareText == null || compareText.trim().isEmpty())) {
            // Both texts are empty or null - no differences
            result.setDifferences(new ArrayList<>());
            result.setDifferenceCount(0);
            return result;
        }

        // Handle case where one text is null and the other isn't
        if (baseText == null || baseText.trim().isEmpty()) {
            TextDifferenceItem diff = new TextDifferenceItem();
            diff.setLineNumber(1);
            diff.setBaseText("");
            diff.setCompareText(compareText);
            diff.setDifferenceType(TextDifferenceType.ADDED);
            result.setDifferences(Collections.singletonList(diff));
            result.setDifferenceCount(1);
            return result;
        }

        if (compareText == null || compareText.trim().isEmpty()) {
            TextDifferenceItem diff = new TextDifferenceItem();
            diff.setLineNumber(1);
            diff.setBaseText(baseText);
            diff.setCompareText("");
            diff.setDifferenceType(TextDifferenceType.DELETED);
            result.setDifferences(Collections.singletonList(diff));
            result.setDifferenceCount(1);
            return result;
        }

        // Normalize line endings
        baseText = baseText.replaceAll("\r\n", "\n").replaceAll("\r", "\n");
        compareText = compareText.replaceAll("\r\n", "\n").replaceAll("\r", "\n");

        // Split texts into lines for line-by-line comparison
        String[] baseLines = baseText.split("\n");
        String[] compareLines = compareText.split("\n");

        List<TextDifferenceItem> differences = new ArrayList<>();
        int maxLines = Math.max(baseLines.length, compareLines.length);

        for (int i = 0; i < maxLines; i++) {
            if (i < baseLines.length && i < compareLines.length) {
                // Both lines exist - compare them
                if (!baseLines[i].equals(compareLines[i])) {
                    TextDifferenceItem diff = new TextDifferenceItem();
                    diff.setLineNumber(i + 1);
                    diff.setBaseText(baseLines[i]);
                    diff.setCompareText(compareLines[i]);
                    diff.setDifferenceType(TextDifferenceType.MODIFIED);
                    differences.add(diff);
                    logger.debug("Modified text at line {}: '{}' vs '{}'",
                            i + 1, baseLines[i], compareLines[i]);
                }
            } else if (i < baseLines.length) {
                // Line only in base document
                TextDifferenceItem diff = new TextDifferenceItem();
                diff.setLineNumber(i + 1);
                diff.setBaseText(baseLines[i]);
                diff.setCompareText("");
                diff.setDifferenceType(TextDifferenceType.DELETED);
                differences.add(diff);
                logger.debug("Deleted text at line {}: '{}'", i + 1, baseLines[i]);
            } else {
                // Line only in compare document
                TextDifferenceItem diff = new TextDifferenceItem();
                diff.setLineNumber(i + 1);
                diff.setBaseText("");
                diff.setCompareText(compareLines[i]);
                diff.setDifferenceType(TextDifferenceType.ADDED);
                differences.add(diff);
                logger.debug("Added text at line {}: '{}'", i + 1, compareLines[i]);
            }
        }

        result.setDifferences(differences);
        result.setDifferenceCount(differences.size());

        // Log the comparison results clearly
        logger.info("Text comparison found {} differences between texts", differences.size());
        if (differences.size() > 0) {
            logger.debug("Text differences sample: {}", differences.get(0).toString());
        }

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

        // Handle null cases
        if (baseElements == null) baseElements = new ArrayList<>();
        if (compareElements == null) compareElements = new ArrayList<>();

        // Enhanced logging
        logger.debug("Comparing {} base text elements with {} compare text elements",
                baseElements.size(), compareElements.size());

        // Create maps for quicker lookup - improve the key calculation
        Map<String, TextElement> baseElementMap = new HashMap<>();
        for (TextElement element : baseElements) {
            // Create a unique key combining text and position (rounded)
            String key = element.getText() + "_" + Math.round(element.getX()) + "_" + Math.round(element.getY());
            baseElementMap.put(key, element);
        }

        Map<String, TextElement> compareElementMap = new HashMap<>();
        for (TextElement element : compareElements) {
            // Create a unique key combining text and position (rounded)
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
                logger.debug("Text element '{}' only exists in base document",
                        baseElement.getText());
            } else {
                // Element exists in both, check for style differences
                boolean stylesDiffer = !compareTextElementStyles(baseElement, compareElement);
                if (stylesDiffer) {
                    TextElementDifference diff = new TextElementDifference();
                    diff.setBaseElement(baseElement);
                    diff.setCompareElement(compareElement);
                    diff.setStyleDifferent(true);
                    differences.add(diff);
                    logger.debug("Text element '{}' has style differences", baseElement.getText());

                    // Add detailed logging for style differences
                    if (baseElement.getFontName() != null && compareElement.getFontName() != null &&
                            !baseElement.getFontName().equals(compareElement.getFontName())) {
                        logger.debug("  Font name differs: '{}' vs '{}'",
                                baseElement.getFontName(), compareElement.getFontName());
                    }

                    if (Math.abs(baseElement.getFontSize() - compareElement.getFontSize()) > 0.1) {
                        logger.debug("  Font size differs: {} vs {}",
                                baseElement.getFontSize(), compareElement.getFontSize());
                    }

                    // Log other style differences...
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
            logger.debug("Text element '{}' only exists in compare document", element.getText());
        }

        logger.info("Found {} text element differences", differences.size());
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
            logger.debug("Font name differs: '{}' vs '{}'",
                    baseElement.getFontName(), compareElement.getFontName());
            return false;
        }

        // Compare font size (with small tolerance for floating point differences)
        if (Math.abs(baseElement.getFontSize() - compareElement.getFontSize()) > 0.1) {
            logger.debug("Font size differs: {} vs {}",
                    baseElement.getFontSize(), compareElement.getFontSize());
            return false;
        }

        // Compare font style
        if (!Objects.equals(baseElement.getFontStyle(), compareElement.getFontStyle())) {
            logger.debug("Font style differs: '{}' vs '{}'",
                    baseElement.getFontStyle(), compareElement.getFontStyle());
            return false;
        }

        // Compare color
        if (!Arrays.equals(baseElement.getColor(), compareElement.getColor())) {
            logger.debug("Text color differs");
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
                logger.debug("Image '{}' only exists in base document",
                        baseImage.getName() != null ? baseImage.getName() : "unnamed");
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

                    if (dimensionsDiffer) {
                        logger.debug("Image '{}' has different dimensions: [{}x{}] vs [{}x{}]",
                                baseImage.getName() != null ? baseImage.getName() : "unnamed",
                                baseImage.getWidth(), baseImage.getHeight(),
                                compareImage.getWidth(), compareImage.getHeight());
                    }
                    if (positionDiffers) {
                        logger.debug("Image '{}' has different position: [{},{}] vs [{},{}]",
                                baseImage.getName() != null ? baseImage.getName() : "unnamed",
                                baseImage.getX(), baseImage.getY(),
                                compareImage.getX(), compareImage.getY());
                    }
                    if (formatDiffers) {
                        logger.debug("Image '{}' has different format: '{}' vs '{}'",
                                baseImage.getName() != null ? baseImage.getName() : "unnamed",
                                baseImage.getFormat(), compareImage.getFormat());
                    }
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
            logger.debug("Image '{}' only exists in compare document",
                    image.getName() != null ? image.getName() : "unnamed");
        }

        return differences;
    }

    /**
     * Compare fonts between two pages
     * @param baseFonts The base page fonts
     * @param compareFonts The fonts to compare against the base
     * @return List of font differences
     */
    private List<FontDifference> compareFonts(List<FontInfo> baseFonts, List<FontInfo> compareFonts) {
        List<FontDifference> differences = new ArrayList<>();

        // Match fonts by name
        Map<String, FontInfo> baseFontMap = new HashMap<>();
        for (FontInfo font : baseFonts) {
            baseFontMap.put(font.getName(), font);
        }

        Map<String, FontInfo> compareFontMap = new HashMap<>();
        for (FontInfo font : compareFonts) {
            compareFontMap.put(font.getName(), font);
        }

        // Find differences in base fonts
        for (String name : baseFontMap.keySet()) {
            FontInfo baseFont = baseFontMap.get(name);
            FontInfo compareFont = compareFontMap.get(name);

            if (compareFont == null) {
                // Font exists only in base document
                FontDifference diff = new FontDifference();
                diff.setBaseFont(baseFont);
                diff.setCompareFont(null);
                diff.setOnlyInBase(true);
                differences.add(diff);
                logger.debug("Font '{}' only exists in base document", baseFont.getName());
            } else {
                // Font exists in both, check for differences
                boolean embeddingDiffers = baseFont.isEmbedded() != compareFont.isEmbedded();
                boolean subsetDiffers = baseFont.isSubset() != compareFont.isSubset();

                if (embeddingDiffers || subsetDiffers) {
                    FontDifference diff = new FontDifference();
                    diff.setBaseFont(baseFont);
                    diff.setCompareFont(compareFont);
                    diff.setEmbeddingDifferent(embeddingDiffers);
                    diff.setSubsetDifferent(subsetDiffers);
                    differences.add(diff);

                    if (embeddingDiffers) {
                        logger.debug("Font '{}' has different embedding: {} vs {}",
                                baseFont.getName(),
                                baseFont.isEmbedded(), compareFont.isEmbedded());
                    }
                    if (subsetDiffers) {
                        logger.debug("Font '{}' has different subsetting: {} vs {}",
                                baseFont.getName(),
                                baseFont.isSubset(), compareFont.isSubset());
                    }
                }

                // Remove from compare map to track processed elements
                compareFontMap.remove(name);
            }
        }

        // Add fonts that exist only in compare document
        for (FontInfo font : compareFontMap.values()) {
            FontDifference diff = new FontDifference();
            diff.setBaseFont(null);
            diff.setCompareFont(font);
            diff.setOnlyInCompare(true);
            differences.add(diff);
            logger.debug("Font '{}' only exists in compare document", font.getName());
        }

        return differences;
    }

    /**
     * Calculate summary statistics for comparison result
     * @param result The comparison result to update with statistics
     */
    private void calculateSummaryStatistics(PDFComparisonResult result) {
        int totalDifferences = 0;
        int textDifferences = 0;
        int imageDifferences = 0;
        int fontDifferences = 0;
        int styleDifferences = 0;

        // Count metadata differences
        if (result.getMetadataDifferences() != null) {
            totalDifferences += result.getMetadataDifferences().size();
        }

        // Count page structure differences
        if (result.isPageCountDifferent()) {
            totalDifferences++;
        }

        // Count differences for each page
        for (PageComparisonResult page : result.getPageDifferences()) {
            // Count page structure differences
            if (page.isOnlyInBase() || page.isOnlyInCompare()) {
                totalDifferences++;
            } else if (page.isDimensionsDifferent()) {
                totalDifferences++;
            }

            // Count text differences
            if (page.getTextDifferences() != null && page.getTextDifferences().getDifferences() != null) {
                int pageDiffs = page.getTextDifferences().getDifferences().size();
                textDifferences += pageDiffs;
                totalDifferences += pageDiffs;
            }

            // Count text element differences
            if (page.getTextElementDifferences() != null) {
                for (TextElementDifference diff : page.getTextElementDifferences()) {
                    if (diff.isStyleDifferent()) {
                        styleDifferences++;
                    } else {
                        textDifferences++;
                    }
                    totalDifferences++;
                }
            }

            // Count image differences
            if (page.getImageDifferences() != null) {
                int pageDiffs = page.getImageDifferences().size();
                imageDifferences += pageDiffs;
                totalDifferences += pageDiffs;
            }

            // Count font differences
            if (page.getFontDifferences() != null) {
                int pageDiffs = page.getFontDifferences().size();
                fontDifferences += pageDiffs;
                totalDifferences += pageDiffs;
            }
        }

        // Set statistics
        result.setTotalDifferences(totalDifferences);
        result.setTotalTextDifferences(textDifferences);
        result.setTotalImageDifferences(imageDifferences);
        result.setTotalFontDifferences(fontDifferences);
        result.setTotalStyleDifferences(styleDifferences);

        logger.info("Comparison statistics: total={}, text={}, image={}, font={}, style={}",
                totalDifferences, textDifferences, imageDifferences, fontDifferences, styleDifferences);
    }
}