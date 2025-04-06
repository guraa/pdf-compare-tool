package guraa.pdfcompare.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class FontAnalyzer {

    private final FontHandler fontHandler;

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

        try {
            // Use the robust font handler to extract font information
            List<FontHandler.FontInfo> handlerFonts = fontHandler.extractFontsFromPage(document, pageIndex);

            // Convert from handler FontInfo to our FontInfo
            fontInfoList = handlerFonts.stream()
                    .map(this::convertToFontInfo)
                    .collect(Collectors.toList());

            log.debug("Extracted {} fonts from page {}", fontInfoList.size(), pageIndex + 1);

            // Log any damaged fonts that were detected
            int damagedFonts = (int) handlerFonts.stream().filter(FontHandler.FontInfo::isDamaged).count();
            if (damagedFonts > 0) {
                log.warn("Detected {} damaged fonts on page {} that were safely handled",
                        damagedFonts, pageIndex + 1);
            }

        } catch (Exception e) {
            log.error("Error analyzing fonts on page " + pageIndex, e);
        }

        return fontInfoList;
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