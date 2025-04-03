package guraa.pdfcompare.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.contentstream.PDFStreamEngine;
import org.apache.pdfbox.contentstream.operator.DrawObject;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.contentstream.operator.state.*;
import org.apache.pdfbox.contentstream.operator.text.*;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.state.PDGraphicsState;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Component;

import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Utility for extracting text from PDF documents with detailed formatting and position information.
 */
@Slf4j
@Component
public class TextExtractor extends PDFStreamEngine {

    private final List<TextElement> textElements = new ArrayList<>();
    private int currentPageIndex;

    /**
     * Constructor to initialize text extraction operators.
     */
    public TextExtractor() {
        // Register operators for handling images
        addOperator(new BeginText());
        addOperator(new EndText());
        addOperator(new SetGraphicsStateParameters());
        addOperator(new Save());
        addOperator(new Restore());
        addOperator(new NextLine());
        addOperator(new SetCharSpacing());
        addOperator(new MoveText());
        addOperator(new MoveTextSetLeading());
        addOperator(new SetFontAndSize());
        addOperator(new ShowText());
        addOperator(new ShowTextAdjusted());
        addOperator(new SetTextLeading());
        addOperator(new SetMatrix());
        addOperator(new SetTextRenderingMode());
        addOperator(new SetTextRise());
        addOperator(new SetWordSpacing());
        addOperator(new SetTextHorizontalScaling());
        addOperator(new DrawObject());
    }

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
        stripper.setSortByPosition(true);
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
        textElements.clear();
        currentPageIndex = pageIndex;

        // Custom text stripper to extract detailed text position and font information
        PDFTextStripper stripper = new PDFTextStripper() {
            @Override
            protected void processTextPosition(TextPosition text) {
                TextElement element = new TextElement();
                element.setId(UUID.randomUUID().toString());
                element.setText(text.getUnicode());
                element.setX(text.getX());
                element.setY(text.getY());
                element.setWidth(text.getWidth());
                element.setHeight(text.getHeight());
                element.setFontSize(text.getFontSize());
                element.setFontName(text.getFont().getName());
                element.setPageIndex(currentPageIndex);

                // Store rotation and transform
                element.setRotation(text.getRotation());

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

                // Store font properties
                try {
                    if (text.getFont() != null) {
                        element.setIsBold(text.getFont().getName().toLowerCase().contains("bold"));
                        element.setIsItalic(text.getFont().getName().toLowerCase().contains("italic") ||
                                text.getFont().getName().toLowerCase().contains("oblique"));
                        element.setIsEmbedded(text.getFont().isEmbedded());
                    }
                } catch (Exception e) {
                    log.warn("Error extracting font details", e);
                }

                textElements.add(element);
                super.processTextPosition(text);
            }
        };

        stripper.setSortByPosition(true);
        stripper.setStartPage(pageIndex + 1);
        stripper.setEndPage(pageIndex + 1);
        stripper.getText(document);

        // Group text elements by words and lines for better comparison
        groupTextElements();

