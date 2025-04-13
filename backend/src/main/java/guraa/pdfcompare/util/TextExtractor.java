package guraa.pdfcompare.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringWriter;
import java.util.*;

/**
 * Utility class for extracting text from PDF documents with improved positioning information.
 * Handles coordinate transformation from PDF space to display space.
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
     * Correctly transforms PDF coordinates to display coordinates.
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

            // Get the page height for coordinate transformation
            PDPage currentPage = document.getPage(pageIndex);
            float pageHeight = currentPage.getMediaBox().getHeight();

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

                // PDF space: Y origin is at bottom of page, increasing upward
                // Display space: Y origin is at top of page, increasing downward
                // Need to transform Y coordinate
                float pdfY = (float) pos.getY();
                float height = (float) pos.getHeightDir();

                // Transform: PDF bottom-left origin to display top-left origin
                float displayY = pageHeight - pdfY - height; // This is the key transformation

                // Create a TextElement with transformed positioning information
                TextElement element = new TextElement(
                        id,
                        pos.getUnicode(),
                        (float) pos.getX(), // X remains the same
                        displayY,           // Y is transformed
                        (float) pos.getWidth(),
                        height,
                        normalizeFontName(pos.getFont().getName()),
                        pos.getFontSize(),
                        pos.getFontSizeInPt()
                );

                elements.add(element);
            }

            // Group text elements (combine characters into words, etc.)
            return groupTextElements(elements);
        } catch (Exception e) {
            log.warn("Error extracting text elements from page {}: {}", pageIndex + 1, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Extract color information from a text position if available.
     * This is a placeholder method - the exact implementation depends on PDFBox version and customizations.
     *
     * @param position The text position
     * @return The extracted color or null if not available
     */
    private java.awt.Color extractTextColor(TextPosition position) {
        // This is a placeholder - actual implementation depends on PDFBox capabilities
        // In some PDFBox versions, this information might not be directly available
        return java.awt.Color.BLACK; // Default to black
    }

    /**
     * Group text elements to form words and lines.
     * Combines individual characters into meaningful text elements.
     * Maintains correct coordinate transformation throughout.
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

            // Sort by Y position (primary) and X position (secondary)
            Collections.sort(sortedElements, Comparator
                    .comparing(TextElement::getY) // Y is already in display space (smaller Y is higher on page)
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
     * Maintains correct coordinate transformation.
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
     * Ensures that coordinate transformations are preserved.
     *
     * @param characterElements The list of character elements
     * @return A single word element
     */
    private TextElement createWordElement(List<TextElement> characterElements) {
        if (characterElements == null || characterElements.isEmpty()) {
            return null;
        }

        // Calculate bounds of the word
        // Because we're already working in display coordinates (top-left origin),
        // we don't need additional coordinate transformation
        float wordMinX = Float.MAX_VALUE;
        float wordMinY = Float.MAX_VALUE;
        float wordMaxX = Float.MIN_VALUE;
        float wordMaxY = Float.MIN_VALUE;

        StringBuilder text = new StringBuilder();
        String fontName = null;
        float fontSize = 0;
        java.awt.Color color = null;

        for (TextElement element : characterElements) {
            if (element == null) continue; // Skip null elements

            wordMinX = Math.min(wordMinX, element.getX());
            wordMinY = Math.min(wordMinY, element.getY());
            wordMaxX = Math.max(wordMaxX, element.getX() + element.getWidth());
            wordMaxY = Math.max(wordMaxY, element.getY() + element.getHeight());

            text.append(element.getText());

            // Use the font of the first character as the word's font
            if (fontName == null) {
                fontName = element.getFontName();
                fontSize = element.getFontSize();
                color = element.getColor();
            }
        }

        // Calculate final word bounds
        float finalWidth = wordMaxX - wordMinX;
        float finalHeight = wordMaxY - wordMinY;

        // Ensure dimensions are valid
        if (finalWidth <= 0) finalWidth = 1.0f;
        if (finalHeight <= 0) finalHeight = fontSize > 0 ? fontSize : 10.0f;

        // Create a word element using the calculated bounds
        TextElement wordElement = new TextElement(
                UUID.randomUUID().toString(),
                text.toString(),
                wordMinX,
                wordMinY,
                finalWidth,
                finalHeight,
                fontName != null ? fontName : "Unknown",
                fontSize,
                fontSize
        );

        if (color != null) {
            wordElement.setColor(color);
        }

        return wordElement;
    }

    /**
     * Normalize font name by removing prefixes like "OKDFSH+" or similar.
     *
     * @param originalName The original font name
     * @return Normalized font name
     */
    private String normalizeFontName(String originalName) {
        if (originalName == null) return "Unknown";

        // Remove common prefixes like "OKDFSH+" or other embedded font markers
        int plusIndex = originalName.indexOf('+');
        if (plusIndex != -1 && plusIndex < originalName.length() - 1) {
            return originalName.substring(plusIndex + 1);
        }

        return originalName;
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