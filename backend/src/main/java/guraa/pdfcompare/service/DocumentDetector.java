package guraa.pdfcompare.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Responsible for detecting document boundaries within a PDF.
 * This class analyzes page content to determine where logical document breaks occur.
 */
@Slf4j
@Component
public class DocumentDetector {

    private final int minPagesPerDocument;

    // Regular expressions for document structure detection
    private static final Pattern TITLE_PATTERN = Pattern.compile("(?i)\\b(report|document|presentation|analysis|proposal|plan|agreement|contract)\\b");
    private static final Pattern TOC_PATTERN = Pattern.compile("(?i)\\b(table\\s+of\\s+contents|contents|index)\\b");
    private static final Pattern HEADER_PATTERN = Pattern.compile("(?m)^\\s*\\d+(\\.\\d+)*\\s+[A-Z]");
    private static final Pattern PAGE_NUMBER_PATTERN = Pattern.compile("(?m)^\\s*Page\\s+\\d+\\s+of\\s+\\d+\\s*$");
    private static final Pattern NEW_SECTION_PATTERN = Pattern.compile("(?m)^\\s*(\\d+(\\.\\d+)*\\s+)?[A-Z][A-Za-z0-9 \\-:]{5,}\\s*$");
    private static final Pattern COPYRIGHT_PATTERN = Pattern.compile("(?i)\\bcopyright\\b.{0,20}\\d{4}");
    private static final Pattern DATE_PATTERN = Pattern.compile("(?i)(january|february|march|april|may|june|july|august|september|october|november|december)\\s+\\d{1,2},\\s+\\d{4}");

    /**
     * Constructor for document detector.
     *
     * @param minPagesPerDocument Minimum pages required for a document section
     */
    public DocumentDetector(@Value("${app.comparison.min-pages-per-document:1}") int minPagesPerDocument) {
        this.minPagesPerDocument = minPagesPerDocument;
    }

    /**
     * Identify document boundaries within a PDF based on page texts.
     *
     * @param pageTexts List of text contents for each page
     * @return List of document boundaries
     */
    public List<DocumentBoundary> identifyDocumentBoundaries(List<String> pageTexts) {
        List<DocumentBoundary> boundaries = new ArrayList<>();

        // If the PDF is empty, return an empty list
        if (pageTexts.isEmpty()) {
            return boundaries;
        }

        // If there's only one page, return it as a single document
        if (pageTexts.size() == 1) {
            boundaries.add(new DocumentBoundary(0, 0));
            return boundaries;
        }

        // Find potential document start pages based on title patterns, TOC, etc.
        List<Integer> potentialStartPages = findPotentialDocumentStartPages(pageTexts);

        // If no potential start pages were found, treat entire PDF as one document
        if (potentialStartPages.isEmpty() || potentialStartPages.size() == 1) {
            boundaries.add(new DocumentBoundary(0, pageTexts.size() - 1));
            return boundaries;
        }

        // Create boundaries based on start pages
        for (int i = 0; i < potentialStartPages.size(); i++) {
            int startPage = potentialStartPages.get(i);
            int endPage = (i < potentialStartPages.size() - 1)
                    ? potentialStartPages.get(i + 1) - 1
                    : pageTexts.size() - 1;

            // Create boundary if it has at least the minimum number of pages
            if (endPage - startPage + 1 >= minPagesPerDocument) {
                boundaries.add(new DocumentBoundary(startPage, endPage));
            }
        }

        // If no boundaries were created, treat the entire PDF as one document
        if (boundaries.isEmpty()) {
            boundaries.add(new DocumentBoundary(0, pageTexts.size() - 1));
        }

        log.debug("Identified {} document boundaries within PDF with {} pages",
                boundaries.size(), pageTexts.size());

        // Log details of each boundary
        for (int i = 0; i < boundaries.size(); i++) {
            DocumentBoundary boundary = boundaries.get(i);
            log.debug("Document {}: pages {}-{} ({} pages)",
                    i+1, boundary.getStartPage()+1, boundary.getEndPage()+1, boundary.getPageCount());
        }

        return boundaries;
    }

    /**
     * Find potential document start pages based on document characteristics.
     *
     * @param pageTexts List of text contents for each page
     * @return List of page indices that likely start a new document
     */
    private List<Integer> findPotentialDocumentStartPages(List<String> pageTexts) {
        List<Integer> startPages = new ArrayList<>();

        // First page is always a start page
        startPages.add(0);

        // Analyze each page for document start characteristics
        for (int i = 1; i < pageTexts.size(); i++) {
            String currentText = pageTexts.get(i);
            String prevText = pageTexts.get(i - 1);

            // Check if this page likely starts a new document
            if (isNewDocumentStart(currentText, prevText, pageTexts, i)) {
                startPages.add(i);
            }
        }

        return startPages;
    }