        return new ArrayList<>(textElements);
    }

    /**
     * Group text elements into words and lines based on proximity.
     */
    private void groupTextElements() {
        if (textElements.isEmpty()) {
            return;
        }

        // Sort by Y position (line), then X position
        textElements.sort((a, b) -> {
            float yDiff = a.getY() - b.getY();
            if (Math.abs(yDiff) < 3.0f) { // Within same line tolerance
                return Float.compare(a.getX(), b.getX());
            }
            return Float.compare(a.getY(), b.getY());
        });

        // Assign line and word IDs
        String currentLineId = UUID.randomUUID().toString();
        String currentWordId = UUID.randomUUID().toString();
        float lastY = textElements.get(0).getY();
        float lastEndX = textElements.get(0).getX() + textElements.get(0).getWidth();

        textElements.get(0).setLineId(currentLineId);
        textElements.get(0).setWordId(currentWordId);

        for (int i = 1; i < textElements.size(); i++) {
            TextElement current = textElements.get(i);
            float yDiff = Math.abs(current.getY() - lastY);

            // Check if this is a new line
            if (yDiff > 3.0f) {
                currentLineId = UUID.randomUUID().toString();
                currentWordId = UUID.randomUUID().toString();
                lastEndX = current.getX() + current.getWidth();
            }
            // Check if this is a new word on the same line
            else if (current.getX() - lastEndX > current.getWidth() * 0.3) {
                currentWordId = UUID.randomUUID().toString();
                lastEndX = current.getX() + current.getWidth();
            } else {
                // Continue the current word
                lastEndX = Math.max(lastEndX, current.getX() + current.getWidth());
            }

            current.setLineId(currentLineId);
            current.setWordId(currentWordId);
            lastY = current.getY();
        }
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
     * Identify paragraphs in a page.
     *
     * @param document The PDF document
     * @param pageIndex The zero-based page index
     * @return List of paragraphs with their bounding boxes
     * @throws IOException If there's an error processing the page
     */
    public List<Paragraph> identifyParagraphs(PDDocument document, int pageIndex) throws IOException {
        List<TextElement> elements = extractTextElementsFromPage(document, pageIndex);
        List<Paragraph> paragraphs = new ArrayList<>();

        if (elements.isEmpty()) {
            return paragraphs;
        }

        // Group elements by line
        Map<String, List<TextElement>> lineMap = new HashMap<>();
        for (TextElement element : elements) {
            lineMap.computeIfAbsent(element.getLineId(), k -> new ArrayList<>()).add(element);
        }

        // Sort lines by Y position
        List<String> sortedLineIds = new ArrayList<>(lineMap.keySet());
        sortedLineIds.sort((id1, id2) -> {
            float y1 = lineMap.get(id1).get(0).getY();
            float y2 = lineMap.get(id2).get(0).getY();
            return Float.compare(y1, y2);
        });

        // Create paragraphs by grouping lines
        Paragraph currentParagraph = null;
        float lastBottomY = 0;

        for (String lineId : sortedLineIds) {
            List<TextElement> line = lineMap.get(lineId);

            // Sort elements in line by X position
            line.sort((e1, e2) -> Float.compare(e1.getX(), e2.getX()));

            // Calculate line bounds
            float minX = line.get(0).getX();
            float minY = line.get(0).getY();
            float maxX = line.get(line.size() - 1).getX() + line.get(line.size() - 1).getWidth();
            float maxY = minY;
            float maxHeight = 0;

            for (TextElement element : line) {
                float elementBottom = element.getY() + element.getHeight();
                if (elementBottom > maxY) {
                    maxY = elementBottom;
                }
                if (element.getHeight() > maxHeight) {
                    maxHeight = element.getHeight();
                }
            }

            // Build line text
            StringBuilder lineText = new StringBuilder();
            for (TextElement element : line) {
                lineText.append(element.getText());
            }

            // Check if this is a new paragraph
            boolean isNewParagraph = currentParagraph == null ||
                    (maxY - lastBottomY) > maxHeight * 1.5 ||
                    lineText.toString().trim().endsWith(".") ||
                    line.get(0).getX() > currentParagraph.getMinX() + 10; // Indentation

            if (isNewParagraph) {
                // Finish previous paragraph if exists
                if (currentParagraph != null) {
                    paragraphs.add(currentParagraph);
                }

                // Start new paragraph
                currentParagraph = new Paragraph();
                currentParagraph.setId(UUID.randomUUID().toString());
                currentParagraph.setMinX(minX);
                currentParagraph.setMinY(minY);
                currentParagraph.setMaxX(maxX);
                currentParagraph.setMaxY(maxY);
                currentParagraph.setText(lineText.toString());
                currentParagraph.getLineIds().add(lineId);
            } else {
                // Add to existing paragraph
                currentParagraph.setText(currentParagraph.getText() + " " + lineText.toString());
                currentParagraph.getLineIds().add(lineId);
                currentParagraph.setMaxX(Math.max(currentParagraph.getMaxX(), maxX));
                currentParagraph.setMaxY(maxY);
            }

            lastBottomY = maxY;
        }

        // Add the last paragraph
        if (currentParagraph != null) {
            paragraphs.add(currentParagraph);
        }

        return paragraphs;
    }

    /**
     * Class representing a paragraph in a PDF document.
     */
    public static class Paragraph {
        private String id;
        private String text;
        private float minX;
        private float minY;
        private float maxX;
        private float maxY;
        private List<String> lineIds = new ArrayList<>();

        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getText() { return text; }
        public void setText(String text) { this.text = text; }

        public float getMinX() { return minX; }
        public void setMinX(float minX) { this.minX = minX; }

        public float getMinY() { return minY; }
        public void setMinY(float minY) { this.minY = minY; }

        public float getMaxX() { return maxX; }
        public void setMaxX(float maxX) { this.maxX = maxX; }

        public float getMaxY() { return maxY; }
        public void setMaxY(float maxY) { this.maxY = maxY; }

        public List<String> getLineIds() { return lineIds; }

        // Get width and height
        public float getWidth() { return maxX - minX; }
        public float getHeight() { return maxY - minY; }

        // Get bounding box as Rectangle2D
        public Rectangle2D.Float getBoundingBox() {
            return new Rectangle2D.Float(minX, minY, getWidth(), getHeight());
        }
    }
}