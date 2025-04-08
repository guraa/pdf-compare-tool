package guraa.pdfcompare.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Optimized utility class for robust font handling that can recover from PDFBox font errors.
 * Includes caching, timeout mechanisms, and performance optimizations.
 */
@Slf4j
@Component
public class FontHandler {

    // Cache for font information to avoid repeated extraction
    private final Map<String, FontInfo> fontCache = new ConcurrentHashMap<>();

    // Flag to enable/disable detailed font analysis
    @Value("${app.font.detailed-analysis:false}")
    private boolean detailedFontAnalysis;

    // Maximum time to spend on font extraction per page (in milliseconds)
    @Value("${app.font.extraction-timeout-ms:1000}")
    private long fontExtractionTimeoutMs;

    /**
     * Safely extracts font information from a PDF page, handling potential font errors.
     * Includes optimizations for performance and robustness.
     *
     * @param document The PDF document
     * @param pageIndex The 0-based page index
     * @return List of font information objects
     */
    public List<FontInfo> extractFontsFromPage(PDDocument document, int pageIndex) {
        // Create a unique hash key for caching
        String documentHash = document.getDocumentId() + "_" + pageIndex;

        // Check if we've already processed this page
        List<FontInfo> cachedFontList = getCachedFontList(documentHash);
        if (cachedFontList != null) {
            return cachedFontList;
        }

        List<FontInfo> fontInfoList = new ArrayList<>();

        // Skip detailed font analysis if disabled
        if (!detailedFontAnalysis) {
            // Return minimal font info (just names) for better performance
            List<FontInfo> minimalInfo = extractMinimalFontInfo(document, pageIndex);
            // Store in cache
            storeFontListInCache(documentHash, minimalInfo);
            return minimalInfo;
        }

        // Set a timeout for font extraction
        long startTime = System.currentTimeMillis();
        AtomicBoolean timeoutOccurred = new AtomicBoolean(false);

        try {
            PDPage page = document.getPage(pageIndex);
            PDResources resources = page.getResources();

            if (resources == null) {
                log.debug("No resources found on page {}", pageIndex + 1);
                return fontInfoList;
            }

            // Use LinkedHashMap to maintain font order consistency
            LinkedHashMap<String, PDFont> fontMap = new LinkedHashMap<>();

            // Extract fonts with timeout protection
            try {
                // First batch process all font names to avoid repeated iterations
                for (COSName fontName : resources.getFontNames()) {
                    // Check if we've exceeded the timeout
                    if (System.currentTimeMillis() - startTime > fontExtractionTimeoutMs) {
                        log.warn("Font extraction timeout for page {}, returning partial results", pageIndex + 1);
                        timeoutOccurred.set(true);
                        break;
                    }

                    try {
                        PDFont font = resources.getFont(fontName);
                        if (font != null) {
                            fontMap.put(fontName.getName(), font);
                        }
                    } catch (Exception e) {
                        // Handle font loading errors silently
                        log.debug("Error loading font {} on page {}: {}",
                                fontName.getName(), pageIndex + 1, e.getMessage());
                    }
                }

                // Now process the successfully loaded fonts
                for (Map.Entry<String, PDFont> entry : fontMap.entrySet()) {
                    // Check cache first
                    String cacheKey = documentHash + "_" + entry.getKey();
                    if (fontCache.containsKey(cacheKey)) {
                        fontInfoList.add(fontCache.get(cacheKey));
                        continue;
                    }

                    // Extract new font info
                    FontInfo fontInfo = extractFontInfo(entry.getValue(), entry.getKey());
                    fontInfoList.add(fontInfo);

                    // Add to cache
                    fontCache.put(cacheKey, fontInfo);
                }
            } catch (Exception e) {
                log.warn("Error processing fonts on page {}: {}", pageIndex + 1, e.getMessage());
            }
        } catch (Exception e) {
            log.warn("Error accessing page {} for font extraction: {}", pageIndex + 1, e.getMessage());
        }

        // Log performance metrics if timeout occurred
        if (timeoutOccurred.get()) {
            log.warn("Font extraction for page {} took too long (>{}ms) and was interrupted. " +
                            "Extracted {} fonts before timeout.",
                    pageIndex + 1, fontExtractionTimeoutMs, fontInfoList.size());
        }

        // Store in cache and return
        storeFontListInCache(documentHash, fontInfoList);
        return fontInfoList;
    }

    /**
     * Store a list of font info objects in the cache
     *
     * @param key Cache key
     * @param fontInfoList Font info list
     */
    private void storeFontListInCache(String key, List<FontInfo> fontInfoList) {
        // We store individual fonts in the cache, and also the complete list
        // as a special entry with "_list" suffix
        fontCache.put(key + "_list", FontInfo.createListPlaceholder(fontInfoList));
    }

    /**
     * Get a cached font list if available
     *
     * @param key Cache key
     * @return List of font info objects or null if not cached
     */
    private List<FontInfo> getCachedFontList(String key) {
        FontInfo listPlaceholder = fontCache.get(key + "_list");
        if (listPlaceholder != null && listPlaceholder.getListReference() != null) {
            return listPlaceholder.getListReference();
        }
        return null;
    }

