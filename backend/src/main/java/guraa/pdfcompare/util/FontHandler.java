package guraa.pdfcompare.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Utility class for robust font handling that can recover from PDFBox font errors.
 */
@Slf4j
@Component
public class FontHandler {

    /**
     * Safely extracts font information from a PDF page, handling potential font errors.
     *
     * @param document The PDF document
     * @param pageIndex The 0-based page index
     * @return List of font information objects
     */
    public List<FontInfo> extractFontsFromPage(PDDocument document, int pageIndex) {
        List<FontInfo> fontInfoList = new ArrayList<>();
        Map<String, PDFont> fontMap = new HashMap<>();

        try {
            PDPage page = document.getPage(pageIndex);
            PDResources resources = page.getResources();

            if (resources == null) {
                log.warn("No resources found on page {}", pageIndex + 1);
                return fontInfoList;
            }

            // Extract fonts from the page resources
            for (COSName fontName : resources.getFontNames()) {
                try {
                    PDFont font = resources.getFont(fontName);

                    if (font != null && !fontMap.containsValue(font)) {
                        FontInfo fontInfo = extractFontInfo(font, fontName.getName());
                        fontInfoList.add(fontInfo);
                        fontMap.put(fontName.getName(), font);
                    }
                } catch (IOException e) {
                    // Handle ASCII85 stream errors and other font extraction issues
                    if (e.getMessage() != null &&
                            (e.getMessage().contains("Invalid data in Ascii85 stream") ||
                                    e.getMessage().contains("Could not read embedded"))) {
                        log.warn("Recoverable font error for {} on page {}: {}",
                                fontName.getName(), pageIndex + 1, e.getMessage());
                        // Create placeholder font info for corrupted font
                        FontInfo placeholderInfo = createPlaceholderFontInfo(fontName.getName());
                        fontInfoList.add(placeholderInfo);
                    } else {
                        log.warn("Error processing font {} on page {}: {}",
                                fontName.getName(), pageIndex + 1, e.getMessage());
                    }
                } catch (Exception e) {
                    log.warn("Unexpected error with font {} on page {}: {}",
                            fontName.getName(), pageIndex + 1, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Error analyzing fonts on page " + pageIndex, e);
        }

        return fontInfoList;
    }

    /**
     * Extract information about a font.
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
            log.warn("Error determining if font is embedded: {}", e.getMessage());
            info.setEmbedded(false);
        }

        // Determine font family and other attributes
        info.setFontFamily(getFontFamily(font));
        info.setIsBold(isBold(font));
        info.setIsItalic(isItalic(font));

        // Get encoding information
        try {
            // PDFont doesn't have a direct getEncoding() method, we need to handle this differently
            String encoding = "Unknown";

            // Try to get encoding based on font type
            if (font instanceof PDType1Font) {
                PDType1Font type1Font = (PDType1Font) font;
                if (type1Font.getEncoding() != null) {
                    encoding = type1Font.getEncoding().toString();
                }
            } else if (font instanceof PDType0Font) {
                encoding = "CID";  // Composite fonts typically use CID encoding
            }

            info.setEncoding(encoding);
        } catch (Exception e) {
            info.setEncoding("Unknown");
            log.warn("Could not determine font encoding: {}", e.getMessage());
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
        info.setFontFamily("Unknown (damaged font)");
        info.setEmbedded(true); // Assume it was embedded but corrupted
        info.setIsBold(false);
        info.setIsItalic(false);
        info.setEncoding("Unknown");
        info.setDamaged(true); // Mark as damaged for special handling
        return info;
    }

    /**
     * Determine the font family from a PDF font.
     *
     * @param font The PDF font
     * @return Font family name
     */
    private String getFontFamily(PDFont font) {
        String fontName = font.getName();

        // Extract family name from font name
        if (fontName != null) {
            // Remove style indicators
            String family = fontName.replaceAll("(?i)[-\\+]?(bold|italic|oblique|light|medium|black|regular|condensed|extended)", "");

            // Remove common prefixes
            family = family.replaceAll("^[A-Z]{6}\\+", "");

            // Remove any remaining special characters
            family = family.replaceAll("[^a-zA-Z0-9 ]", "").trim();

            if (!family.isEmpty()) {
                return family;
            }
        }

        return "Unknown";
    }

    /**
     * Determine if a font is bold.
     *
     * @param font The PDF font
     * @return True if the font is bold
     */
    private boolean isBold(PDFont font) {
        String fontName = font.getName();
        if (fontName != null) {
            return fontName.toLowerCase().contains("bold");
        }
        return false;
    }

    /**
     * Determine if a font is italic.
     *
     * @param font The PDF font
     * @return True if the font is italic
     */
    private boolean isItalic(PDFont font) {
        String fontName = font.getName();
        if (fontName != null) {
            String lowerName = fontName.toLowerCase();
            return lowerName.contains("italic") || lowerName.contains("oblique");
        }
        return false;
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