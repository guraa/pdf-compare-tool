package guraa.pdfcompare.service;

import com.itextpdf.kernel.pdf.PdfReader;
import guraa.pdfcompare.extraction.TextDifferenceExtractor;
import guraa.pdfcompare.extraction.TextDifferenceExtractor.TextElement;
import guraa.pdfcompare.model.PdfDocument;
import guraa.pdfcompare.model.difference.TextDifference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/**
 * Enhanced service for comparing text elements between PDF documents.
 * This service extracts text from PDFs along with spatial information and uses
 * diff algorithms to detect differences.
 */
@Slf4j
@Service
public class TextElementComparisonService {

    private final ExecutorService executorService;
    private final PdfRenderingService pdfRenderingService;

    /**
     * Constructor with qualifier to specify which executor service to use.
     *
     * @param executorService The executor service for comparison operations
     * @param pdfRenderingService The PDF rendering service
     */
    public TextElementComparisonService(
            @Qualifier("comparisonExecutor") ExecutorService executorService,
            PdfRenderingService pdfRenderingService) {
        this.executorService = executorService;
        this.pdfRenderingService = pdfRenderingService;
    }

    @Value("${app.comparison.text-similarity-threshold:0.8}")
    private double textSimilarityThreshold;

    /**
     * Compare text elements between two pages with complete spatial information.
     *
     * @param baseDocument The base document
     * @param compareDocument The document to compare against the base
     * @param basePageNumber The page number in the base document (1-based)
     * @param comparePageNumber The page number in the compare document (1-based)
     * @return A list of text differences with full coordinate information
     * @throws IOException If there is an error comparing the text
     */
    public List<TextDifference> compareText(
            PdfDocument baseDocument, PdfDocument compareDocument,
            int basePageNumber, int comparePageNumber) throws IOException {

        // Directly perform the comparison without internal caching or async execution
        try {
            return doCompareText(baseDocument, compareDocument, basePageNumber, comparePageNumber);
        } catch (IOException e) {
            log.error("Error comparing text for pages {} and {}: {}",
                    basePageNumber, comparePageNumber, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Perform the text comparison with full coordinate extraction.
     *
     * @param baseDocument The base document
     * @param compareDocument The document to compare against the base
     * @param basePageNumber The page number in the base document (1-based)
     * @param comparePageNumber The page number in the compare document (1-based)
     * @return A list of text differences with coordinates
     * @throws IOException If there is an error comparing the text
     */
    private List<TextDifference> doCompareText(
            PdfDocument baseDocument, PdfDocument compareDocument,
            int basePageNumber, int comparePageNumber) throws IOException {

        // Extract text elements with coordinates
        List<TextElement> baseElements;
        List<TextElement> compareElements;

        try {
            baseElements = TextDifferenceExtractor.extractTextElements(
                    baseDocument.getFilePath(), basePageNumber);

            compareElements = TextDifferenceExtractor.extractTextElements(
                    compareDocument.getFilePath(), comparePageNumber);
        } catch (IOException e) {
            log.error("Error extracting text elements: {}", e.getMessage(), e);
            // Fall back to simpler extraction if detailed extraction fails
            return extractAndCompareTextLinesOnly(baseDocument, compareDocument,
                    basePageNumber, comparePageNumber);
        }

        // Combine adjacent text elements into lines
        List<TextLine> baseLines = groupElementsIntoLines(baseElements);
        List<TextLine> compareLines = groupElementsIntoLines(compareElements);

        List<TextDifference> differences = new ArrayList<>();

        // More comprehensive text comparison
        for (int i = 0; i < Math.max(baseLines.size(), compareLines.size()); i++) {
            TextLine baseLine = i < baseLines.size() ? baseLines.get(i) : null;
            TextLine compareLine = i < compareLines.size() ? compareLines.get(i) : null;

            TextDifference lineDifference = detectLineDifference(
                    baseLine, compareLine,
                    basePageNumber, comparePageNumber);

            if (lineDifference != null) {
                differences.add(lineDifference);
            }
        }

        return differences;
    }

    /**
     * Group text elements into lines based on vertical position and spacing.
     *
     * @param elements The text elements to group
     * @return A list of text lines
     */
    private List<TextLine> groupElementsIntoLines(List<TextElement> elements) {
        if (elements == null || elements.isEmpty()) {
            return new ArrayList<>();
        }

        // Sort elements by y-coordinate (descending) and then by x-coordinate (ascending)
        elements.sort((e1, e2) -> {
            float yDiff = e2.getY() - e1.getY();
            if (Math.abs(yDiff) < 2.0f) { // Elements on the same line (allowing small differences)
                return Float.compare(e1.getX(), e2.getX()); // Left to right
            }
            return Float.compare(e2.getY(), e1.getY()); // Top to bottom
        });

        List<TextLine> lines = new ArrayList<>();
        List<TextElement> currentLine = new ArrayList<>();
        float currentY = elements.get(0).getY();

        for (TextElement element : elements) {
            // If this element is significantly different in Y position, start a new line
            if (Math.abs(element.getY() - currentY) > element.getHeight() * 0.8) {
                if (!currentLine.isEmpty()) {
                    lines.add(new TextLine(currentLine));
                    currentLine = new ArrayList<>();
                }
                currentY = element.getY();
            }

            currentLine.add(element);
        }

        // Add the last line if not empty
        if (!currentLine.isEmpty()) {
            lines.add(new TextLine(currentLine));
        }

        return lines;
    }

    /**
     * Detect differences between two text lines.
     *
     * @param baseLine The base text line
     * @param compareLine The compare text line
     * @param basePageNumber The page number in the base document
     * @param comparePageNumber The page number in the compare document
     * @return The text difference, or null if no significant difference
     */
    private TextDifference detectLineDifference(
            TextLine baseLine, TextLine compareLine,
            int basePageNumber, int comparePageNumber) {

        // If both lines are null, no difference
        if (baseLine == null && compareLine == null) {
            return null;
        }

        // Line added
        if (baseLine == null) {
            return TextDifference.builder()
                    .compareText(compareLine.getText())
                    .comparePageNumber(comparePageNumber)
                    .basePageNumber(0) // No base page
                    .compareTextLength(compareLine.getText().length())
                    .textDifference(true)
                    .severity(compareLine.getText().length() > 50 ? "major" : "minor")
                    .type("text")
                    .addition(true)
                    .changeType("added")
                    .compareX(compareLine.getX())
                    .compareY(compareLine.getY())
                    .compareWidth(compareLine.getWidth())
                    .compareHeight(compareLine.getHeight())
                    .x(compareLine.getX()) // Use compare coordinates for display
                    .y(compareLine.getY())
                    .width(compareLine.getWidth())
                    .height(compareLine.getHeight())
                    .build();
        }

        // Line deleted
        if (compareLine == null) {
            return TextDifference.builder()
                    .changeType("deleted")
                    .baseText(baseLine.getText())
                    .basePageNumber(basePageNumber)
                    .comparePageNumber(0) // No compare page
                    .baseTextLength(baseLine.getText().length())
                    .textDifference(true)
                    .severity(baseLine.getText().length() > 50 ? "major" : "minor")
                    .type("text")
                    .deletion(true)
                    .baseX(baseLine.getX())
                    .baseY(baseLine.getY())
                    .baseWidth(baseLine.getWidth())
                    .baseHeight(baseLine.getHeight())
                    .x(baseLine.getX()) // Use base coordinates for display
                    .y(baseLine.getY())
                    .width(baseLine.getWidth())
                    .height(baseLine.getHeight())
                    .build();
        }

        // Compute text similarity
        double similarity = computeTextSimilarity(baseLine.getText(), compareLine.getText());

        // If significant difference
        if (similarity < textSimilarityThreshold) {
            // Compute average coordinates for display
            double displayX = (baseLine.getX() + compareLine.getX()) / 2;
            double displayY = (baseLine.getY() + compareLine.getY()) / 2;
            double displayWidth = Math.max(baseLine.getWidth(), compareLine.getWidth());
            double displayHeight = Math.max(baseLine.getHeight(), compareLine.getHeight());

            return TextDifference.builder()
                    .changeType("modified")
                    .baseText(baseLine.getText())
                    .compareText(compareLine.getText())
                    .basePageNumber(basePageNumber)
                    .comparePageNumber(comparePageNumber)
                    .baseTextLength(baseLine.getText().length())
                    .compareTextLength(compareLine.getText().length())
                    .similarityScore(similarity)
                    .textDifference(true)
                    .modification(true)
                    .severity(computeSeverity(baseLine.getText(), compareLine.getText()))
                    .type("text")
                    .baseX(baseLine.getX())
                    .baseY(baseLine.getY())
                    .compareX(compareLine.getX())
                    .compareY(compareLine.getY())
                    .baseWidth(baseLine.getWidth())
                    .baseHeight(baseLine.getHeight())
                    .compareWidth(compareLine.getWidth())
                    .compareHeight(compareLine.getHeight())
                    .x(displayX) // Use average coordinates for display
                    .y(displayY)
                    .width(displayWidth)
                    .height(displayHeight)
                    .xdifference(compareLine.getX() - baseLine.getX())
                    .ydifference(compareLine.getY() - baseLine.getY())
                    .widthDifference(compareLine.getWidth() - baseLine.getWidth())
                    .heightDifference(compareLine.getHeight() - baseLine.getHeight())
                    .build();
        }

        return null;
    }

    /**
     * Fall back method to extract and compare text lines only.
     * Used when detailed text extraction fails.
     */
    private List<TextDifference> extractAndCompareTextLinesOnly(
            PdfDocument baseDocument, PdfDocument compareDocument,
            int basePageNumber, int comparePageNumber) throws IOException {

        // Extract text from the pages as simple lines
        List<String> baseLines = extractTextLines(baseDocument, basePageNumber);
        List<String> compareLines = extractTextLines(compareDocument, comparePageNumber);

        List<TextDifference> differences = new ArrayList<>();

        // Simple text comparison
        for (int i = 0; i < Math.max(baseLines.size(), compareLines.size()); i++) {
            String baseLine = i < baseLines.size() ? baseLines.get(i) : null;
            String compareLine = i < compareLines.size() ? compareLines.get(i) : null;

            // Use default coordinates since we don't have detailed information
            TextDifference difference = detectSimpleLineDifference(
                    baseLine, compareLine,
                    basePageNumber, comparePageNumber);

            if (difference != null) {
                differences.add(difference);
            }
        }

        return differences;
    }

    /**
     * Simplified line difference detection for fallback mode.
     */
    private TextDifference detectSimpleLineDifference(
            String baseLine, String compareLine,
            int basePageNumber, int comparePageNumber) {

        // If both lines are null, no difference
        if (baseLine == null && compareLine == null) {
            return null;
        }

        // Line added
        if (baseLine == null) {
            return TextDifference.builder()
                    .compareText(compareLine)
                    .comparePageNumber(comparePageNumber)
                    .basePageNumber(0)
                    .compareTextLength(compareLine.length())
                    .textDifference(true)
                    .severity(compareLine.length() > 50 ? "major" : "minor")
                    .type("text")
                    .addition(true)
                    .changeType("added")
                    // Use default coordinates since we don't have detailed information
                    .compareX(0)
                    .compareY(0)
                    .compareWidth(100)
                    .compareHeight(12)
                    .x(0)
                    .y(0)
                    .width(100)
                    .height(12)
                    .build();
        }

        // Line deleted
        if (compareLine == null) {
            return TextDifference.builder()
                    .changeType("deleted")
                    .baseText(baseLine)
                    .basePageNumber(basePageNumber)
                    .comparePageNumber(0)
                    .baseTextLength(baseLine.length())
                    .textDifference(true)
                    .severity(baseLine.length() > 50 ? "major" : "minor")
                    .type("text")
                    .deletion(true)
                    // Use default coordinates
                    .baseX(0)
                    .baseY(0)
                    .baseWidth(100)
                    .baseHeight(12)
                    .x(0)
                    .y(0)
                    .width(100)
                    .height(12)
                    .build();
        }

        // Compute text similarity
        double similarity = computeTextSimilarity(baseLine, compareLine);

        // If significant difference
        if (similarity < textSimilarityThreshold) {
            return TextDifference.builder()
                    .changeType("modified")
                    .baseText(baseLine)
                    .compareText(compareLine)
                    .basePageNumber(basePageNumber)
                    .comparePageNumber(comparePageNumber)
                    .baseTextLength(baseLine.length())
                    .compareTextLength(compareLine.length())
                    .similarityScore(similarity)
                    .textDifference(true)
                    .modification(true)
                    .severity(computeSeverity(baseLine, compareLine))
                    .type("text")
                    // Use default coordinates
                    .baseX(0)
                    .baseY(0)
                    .compareX(0)
                    .compareY(0)
                    .baseWidth(100)
                    .baseHeight(12)
                    .compareWidth(100)
                    .compareHeight(12)
                    .x(0)
                    .y(0)
                    .width(100)
                    .height(12)
                    .xdifference(0)
                    .ydifference(0)
                    .widthDifference(0)
                    .heightDifference(0)
                    .build();
        }

        return null;
    }

    /**
     * Extract text lines from a page.
     *
     * @param document The document
     * @param pageNumber The page number (1-based)
     * @return A list of text lines
     * @throws IOException If there is an error extracting the text
     */
    private List<String> extractTextLines(PdfDocument document, int pageNumber) throws IOException {
        try (PdfReader reader = new PdfReader(document.getFilePath());
             com.itextpdf.kernel.pdf.PdfDocument pdfDoc = new com.itextpdf.kernel.pdf.PdfDocument(reader)) {

            if (pageNumber > 0 && pageNumber <= pdfDoc.getNumberOfPages()) {
                com.itextpdf.kernel.pdf.PdfPage page = pdfDoc.getPage(pageNumber);
                String text = com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor.getTextFromPage(
                        page, new com.itextpdf.kernel.pdf.canvas.parser.listener.LocationTextExtractionStrategy());

                // Split text into lines using a regex that handles different line endings
                return Arrays.asList(text.split("\\R"));
            } else {
                log.warn("Invalid page number {} requested for document {} with {} pages.",
                        pageNumber, document.getFileId(), pdfDoc.getNumberOfPages());
                return new ArrayList<>(); // Return empty list for invalid page number
            }
        } catch (Exception e) {
            log.error("Error extracting text lines: {}", e.getMessage(), e);
            throw new IOException("Failed to extract text lines", e);
        }
    }

    /**
     * Compute text similarity.
     *
     * @param text1 The first text
     * @param text2 The second text
     * @return The similarity score (0.0 to 1.0)
     */
    private double computeTextSimilarity(String text1, String text2) {
        // Use Levenshtein distance or other string similarity algorithms
        // Lower value means less similarity
        return 1.0 - (double) computeLevenshteinDistance(text1, text2) /
                Math.max(text1.length(), text2.length());
    }

    /**
     * Compute severity based on the amount of difference.
     *
     * @param baseText The base text
     * @param compareText The compare text
     * @return The severity
     */
    private String computeSeverity(String baseText, String compareText) {
        int lengthDiff = Math.abs(baseText.length() - compareText.length());
        int contentDiff = computeLevenshteinDistance(baseText, compareText);

        if (contentDiff > baseText.length() * 0.5) return "major";
        if (contentDiff > baseText.length() * 0.2) return "minor";
        return "cosmetic";
    }

    /**
     * Compute Levenshtein distance between two strings.
     *
     * @param s1 The first string
     * @param s2 The second string
     * @return The Levenshtein distance
     */
    private int computeLevenshteinDistance(String s1, String s2) {
        // Levenshtein distance implementation to measure string differences
        // This is a common string similarity metric
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];

        for (int i = 0; i <= s1.length(); i++) {
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0) dp[i][j] = j;
                else if (j == 0) dp[i][j] = i;
                else {
                    dp[i][j] = Math.min(
                            Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                            dp[i - 1][j - 1] + (s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1)
                    );
                }
            }
        }

        return dp[s1.length()][s2.length()];
    }

