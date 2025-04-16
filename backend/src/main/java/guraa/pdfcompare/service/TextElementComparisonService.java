package guraa.pdfcompare.service;

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.EventType;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.itextpdf.kernel.pdf.canvas.parser.data.IEventData;
import com.itextpdf.kernel.pdf.canvas.parser.data.TextRenderInfo;
import com.itextpdf.kernel.pdf.canvas.parser.listener.LocationTextExtractionStrategy;
import guraa.pdfcompare.comparison.TextElementDifference;
import guraa.pdfcompare.model.PdfDocument;
import guraa.pdfcompare.model.difference.TextDifference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/**
 * Service for comparing text elements between PDF documents.
 * This service extracts text from PDFs and uses diff algorithms to
 * detect differences.
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
     * Compare text elements between two pages synchronously.
     * The PDFComparisonEngine handles the asynchronous execution.
     *
     * @param baseDocument The base document
     * @param compareDocument The document to compare against the base
     * @param basePageNumber The page number in the base document (1-based)
     * @param comparePageNumber The page number in the compare document (1-based)
     * @return A list of text differences
     * @throws IOException If there is an error comparing the text
     */
    public List<TextDifference> compareText(
            PdfDocument baseDocument, PdfDocument compareDocument,
            int basePageNumber, int comparePageNumber) throws IOException {

        log.info("Starting text comparison for documents: {} and {}, pages: {} and {}",
                baseDocument.getFileId(), compareDocument.getFileId(), basePageNumber, comparePageNumber);

        try {
            // Extract text from the pages with position information
            List<TextElement> baseElements = extractTextElements(baseDocument, basePageNumber);
            List<TextElement> compareElements = extractTextElements(compareDocument, comparePageNumber);

            log.debug("Extracted {} text elements from base page {} and {} elements from compare page {}",
                    baseElements.size(), basePageNumber, compareElements.size(), comparePageNumber);

            // Match text elements between pages
            Map<Integer, Integer> matchedElements = matchTextElements(baseElements, compareElements);
            log.debug("Matched {} text elements between pages", matchedElements.size());

            List<TextDifference> differences = new ArrayList<>();

            // Process additions (elements in compare doc not matched to base)
            Set<Integer> matchedCompareIndices = new HashSet<>(matchedElements.values());
            for (int i = 0; i < compareElements.size(); i++) {
                if (!matchedCompareIndices.contains(i)) {
                    TextElement element = compareElements.get(i);
                    differences.add(createAdditionDifference(element, comparePageNumber));
                }
            }

            // Process deletions and modifications
            for (int baseIndex = 0; baseIndex < baseElements.size(); baseIndex++) {
                TextElement baseElement = baseElements.get(baseIndex);

                if (!matchedElements.containsKey(baseIndex)) {
                    // Deletion - element in base not matched to compare
                    differences.add(createDeletionDifference(baseElement, basePageNumber));
                } else {
                    // Modification - elements matched but with differences
                    int compareIndex = matchedElements.get(baseIndex);
                    TextElement compareElement = compareElements.get(compareIndex);

                    double similarity = computeTextSimilarity(baseElement.getText(), compareElement.getText());

                    if (similarity < textSimilarityThreshold) {
                        differences.add(createModificationDifference(
                                baseElement, compareElement,
                                basePageNumber, comparePageNumber,
                                similarity));
                    }
                }
            }

            log.info("Completed text comparison for pages {} and {}, found {} differences",
                    basePageNumber, comparePageNumber, differences.size());
            return differences;
        } catch (Exception e) {
            log.error("Error comparing text for pages {} and {}: {}",
                    basePageNumber, comparePageNumber, e.getMessage(), e);
            // Return empty list to avoid breaking the entire comparison
            return new ArrayList<>();
        }
    }

    /**
     * Create a text difference for an addition.
     */
    private TextDifference createAdditionDifference(TextElement element, int comparePageNumber) {
        log.debug("Creating addition difference for text: '{}'", element.getText());

        return TextDifference.builder()
                .id(UUID.randomUUID().toString())
                .type("text")
                .changeType("added")
                .compareText(element.getText())
                .comparePageNumber(comparePageNumber)
                .compareTextLength(element.getText().length())
                .textDifference(true)
                .severity(element.getText().length() > 50 ? "major" : "minor")
                .addition(true)
                .compareX(element.getX())
                .compareY(element.getY())
                .compareWidth(element.getWidth())
                .compareHeight(element.getHeight())
                .compareFont(element.getFontName())
                .compareFontSize(element.getFontSize())
                .description("Added text: " + element.getText())
                .build();
    }

    /**
     * Create a text difference for a deletion.
     */
    private TextDifference createDeletionDifference(TextElement element, int basePageNumber) {
        log.debug("Creating deletion difference for text: '{}'", element.getText());

        return TextDifference.builder()
                .id(UUID.randomUUID().toString())
                .type("text")
                .changeType("deleted")
                .baseText(element.getText())
                .basePageNumber(basePageNumber)
                .baseTextLength(element.getText().length())
                .textDifference(true)
                .severity(element.getText().length() > 50 ? "major" : "minor")
                .deletion(true)
                .baseX(element.getX())
                .baseY(element.getY())
                .baseWidth(element.getWidth())
                .baseHeight(element.getHeight())
                .baseFont(element.getFontName())
                .baseFontSize(element.getFontSize())
                .description("Deleted text: " + element.getText())
                .build();
    }

    /**
     * Create a text difference for a modification.
     */
    private TextDifference createModificationDifference(
            TextElement baseElement, TextElement compareElement,
            int basePageNumber, int comparePageNumber, double similarity) {

        log.debug("Creating modification difference for text: '{}' -> '{}'",
                baseElement.getText(), compareElement.getText());

        String severity = computeSeverity(baseElement.getText(), compareElement.getText());

        return TextDifference.builder()
                .id(UUID.randomUUID().toString())
                .type("text")
                .changeType("modified")
                .baseText(baseElement.getText())
                .compareText(compareElement.getText())
                .basePageNumber(basePageNumber)
                .comparePageNumber(comparePageNumber)
                .baseTextLength(baseElement.getText().length())
                .compareTextLength(compareElement.getText().length())
                .similarityScore(similarity)
                .textDifference(true)
                .modification(true)
                .severity(severity)
                .baseX(baseElement.getX())
                .baseY(baseElement.getY())
                .compareX(compareElement.getX())
                .compareY(compareElement.getY())
                .baseWidth(baseElement.getWidth())
                .baseHeight(baseElement.getHeight())
                .compareWidth(compareElement.getWidth())
                .compareHeight(compareElement.getHeight())
                .baseFont(baseElement.getFontName())
                .compareFont(compareElement.getFontName())
                .baseFontSize(baseElement.getFontSize())
                .compareFontSize(compareElement.getFontSize())
                .xdifference(compareElement.getX() - baseElement.getX())
                .ydifference(compareElement.getY() - baseElement.getY())
                .widthDifference(compareElement.getWidth() - baseElement.getWidth())
                .heightDifference(compareElement.getHeight() - baseElement.getHeight())
                .description("Text changed from: '" + baseElement.getText() +
                        "' to: '" + compareElement.getText() + "'")
                .build();
    }

    /**
     * Match text elements between pages based on content and position.
     */
    private Map<Integer, Integer> matchTextElements(List<TextElement> baseElements, List<TextElement> compareElements) {
        Map<Integer, Integer> matches = new HashMap<>();

        log.debug("Matching {} base elements with {} compare elements",
                baseElements.size(), compareElements.size());

        // Simple greedy matching algorithm
        for (int i = 0; i < baseElements.size(); i++) {
            TextElement baseElement = baseElements.get(i);
            double bestSimilarity = textSimilarityThreshold;
            int bestMatch = -1;

            for (int j = 0; j < compareElements.size(); j++) {
                // Skip already matched compare elements
                if (matches.containsValue(j)) {
                    continue;
                }

                TextElement compareElement = compareElements.get(j);

                // Calculate text similarity
                double textSim = computeTextSimilarity(baseElement.getText(), compareElement.getText());

                // Calculate position similarity (simplified)
                double posSim = computePositionSimilarity(baseElement, compareElement);

                // Combined similarity (weighted)
                double similarity = 0.7 * textSim + 0.3 * posSim;

                if (similarity > bestSimilarity) {
                    bestSimilarity = similarity;
                    bestMatch = j;
                }
            }

            // If we found a good match, record it
            if (bestMatch >= 0) {
                matches.put(i, bestMatch);
            }
        }

        return matches;
    }

    /**
     * Calculate similarity between positions of two text elements.
     */
    private double computePositionSimilarity(TextElement e1, TextElement e2) {
        // Normalized distance between elements
        double xDistance = Math.abs(e1.getX() - e2.getX());
        double yDistance = Math.abs(e1.getY() - e2.getY());

        // Normalize by a typical page size (letter size in points)
        double normalizedDist = Math.sqrt(Math.pow(xDistance / 612.0, 2) + Math.pow(yDistance / 792.0, 2));

        // Convert to similarity (1 when identical, 0 when far apart)
        return Math.max(0, 1 - normalizedDist);
    }

    /**
     * Helper class to store text element information.
     */
    private static class TextElement {
        private final String text;
        private final double x;
        private final double y;
        private final double width;
        private final double height;
        private final String fontName;
        private final float fontSize;

        public TextElement(String text, double x, double y, double width, double height,
                           String fontName, float fontSize) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.fontName = fontName;
            this.fontSize = fontSize;
        }

        public String getText() { return text; }
        public double getX() { return x; }
        public double getY() { return y; }
        public double getWidth() { return width; }
        public double getHeight() { return height; }
        public String getFontName() { return fontName; }
        public float getFontSize() { return fontSize; }
    }

    /**
     * Extract text elements with position information from a PDF page.
     */
    private List<TextElement> extractTextElements(PdfDocument document, int pageNumber) {
        List<TextElement> elements = new ArrayList<>();

        try {
            log.debug("Extracting text elements from document {}, page {}",
                    document.getFileId(), pageNumber);

            if (pageNumber <= 0 || pageNumber > document.getPageCount()) {
                log.warn("Invalid page number {} for document {}", pageNumber, document.getFileId());
                return elements;
            }

            // First try to extract with iText for accurate positions
            try (PdfReader reader = new PdfReader(document.getFilePath())) {
                com.itextpdf.kernel.pdf.PdfDocument pdfDoc = new com.itextpdf.kernel.pdf.PdfDocument(reader);
                PdfPage page = pdfDoc.getPage(pageNumber);

                // Use a custom extraction strategy
                PositionalExtractionStrategy strategy = new PositionalExtractionStrategy();
                PdfTextExtractor.getTextFromPage(page, strategy);

                elements = strategy.getTextElements();
                log.debug("Extracted {} text elements with iText", elements.size());

                // Close the pdf document explicitly
                pdfDoc.close();
            } catch (Exception e) {
                log.warn("Error extracting text with iText: {}", e.getMessage());
            }

            // If iText extraction failed or didn't find any elements, fall back to basic extraction
            if (elements.isEmpty()) {
                log.debug("Falling back to basic text extraction");
                List<String> lines = extractTextLines(document, pageNumber);

                // Create elements with placeholder positions
                double y = 100.0;
                for (String line : lines) {
                    if (line != null && !line.trim().isEmpty()) {
                        elements.add(new TextElement(
                                line,
                                100.0,             // x
                                y,                 // y
                                line.length() * 7, // width (estimate based on length)
                                15.0,              // height
                                "Unknown",         // fontName
                                12.0f              // fontSize
                        ));
                        y += 20.0; // increase y for next line
                    }
                }
                log.debug("Created {} text elements from lines with placeholder positions", elements.size());
            }
        } catch (Exception e) {
            log.error("Error in text element extraction: {}", e.getMessage(), e);
        }

        return elements;
    }

    /**
     * Custom text extraction strategy that collects position information.
     */
    private static class PositionalExtractionStrategy extends LocationTextExtractionStrategy {
        private final List<TextElement> textElements = new ArrayList<>();

        @Override
        public void eventOccurred(IEventData data, EventType type) {
            if (type == EventType.RENDER_TEXT) {
                TextRenderInfo renderInfo = (TextRenderInfo) data;

                String text = renderInfo.getText();
                if (text != null && !text.isEmpty()) {
                    // Get bounding rectangle for text
                    Rectangle textRect = renderInfo.getBaseline().getBoundingRectangle();

                    // Get font information if available
                    String fontName = "Unknown";
                    float fontSize = 12.0f;

                    try {
                        fontName = renderInfo.getFont() != null ?
                                renderInfo.getFont().getFontProgram().getFontNames().getFontName() : "Unknown";
                        fontSize = renderInfo.getFontSize();
                    } catch (Exception e) {
                        // Ignore font extraction errors
                    }

                    // Calculate height from ascent/descent if available, or use default
                    float height = 12.0f;
                    try {
                        height = renderInfo.getAscentLine().getBoundingRectangle().getY() -
                                renderInfo.getDescentLine().getBoundingRectangle().getY();
                    } catch (Exception e) {
                        // Use default height if calculation fails
                    }

                    textElements.add(new TextElement(
                            text,
                            textRect.getX(),
                            textRect.getY(),
                            textRect.getWidth(),
                            height,
                            fontName,
                            fontSize
                    ));
                }
            }

            // Make sure to call parent implementation
            super.eventOccurred(data, type);
        }

        public List<TextElement> getTextElements() {
            return textElements;
        }
    }

    /**
     * Extract text lines from a page.
     */
    private List<String> extractTextLines(PdfDocument document, int pageNumber) throws IOException {
        log.debug("Extracting text lines from document {}, page {}",
                document.getFileId(), pageNumber);

        // Path to extracted text file
        String extractedTextPath = document.getExtractedTextPath(pageNumber);
        File extractedTextFile = new File(extractedTextPath);

        if (extractedTextFile.exists()) {
            // Read text lines from previously extracted file
            try (BufferedReader reader = new BufferedReader(new FileReader(extractedTextFile))) {
                List<String> lines = reader.lines().collect(Collectors.toList());
                log.debug("Read {} lines from extracted text file", lines.size());
                return lines;
            }
        } else {
            log.debug("No extracted text file found, using simple extraction");

            // For simplicity, return placeholder text
            List<String> lines = new ArrayList<>();
            lines.add("Text content from page " + pageNumber);
            return lines;
        }
    }

    /**
     * Calculate text similarity using Levenshtein distance.
     */
    private double computeTextSimilarity(String text1, String text2) {
        // Handle null or empty strings
        if (text1 == null || text2 == null) {
            return 0.0;
        }
        if (text1.isEmpty() && text2.isEmpty()) {
            return 1.0;
        }
        if (text1.isEmpty() || text2.isEmpty()) {
            return 0.0;
        }

        // Calculate Levenshtein distance
        int distance = computeLevenshteinDistance(text1, text2);
        int maxLength = Math.max(text1.length(), text2.length());

        // Convert to similarity score (0 to 1)
        return 1.0 - (double) distance / maxLength;
    }

    /**
     * Compute Levenshtein distance between two strings.
     */
    private int computeLevenshteinDistance(String s1, String s2) {
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
     * Determine severity of a text difference.
     */
    private String computeSeverity(String baseText, String compareText) {
        // Handle null cases
        if (baseText == null || compareText == null) {
            return "major";
        }

        // Calculate difference metrics
        int lengthDiff = Math.abs(baseText.length() - compareText.length());
        int contentDiff = computeLevenshteinDistance(baseText, compareText);
        double maxLength = Math.max(baseText.length(), compareText.length());

        if (maxLength == 0) {
            return "minor"; // Empty strings
        }

        // Difference as percentage of total length
        double diffRatio = contentDiff / maxLength;

        // Assign severity based on extent of differences
        if (diffRatio > 0.5) return "major";
        if (diffRatio > 0.2) return "minor";
        return "cosmetic";
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

        log.info("Starting text comparison for documents: {} and {}, with {} page pairs",
                baseDocument.getFileId(), compareDocument.getFileId(), pagePairs.size());

        Map<PagePair, List<TextDifference>> result = new HashMap<>();

        for (PagePair pagePair : pagePairs) {
            if (pagePair.isMatched()) {
                try {
                    // Compare the matched pages
                    List<TextDifference> differences = compareText(
                            baseDocument, compareDocument,
                            pagePair.getBasePageNumber(), pagePair.getComparePageNumber());

                    result.put(pagePair, differences);
                } catch (Exception e) {
                    log.error("Error comparing text for page pair {}: {}", pagePair.getId(), e.getMessage(), e);
                    // Continue with other page pairs
                    result.put(pagePair, new ArrayList<>());
                }
            }
        }

        log.info("Completed text comparison for all page pairs");
        return result;
    }

    /**
     * Get text elements from a page.
     *
     * @param document The document
     * @param pageNumber The page number (1-based)
     * @return A list of text elements
     * @throws IOException If there is an error extracting the text elements
     */
    public List<TextElementDifference> getTextElements(PdfDocument document, int pageNumber) throws IOException {
        log.info("Getting text elements for document: {}, page: {}", document.getFileId(), pageNumber);

        List<TextElementDifference> result = new ArrayList<>();

        try {
            // Extract text elements using our internal method
            List<TextElement> elements = extractTextElements(document, pageNumber);

            // Convert to TextElementDifference objects
            for (TextElement element : elements) {
                TextElementDifference diff = TextElementDifference.builder()
                        .id(UUID.randomUUID().toString())
                        .type("text")
                        .baseText(element.getText())
                        .baseX(element.getX())
                        .baseY(element.getY())
                        .baseWidth(element.getWidth())
                        .baseHeight(element.getHeight())
                        .build();

                result.add(diff);
            }

            log.info("Found {} text elements on page {}", result.size(), pageNumber);
        } catch (Exception e) {
            log.error("Error getting text elements: {}", e.getMessage(), e);
        }

        return result;
    }
}