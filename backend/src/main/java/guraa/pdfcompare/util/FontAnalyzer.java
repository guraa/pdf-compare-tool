package guraa.pdfcompare.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class FontAnalyzer {

    private final FontHandler fontHandler;
    private final ObjectMapper objectMapper;

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
        List<FontInfo> fontInfoList = new ArrayList<>();
        int pageNumber = pageIndex + 1;

        try {
            // First check if we already have font information saved
            Path fontInfoPath = outputDir.resolve(String.format("page_%d_fonts.json", pageNumber));
            if (Files.exists(fontInfoPath)) {
                try {
                    // Try to load the pre-extracted font information
                    fontInfoList = objectMapper.readValue(fontInfoPath.toFile(),
                            objectMapper.getTypeFactory().constructCollectionType(List.class, FontInfo.class));
                    
                    if (fontInfoList != null && !fontInfoList.isEmpty()) {
                        log.debug("Loaded {} pre-extracted fonts for page {} from {}", 
                                fontInfoList.size(), pageNumber, fontInfoPath);
                        return fontInfoList;
                    }
                } catch (Exception e) {
                    log.warn("Error loading pre-extracted font information for page {}: {}", 
                            pageNumber, e.getMessage());
                }
            }
            try {
                // Ensure the output directory exists
                Files.createDirectories(outputDir);
                
                // Save the font information as JSON
                File fontInfoFile = fontInfoPath.toFile();
                objectMapper.writeValue(fontInfoFile, fontInfoList);
                log.debug("Saved font information for page {} to {}", pageNumber, fontInfoFile.getPath());
            } catch (Exception e) {
                log.warn("Error saving font information as JSON for page {}: {}", pageNumber, e.getMessage());
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
