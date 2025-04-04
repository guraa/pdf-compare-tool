package guraa.pdfcompare.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import guraa.pdfcompare.model.Comparison;
import guraa.pdfcompare.model.ComparisonResult;
import guraa.pdfcompare.model.PageDetails;
import guraa.pdfcompare.model.PdfDocument;
import guraa.pdfcompare.model.difference.Difference;
import guraa.pdfcompare.model.difference.FontDifference;
import guraa.pdfcompare.model.difference.ImageDifference;
import guraa.pdfcompare.model.difference.TextDifference;
import guraa.pdfcompare.util.DifferenceCalculator;
import guraa.pdfcompare.util.FontAnalyzer;
import guraa.pdfcompare.util.ImageExtractor;
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
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DifferenceDetectionService {

    private final TextExtractor textExtractor;
    private final ImageExtractor imageExtractor;
    private final FontAnalyzer fontAnalyzer;
    private final DifferenceCalculator differenceCalculator;
    private final ObjectMapper objectMapper;

    /**
     * Compare all pages between two PDF documents.
     *
     * @param comparison The comparison entity
     * @param baseDocument The base document
     * @param compareDocument The comparison document
     * @return List of page differences
     * @throws IOException If there's an error processing the documents
     */
    public List<ComparisonResult.PageDifference> compareAllPages(
            Comparison comparison,
            PdfDocument baseDocument,
            PdfDocument compareDocument) throws IOException {

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
                // Check if page exists in both documents
                boolean pageInBase = i < basePageCount;
                boolean pageInCompare = i < comparePageCount;

                // Create page difference object
                ComparisonResult.PageDifference pageDiff = new ComparisonResult.PageDifference();
                pageDiff.setPageNumber(i + 1); // 1-based page numbers for the API
                pageDiff.setOnlyInBase(pageInBase && !pageInCompare);
                pageDiff.setOnlyInCompare(!pageInBase && pageInCompare);

                // If page exists in only one document, add minimal information
                if (!pageInBase || !pageInCompare) {
                    pageDifferences.add(pageDiff);
                    continue;
                }

                // Get pages from both documents
                PDPage basePage = basePdf.getPage(i);
                PDPage comparePage = comparePdf.getPage(i);

                // Check dimensions
                PDRectangle baseSize = basePage.getMediaBox();
                PDRectangle compareSize = comparePage.getMediaBox();

                pageDiff.setDimensionsDifferent(!baseSize.equals(compareSize));

                // Extract text from both pages
                String baseText = textExtractor.extractTextFromPage(basePdf, i);
                String compareText = textExtractor.extractTextFromPage(comparePdf, i);

                // Compare text content
                List<TextDifference> textDiffs = differenceCalculator.compareText(
                        baseText, compareText, comparison.getTextComparisonMethod());

                if (!textDiffs.isEmpty()) {
                    ComparisonResult.TextDifferences textDifferences = new ComparisonResult.TextDifferences();
                    textDifferences.setBaseText(baseText);
                    textDifferences.setCompareText(compareText);
                    textDifferences.setDifferences(new ArrayList<>(textDiffs));

                    pageDiff.setTextDifferences(textDifferences);
                }

                // Compare text elements (style, fonts, positioning)
                List<TextElement> baseElements = textExtractor.extractTextElementsFromPage(basePdf, i);
                List<TextElement> compareElements = textExtractor.extractTextElementsFromPage(comparePdf, i);

                List<Difference> textElementDiffs = compareTextElements(baseElements, compareElements);
                if (!textElementDiffs.isEmpty()) {
                    pageDiff.setTextElementDifferences(textElementDiffs);
                }

                // Compare images
                List<ImageExtractor.ImageInfo> baseImages = imageExtractor.extractImagesFromPage(
                        basePdf, i, comparisonDir.resolve("base_images"));
                List<ImageExtractor.ImageInfo> compareImages = imageExtractor.extractImagesFromPage(
                        comparePdf, i, comparisonDir.resolve("compare_images"));

                List<Difference> imageDiffs = compareImages(baseImages, compareImages);
                if (!imageDiffs.isEmpty()) {
                    pageDiff.setImageDifferences(imageDiffs);
                }

                // Compare fonts
                List<FontAnalyzer.FontInfo> baseFonts = fontAnalyzer.analyzeFontsOnPage(
                        basePdf, i, comparisonDir.resolve("base_fonts"));
                List<FontAnalyzer.FontInfo> compareFonts = fontAnalyzer.analyzeFontsOnPage(
                        comparePdf, i, comparisonDir.resolve("compare_fonts"));

                List<Difference> fontDiffs = compareFonts(baseFonts, compareFonts);
                if (!fontDiffs.isEmpty()) {
                    pageDiff.setFontDifferences(fontDiffs);
                }

                // Also create detailed page analysis for the API to serve
                createPageDetails(
                        comparison.getComparisonId(),
                        i + 1, // 1-based page number
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

                pageDifferences.add(pageDiff);
            }

            return pageDifferences;
        } finally {
            // Close PDFs
            basePdf.close();
            comparePdf.close();
        }
    }

    /**
     * Compare text elements between two pages.
     *
     * @param baseElements Text elements from the base page
     * @param compareElements Text elements from the comparison page
     * @return List of differences
     */
    private List<Difference> compareTextElements(
            List<TextElement> baseElements,
            List<TextElement> compareElements) {

        List<Difference> differences = new ArrayList<>();

        // This is a simplified implementation that focuses on basic text differences
        // A complete implementation would use more sophisticated matching algorithms

        // Match elements based on similar position and text
        Map<TextElement, TextElement> matches =
                matchTextElements(baseElements, compareElements);

        // Track elements that have been matched
        Set<TextElement> matchedBaseElements = new HashSet<>(matches.keySet());
        Set<TextElement> matchedCompareElements = new HashSet<>(matches.values());

        // Find style differences in matched elements
        for (Map.Entry<TextElement, TextElement> match : matches.entrySet()) {
            TextElement baseElement = match.getKey();
            TextElement compareElement = match.getValue();

            // Compare font size
            boolean fontSizeDifferent = Math.abs(baseElement.getFontSize() - compareElement.getFontSize()) > 0.1;

            String baseFontName = baseElement.getFontName();
            String compareFontName = compareElement.getFontName();

            baseFontName = baseFontName.contains("+") ? baseFontName.substring(baseFontName.indexOf("+") + 1) : baseFontName;
            compareFontName = compareFontName.contains("+") ? compareFontName.substring(compareFontName.indexOf("+") + 1) : compareFontName;

            boolean fontNameDifferent = !baseFontName.equals(compareFontName);

            // Compare color
            boolean colorDifferent = !compareColors(baseElement.getColor(), compareElement.getColor());

            if (fontSizeDifferent || fontNameDifferent || colorDifferent) {
                // Create style difference
                String diffId = UUID.randomUUID().toString();

                // Create description
                StringBuilder description = new StringBuilder("Style differs for text \"")
                        .append(baseElement.getText())
                        .append("\": ");

                if (fontSizeDifferent) {
                    description.append("Font size changed from ")
                            .append(baseElement.getFontSize())
                            .append(" to ")
                            .append(compareElement.getFontSize())
                            .append(". ");
                }

                if (fontNameDifferent) {
                    description.append("Font changed from \"")
                            .append(baseElement.getFontName())
                            .append("\" to \"")
                            .append(compareElement.getFontName())
                            .append("\". ");
                }

                if (colorDifferent) {
                    description.append("Color changed from ")
                            .append(baseElement.getColor())
                            .append(" to ")
                            .append(compareElement.getColor())
                            .append(".");
                }

                // Create style difference
                guraa.pdfcompare.model.difference.StyleDifference diff =
                        guraa.pdfcompare.model.difference.StyleDifference.builder()
                                .id(diffId)
                                .type("style")
                                .changeType("modified")
                                .severity("minor")
                                .description(description.toString())
                                .text(baseElement.getText())
                                .baseColor(baseElement.getColor())
                                .compareColor(compareElement.getColor())
                                .build();

                // Set position and bounds
                differenceCalculator.setPositionAndBounds(
                        diff,
                        baseElement.getX(),
                        baseElement.getY(),
                        baseElement.getWidth(),
                        baseElement.getHeight());

                differences.add(diff);
            }
        }

        // Elements only in base document (deleted)
        for (TextElement element : baseElements) {
            if (!matchedBaseElements.contains(element)) {
                // Create text difference for deleted element
                String diffId = UUID.randomUUID().toString();

                TextDifference diff = TextDifference.builder()
                        .id(diffId)
                        .type("text")
                        .changeType("deleted")
                        .severity("minor")
                        .text(element.getText())
                        .baseText(element.getText())
                        .description("Text \"" + element.getText() + "\" deleted")
                        .build();

                // Set position and bounds
                differenceCalculator.setPositionAndBounds(
                        diff,
                        element.getX(),
                        element.getY(),
                        element.getWidth(),
                        element.getHeight());

                differences.add(diff);
            }
        }

        // Elements only in compare document (added)
        for (TextElement element : compareElements) {
            if (!matchedCompareElements.contains(element)) {
                // Create text difference for added element
                String diffId = UUID.randomUUID().toString();

                TextDifference diff = TextDifference.builder()
                        .id(diffId)
                        .type("text")
                        .changeType("added")
                        .severity("minor")
                        .text(element.getText())
                        .compareText(element.getText())
                        .description("Text \"" + element.getText() + "\" added")
                        .build();

                // Set position and bounds
                differenceCalculator.setPositionAndBounds(
                        diff,
                        element.getX(),
                        element.getY(),
                        element.getWidth(),
                        element.getHeight());

                differences.add(diff);
            }
        }

        return differences;
    }

    /**
     * Match text elements between base and comparison pages.
     *
     * @param baseElements Text elements from base page
     * @param compareElements Text elements from comparison page
     * @return Map of matched elements
     */
    private Map<TextElement, TextElement> matchTextElements(
            List<TextElement> baseElements,
            List<TextElement> compareElements) {

        Map<TextElement, TextElement> matches = new HashMap<>();

        // This is a simplified matching algorithm
        // A real implementation would use more sophisticated techniques

        // Create a list of potential matches
        List<ElementMatch> potentialMatches = new ArrayList<>();

        for (TextElement baseElement : baseElements) {
            for (TextElement compareElement : compareElements) {
                // Calculate similarity based on text content and position
                double textSimilarity = calculateTextSimilarity(
                        baseElement.getText(), compareElement.getText());

                // Calculate position similarity
                double positionSimilarity = 1.0 -
                        (Math.abs(baseElement.getX() - compareElement.getX()) +
                                Math.abs(baseElement.getY() - compareElement.getY())) / 100.0;

                // Ensure position similarity is between 0 and 1
                positionSimilarity = Math.max(0.0, Math.min(1.0, positionSimilarity));

                // Combine similarities
                double overallSimilarity = 0.7 * textSimilarity + 0.3 * positionSimilarity;

                // Only consider matches with at least 50% similarity
                if (overallSimilarity >= 0.5) {
                    potentialMatches.add(new ElementMatch(
                            baseElement, compareElement, overallSimilarity));
                }
            }
        }

        // Sort potential matches by similarity (highest first)
        potentialMatches.sort((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()));

        // Assign matches greedily
        Set<TextElement> matchedBaseElements = new HashSet<>();
        Set<TextElement> matchedCompareElements = new HashSet<>();

        for (ElementMatch match : potentialMatches) {
            TextElement baseElement = match.getBaseElement();
            TextElement compareElement = match.getCompareElement();

            // Skip if either element is already matched
            if (matchedBaseElements.contains(baseElement) ||
                    matchedCompareElements.contains(compareElement)) {
                continue;
            }

            // Create match
            matches.put(baseElement, compareElement);

            // Mark as matched
            matchedBaseElements.add(baseElement);
            matchedCompareElements.add(compareElement);
        }

        return matches;
    }

    /**
     * Compare images between two pages.
     *
     * @param baseImages Images from base page
     * @param compareImages Images from comparison page
     * @return List of differences
     */
    private List<Difference> compareImages(
            List<ImageExtractor.ImageInfo> baseImages,
            List<ImageExtractor.ImageInfo> compareImages) {

        List<Difference> differences = new ArrayList<>();

        // Match images based on hash and content
        Map<ImageExtractor.ImageInfo, ImageExtractor.ImageInfo> matches =
                matchImages(baseImages, compareImages);

        // Track images that have been matched
        Set<ImageExtractor.ImageInfo> matchedBaseImages = new HashSet<>(matches.keySet());
        Set<ImageExtractor.ImageInfo> matchedCompareImages = new HashSet<>(matches.values());

        // Find differences in matched images
        for (Map.Entry<ImageExtractor.ImageInfo, ImageExtractor.ImageInfo> match : matches.entrySet()) {
            ImageExtractor.ImageInfo baseImage = match.getKey();
            ImageExtractor.ImageInfo compareImage = match.getValue();

            // Check for differences
            boolean sizeDifferent = baseImage.getWidth() != compareImage.getWidth() ||
                    baseImage.getHeight() != compareImage.getHeight();

            boolean formatDifferent = !baseImage.getFormat().equals(compareImage.getFormat());

            // Compare image content
            double differenceScore = differenceCalculator.compareImages(
                    baseImage.getImage(), compareImage.getImage());

            boolean contentDifferent = differenceScore > 0.1; // 10% threshold for content difference

            if (sizeDifferent || formatDifferent || contentDifferent) {
                // Create image difference
                String diffId = UUID.randomUUID().toString();

                // Create description
                StringBuilder description = new StringBuilder("Image differs: ");

                if (sizeDifferent) {
                    description.append("Size changed from ")
                            .append(baseImage.getWidth())
                            .append("x")
                            .append(baseImage.getHeight())
                            .append(" to ")
                            .append(compareImage.getWidth())
                            .append("x")
                            .append(compareImage.getHeight())
                            .append(". ");
                }

                if (formatDifferent) {
                    description.append("Format changed from ")
                            .append(baseImage.getFormat())
                            .append(" to ")
                            .append(compareImage.getFormat())
                            .append(". ");
                }

                if (contentDifferent) {
                    description.append("Image content changed (")
                            .append(Math.round(differenceScore * 100))
                            .append("% different).");
                }

                // Create image difference
                ImageDifference diff = ImageDifference.builder()
                        .id(diffId)
                        .type("image")
                        .changeType("modified")
                        .severity(differenceCalculator.calculateSeverity("image", differenceScore))
                        .description(description.toString())
                        .baseImageHash(baseImage.getImageHash())
                        .compareImageHash(compareImage.getImageHash())
                        .baseFormat(baseImage.getFormat())
                        .compareFormat(compareImage.getFormat())
                        .baseWidth(baseImage.getWidth())
                        .baseHeight(baseImage.getHeight())
                        .compareWidth(compareImage.getWidth())
                        .compareHeight(compareImage.getHeight())
                        .baseThumbnailPath(baseImage.getImagePath())
                        .compareThumbnailPath(compareImage.getImagePath())
                        .visualDifferenceScore(differenceScore)
                        .build();

                // Set position using the base image
                if (baseImage.getPosition() != null) {
                    differenceCalculator.setPositionAndBounds(
                            diff,
                            baseImage.getPosition().getX(),
                            baseImage.getPosition().getY(),
                            baseImage.getWidth(),
                            baseImage.getHeight());
                }

                differences.add(diff);
            }
        }

        // Images only in base document (deleted)
        for (ImageExtractor.ImageInfo image : baseImages) {
            if (!matchedBaseImages.contains(image)) {
                // Create image difference for deleted image
                String diffId = UUID.randomUUID().toString();

                ImageDifference diff = ImageDifference.builder()
                        .id(diffId)
                        .type("image")
                        .changeType("deleted")
                        .severity("major")
                        .description("Image removed")
                        .baseImageHash(image.getImageHash())
                        .baseFormat(image.getFormat())
                        .baseWidth(image.getWidth())
                        .baseHeight(image.getHeight())
                        .baseThumbnailPath(image.getImagePath())
                        .build();

                // Set position
                if (image.getPosition() != null) {
                    differenceCalculator.setPositionAndBounds(
                            diff,
                            image.getPosition().getX(),
                            image.getPosition().getY(),
                            image.getWidth(),
                            image.getHeight());
                }

                differences.add(diff);
            }
        }

        // Images only in compare document (added)
        for (ImageExtractor.ImageInfo image : compareImages) {
            if (!matchedCompareImages.contains(image)) {
                // Create image difference for added image
                String diffId = UUID.randomUUID().toString();

                ImageDifference diff = ImageDifference.builder()
                        .id(diffId)
                        .type("image")
                        .changeType("added")
                        .severity("major")
                        .description("Image added")
                        .compareImageHash(image.getImageHash())
                        .compareFormat(image.getFormat())
                        .compareWidth(image.getWidth())
                        .compareHeight(image.getHeight())
                        .compareThumbnailPath(image.getImagePath())
                        .build();

                // Set position
                if (image.getPosition() != null) {
                    differenceCalculator.setPositionAndBounds(
                            diff,
                            image.getPosition().getX(),
                            image.getPosition().getY(),
                            image.getWidth(),
                            image.getHeight());
                }

                differences.add(diff);
            }
        }

        return differences;
    }

    /**
     * Match images between base and comparison pages.
     *
     * @param baseImages Images from base page
     * @param compareImages Images from comparison page
     * @return Map of matched images
     */
    private Map<ImageExtractor.ImageInfo, ImageExtractor.ImageInfo> matchImages(
            List<ImageExtractor.ImageInfo> baseImages,
            List<ImageExtractor.ImageInfo> compareImages) {

        Map<ImageExtractor.ImageInfo, ImageExtractor.ImageInfo> matches = new HashMap<>();

        // First match by hash (exact matches)
        for (ImageExtractor.ImageInfo baseImage : baseImages) {
            for (ImageExtractor.ImageInfo compareImage : compareImages) {
                if (baseImage.getImageHash() != null &&
                        baseImage.getImageHash().equals(compareImage.getImageHash())) {
                    matches.put(baseImage, compareImage);
                    break;
                }
            }
        }

        // Track images that have been matched
        Set<ImageExtractor.ImageInfo> matchedBaseImages = new HashSet<>(matches.keySet());
        Set<ImageExtractor.ImageInfo> matchedCompareImages = new HashSet<>(matches.values());

        // For unmatched images, try matching by similarity
        for (ImageExtractor.ImageInfo baseImage : baseImages) {
            if (matchedBaseImages.contains(baseImage)) {
                continue;
            }

            double bestSimilarity = 0.5; // Minimum threshold for similarity
            ImageExtractor.ImageInfo bestMatch = null;

            for (ImageExtractor.ImageInfo compareImage : compareImages) {
                if (matchedCompareImages.contains(compareImage)) {
                    continue;
                }

                // Compare images
                double similarity = 1.0 - differenceCalculator.compareImages(
                        baseImage.getImage(), compareImage.getImage());

                if (similarity > bestSimilarity) {
                    bestSimilarity = similarity;
                    bestMatch = compareImage;
                }
            }

            if (bestMatch != null) {
                matches.put(baseImage, bestMatch);
                matchedBaseImages.add(baseImage);
                matchedCompareImages.add(bestMatch);
            }
        }

        return matches;
    }

    /**
     * Compare fonts between two pages.
     *
     * @param baseFonts Fonts from base page
     * @param compareFonts Fonts from comparison page
     * @return List of differences
     */
    private List<Difference> compareFonts(
            List<FontAnalyzer.FontInfo> baseFonts,
            List<FontAnalyzer.FontInfo> compareFonts) {

        List<Difference> differences = new ArrayList<>();

        // Match fonts based on name and properties
        Map<FontAnalyzer.FontInfo, FontAnalyzer.FontInfo> matches =
                matchFonts(baseFonts, compareFonts);

        // Track fonts that have been matched
        Set<FontAnalyzer.FontInfo> matchedBaseFonts = new HashSet<>(matches.keySet());
        Set<FontAnalyzer.FontInfo> matchedCompareFonts = new HashSet<>(matches.values());

        // Find differences in matched fonts
        for (Map.Entry<FontAnalyzer.FontInfo, FontAnalyzer.FontInfo> match : matches.entrySet()) {
            FontAnalyzer.FontInfo baseFont = match.getKey();
            FontAnalyzer.FontInfo compareFont = match.getValue();

            String baseFontName = baseFont.getFontName();
            String compareFontName = compareFont.getFontName();
            baseFontName = baseFontName.contains("+") ? baseFontName.substring(baseFontName.indexOf("+") + 1) : baseFontName;
            compareFontName = compareFontName.contains("+") ? compareFontName.substring(compareFontName.indexOf("+") + 1) : compareFontName;

            String baseFamily = baseFont.getFontFamily();
            String compareFamily = compareFont.getFontFamily();
            baseFamily = baseFamily.contains("+") ? baseFamily.substring(baseFamily.indexOf("+") + 1) : baseFamily;
            compareFamily = compareFamily.contains("+") ? compareFamily.substring(compareFamily.indexOf("+") + 1) : compareFamily;

// Comparisons
            boolean nameDifferent = !baseFontName.equals(compareFontName);
            boolean familyDifferent = !baseFamily.equals(compareFamily);
            boolean embeddingDifferent = baseFont.isEmbedded() != compareFont.isEmbedded();
            boolean boldDifferent = baseFont.isBold() != compareFont.isBold();
            boolean italicDifferent = baseFont.isItalic() != compareFont.isItalic();


            if (nameDifferent || familyDifferent || embeddingDifferent ||
                    boldDifferent || italicDifferent) {

                // Create font difference
                String diffId = UUID.randomUUID().toString();

                // Create description
                StringBuilder description = new StringBuilder("Font differs: ");

                if (nameDifferent) {
                    description.append("Name changed from \"")
                            .append(baseFont.getFontName())
                            .append("\" to \"")
                            .append(compareFont.getFontName())
                            .append("\". ");
                }

                if (familyDifferent) {
                    description.append("Family changed from \"")
                            .append(baseFont.getFontFamily())
                            .append("\" to \"")
                            .append(compareFont.getFontFamily())
                            .append("\". ");
                }

                if (embeddingDifferent) {
                    description.append("Font ")
                            .append(baseFont.isEmbedded() ? "was" : "was not")
                            .append(" embedded in base and ")
                            .append(compareFont.isEmbedded() ? "is" : "is not")
                            .append(" embedded in comparison. ");
                }

                if (boldDifferent) {
                    description.append("Font ")
                            .append(baseFont.isBold() ? "was" : "was not")
                            .append(" bold in base and ")
                            .append(compareFont.isBold() ? "is" : "is not")
                            .append(" bold in comparison. ");
                }

                if (italicDifferent) {
                    description.append("Font ")
                            .append(baseFont.isItalic() ? "was" : "was not")
                            .append(" italic in base and ")
                            .append(compareFont.isItalic() ? "is" : "is not")
                            .append(" italic in comparison.");
                }

                // Create font difference
                FontDifference diff = FontDifference.builder()
                        .id(diffId)
                        .type("font")
                        .changeType("modified")
                        .severity("minor")
                        .description(description.toString())
                        .fontName(baseFont.getFontName())
                        .baseFont(baseFont.getFontName())
                        .compareFont(compareFont.getFontName())
                        .baseFontFamily(baseFont.getFontFamily())
                        .compareFontFamily(compareFont.getFontFamily())
                        .isBaseEmbedded(baseFont.isEmbedded())
                        .isCompareEmbedded(compareFont.isEmbedded())
                        .isBaseBold(baseFont.isBold())
                        .isCompareBold(compareFont.isBold())
                        .isBaseItalic(baseFont.isItalic())
                        .isCompareItalic(compareFont.isItalic())
                        .build();

                differences.add(diff);
            }
        }

        // Fonts only in base document (deleted)
        for (FontAnalyzer.FontInfo font : baseFonts) {
            if (!matchedBaseFonts.contains(font)) {
                // Create font difference for deleted font
                String diffId = UUID.randomUUID().toString();

                FontDifference diff = FontDifference.builder()
                        .id(diffId)
                        .type("font")
                        .changeType("deleted")
                        .severity("minor")
                        .description("Font \"" + font.getFontName() + "\" removed")
                        .fontName(font.getFontName())
                        .baseFont(font.getFontName())
                        .baseFontFamily(font.getFontFamily())
                        .isBaseEmbedded(font.isEmbedded())
                        .isBaseBold(font.isBold())
                        .isBaseItalic(font.isItalic())
                        .build();

                differences.add(diff);
            }
        }

        // Fonts only in compare document (added)
        for (FontAnalyzer.FontInfo font : compareFonts) {
            if (!matchedCompareFonts.contains(font)) {
                // Create font difference for added font
                String diffId = UUID.randomUUID().toString();

                FontDifference diff = FontDifference.builder()
                        .id(diffId)
                        .type("font")
                        .changeType("added")
                        .severity("minor")
                        .description("Font \"" + font.getFontName() + "\" added")
                        .fontName(font.getFontName())
                        .compareFont(font.getFontName())
                        .compareFontFamily(font.getFontFamily())
                        .isCompareEmbedded(font.isEmbedded())
                        .isCompareBold(font.isBold())
                        .isCompareItalic(font.isItalic())
                        .build();

                differences.add(diff);
            }
        }

        return differences;
    }

    /**
     * Match fonts between base and comparison pages.
     *
     * @param baseFonts Fonts from base page
     * @param compareFonts Fonts from comparison page
     * @return Map of matched fonts
     */
    private Map<FontAnalyzer.FontInfo, FontAnalyzer.FontInfo> matchFonts(
            List<FontAnalyzer.FontInfo> baseFonts,
            List<FontAnalyzer.FontInfo> compareFonts) {

        Map<FontAnalyzer.FontInfo, FontAnalyzer.FontInfo> matches = new HashMap<>();

        // First match by name (exact matches)
        for (FontAnalyzer.FontInfo baseFont : baseFonts) {
            for (FontAnalyzer.FontInfo compareFont : compareFonts) {
                if (baseFont.getFontName().equals(compareFont.getFontName())) {
                    matches.put(baseFont, compareFont);
                    break;
                }
            }
        }

        // Track fonts that have been matched
        Set<FontAnalyzer.FontInfo> matchedBaseFonts = new HashSet<>(matches.keySet());
        Set<FontAnalyzer.FontInfo> matchedCompareFonts = new HashSet<>(matches.values());

        // For unmatched fonts, try matching by family and properties
        for (FontAnalyzer.FontInfo baseFont : baseFonts) {
            if (matchedBaseFonts.contains(baseFont)) {
                continue;
            }

            for (FontAnalyzer.FontInfo compareFont : compareFonts) {
                if (matchedCompareFonts.contains(compareFont)) {
                    continue;
                }

                // Check if families match
                if (baseFont.getFontFamily().equals(compareFont.getFontFamily())) {
                    // Check if other properties match
                    boolean boldMatches = baseFont.isBold() == compareFont.isBold();
                    boolean italicMatches = baseFont.isItalic() == compareFont.isItalic();

                    if (boldMatches && italicMatches) {
                        matches.put(baseFont, compareFont);
                        matchedBaseFonts.add(baseFont);
                        matchedCompareFonts.add(compareFont);
                        break;
                    }
                }
            }
        }

        return matches;
    }

    /**
     * Create detailed page analysis for a standard comparison.
     *
     * @param comparisonId The comparison ID
     * @param pageNumber The page number (1-based)
     * @param baseDocument The base document
     * @param compareDocument The comparison document
     * @param baseText Text content from base page
     * @param compareText Text content from comparison page
     * @param textDiffs Text differences
     * @param textElementDiffs Text element differences
     * @param imageDiffs Image differences
     * @param fontDiffs Font differences
     * @param baseSize Base page dimensions
     * @param compareSize Comparison page dimensions
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

        // Organize differences by source document
        List<Difference> baseDifferences = new ArrayList<>();
        List<Difference> compareDifferences = new ArrayList<>();

        // Add text differences
        for (TextDifference diff : textDiffs) {
            if (!"added".equals(diff.getChangeType())) {
                baseDifferences.add(diff);
            }

            if (!"deleted".equals(diff.getChangeType())) {
                compareDifferences.add(diff);
            }
        }

        // Add text element differences
        for (Difference diff : textElementDiffs) {
            if (!"added".equals(diff.getChangeType())) {
                baseDifferences.add(diff);
            }

            if (!"deleted".equals(diff.getChangeType())) {
                compareDifferences.add(diff);
            }
        }

        // Add image differences
        for (Difference diff : imageDiffs) {
            if (!"added".equals(diff.getChangeType())) {
                baseDifferences.add(diff);
            }

            if (!"deleted".equals(diff.getChangeType())) {
                compareDifferences.add(diff);
            }
        }

        // Add font differences
        for (Difference diff : fontDiffs) {
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
     * Calculate similarity between two text strings.
     *
     * @param text1 First text
     * @param text2 Second text
     * @return Similarity score between 0.0 and 1.0
     */
    private double calculateTextSimilarity(String text1, String text2) {
        if (text1 == null || text2 == null) {
            return 0.0;
        }

        if (text1.equals(text2)) {
            return 1.0;
        }

        // Calculate Levenshtein distance
        int[][] distance = new int[text1.length() + 1][text2.length() + 1];

        for (int i = 0; i <= text1.length(); i++) {
            distance[i][0] = i;
        }

        for (int j = 0; j <= text2.length(); j++) {
            distance[0][j] = j;
        }

        for (int i = 1; i <= text1.length(); i++) {
            for (int j = 1; j <= text2.length(); j++) {
                if (text1.charAt(i - 1) == text2.charAt(j - 1)) {
                    distance[i][j] = distance[i - 1][j - 1];
                } else {
                    distance[i][j] = Math.min(
                            distance[i - 1][j] + 1,     // Delete
                            Math.min(
                                    distance[i][j - 1] + 1,     // Insert
                                    distance[i - 1][j - 1] + 1  // Substitute
                            )
                    );
                }
            }
        }

        int maxLength = Math.max(text1.length(), text2.length());
        if (maxLength == 0) {
            return 1.0; // Both strings are empty
        }

        return 1.0 - (double) distance[text1.length()][text2.length()] / maxLength;
    }

    /**
     * Compare pages for a specific document pair.
     *
     * @param comparison The comparison entity
     * @param baseDocument The base document
     * @param compareDocument The comparison document
     * @param baseStartPage Start page in base document
     * @param baseEndPage End page in base document
     * @param compareStartPage Start page in comparison document
     * @param compareEndPage End page in comparison document
     * @param pairIndex Index of the document pair
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

        // Load PDF documents
        PDDocument basePdf = PDDocument.load(new File(baseDocument.getFilePath()));
        PDDocument comparePdf = PDDocument.load(new File(compareDocument.getFilePath()));

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

                // Split differences by source document
                List<Difference> baseTextDiffs = textDiffs.stream()
                        .filter(diff -> !"added".equals(diff.getChangeType()))
                        .collect(Collectors.toList());

                List<Difference> compareTextDiffs = textDiffs.stream()
                        .filter(diff -> !"deleted".equals(diff.getChangeType()))
                        .collect(Collectors.toList());

                baseDifferences.addAll(baseTextDiffs);
                compareDifferences.addAll(compareTextDiffs);

                // Get page dimensions if available
                PDRectangle baseSize = pageInBase ? basePdf.getPage(basePageIdx).getMediaBox() : null;
                PDRectangle compareSize = pageInCompare ? comparePdf.getPage(comparePageIdx).getMediaBox() : null;

                // Compare text elements (style, fonts, positioning) if both pages exist
                if (pageInBase && pageInCompare) {
                    List<TextElement> baseElements =
                            textExtractor.extractTextElementsFromPage(basePdf, basePageIdx);
                    List<TextElement> compareElements =
                            textExtractor.extractTextElementsFromPage(comparePdf, comparePageIdx);

                    List<Difference> textElementDiffs = compareTextElements(baseElements, compareElements);

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
                    List<ImageExtractor.ImageInfo> baseImages = imageExtractor.extractImagesFromPage(
                            basePdf, basePageIdx, comparisonDir.resolve("base_images"));
                    List<ImageExtractor.ImageInfo> compareImages = imageExtractor.extractImagesFromPage(
                            comparePdf, comparePageIdx, comparisonDir.resolve("compare_images"));

                    List<Difference> imageDiffs = compareImages(baseImages, compareImages);

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
                    List<FontAnalyzer.FontInfo> baseFonts = fontAnalyzer.analyzeFontsOnPage(
                            basePdf, basePageIdx, comparisonDir.resolve("base_fonts"));
                    List<FontAnalyzer.FontInfo> compareFonts = fontAnalyzer.analyzeFontsOnPage(
                            comparePdf, comparePageIdx, comparisonDir.resolve("compare_fonts"));

                    List<Difference> fontDiffs = compareFonts(baseFonts, compareFonts);

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
        }
    }

    /**
     * Create detailed page analysis for a document pair in smart comparison mode.
     *
     * @param comparisonId The comparison ID
     * @param pairIndex The pair index
     * @param relPageNumber The relative page number (1-based) within the pair
     * @param baseDocument The base document
     * @param compareDocument The comparison document
     * @param baseText Text content from base page
     * @param compareText Text content from comparison page
     * @param baseDifferences Differences in base document
     * @param compareDifferences Differences in comparison document
     * @param baseSize Base page dimensions
     * @param compareSize Comparison page dimensions
     * @param pageExistsInBase Whether the page exists in base document
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

    /**
     * Parse RGB values from a CSS color string.
     *
     * @param color The color string (like "rgb(r,g,b)")
     * @return Array of RGB values
     */
    private int[] parseRgb(String color) {
        if (color == null) {
            return null;
        }

        // Handle rgb() format
        if (color.startsWith("rgb(") && color.endsWith(")")) {
            String[] parts = color.substring(4, color.length() - 1).split(",");
            if (parts.length >= 3) {
                try {
                    int r = Integer.parseInt(parts[0].trim());
                    int g = Integer.parseInt(parts[1].trim());
                    int b = Integer.parseInt(parts[2].trim());
                    return new int[]{ r, g, b };
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }

        // Handle hex format (#rrggbb)
        if (color.startsWith("#") && (color.length() == 7 || color.length() == 4)) {
            try {
                if (color.length() == 7) {
                    int r = Integer.parseInt(color.substring(1, 3), 16);
                    int g = Integer.parseInt(color.substring(3, 5), 16);
                    int b = Integer.parseInt(color.substring(5, 7), 16);
                    return new int[]{ r, g, b };
                } else {
                    // Short form #rgb
                    int r = Integer.parseInt(color.substring(1, 2) + color.substring(1, 2), 16);
                    int g = Integer.parseInt(color.substring(2, 3) + color.substring(2, 3), 16);
                    int b = Integer.parseInt(color.substring(3, 4) + color.substring(3, 4), 16);
                    return new int[]{ r, g, b };
                }
            } catch (NumberFormatException e) {
                return null;
            }
        }

        return null;
    }

    /**
     * Compare two colors.
     *
     * @param color1 First color
     * @param color2 Second color
     * @return True if colors are similar
     */
    private boolean compareColors(String color1, String color2) {
        if (color1 == null || color2 == null) {
            return color1 == color2;
        }

        if (color1.equals(color2)) {
            return true;
        }

        // Parse RGB values
        int[] rgb1 = parseRgb(color1);
        int[] rgb2 = parseRgb(color2);

        if (rgb1 == null || rgb2 == null) {
            return false;
        }

        // Calculate color distance
        double distance = Math.sqrt(
                Math.pow(rgb1[0] - rgb2[0], 2) +
                        Math.pow(rgb1[1] - rgb2[1], 2) +
                        Math.pow(rgb1[2] - rgb2[2], 2)
        );

        // Colors are similar if distance is less than a threshold
        return distance < 30; // Threshold of 30 (out of 441.67 max)
    }

    /**
     * Text element match structure.
     */
    private static class ElementMatch {
        private final TextElement baseElement;
        private final TextElement compareElement;
        private final double similarity;

        public ElementMatch(TextElement baseElement,
                            TextElement compareElement,
                            double similarity) {
            this.baseElement = baseElement;
            this.compareElement = compareElement;
            this.similarity = similarity;
        }

        public TextElement getBaseElement() { return baseElement; }
        public TextElement getCompareElement() { return compareElement; }
        public double getSimilarity() { return similarity; }
    }
}