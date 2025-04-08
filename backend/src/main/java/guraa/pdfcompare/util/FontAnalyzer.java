package guraa.pdfcompare.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class FontAnalyzer {

    private final FontHandler fontHandler;
    private final ObjectMapper objectMapper;

    // This is a cache map that stores font info by document ID + page
    private final Map<String, List<FontInfo>> fontInfoCache = new ConcurrentHashMap<>();

    // Flag to control detailed font analysis with a lower default value for faster operation
    @Value("${app.font.detailed-analysis:false}")
    private boolean detailedFontAnalysis;

    // Custom thread pool for font analyses to limit impact on system resources
    private static final ForkJoinPool fontProcessingPool = new ForkJoinPool(
            Math.min(4, Runtime.getRuntime().availableProcessors() / 2), // Use max half the processors, but no more than 4
            ForkJoinPool.defaultForkJoinWorkerThreadFactory,
            null,
            true
    );

    /**
     * Analyze fonts used on a specific page in a PDF document.
     * This method first checks if pre-extracted font information exists in the output directory.
     * If it does, it loads that information instead of re-extracting the fonts.
     *
     * @param document The PDF document
     * @param pageIndex The zero-based page index
     * @param outputDir Output directory to save font information
     * @return List of information about fonts on the page
     */
    public List<FontInfo> analyzeFontsOnPage(PDDocument document, int pageIndex, Path outputDir) {
        // Generate a unique cache key
        String cacheKey = document.getDocumentId() + "_" + pageIndex;

        // Check in-memory cache first (fastest)
        List<FontInfo> cachedFonts = fontInfoCache.get(cacheKey);
        if (cachedFonts != null) {
            return cachedFonts;
        }

        int pageNumber = pageIndex + 1;

        try {
            // First check if we already have font information saved
            Path fontInfoPath = outputDir.resolve(String.format("page_%d_fonts.json", pageNumber));
            if (Files.exists(fontInfoPath)) {
                try {
                    // Try to load the pre-extracted font information
                    List<FontInfo> loadedFonts = objectMapper.readValue(fontInfoPath.toFile(),
                            objectMapper.getTypeFactory().constructCollectionType(List.class, FontInfo.class));

                    if (loadedFonts != null && !loadedFonts.isEmpty()) {
                        log.debug("Loaded {} pre-extracted fonts for page {} from {}",
                                loadedFonts.size(), pageNumber, fontInfoPath);

                        // Store in cache for future lookups
                        fontInfoCache.put(cacheKey, loadedFonts);
                        return loadedFonts;
                    }
                } catch (Exception e) {
                    log.debug("Error loading pre-extracted font information: {}", e.getMessage());
                }
            }

            // Extract fonts using the optimized font handler
            List<FontHandler.FontInfo> handlerFonts = fontHandler.extractFontsFromPage(document, pageIndex);

            // Convert handler fonts to our FontInfo format
            List<FontInfo> fontInfoList = new ArrayList<>(handlerFonts.size());
            for (FontHandler.FontInfo handlerFont : handlerFonts) {
                fontInfoList.add(convertToFontInfo(handlerFont));
            }

            // Update the cache
            fontInfoCache.put(cacheKey, fontInfoList);

            // Asynchronously save to disk without blocking
            if (!fontInfoList.isEmpty()) {
                saveJsonAsync(fontInfoList, outputDir, pageNumber);
            }

            return fontInfoList;
        } catch (Exception e) {
            log.warn("Error analyzing fonts on page {}: {}", pageNumber, e.getMessage());
            // Return empty list instead of null
            return Collections.emptyList();
        }
    }

    /**
     * Asynchronously save font information as JSON to avoid blocking
     *
     * @param fontInfoList Font information list
     * @param outputDir Output directory
     * @param pageNumber Page number
     */
    @Async("taskExecutor")
    public void saveJsonAsync(List<FontInfo> fontInfoList, Path outputDir, int pageNumber) {
        try {
            // Add a small random delay to prevent disk I/O bursts if multiple calls happen
            if (ThreadLocalRandom.current().nextBoolean()) {
                TimeUnit.MILLISECONDS.sleep(ThreadLocalRandom.current().nextInt(5, 20));
            }

            // Ensure the output directory exists
            Files.createDirectories(outputDir);

            // Save the font information as JSON
            File fontInfoFile = outputDir.resolve(String.format("page_%d_fonts.json", pageNumber)).toFile();
            objectMapper.writeValue(fontInfoFile, fontInfoList);

            log.debug("Saved font information for page {} to {}", pageNumber, fontInfoFile.getPath());
        } catch (Exception e) {
            log.warn("Error saving font information as JSON for page {}: {}", pageNumber, e.getMessage());
        }
    }

    /**
     * Convert from FontHandler.FontInfo to FontInfo.
     */
    private FontInfo convertToFontInfo(FontHandler.FontInfo handlerInfo) {
        FontInfo info = new FontInfo();
        info.setId(handlerInfo.getId());
        info.setFontName(handlerInfo.getFontName());
        info.setPdfName(handlerInfo.getPdfName());
        info.setFontFamily(handlerInfo.getFontFamily());
        info.setEmbedded(handlerInfo.isEmbedded());
        info.setIsBold(handlerInfo.isBold());
        info.setIsItalic(handlerInfo.isItalic());
        info.setEncoding(handlerInfo.getEncoding());
        info.setFontFilePath(handlerInfo.getFontFilePath());

        // Set the damaged flag if applicable
        if (handlerInfo.isDamaged()) {
            info.setDamaged(true);
        }

        return info;
    }

    /**
     * Preload and analyze fonts for multiple pages efficiently
     *
     * @param document PDF document
     * @param startPage Start page (0-based)
     * @param endPage End page (0-based)
     * @param outputDir Output directory
     */
    public void preloadFontsForPages(PDDocument document, int startPage, int endPage, Path outputDir) {
        // Skip if too many pages to analyze
        if (endPage - startPage > 20) {
            log.debug("Skipping font preloading for large page range {} to {}", startPage, endPage);
            return;
        }

        // Submit tasks to the dedicated thread pool
        fontProcessingPool.submit(() -> {
            try {
                // First process critical pages (first, middle, last)
                int pageCount = endPage - startPage + 1;
                if (pageCount > 3) {
                    analyzeFontsOnPage(document, startPage, outputDir);
                    analyzeFontsOnPage(document, startPage + pageCount / 2, outputDir);
                    analyzeFontsOnPage(document, endPage, outputDir);
                } else {
                    // For small ranges, process sequentially
                    for (int i = startPage; i <= endPage; i++) {
                        analyzeFontsOnPage(document, i, outputDir);
                    }
                }
            } catch (Exception e) {
                log.warn("Error during font preloading: {}", e.getMessage());
            }
        });
    }

    /**
     * Quickly estimate if two font lists are visually different
     * This provides a fast way to determine if full font comparison is needed
     *
     * @param baseFonts List of base fonts
     * @param compareFonts List of compare fonts
     * @return true if differences are likely, false if fonts appear similar
     */
    public boolean hasFontDifferences(List<FontInfo> baseFonts, List<FontInfo> compareFonts) {
        // Quick checks first
        if ((baseFonts == null || baseFonts.isEmpty()) && (compareFonts == null || compareFonts.isEmpty())) {
            return false;
        }

        if ((baseFonts == null || baseFonts.isEmpty()) || (compareFonts == null || compareFonts.isEmpty())) {
            return true;
        }

        if (baseFonts.size() != compareFonts.size()) {
            return true;
        }

        // Check for significant font name or style differences
        int similarFontsCount = 0;
        for (FontInfo baseFont : baseFonts) {
            boolean foundMatch = false;
            for (FontInfo compareFont : compareFonts) {
                // Check for similar fonts (font family or name match)
                if (matchFontNames(baseFont, compareFont)) {
                    // Check if styles match
                    if (baseFont.isBold() == compareFont.isBold() &&
                            baseFont.isItalic() == compareFont.isItalic()) {
                        similarFontsCount++;
                        foundMatch = true;
                        break;
                    }
                }
            }
        }

        // Calculate a similarity ratio
        double similarity = (double) similarFontsCount / baseFonts.size();

        // Return true if fonts appear significantly different
        return similarity < 0.75;
    }

    /**
     * Check if two fonts likely represent the same font
     *
     * @param font1 First font
     * @param font2 Second font
     * @return true if fonts appear similar
     */
    private boolean matchFontNames(FontInfo font1, FontInfo font2) {
        // Check for exact font name or PDF name match
        if (font1.getFontName() != null && font2.getFontName() != null &&
                font1.getFontName().equals(font2.getFontName())) {
            return true;
        }

        // Check for font family match
        if (font1.getFontFamily() != null && font2.getFontFamily() != null &&
                font1.getFontFamily().equals(font2.getFontFamily())) {
            return true;
        }

        // Normalize font names and check for similarity
        String name1 = normalizeFontName(font1.getFontName());
        String name2 = normalizeFontName(font2.getFontName());

        return !name1.isEmpty() && !name2.isEmpty() && name1.equals(name2);
    }

    /**
     * Normalize a font name by removing prefixes, suffixes, and version information
     *
     * @param fontName Font name to normalize
     * @return Normalized font name
     */
    private String normalizeFontName(String fontName) {
        if (fontName == null) {
            return "";
        }

        // Remove NTPT and similar prefixes
        if (fontName.contains("+")) {
            fontName = fontName.substring(fontName.indexOf("+") + 1);
        }

        // Remove suffixes like ",Bold" or ",Italic"
        if (fontName.contains(",")) {
            fontName = fontName.substring(0, fontName.indexOf(","));
        }

        // Remove version numbers like "-1.23"
        if (fontName.contains("-")) {
            fontName = fontName.substring(0, fontName.indexOf("-"));
        }

        return fontName.trim();
    }

    /**
     * Contains information about a font.
     */
    public static class FontInfo {
        private String id;
        private String fontName;
        private String pdfName;
        private String fontFamily;
        private boolean isEmbedded;
        private boolean isBold;
        private boolean isItalic;
        private String encoding;
        private String fontFilePath;
        private boolean isDamaged = false;

        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getFontName() { return fontName; }
        public void setFontName(String fontName) { this.fontName = fontName; }

        public String getPdfName() { return pdfName; }
        public void setPdfName(String pdfName) { this.pdfName = pdfName; }

        public String getFontFamily() { return fontFamily; }
        public void setFontFamily(String fontFamily) { this.fontFamily = fontFamily; }

        public boolean isEmbedded() { return isEmbedded; }
        public void setEmbedded(boolean embedded) { isEmbedded = embedded; }

        public boolean isBold() { return isBold; }
        public void setIsBold(boolean bold) { isBold = bold; }

        public boolean isItalic() { return isItalic; }
        public void setIsItalic(boolean italic) { isItalic = italic; }

        public String getEncoding() { return encoding; }
        public void setEncoding(String encoding) { this.encoding = encoding; }

        public String getFontFilePath() { return fontFilePath; }
        public void setFontFilePath(String fontFilePath) { this.fontFilePath = fontFilePath; }

        public boolean isDamaged() { return isDamaged; }
        public void setDamaged(boolean damaged) { isDamaged = damaged; }
    }
}