    /**
     * Utility class to represent a line of text with spatial information.
     */
    private static class TextLine {
        private final List<TextElement> elements;
        private final String text;
        private final float x;
        private final float y;
        private final float width;
        private final float height;

        /**
         * Constructor.
         *
         * @param elements The text elements in this line
         */
        public TextLine(List<TextElement> elements) {
            this.elements = new ArrayList<>(elements);

            // Sort elements by x-coordinate
            this.elements.sort(Comparator.comparing(TextElement::getX));

            // Calculate text content
            this.text = this.elements.stream()
                    .map(TextElement::getText)
                    .collect(Collectors.joining(" "));

            // Calculate bounding box
            float minX = Float.MAX_VALUE;
            float minY = Float.MAX_VALUE;
            float maxX = Float.MIN_VALUE;
            float maxY = Float.MIN_VALUE;

            for (TextElement element : this.elements) {
                minX = Math.min(minX, element.getX());
                minY = Math.min(minY, element.getY());
                maxX = Math.max(maxX, element.getX() + element.getWidth());
                maxY = Math.max(maxY, element.getY() + element.getHeight());
            }

            this.x = minX;
            this.y = minY;
            this.width = maxX - minX;
            this.height = maxY - minY;
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

        @Override
        public String toString() {
            return "TextLine{" +
                    "text='" + text + '\'' +
                    ", x=" + x +
                    ", y=" + y +
                    ", width=" + width +
                    ", height=" + height +
                    '}';
        }
    }