    /**
     * Extract minimal font information (just names) for better performance.
     * This is used when detailed font analysis is disabled.
     *
     * @param document The PDF document
     * @param pageIndex The 0-based page index
     * @return List of minimal font information objects
     */
    private List<FontInfo> extractMinimalFontInfo(PDDocument document, int pageIndex) {
        List<FontInfo> fontInfoList = new ArrayList<>();

        try {
            PDPage page = document.getPage(pageIndex);
            PDResources resources = page.getResources();

            if (resources == null) {
                return fontInfoList;
            }

            // Use a single fast pass to extract font names
            for (COSName fontName : resources.getFontNames()) {
                try {
                    FontInfo info = new FontInfo();
                    info.setId(UUID.randomUUID().toString());
                    info.setPdfName(fontName.getName());
                    info.setFontName(fontName.getName());

                    // Try to get the actual font name if possible (quick operation)
                    try {
                        PDFont font = resources.getFont(fontName);
                        if (font != null) {
                            info.setFontName(font.getName());

                            // Fast font family extraction without regex
                            String fontNameStr = font.getName();
                            if (fontNameStr != null) {
                                int plusIndex = fontNameStr.indexOf('+');
                                if (plusIndex >= 0 && plusIndex < fontNameStr.length() - 1) {
                                    info.setFontFamily(fontNameStr.substring(plusIndex + 1));
                                } else {
                                    info.setFontFamily(fontNameStr);
                                }

                                // Fast style detection
                                String lowerName = fontNameStr.toLowerCase();
                                info.setIsBold(lowerName.contains("bold"));
                                info.setIsItalic(lowerName.contains("italic") || lowerName.contains("oblique"));
                            } else {
                                info.setFontFamily("Unknown");
                            }
                        } else {
                            info.setFontFamily("Unknown");
                        }
                    } catch (Exception e) {
                        // Silent fail - just use font name as is
                        info.setFontFamily("Unknown");
                    }

                    fontInfoList.add(info);
                } catch (Exception e) {
                    // Ignore errors for minimal extraction
                }
            }
        } catch (Exception e) {
            log.debug("Error in minimal font extraction for page {}: {}", pageIndex + 1, e.getMessage());
        }

        return fontInfoList;
    }

    /**
     * Extract information about a font.
     * Optimized for performance with minimal exception handling.
     *
     * @param font The PDF font
     * @param name The font name in the PDF
     * @return FontInfo object with extracted information
     */
    private FontInfo extractFontInfo(PDFont font, String name) {
        FontInfo info = new FontInfo();
        info.setId(UUID.randomUUID().toString());
        info.setFontName(font.getName());
        info.setPdfName(name);

        try {
            info.setEmbedded(font.isEmbedded());
        } catch (Exception e) {
            info.setEmbedded(false);
        }

        // Quick font family extraction (simplified)
        String fontName = font.getName();
        if (fontName != null) {
            // Simple font family extraction without expensive regex
            int plusIndex = fontName.indexOf('+');
            if (plusIndex >= 0 && plusIndex < fontName.length() - 1) {
                info.setFontFamily(fontName.substring(plusIndex + 1));
            } else {
                info.setFontFamily(fontName);
            }

            // Quick bold/italic detection
            String lowerName = fontName.toLowerCase();
            info.setIsBold(lowerName.contains("bold"));
            info.setIsItalic(lowerName.contains("italic") || lowerName.contains("oblique"));
        } else {
            info.setFontFamily("Unknown");
            info.setIsBold(false);
            info.setIsItalic(false);
        }

        // Simplified encoding detection
        try {
            if (font instanceof PDType1Font) {
                info.setEncoding("Type1");
            } else if (font instanceof PDType0Font) {
                info.setEncoding("CID");
            } else {
                info.setEncoding("Other");
            }
        } catch (Exception e) {
            info.setEncoding("Unknown");
        }

        return info;
    }

    /**
     * Create a placeholder font info for fonts that could not be properly loaded.
     *
     * @param fontName The font name from the PDF
     * @return FontInfo placeholder
     */
    private FontInfo createPlaceholderFontInfo(String fontName) {
        FontInfo info = new FontInfo();
        info.setId(UUID.randomUUID().toString());
        info.setFontName(fontName + " (damaged)");
        info.setPdfName(fontName);
        info.setFontFamily("Unknown");
        info.setEmbedded(true);
        info.setIsBold(false);
        info.setIsItalic(false);
        info.setEncoding("Unknown");
        info.setDamaged(true);
        return info;
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
        private List<FontInfo> listReference = null; // Used for caching font lists

        /**
         * Create a placeholder object to reference a list of fonts in the cache
         */
        public static FontInfo createListPlaceholder(List<FontInfo> list) {
            FontInfo placeholder = new FontInfo();
            placeholder.setId("placeholder_" + UUID.randomUUID().toString());
            placeholder.setListReference(list);
            return placeholder;
        }

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

        public List<FontInfo> getListReference() { return listReference; }
        public void setListReference(List<FontInfo> listReference) { this.listReference = listReference; }
    }
}