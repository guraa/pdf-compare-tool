package guraa.pdfcompare.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Component;

import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Utility class for extracting text from PDF documents with improved positioning information.
 */
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
        try {
            // Create custom text stripper for extracting text
            PDFTextStripper stripper = new PDFTextStripper();

            // Set the page range to extract text from a specific page
            stripper.setStartPage(pageIndex + 1); // 1-based page numbers
            stripper.setEndPage(pageIndex + 1);

            // Capture the text in a StringWriter
            StringWriter writer = new StringWriter();
            stripper.writeText(document, writer);

            return writer.toString();
        } catch (Exception e) {
            log.warn("Error extracting text from page {}: {}", pageIndex + 1, e.getMessage());
            return ""; // Return empty string in case of error instead of null
        }
    }

    /**
     * Extract text elements with detailed positioning information from a page.
     *
     * @param document The PDF document
     * @param pageIndex The zero-based page index
     * @return List of text elements with positioning information
     * @throws IOException If there's an error extracting text
     */
    public List<TextElement> extractTextElementsFromPage(PDDocument document, int pageIndex) throws IOException {
        try {
            // Create a custom implementation of PDFTextStripper
            PositionedTextStripper stripper = new PositionedTextStripper();

            // Set the page range to extract text from a specific page
            stripper.setStartPage(pageIndex + 1); // 1-based page numbers
            stripper.setEndPage(pageIndex + 1);

            // Reset text positions before extracting from a new page
            stripper.resetTextPositions();

            // Extract text (this populates the text positions)
            stripper.getText(document);

            // Get the text positions and convert to TextElement objects
            List<TextPosition> positions = stripper.getTextPositions();
            List<TextElement> elements = new ArrayList<>();

            for (int i = 0; i < positions.size(); i++) {
                TextPosition pos = positions.get(i);
                if (pos == null) continue; // Skip null positions

                // Create a unique ID for each text element
                String id = UUID.randomUUID().toString();

                // Get page height for coordinate transformation
                float pageHeight = pos.getPage().getMediaBox().getHeight();
                
                // PDFBox Y is baseline from bottom-left. We need top-left Y for the element's bounding box top edge.
                float pdfBoxY = (float) pos.getY();
                float pdfBoxHeight = (float) pos.getHeightDir(); // Use height adjusted for direction/rotation

                // Calculate the Y coordinate of the top edge relative to the top-left origin
                // Formula: pageHeight - (baselineY_from_bottom + ascent)
                // Approximating ascent with height for now: pageHeight - (pdfBoxY + pdfBoxHeight) - this seems wrong.
                // Let's try: pageHeight - pdfBoxY. This gives baseline relative to top.
                // To get top edge from top-left: pageHeight - (pdfBoxY + ascent).
                // Let's use a simpler approximation: top_y = pageHeight - pdfBoxY
                // This sends the baseline relative to top-left. Frontend might need adjustment OR
                // we calculate top edge here: top_y = pageHeight - (pdfBoxY + pdfBoxHeight) -- Let's try this first.
                // It assumes pdfBoxY is bottom edge, which is wrong. It's baseline.
                
                // Correct approach: Transform baseline Y to top-left origin.
                float transformedY = pageHeight - pdfBoxY;

                // Create a TextElement with positioning information, using the TRANSFORMED Y (baseline from top)
                // The grouping logic below will calculate the final bounding box based on these transformed coordinates.
                TextElement element = new TextElement(
                        id,
                        pos.getUnicode(),
                        (float) pos.getX(),
                        transformedY, // Use baseline relative to top-left for initial element
                        (float) pos.getWidth(),
                        pdfBoxHeight, // Use directional height
                        pos.getFont().getName(),
                        pos.getFontSize(),
                        pos.getFontSizeInPt()
                );

                elements.add(element);
            }

            // Group text elements (combine characters into words, etc.)
            return groupTextElements(elements);
        } catch (Exception e) {
            log.warn("Error extracting text elements from page {}: {}", pageIndex + 1, e.getMessage());
            return Collections.emptyList(); // Return empty list instead of null
        }
    }

    /**
     * Group text elements to form words and lines.
     * Combines individual characters into meaningful text elements.
     *
     * @param elements The list of raw text elements (typically individual characters)
     * @return List of grouped text elements
     */
    protected List<TextElement> groupTextElements(List<TextElement> elements) {
        // First, sort text elements by Y position (to identify lines) then by X position
        if (elements == null || elements.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            // Copy the list to avoid modifying the original
            List<TextElement> sortedElements = new ArrayList<>(elements);

            // Sort by Y position (primary) and X position (secondary).
            // Since Y is now relative to top-left, smaller Y means higher up the page. This sort order is correct.
            Collections.sort(sortedElements, Comparator
                    .comparing(TextElement::getY) // Smaller Y is higher on page
                    .thenComparing(TextElement::getX));

            List<TextElement> groupedElements = new ArrayList<>();
            List<TextElement> currentLine = new ArrayList<>();
            float lastY = -1;
            float lineHeight = 0;
            float yTolerance = 2.0f; // Tolerance for Y-position to consider text on the same line

            for (int i = 0; i < sortedElements.size(); i++) {
                TextElement element = sortedElements.get(i);
                if (element == null) continue; // Skip null elements

                // Determine if this is part of the current line
                if (lastY < 0 || Math.abs(element.getY() - lastY) <= yTolerance) {
                    // Same line, add to current line
                    currentLine.add(element);
                    lastY = element.getY();
                    lineHeight = Math.max(lineHeight, element.getHeight());
                } else {
                    // New line, process the current line and start a new one
                    groupedElements.addAll(groupWordsInLine(currentLine));
                    currentLine.clear();
                    currentLine.add(element);
                    lastY = element.getY();
                    lineHeight = element.getHeight();
                }
            }

            // Process the last line
            if (!currentLine.isEmpty()) {
                groupedElements.addAll(groupWordsInLine(currentLine));
            }

            return groupedElements;
        } catch (Exception e) {
            log.warn("Error grouping text elements: {}", e.getMessage());
            return elements; // Return original elements if grouping fails
        }
    }

    /**
     * Group elements in a line into words.
     *
     * @param lineElements The elements in a single line
     * @return List of word elements
     */
    private List<TextElement> groupWordsInLine(List<TextElement> lineElements) {
        if (lineElements == null || lineElements.isEmpty()) {
            return Collections.emptyList();
        }

        List<TextElement> wordElements = new ArrayList<>();
        List<TextElement> currentWord = new ArrayList<>();
        float lastEndX = -1;
        float spaceTolerance = 3.0f; // Tolerance for space between characters to consider them part of the same word

        // Sort elements by X position
        Collections.sort(lineElements, Comparator.comparing(TextElement::getX));

        for (int i = 0; i < lineElements.size(); i++) {
            TextElement element = lineElements.get(i);
            if (element == null) continue; // Skip null elements

            // Determine if this is part of the current word
            if (lastEndX < 0 || Math.abs(element.getX() - lastEndX) <= spaceTolerance) {
                // Same word, add to current word
                currentWord.add(element);
                lastEndX = element.getX() + element.getWidth();
            } else {
                // New word, create a word element and start a new word
                if (!currentWord.isEmpty()) {
                    wordElements.add(createWordElement(currentWord));
                    currentWord.clear();
                }
                currentWord.add(element);
                lastEndX = element.getX() + element.getWidth();
            }
        }

        // Process the last word
        if (!currentWord.isEmpty()) {
            wordElements.add(createWordElement(currentWord));
        }

        return wordElements;
    }

    /**
     * Create a single word element from a list of character elements.
     *
     * @param characterElements The list of character elements
     * @return A single word element
     */
    private TextElement createWordElement(List<TextElement> characterElements) {
        if (characterElements == null || characterElements.isEmpty()) {
            return null;
        }

        // Calculate bounds of the word using the transformed coordinates
        float wordMinX = Float.MAX_VALUE;
        float wordMaxX = Float.MIN_VALUE;
        float wordMinY = Float.MAX_VALUE; // Top-most Y (smallest value)
        float wordMaxY = Float.MIN_VALUE; // Bottom-most Y (largest value)

        StringBuilder text = new StringBuilder();
        String fontName = null;
        float fontSize = 0;

        for (TextElement element : characterElements) {
            if (element == null) continue; // Skip null elements

            wordMinX = Math.min(wordMinX, element.getX());
            wordMaxX = Math.max(wordMaxX, element.getX() + element.getWidth());
            // Y is baseline relative to top. The bounding box top is roughly Y - height.
            // Let's calculate min/max based on the element's baseline Y and height.
            wordMinY = Math.min(wordMinY, element.getY() - element.getHeight()); // Approximate top edge
            wordMaxY = Math.max(wordMaxY, element.getY()); // Baseline is approx bottom edge

            text.append(element.getText());

            // Use the font of the first character as the word's font
            if (fontName == null) {
                fontName = element.getFontName();
                fontSize = element.getFontSize();
            }
        }

        // Calculate final word bounds
        float finalWidth = wordMaxX - wordMinX;
        float finalHeight = wordMaxY - wordMinY; // Height based on min/max Y

        // Ensure dimensions are valid
        if (finalWidth <= 0) finalWidth = 1.0f; 
        if (finalHeight <= 0) finalHeight = fontSize > 0 ? fontSize : 10.0f; // Use font size if height is invalid

        // Create a word element using the calculated top-left corner (wordMinX, wordMinY) and dimensions
        return new TextElement(
                UUID.randomUUID().toString(),
                text.toString(),
                wordMinX,
                wordMinY, // This is the calculated top Y coordinate of the word box
                finalWidth,
                finalHeight,
                fontName != null ? fontName : "Unknown",
                fontSize,
                fontSize
        );
    }

    /**
     * Custom PDFTextStripper that captures position information.
     */
    private static class PositionedTextStripper extends PDFTextStripper {
        private List<TextPosition> textPositions = new ArrayList<>();

        public PositionedTextStripper() throws IOException {
            super();
            setSortByPosition(true);
        }

        public List<TextPosition> getTextPositions() {
            return textPositions;
        }

        public void resetTextPositions() {
            textPositions.clear();
        }

        @Override
        protected void processTextPosition(TextPosition text) {
            if (text != null) {
                textPositions.add(text);
            }
            super.processTextPosition(text);
        }
    }
}