    /**
     * Compare text elements between two documents.
     *
     * @param baseDocument The base document
     * @param compareDocument The document to compare against the base
     * @param pagePairs The page pairs to compare
     * @return A map of page pairs to text differences
     * @throws IOException If there is an error comparing the text
     */
    public Map<PagePair, List<TextDifference>> compareText(
            PdfDocument baseDocument, PdfDocument compareDocument,
            List<PagePair> pagePairs) throws IOException {

        Map<PagePair, List<TextDifference>> result = new HashMap<>();

        // Process each page pair sequentially for better stability
        for (PagePair pagePair : pagePairs) {
            if (pagePair.isMatched()) {
                try {
                    log.info("Processing text comparison for page pair: base={}, compare={}",
                            pagePair.getBasePageNumber(), pagePair.getComparePageNumber());

                    // Compare the matched pages
                    List<TextDifference> differences = compareText(
                            baseDocument, compareDocument,
                            pagePair.getBasePageNumber(), pagePair.getComparePageNumber());

                    // Verify and fix any missing coordinates in text differences
                    if (differences != null && !differences.isEmpty()) {
                        differences = ensureCoordinates(differences);
                        log.info("Found {} text differences for page pair: base={}, compare={}",
                                differences.size(), pagePair.getBasePageNumber(), pagePair.getComparePageNumber());
                    } else {
                        log.info("No text differences found for page pair: base={}, compare={}",
                                pagePair.getBasePageNumber(), pagePair.getComparePageNumber());
                        differences = new ArrayList<>();
                    }

                    result.put(pagePair, differences);
                } catch (Exception e) {
                    log.error("Error comparing text for page pair: base={}, compare={}: {}",
                            pagePair.getBasePageNumber(), pagePair.getComparePageNumber(), e.getMessage(), e);
                    // Put an empty list to avoid null reference issues
                    result.put(pagePair, new ArrayList<>());
                }
            }
        }

        return result;
    }

