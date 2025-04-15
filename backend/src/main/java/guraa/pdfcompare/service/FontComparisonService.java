package guraa.pdfcompare.service;

import guraa.pdfcompare.model.difference.FontDifference;
import guraa.pdfcompare.model.PdfDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * Service for comparing fonts between PDF documents.
 * This service extracts font information from PDFs and detects differences
 * in font usage, substitution, and properties.
 */
@Slf4j
@Service
public class FontComparisonService {

    private final ExecutorService executorService;
    
    /**
     * Constructor with qualifier to specify which executor service to use.
     * 
     * @param executorService The executor service for comparison operations
     */
    public FontComparisonService(@Qualifier("comparisonExecutor") ExecutorService executorService) {
        this.executorService = executorService;
    }

    @Value("${app.font.detailed-analysis:false}")
    private boolean detailedAnalysis;

    @Value("${app.font.extraction-timeout-ms:2000}")
    private int extractionTimeoutMs;

    // Cache of font comparison results
    private final ConcurrentHashMap<String, CompletableFuture<List<FontDifference>>> comparisonTasks = new ConcurrentHashMap<>();

    /**
     * Compare fonts between two pages.
     *
     * @param baseDocument The base document
     * @param compareDocument The document to compare against the base
     * @param basePageNumber The page number in the base document (1-based)
     * @param comparePageNumber The page number in the compare document (1-based)
     * @return A list of font differences
     * @throws IOException If there is an error comparing the fonts
     */
    public List<FontDifference> compareFonts(
            PdfDocument baseDocument, PdfDocument compareDocument,
            int basePageNumber, int comparePageNumber) throws IOException {
        
        String cacheKey = baseDocument.getFileId() + "_" + basePageNumber + "_" +
                compareDocument.getFileId() + "_" + comparePageNumber;
        
        // Check if we already have a comparison task for these pages
        return comparisonTasks.computeIfAbsent(cacheKey, key -> {
            // Submit a new comparison task
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return doCompareFonts(baseDocument, compareDocument, basePageNumber, comparePageNumber);
                } catch (IOException e) {
                    log.error("Error comparing fonts: {}", e.getMessage(), e);
                    return new ArrayList<>();
                }
            }, executorService);
        }).join(); // Wait for the task to complete
    }

    /**
     * Perform the actual font comparison.
     *
     * @param baseDocument The base document
     * @param compareDocument The document to compare against the base
     * @param basePageNumber The page number in the base document (1-based)
     * @param comparePageNumber The page number in the compare document (1-based)
     * @return A list of font differences
     * @throws IOException If there is an error comparing the fonts
     */
    private List<FontDifference> doCompareFonts(
            PdfDocument baseDocument, PdfDocument compareDocument,
            int basePageNumber, int comparePageNumber) throws IOException {
        
        // Extract font information from the pages
        Map<String, FontInfo> baseFonts = extractFontInfo(baseDocument, basePageNumber);
        Map<String, FontInfo> compareFonts = extractFontInfo(compareDocument, comparePageNumber);
        
        // Compare the fonts
        List<FontDifference> differences = new ArrayList<>();
        
        // First pass: Find fonts that are in both documents but have differences
        for (Map.Entry<String, FontInfo> entry : baseFonts.entrySet()) {
            String fontName = entry.getKey();
            FontInfo baseFont = entry.getValue();
            
            if (compareFonts.containsKey(fontName)) {
                // Font exists in both documents, check for differences
                FontInfo compareFont = compareFonts.get(fontName);
                
                if (!baseFont.equals(compareFont)) {
                    // Fonts are different
                    differences.add(createFontDifference(
                            baseFont, compareFont, "modified", basePageNumber, comparePageNumber));
                }
            } else {
                // Font exists only in the base document
                differences.add(createFontDifference(
                        baseFont, null, "deleted", basePageNumber, comparePageNumber));
            }
        }
        
        // Second pass: Find fonts that are only in the compare document
        for (Map.Entry<String, FontInfo> entry : compareFonts.entrySet()) {
            String fontName = entry.getKey();
            
            if (!baseFonts.containsKey(fontName)) {
                // Font exists only in the compare document
                FontInfo compareFont = entry.getValue();
                differences.add(createFontDifference(
                        null, compareFont, "added", basePageNumber, comparePageNumber));
            }
        }
        
        return differences;
    }

    /**
     * Extract font information from a page.
     *
     * @param document The document
     * @param pageNumber The page number (1-based)
     * @return A map of font names to font information
     * @throws IOException If there is an error extracting the font information
     */
    private Map<String, FontInfo> extractFontInfo(PdfDocument document, int pageNumber) throws IOException {
        // In a real implementation, this would use iText to extract font information from the PDF
        // For now, we'll return an empty map
        return new HashMap<>();
    }

    /**
     * Create a font difference.
     *
     * @param baseFont The base font
     * @param compareFont The compare font
     * @param changeType The change type (added, deleted, modified)
     * @param basePageNumber The page number in the base document (1-based)
     * @param comparePageNumber The page number in the compare document (1-based)
     * @return The font difference
     */
    private FontDifference createFontDifference(
            FontInfo baseFont, FontInfo compareFont, String changeType,
            int basePageNumber, int comparePageNumber) {
        
        FontDifference.FontDifferenceBuilder builder = FontDifference.builder()
                .id(UUID.randomUUID().toString())
                .type("font")
                .changeType(changeType)
                .basePageNumber(basePageNumber)
                .comparePageNumber(comparePageNumber);
        
        // Set font properties
        if (baseFont != null) {
            builder.baseFontName(baseFont.getName())
                   .baseFontFamily(baseFont.getFamily())
                   .baseFontStyle(baseFont.getStyle())
                   .baseFontSize(baseFont.getSize())
                   .baseFontEncoding(baseFont.getEncoding())
                   .baseFontEmbedded(baseFont.isEmbedded());
        }
        
        if (compareFont != null) {
            builder.compareFontName(compareFont.getName())
                   .compareFontFamily(compareFont.getFamily())
                   .compareFontStyle(compareFont.getStyle())
                   .compareFontSize(compareFont.getSize())
                   .compareFontEncoding(compareFont.getEncoding())
                   .compareFontEmbedded(compareFont.isEmbedded());
        }
        
        // Set severity based on the type of change
        switch (changeType) {
            case "added":
            case "deleted":
                builder.severity("major");
                break;
            case "modified":
                // Check if the font family changed
                if (baseFont != null && compareFont != null &&
                        !baseFont.getFamily().equals(compareFont.getFamily())) {
                    builder.severity("major");
                } else if (baseFont != null && compareFont != null &&
                        !baseFont.getStyle().equals(compareFont.getStyle())) {
                    builder.severity("minor");
                } else {
                    builder.severity("cosmetic");
                }
                break;
        }
        
        // Set description based on change type
        switch (changeType) {
            case "added":
                builder.description("Font added: " + compareFont.getName());
                break;
            case "deleted":
                builder.description("Font deleted: " + baseFont.getName());
                break;
            case "modified":
                builder.description("Font modified: " + baseFont.getName() +
                        " to " + compareFont.getName());
                break;
        }
        
        return builder.build();
    }

    /**
     * Compare fonts between two documents.
     *
     * @param baseDocument The base document
     * @param compareDocument The document to compare against the base
     * @param pagePairs The page pairs to compare
     * @return A map of page pairs to font differences
     * @throws IOException If there is an error comparing the fonts
     */
    public Map<PagePair, List<FontDifference>> compareFonts(
            PdfDocument baseDocument, PdfDocument compareDocument,
            List<PagePair> pagePairs) throws IOException {
        
        Map<PagePair, List<FontDifference>> result = new HashMap<>();
        
        for (PagePair pagePair : pagePairs) {
            if (pagePair.isMatched()) {
                // Compare the matched pages
                List<FontDifference> differences = compareFonts(
                        baseDocument, compareDocument,
                        pagePair.getBasePageNumber(), pagePair.getComparePageNumber());
                
                result.put(pagePair, differences);
            }
        }
        
        return result;
    }

    /**
     * Information about a font in a PDF document.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class FontInfo {
        private String name;
        private String family;
        private String style;
        private float size;
        private String encoding;
        private boolean embedded;
        private int usageCount;
    }
}
