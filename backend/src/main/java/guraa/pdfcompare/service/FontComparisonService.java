package guraa.pdfcompare.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import guraa.pdfcompare.model.PdfDocument;
import guraa.pdfcompare.model.difference.Difference;
import guraa.pdfcompare.model.difference.FontDifference;
import guraa.pdfcompare.util.DifferenceCalculator;
import guraa.pdfcompare.util.FontAnalyzer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Service for comparing fonts between PDF documents.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FontComparisonService {

    private final FontAnalyzer fontAnalyzer;
    private final DifferenceCalculator differenceCalculator;
    private final ObjectMapper objectMapper;

    /**
     * Compare fonts on a page between two PDF documents.
     *
     * @param basePdf Base PDF document
     * @param comparePdf Compare PDF document
     * @param pageIndex Page index to compare
     * @param baseDocument Base document info
     * @param compareDocument Compare document info
     * @param outputDir Directory for analysis output
     * @return List of font differences
     * @throws IOException If there's an error processing the fonts
     */
    public List<Difference> compareFontsOnPage(
            PDDocument basePdf,
            PDDocument comparePdf,
            int pageIndex,
            PdfDocument baseDocument,
            PdfDocument compareDocument,
            Path outputDir) throws IOException {

        // Try to use pre-extracted font information first
        List<FontAnalyzer.FontInfo> baseFonts = loadPreExtractedFonts(
                baseDocument.getFileId(), pageIndex + 1);

        // If pre-extracted fonts not available, extract them now
        if (baseFonts == null || baseFonts.isEmpty()) {
            baseFonts = fontAnalyzer.analyzeFontsOnPage(
                    basePdf, pageIndex, outputDir.resolve("base_fonts"));
        }

        List<FontAnalyzer.FontInfo> compareFonts = loadPreExtractedFonts(
                compareDocument.getFileId(), pageIndex + 1);

        // If pre-extracted fonts not available, extract them now
        if (compareFonts == null || compareFonts.isEmpty()) {
            compareFonts = fontAnalyzer.analyzeFontsOnPage(
                    comparePdf, pageIndex, outputDir.resolve("compare_fonts"));
        }

        return compareFonts(baseFonts, compareFonts);
    }

    /**
     * Compare fonts on a page between two PDF documents in document pair mode.
     *
     * @param basePdf Base PDF document
     * @param comparePdf Compare PDF document
     * @param basePageIndex Base page index
     * @param comparePageIndex Compare page index
     * @param baseDocument Base document info
     * @param compareDocument Compare document info
     * @param outputDir Directory for analysis output
     * @return List of font differences
     * @throws IOException If there's an error processing the fonts
     */
    public List<Difference> compareFontsOnPage(
            PDDocument basePdf,
            PDDocument comparePdf,
            int basePageIndex,
            int comparePageIndex,
            PdfDocument baseDocument,
            PdfDocument compareDocument,
            Path outputDir) throws IOException {

        // Try to use pre-extracted font information first
        List<FontAnalyzer.FontInfo> baseFonts = loadPreExtractedFonts(
                baseDocument.getFileId(), basePageIndex + 1);

        // If pre-extracted fonts not available, extract them now
        if (baseFonts == null || baseFonts.isEmpty()) {
            baseFonts = fontAnalyzer.analyzeFontsOnPage(
                    basePdf, basePageIndex, outputDir.resolve("base_fonts"));
        }

        List<FontAnalyzer.FontInfo> compareFonts = loadPreExtractedFonts(
                compareDocument.getFileId(), comparePageIndex + 1);

        // If pre-extracted fonts not available, extract them now
        if (compareFonts == null || compareFonts.isEmpty()) {
            compareFonts = fontAnalyzer.analyzeFontsOnPage(
                    comparePdf, comparePageIndex, outputDir.resolve("compare_fonts"));
        }

        return compareFonts(baseFonts, compareFonts);
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
            baseFontName = baseFontName != null && baseFontName.contains("+") ?
                    baseFontName.substring(baseFontName.indexOf("+") + 1) : baseFontName;
            compareFontName = compareFontName != null && compareFontName.contains("+") ?
                    compareFontName.substring(compareFontName.indexOf("+") + 1) : compareFontName;

            String baseFamily = baseFont.getFontFamily();
            String compareFamily = compareFont.getFontFamily();
            baseFamily = baseFamily != null && baseFamily.contains("+") ?
                    baseFamily.substring(baseFamily.indexOf("+") + 1) : baseFamily;
            compareFamily = compareFamily != null && compareFamily.contains("+") ?
                    compareFamily.substring(compareFamily.indexOf("+") + 1) : compareFamily;

            // Comparisons
            boolean nameDifferent = !Objects.equals(baseFontName, compareFontName);
            boolean familyDifferent = !Objects.equals(baseFamily, compareFamily);
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
                        .baseFontName(baseFont.getFontName()) // Corrected method name
                        .compareFontName(compareFont.getFontName()) // Corrected method name
                        .baseFontFamily(baseFont.getFontFamily())
                        .compareFontFamily(compareFont.getFontFamily())
                        .baseEmbedded(baseFont.isEmbedded()) // Corrected method name
                        .compareEmbedded(compareFont.isEmbedded()) // Corrected method name
                        .baseBold(baseFont.isBold()) // Corrected method name
                        .compareBold(compareFont.isBold()) // Corrected method name
                        .baseItalic(baseFont.isItalic()) // Corrected method name
                        .compareItalic(compareFont.isItalic()) // Corrected method name
                        .build();

                // Use default positioning for fonts (top of the page)
                double pageWidth = 612; // standard letter size
                double pageHeight = 792;
                differenceCalculator.estimatePositionForDifference(diff, pageWidth, pageHeight, 0.1);

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
                        .baseFontName(font.getFontName()) // Corrected method name
                        .baseFontFamily(font.getFontFamily())
                        .baseEmbedded(font.isEmbedded()) // Corrected method name
                        .baseBold(font.isBold()) // Corrected method name
                        .baseItalic(font.isItalic()) // Corrected method name
                        .build();

                // Use default positioning for fonts (top of the page)
                double pageWidth = 612; // standard letter size
                double pageHeight = 792;
                differenceCalculator.estimatePositionForDifference(diff, pageWidth, pageHeight, 0.15);

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
                        .compareFontName(font.getFontName()) // Corrected method name
                        .compareFontFamily(font.getFontFamily())
                        .compareEmbedded(font.isEmbedded()) // Corrected method name
                        .compareBold(font.isBold()) // Corrected method name
                        .compareItalic(font.isItalic()) // Corrected method name
                        .build();

                // Use default positioning for fonts (top of the page)
                double pageWidth = 612; // standard letter size
                double pageHeight = 792;
                differenceCalculator.estimatePositionForDifference(diff, pageWidth, pageHeight, 0.2);

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
                if (baseFont.getFontName() != null &&
                        baseFont.getFontName().equals(compareFont.getFontName())) {
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

                // Check if families match (with null safety)
                if (baseFont.getFontFamily() != null && compareFont.getFontFamily() != null &&
                        baseFont.getFontFamily().equals(compareFont.getFontFamily())) {
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
     * Load pre-extracted font information from the document's fonts directory.
     *
     * @param documentId The document ID
     * @param pageNumber The page number (1-based)
     * @return List of font information, or null if not available
     */
    private List<FontAnalyzer.FontInfo> loadPreExtractedFonts(String documentId, int pageNumber) {
        try {
            // Check if the fonts directory exists for this document
            Path fontsDir = Paths.get("uploads", "documents", documentId, "fonts");
            if (!Files.exists(fontsDir)) {
                return null;
            }

            // Look for font information files for this page
            Path fontInfoPath = fontsDir.resolve("page_" + pageNumber + "_fonts.json");
            if (!Files.exists(fontInfoPath)) {
                return null;
            }

            // Read and parse the font information
            try {
                String fontInfoJson = new String(Files.readAllBytes(fontInfoPath));
                // If the file exists but is empty or invalid, return null
                if (fontInfoJson == null || fontInfoJson.trim().isEmpty()) {
                    return null;
                }

                // Parse the JSON into a list of FontInfo objects
                try {
                    return objectMapper.readValue(fontInfoPath.toFile(),
                            objectMapper.getTypeFactory().constructCollectionType(List.class, FontAnalyzer.FontInfo.class));
                } catch (Exception e) {
                    log.warn("Error parsing font information for document {} page {}: {}",
                            documentId, pageNumber, e.getMessage());
                    return null;
                }
            } catch (Exception e) {
                log.warn("Error reading font information for document {} page {}: {}",
                        documentId, pageNumber, e.getMessage());
                return null;
            }
        } catch (Exception e) {
            log.warn("Error accessing font information for document {} page {}: {}",
                    documentId, pageNumber, e.getMessage());
            return null;
        }
    }
}