    /**
     * Ensure all text differences have valid coordinates.
     * If coordinates are missing or zero, this method assigns default values.
     *
     * @param differences The list of text differences
     * @return The updated list with valid coordinates
     */
    private List<TextDifference> ensureCoordinates(List<TextDifference> differences) {
        for (TextDifference diff : differences) {
            // Check if any of the main coordinates are missing
            if (diff.getX() == 0 && diff.getY() == 0 && diff.getWidth() == 0 && diff.getHeight() == 0) {
                // First, try to use specific coordinates based on difference type
                if (diff.isAddition() && diff.getCompareX() > 0) {
                    // For additions, use compare coordinates
                    diff.setX(diff.getCompareX());
                    diff.setY(diff.getCompareY());
                    diff.setWidth(diff.getCompareWidth() > 0 ? diff.getCompareWidth() : 100);
                    diff.setHeight(diff.getCompareHeight() > 0 ? diff.getCompareHeight() : 14);
                } else if (diff.isDeletion() && diff.getBaseX() > 0) {
                    // For deletions, use base coordinates
                    diff.setX(diff.getBaseX());
                    diff.setY(diff.getBaseY());
                    diff.setWidth(diff.getBaseWidth() > 0 ? diff.getBaseWidth() : 100);
                    diff.setHeight(diff.getBaseHeight() > 0 ? diff.getBaseHeight() : 14);
                } else if (diff.isModification()) {
                    // For modifications, compute average or use whichever is available
                    double x = 0, y = 0, width = 0, height = 0;

                    if (diff.getBaseX() > 0 && diff.getCompareX() > 0) {
                        // Both available, use average
                        x = (diff.getBaseX() + diff.getCompareX()) / 2;
                        y = (diff.getBaseY() + diff.getCompareY()) / 2;
                        width = Math.max(diff.getBaseWidth(), diff.getCompareWidth());
                        height = Math.max(diff.getBaseHeight(), diff.getCompareHeight());
                    } else if (diff.getBaseX() > 0) {
                        // Only base coordinates available
                        x = diff.getBaseX();
                        y = diff.getBaseY();
                        width = diff.getBaseWidth();
                        height = diff.getBaseHeight();
                    } else if (diff.getCompareX() > 0) {
                        // Only compare coordinates available
                        x = diff.getCompareX();
                        y = diff.getCompareY();
                        width = diff.getCompareWidth();
                        height = diff.getCompareHeight();
                    }

                    // If we got valid coordinates, use them
                    if (x > 0 || y > 0) {
                        diff.setX(x);
                        diff.setY(y);
                        diff.setWidth(width > 0 ? width : 100);
                        diff.setHeight(height > 0 ? height : 14);
                    } else {
                        // Use default values based on text length
                        assignDefaultCoordinates(diff);
                    }
                } else {
                    // Use default values
                    assignDefaultCoordinates(diff);
                }
            }

            // Always ensure width and height are non-zero
            if (diff.getWidth() <= 0) {
                diff.setWidth(100);
            }
            if (diff.getHeight() <= 0) {
                diff.setHeight(14);
            }
        }

        return differences;
    }