    /**
     * Check if a page is likely the start of a new document.
     *
     * @param currentText Current page text
     * @param prevText Previous page text
     * @param allTexts All page texts
     * @param pageIndex Current page index
     * @return True if the page is likely the start of a new document
     */
    private boolean isNewDocumentStart(
            String currentText,
            String prevText,
            List<String> allTexts,
            int pageIndex) {

        // Strong indicators of a new document
        if (hasTitlePageIndicators(currentText, prevText) ||
                hasTableOfContents(currentText, prevText) ||
                hasResetPageNumbering(currentText, prevText) ||
                hasFirstChapterOrSection(currentText, prevText)) {

            return true;
        }

        // Calculate text similarity to check for content discontinuity
        double textSimilarity = TextSimilarityUtils.calculateTextSimilarity(currentText, prevText);

        // If very low similarity to previous page, this might be a new document
        if (textSimilarity < 0.20) {
            // Cross-check with surrounding pages to confirm this isn't just an anomaly
            boolean isContinuationWithNextPages = false;

            // Look ahead at surrounding pages
            int pagesAhead = Math.min(3, allTexts.size() - pageIndex - 1);
            if (pagesAhead > 0) {
                double avgSimilarityAhead = 0;
                for (int i = 1; i <= pagesAhead; i++) {
                    avgSimilarityAhead += TextSimilarityUtils.calculateTextSimilarity(
                            currentText, allTexts.get(pageIndex + i));
                }
                avgSimilarityAhead /= pagesAhead;

                // If current page is more similar to upcoming pages than to previous page,
                // and previous page looks like it could be an ending
                if (avgSimilarityAhead > textSimilarity * 1.2 &&
                        (hasEndOfDocumentMarkers(prevText) || isNearEmptyPage(prevText))) {
                    return true;
                }

                // If there's a radical shift in content style or structure
                return hasStyleBreak(currentText, prevText);
            }
        }

        return false;
    }

    /**
     * Check if a page has title page indicators.
     */
    private boolean hasTitlePageIndicators(String currentText, String prevText) {
        String normalizedText = currentText.toLowerCase().trim();

        // Title page is typically short
        boolean isShortPage = currentText.length() < 1000;

        // Check for typical title page content
        boolean hasTitleKeywords = TITLE_PATTERN.matcher(normalizedText).find();
        boolean hasCopyright = COPYRIGHT_PATTERN.matcher(normalizedText).find();
        boolean hasDate = DATE_PATTERN.matcher(normalizedText).find();

        // Examine the layout - title pages often have centered text
        boolean hasCenteredText = hasCenteredLayout(currentText);

        return isShortPage && (hasTitleKeywords || hasCopyright || hasDate || hasCenteredText);
    }

    /**
     * Check if text appears to have centered layout.
     */
    private boolean hasCenteredLayout(String text) {
        String[] lines = text.split("\n");
        int centeredLines = 0;
        int totalNonEmptyLines = 0;

        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                totalNonEmptyLines++;

                // Check if line has equal whitespace on both sides
                int leftSpace = line.indexOf(trimmed.charAt(0));
                int rightSpace = line.length() - (leftSpace + trimmed.length());

                // Allow for some variation
                if (Math.abs(leftSpace - rightSpace) <= 5) {
                    centeredLines++;
                }
            }
        }

        // Consider centered if at least 40% of non-empty lines appear centered
        return totalNonEmptyLines > 0 &&
                (double)centeredLines / totalNonEmptyLines >= 0.4;
    }

    /**
     * Check if a page contains a table of contents.
     */
    private boolean hasTableOfContents(String currentText, String prevText) {
        return TOC_PATTERN.matcher(currentText).find() &&
                !TOC_PATTERN.matcher(prevText).find();
    }

    /**
     * Check for reset page numbering.
     */
    private boolean hasResetPageNumbering(String currentText, String prevText) {
        // Look for "Page 1 of..." pattern
        return currentText.contains("Page 1 of") ||
                Pattern.compile("(?m)^\\s*1\\s*$").matcher(currentText).find();
    }

    /**
     * Check if the page contains Chapter 1 or Section 1.
     */
    private boolean hasFirstChapterOrSection(String currentText, String prevText) {
        return (currentText.contains("Chapter 1") ||
                Pattern.compile("(?i)^\\s*1\\.\\s+\\w+").matcher(currentText).find()) &&
                !(prevText.contains("Chapter 1") ||
                        Pattern.compile("(?i)^\\s*1\\.\\s+\\w+").matcher(prevText).find());
    }

    /**
     * Check for markers that indicate the end of a document.
     */
    private boolean hasEndOfDocumentMarkers(String text) {
        String normalized = text.toLowerCase();
        return normalized.contains("appendix") ||
                normalized.contains("references") ||
                normalized.contains("bibliography") ||
                normalized.contains("glossary") ||
                normalized.contains("index") ||
                Pattern.compile("(?i)\\bend\\b").matcher(normalized).find();
    }

    /**
     * Check if a page is nearly empty (could be a divider).
     */
    private boolean isNearEmptyPage(String text) {
        // Count non-whitespace characters
        long nonWhitespace = text.chars().filter(c -> !Character.isWhitespace(c)).count();
        return nonWhitespace < 100; // Fewer than 100 non-whitespace characters
    }

    /**
     * Detect significant breaks in style or formatting.
     */
    private boolean hasStyleBreak(String currentText, String prevText) {
        // Check for structural differences

        // Average line length
        double currentAvgLineLength = getAverageLineLength(currentText);
        double prevAvgLineLength = getAverageLineLength(prevText);

        // Density of numbers
        double currentNumberDensity = getNumberDensity(currentText);
        double prevNumberDensity = getNumberDensity(prevText);

        // If there's a significant difference in text structure
        boolean lineBreak = Math.abs(currentAvgLineLength - prevAvgLineLength) > 20;
        boolean numberBreak = Math.abs(currentNumberDensity - prevNumberDensity) > 0.2;

        return lineBreak || numberBreak;
    }

    /**
     * Calculate average line length.
     */
    private double getAverageLineLength(String text) {
        String[] lines = text.split("\n");
        if (lines.length == 0) return 0;

        int totalLength = 0;
        int nonEmptyLines = 0;

        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                totalLength += trimmed.length();
                nonEmptyLines++;
            }
        }

        return nonEmptyLines > 0 ? (double)totalLength / nonEmptyLines : 0;
    }

    /**
     * Calculate density of numbers in text.
     */
    private double getNumberDensity(String text) {
        long digitCount = text.chars().filter(Character::isDigit).count();
        long totalChars = text.length();

        return totalChars > 0 ? (double)digitCount / totalChars : 0;
    }
}