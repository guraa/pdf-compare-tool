package guraa.pdfcompare.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class FontAnalyzer {

    /**
     * Analyze fonts used on a specific page in a PDF document.
     *
     * @param document The PDF document
     * @param pageIndex The zero-based page index
     * @param outputDir Output directory to save font information
     * @return List of information about fonts on the page
     */
    public List<FontInfo> analyzeFontsOnPage(PDDocument document, int pageIndex, Path outputDir) {
        List<FontInfo> fontInfoList = new ArrayList<>();
        Map<String, PDFont> fontMap = new HashMap<>();

        try {
            PDPage page = document.getPage(pageIndex);
            PDResources resources = page.getResources();

            // Extract fonts from the page resources
            for (COSName fontName : resources.getFontNames()) {
                try {
                    PDFont font = resources.getFont(fontName);

                    if (font != null) {
                        // Check if we already processed this font
                        if (!fontMap.containsValue(font)) {
                            FontInfo fontInfo = extractFontInfo(font, fontName.getName());

                            // Try to save font data for embedded fonts
                            if (font.isEmbedded()) {
                                try {
                                    String fontFileName = String.format("page_%d_font_%s.ttf",
                                            pageIndex + 1, UUID.randomUUID().toString());

                                    File fontFile = outputDir.resolve(fontFileName).toFile();
                                    saveFontData(font, fontFile);

                                    fontInfo.setFontFilePath(fontFile.getAbsolutePath());
                                } catch (IOException e) {
                                    log.warn("Could not save embedded font data", e);
                                }
                            }

                            fontInfoList.add(fontInfo);
                            fontMap.put(fontName.getName(), font);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Error processing font: " + fontName.getName(), e);
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
        info.setEmbedded(font.isEmbedded());

        // Determine font family and other attributes
        info.setFontFamily(getFontFamily(font));
        info.setIsBold(isBold(font));
        info.setIsItalic(isItalic(font));

        // Get encoding information
        try {
            info.setEncoding(font.getEncoding() != null ?
                    font.getEncoding().getEncodingName() : "Unknown");
        } catch (Exception e) {
            info.setEncoding("Unknown");
        }

        return info;
    }

    /**
     * Save font data to a file.
     *
     * @param font The PDF font
     * @param outputFile The output file
     * @throws IOException If there's an error saving the font data
     */
    private void saveFontData(PDFont font, File outputFile) throws IOException {
        // Extract font data if possible
        if (font.isEmbedded()) {
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                // Get font data - approach depends on font type
                if (font instanceof PDType0Font) {
                    PDType0Font type0Font = (PDType0Font) font;
                    if (type0Font.getDescendantFont() != null) {
                        byte[] fontData = type0Font.getDescendantFont().getFontFile2().toByteArray();
                        fos.write(fontData);
                    }
                } else if (font instanceof PDType1Font) {
                    PDType1Font type1Font = (PDType1Font) font;
                    if (type1Font.getFontFile() != null) {
                        byte[] fontData = type1Font.getFontFile().toByteArray();
                        fos.write(fontData);
                    } else if (type1Font.getFontFile2() != null) {
                        byte[] fontData = type1Font.getFontFile2().toByteArray();
                        fos.write(fontData);
                    } else if (type1Font.getFontFile3() != null) {
                        byte[] fontData = type1Font.getFontFile3().toByteArray();
                        fos.write(fontData);
                    }
                } else {
                    // Try generic approach for other font types
                    if (font.getFontDescriptor() != null && font.getFontDescriptor().getFontFile2() != null) {
                        byte[] fontData = font.getFontDescriptor().getFontFile2().toByteArray();
                        fos.write(fontData);
                    }
                }
            }
        }
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
     * Import for COSName since it's referenced in the method signature.
     */
    private static class COSName extends org.apache.pdfbox.cos.COSName {
        protected COSName(String name) {
            super(name);
        }
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
    }
}