    /**
     * Assign default coordinates to a text difference based on text length.
     *
     * @param diff The text difference
     */
    private void assignDefaultCoordinates(TextDifference diff) {
        // Create reasonable defaults based on text length
        int textLength = 0;
        if (diff.isAddition() && diff.getCompareText() != null) {
            textLength = diff.getCompareText().length();
        } else if (diff.isDeletion() && diff.getBaseText() != null) {
            textLength = diff.getBaseText().length();
        } else if (diff.isModification()) {
            textLength = Math.max(
                    diff.getBaseText() != null ? diff.getBaseText().length() : 0,
                    diff.getCompareText() != null ? diff.getCompareText().length() : 0
            );
        }

        // Scale width based on text length, with min and max bounds
        double width = Math.min(500, Math.max(50, 20 + textLength * 7));

        // Use page number to vary y position
        int pageOffset = 0;
        if (diff.isAddition() && diff.getComparePageNumber() > 0) {
            pageOffset = diff.getComparePageNumber() - 1;
        } else if (diff.isDeletion() && diff.getBasePageNumber() > 0) {
            pageOffset = diff.getBasePageNumber() - 1;
        } else if (diff.isModification() && diff.getBasePageNumber() > 0) {
            pageOffset = diff.getBasePageNumber() - 1;
        }

        // Create a position that's different for each difference on the page
        // Use hashCode to create pseudo-random but consistent position
        int hashCode = diff.getId() != null ? diff.getId().hashCode() : textLength * 31;
        double x = 100 + (Math.abs(hashCode) % 300);
        double y = 100 + (Math.abs(hashCode / 10) % 500) + pageOffset * 50;

        diff.setX(x);
        diff.setY(y);
        diff.setWidth(width);
        diff.setHeight(14);  // Default text height
    }
}