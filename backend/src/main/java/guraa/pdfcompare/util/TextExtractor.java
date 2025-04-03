package guraa.pdfcompare.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Component;

import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class TextExtractor {

    /**
     * Extract text from a specific page in a PDF document.
     *
     * @param document The PDF document
     * @param pageIndex The zero-based page index
     * @return Extracted text content
     * @throws IOException If there's an error extracting text
     */
    public String extractTextFromPage(PDDocument document, int pageIndex) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(pageIndex + 1); // PDFTextStripper uses 1-based page numbers
        stripper.setEndPage(pageIndex + 1);
        return stripper.getText(document);
    }

    /**
     * Extract text with detailed positioning information from a PDF page.
     *
     * @param document The PDF document
     * @param pageIndex The zero-based page index
     * @return List of text elements with position information
     * @throws IOException If there's an error extracting text
     */
    public List<TextElement> extractTextElementsFromPage(PDDocument document, int pageIndex) throws IOException {
        final List<TextElement> textElements = new ArrayList<>();

        PDFTextStripper stripper = new PDFTextStripper() {
            @Override
            protected void processTextPosition(TextPosition text) {
                TextElement element = new TextElement();
                element.setText(text.getUnicode());
                element.setX(text.getX());
                element.setY(text.getY());
                element.setWidth(text.getWidth());
                element.setHeight(text.getHeight());
                element.setFontSize(text.getFontSize());
                element.setFontName(text.getFont().getName());

                // Store color information if available
                if (getGraphicsState() != null && getGraphicsState().getNonStrokingColor() != null) {
                    float[] color = getGraphicsState().getNonStrokingColor().getComponents();
                    if (color != null && color.length >= 3) {
                        element.setColor(String.format("rgb(%d,%d,%d)",
                                Math.round(color[0] * 255),
                                Math.round(color[1] * 255),
                                Math.round(color[2] * 255)));
                    }
                }

                textElements.add(element);
            }
        };

        stripper.setStartPage(pageIndex + 1);
        stripper.setEndPage(pageIndex + 1);
        stripper.getText(document);

        return textElements;
    }

    /**
     * Extract text from specific regions on a PDF page.
     *
     * @param document The PDF document
     * @param pageIndex The zero-based page index
     * @param regions Map of region names to rectangle areas
     * @return Map of region names to extracted text
     * @throws IOException If there's an error extracting text
     */
    public Map<String, String> extractTextFromRegions(PDDocument document, int pageIndex,
                                                      Map<String, Rectangle> regions) throws IOException {
        PDPage page = document.getPage(pageIndex);
        PDFTextStripperByArea stripper = new PDFTextStripperByArea();
        stripper.setSortByPosition(true);

        // Add all regions
        for (Map.Entry<String, Rectangle> entry : regions.entrySet()) {
            stripper.addRegion(entry.getKey(), entry.getValue());
        }

        stripper.extractRegions(page);

        // Get text for each region
        Map<String, String> regionText = new HashMap<>();
        for (String region : regions.keySet()) {
            regionText.put(region, stripper.getTextForRegion(region));
        }

        return regionText;
    }

    /**
     * Text element with position and style information.
     */
    public static class TextElement {
        private String text;
        private float x;
        private float y;
        private float width;
        private float height;
        private float fontSize;
        private String fontName;
        private String color;

        // Getters and setters
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }

        public float getX() { return x; }
        public void setX(float x) { this.x = x; }

        public float getY() { return y; }
        public void setY(float y) { this.y = y; }

        public float getWidth() { return width; }
        public void setWidth(float width) { this.width = width; }

        public float getHeight() { return height; }
        public void setHeight(float height) { this.height = height; }

        public float getFontSize() { return fontSize; }
        public void setFontSize(float fontSize) { this.fontSize = fontSize; }

        public String getFontName() { return fontName; }
        public void setFontName(String fontName) { this.fontName = fontName; }

        public String getColor() { return color; }
        public void setColor(String color) { this.color = color; }

        // Get bounding box as Rectangle2D
        public Rectangle2D.Float getBoundingBox() {
            return new Rectangle2D.Float(x, y, width, height);
        }
    }
}