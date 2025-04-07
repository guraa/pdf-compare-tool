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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
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
    @Value("${app.font.extraction-timeout-ms:2000}")
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
        List<FontInfo> fontInfoList = new ArrayList<>();
        Map<String, PDFont> fontMap = new HashMap<>();
        
        // Skip detailed font analysis if disabled
        if (!detailedFontAnalysis) {
            // Return minimal font info (just names) for better performance
            return extractMinimalFontInfo(document, pageIndex);
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

            // Extract fonts from the page resources with timeout check
            for (COSName fontName : resources.getFontNames()) {
                // Check if we've exceeded the timeout
                if (System.currentTimeMillis() - startTime > fontExtractionTimeoutMs) {
                    log.warn("Font extraction timeout for page {}, returning partial results", pageIndex + 1);
                    timeoutOccurred.set(true);
                    break;
                }
                
                try {
                    // Check if this font is already in the cache
                    String cacheKey = document.getDocumentId() + "_" + pageIndex + "_" + fontName.getName();
                    if (fontCache.containsKey(cacheKey)) {
                        fontInfoList.add(fontCache.get(cacheKey));
                        continue;
                    }
                    
                    PDFont font = resources.getFont(fontName);

                    if (font != null && !fontMap.containsValue(font)) {
                        FontInfo fontInfo = extractFontInfo(font, fontName.getName());
                        fontInfoList.add(fontInfo);
                        fontMap.put(fontName.getName(), font);
                        
                        // Add to cache for future use
                        fontCache.put(cacheKey, fontInfo);
                    }
                } catch (IOException e) {
                    // Handle ASCII85 stream errors and other font extraction issues
                    if (e.getMessage() != null &&
                            (e.getMessage().contains("Invalid data in Ascii85 stream") ||
                                    e.getMessage().contains("Could not read embedded"))) {
                        log.debug("Recoverable font error for {} on page {}: {}",
                                fontName.getName(), pageIndex + 1, e.getMessage());
                        // Create placeholder font info for corrupted font
                        FontInfo placeholderInfo = createPlaceholderFontInfo(fontName.getName());
                        fontInfoList.add(placeholderInfo);
                    } else {
                        log.debug("Error processing font {} on page {}: {}",
                                fontName.getName(), pageIndex + 1, e.getMessage());
                    }
                } catch (Exception e) {
                    log.debug("Unexpected error with font {} on page {}: {}",
                            fontName.getName(), pageIndex + 1, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Error analyzing fonts on page {}: {}", pageIndex + 1, e.getMessage());
        }
        
        // Log performance metrics if timeout occurred
        if (timeoutOccurred.get()) {
            log.warn("Font extraction for page {} took too long (>{}ms) and was interrupted. " +
                    "Extracted {} fonts before timeout.", 
                    pageIndex + 1, fontExtractionTimeoutMs, fontInfoList.size());
        }

        return fontInfoList;
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

            // Just extract font names without detailed analysis
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
                            
                            // Set font family to avoid null pointer exceptions
                            String fontNameStr = font.getName();
                            if (fontNameStr != null) {
                                // Simple font family extraction without expensive regex
                                int plusIndex = fontNameStr.indexOf('+');
                                if (plusIndex >= 0 && plusIndex < fontNameStr.length() - 1) {
                                    info.setFontFamily(fontNameStr.substring(plusIndex + 1));
                                } else {
                                    info.setFontFamily(fontNameStr);
                                }
                            } else {
                                info.setFontFamily("Unknown");
                            }
                        } else {
                            // Set a default font family to avoid null pointer exceptions
                            info.setFontFamily("Unknown");
                        }
                    } catch (Exception e) {
                        // Ignore errors, just use the PDF name
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
