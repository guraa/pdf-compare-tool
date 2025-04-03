package guraa.pdfcompare.service;

import guraa.pdfcompare.util.TextExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Responsible for detecting document boundaries within a PDF.
 * This class analyzes page content to determine where logical document breaks occur.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentDetector {

    private final int minPagesPerDocument;

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
     * @param prevText    Previous page text
     * @param allTexts    All page texts
     * @param pageIndex   Current page index
     * @return True if the page is likely the start of a new document
     */
    private boolean isNewDocumentStart(String currentText, String prevText,
                                       List<String> allTexts, int pageIndex) {
        // Check for common document starting patterns
        if (hasDocumentTitlePattern(currentText) ||
                hasTableOfContentsPattern(currentText) ||
                isTitlePage(currentText)) {
            return true;
        }

        // Calculate similarity between current and previous page
        double textSimilarity = TextSimilarityUtils.calculateTextSimilarity(currentText, prevText);

        // If similarity is very low, it might be a new document
        if (textSimilarity < 0.2) {
            // Cross-check with a few pages ahead to confirm this isn't just an anomaly
            int pagesAhead = Math.min(3, allTexts.size() - pageIndex - 1);
            if (pagesAhead > 0) {
                double avgSimilarityAhead = 0;
                for (int i = 1; i <= pagesAhead; i++) {
                    avgSimilarityAhead += TextSimilarityUtils.calculateTextSimilarity(
                            currentText, allTexts.get(pageIndex + i));
                }
                avgSimilarityAhead /= pagesAhead;

                // If current page is more similar to upcoming pages than previous page,
                // it's likely a new document
                return avgSimilarityAhead > textSimilarity * 1.5;
            }
            return true;
        }

        return false;
    }

    /**
     * Check if text contains patterns typical of a document title page.
     */
    private boolean hasDocumentTitlePattern(String text) {
        // Look for patterns that indicate a title page, like centered text,
        // followed by author, date, etc.
        Pattern titlePattern = Pattern.compile(
                "(?i)\\b(report|document|presentation|analysis|proposal|plan|agreement|contract)\\b");
        Matcher matcher = titlePattern.matcher(text);

        // Count the number of matches
        int matchCount = 0;
        while (matcher.find()) {
            matchCount++;
        }

        // If multiple matches and text is relatively short, likely a title page
        return matchCount >= 2 && text.length() < 2000;
    }

    /**
     * Check if text resembles a table of contents.
     */
    private boolean hasTableOfContentsPattern(String text) {
        // Look for "Contents", "Table of Contents", or similar headers
        if (text.toLowerCase().contains("table of contents") ||
                text.toLowerCase().contains("contents") ||
                text.toLowerCase().contains("index")) {

            // Also check for patterns of numbers and dots (e.g., "1. Introduction...... 5")
            Pattern tocEntryPattern = Pattern.compile("\\d+\\..*?\\d+");
            Matcher matcher = tocEntryPattern.matcher(text);

            // If we find at least 3 TOC-like entries, it's probably a TOC
            int matchCount = 0;
            while (matcher.find() && matchCount < 3) {
                matchCount++;
            }

            return matchCount >= 3;
        }

        return false;
    }

    /**
     * Check if a page appears to be a title page.
     */
    private boolean isTitlePage(String text) {
        // Title pages typically have short text
        if (text.length() < 1000) {
            // Check for common title page elements
            boolean hasTitle = false;
            boolean hasAuthor = false;
            boolean hasDate = false;

            // Check for title-like pattern (all caps or followed by author)
            Pattern titlePattern = Pattern.compile("([A-Z][A-Z\\s]{10,})|([A-Z][a-zA-Z\\s]{10,})\\s*(?:by|[Aa]uthor)");
            hasTitle = titlePattern.matcher(text).find();

            // Check for author-like pattern
            Pattern authorPattern = Pattern.compile("(?i)\\b(?:by|author|prepared by)\\s+[A-Z][a-zA-Z\\s\\.]+\\b");
            hasAuthor = authorPattern.matcher(text).find();

            // Check for date-like pattern
            Pattern datePattern = Pattern.compile("\\b\\d{1,2}[\\/\\-]\\d{1,2}[\\/\\-]\\d{2,4}\\b|" +
                    "\\b(?:January|February|March|April|May|June|July|August|September|October|November|December)\\s+\\d{1,2},?\\s+\\d{4}\\b");
            hasDate = datePattern.matcher(text).find();

            // If we have at least two of these elements, likely a title page
            return (hasTitle && hasAuthor) || (hasTitle && hasDate) || (hasAuthor && hasDate);
        }

        return false;
    }
}