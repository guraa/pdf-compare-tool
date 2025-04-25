package guraa.pdfcompare.extraction;

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.EventType;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.itextpdf.kernel.pdf.canvas.parser.data.IEventData;
import com.itextpdf.kernel.pdf.canvas.parser.data.TextRenderInfo;
import com.itextpdf.kernel.pdf.canvas.parser.listener.LocationTextExtractionStrategy;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Enhanced text extractor that captures spatial information along with text content.
 * This class extracts text elements with their coordinates, sizes, and style information.
 */
@Slf4j
public class TextDifferenceExtractor {

    /**
     * Extract text elements with coordinates from a page.
     *
     * @param filePath   The PDF file path
     * @param pageNumber The page number (1-based)
     * @return List of text elements with coordinates
     * @throws IOException If there is an error extracting the text
     */
    public static List<TextElement> extractTextElements(String filePath, int pageNumber) throws IOException {
        try (PdfReader reader = new PdfReader(filePath);
             PdfDocument pdfDoc = new PdfDocument(reader)) {

            if (pageNumber < 1 || pageNumber > pdfDoc.getNumberOfPages()) {
                throw new IOException("Invalid page number: " + pageNumber);
            }

            PdfPage page = pdfDoc.getPage(pageNumber);
            CoordinateTextExtractionStrategy strategy = new CoordinateTextExtractionStrategy();
            PdfTextExtractor.getTextFromPage(page, strategy);

            return strategy.getTextElements();
        } catch (Exception e) {
            log.error("Error extracting text elements from page {}: {}", pageNumber, e.getMessage(), e);
            throw new IOException("Failed to extract text elements", e);
        }
    }

    /**
     * Extract text elements with their bounding boxes from a PDF document.
     */
    private static class CoordinateTextExtractionStrategy extends LocationTextExtractionStrategy {
        private final List<TextElement> textElements = new ArrayList<>();

        @Override
        public void eventOccurred(IEventData data, EventType type) {
            if (type == EventType.RENDER_TEXT) {
                try {
                    TextRenderInfo renderInfo = (TextRenderInfo) data;
                    String text = renderInfo.getText();

                    if (text != null && !text.isEmpty()) {
                        Rectangle rect = renderInfo.getBaseline().getBoundingRectangle();

                        // Adjusting the rectangle to better represent the text boundaries
                        float ascent = renderInfo.getAscentLine().getBoundingRectangle().getHeight();
                        float descent = renderInfo.getDescentLine().getBoundingRectangle().getHeight();

                        // Create a proper bounding box for the text element
                        Rectangle textRect = new Rectangle(
                                rect.getX(),              // x coordinate
                                rect.getY() - descent,    // y coordinate (adjusted)
                                rect.getWidth(),          // width
                                ascent + descent          // height (ascent + descent)
                        );

                        // Get font information when available
                        String fontName = "Unknown";
                        try {
                            if (renderInfo.getFont() != null && renderInfo.getFont().getFontProgram() != null) {
                                fontName = renderInfo.getFont().getFontProgram().getFontNames().getFontName();
                            }
                        } catch (Exception e) {
                            // Font information may not be available, use default
                        }

                        float fontSize = renderInfo.getFontSize();

                        // Create text element with all available information
                        TextElement element = new TextElement(
                                text,
                                textRect.getX(),
                                textRect.getY(),
                                textRect.getWidth(),
                                textRect.getHeight(),
                                fontName,
                                fontSize
                        );

                        textElements.add(element);
                    }
                } catch (Exception e) {
                    log.warn("Error processing text render event: {}", e.getMessage());
                }
            }
            super.eventOccurred(data, type);
        }

        /**
         * Get the extracted text elements.
         *
         * @return The list of text elements
         */
        public List<TextElement> getTextElements() {
            return textElements;
        }
    }

    /**
     * Class to store text elements with their coordinates and style information.
     */
    public static class TextElement {
        private final String text;
        private final float x;
        private final float y;
        private final float width;
        private final float height;
        private final String fontName;
        private final float fontSize;

        /**
         * Constructor.
         *
         * @param text     The text content
         * @param x        The x-coordinate
         * @param y        The y-coordinate
         * @param width    The width
         * @param height   The height
         * @param fontName The font name
         * @param fontSize The font size
         */
        public TextElement(String text, float x, float y, float width, float height, String fontName, float fontSize) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.fontName = fontName;
            this.fontSize = fontSize;
        }

        public String getText() {
            return text;
        }

        public float getX() {
            return x;
        }

        public float getY() {
            return y;
        }

        public float getWidth() {
            return width;
        }

        public float getHeight() {
            return height;
        }

        public String getFontName() {
            return fontName;
        }

        public float getFontSize() {
            return fontSize;
        }

        @Override
        public String toString() {
            return "TextElement{" +
                    "text='" + text + '\'' +
                    ", x=" + x +
                    ", y=" + y +
                    ", width=" + width +
                    ", height=" + height +
                    ", fontName='" + fontName + '\'' +
                    ", fontSize=" + fontSize +
                    '}';
        }
    }
}