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

        // Define patterns for document start detection
        Pattern titlePattern = Pattern.compile("(?i)\\b(report|document|presentation|analysis|proposal|plan|agreement|contract)\\b");
        Pattern tocPattern = Pattern.compile("(?i)\\b(table\\s+of\\s+contents|contents|index)\\b");
        Pattern headerPattern = Pattern.compile("(?m)^\\s*\\d+(\\.\\d+)*\\s+[A-Z]");
        Pattern pageNumberPattern = Pattern.compile("(?m)^\\s*Page\\s+\\d+\\s+of\\s+\\d+\\s*$");

        // Analyze each page for document start characteristics
        for (int i = 1; i < pageTexts.size(); i++) {
            String currentText = pageTexts.get(i);
            String prevText = pageTexts.get(i - 1);

            // Check if this page likely starts a new document
            if (isNewDocumentStart(currentText, prevText, pageTexts, i, titlePattern, tocPattern, headerPattern, pageNumberPattern)) {
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
     * @param titlePattern Pattern for titles
     * @param tocPattern Pattern for table of contents
     * @param headerPattern Pattern for headers
     * @param pageNumberPattern Pattern for page numbers
     * @return True if the page is likely the start of a new document
     */
    private boolean isNewDocumentStart(
            String currentText,
            String prevText,
            List<String> allTexts,
            int pageIndex,
            Pattern titlePattern,
            Pattern tocPattern,
            Pattern headerPattern,
            Pattern pageNumberPattern) {

        // Check for explicit indicators of a new document
        boolean hasTitleIndicator = titlePattern.matcher(currentText).find() &&
                currentText.length() < 3000 && // Title pages tend to be short
                (pageIndex < 2 || !titlePattern.matcher(prevText).find());

        boolean hasTocIndicator = tocPattern.matcher(currentText).find() &&
                !tocPattern.matcher(prevText).find();

        boolean hasResetHeadings = headerPattern.matcher(currentText).find() &&
                currentText.contains("1. ") &&
                !prevText.contains("1. ");

        boolean hasResetPageNumbers = pageNumberPattern.matcher(currentText).find() &&
                currentText.contains("Page 1 of");

        // If any explicit indicator is found
        if (hasTitleIndicator || hasTocIndicator || hasResetHeadings || hasResetPageNumbers) {
            return true;
        }

        // Calculate text similarity
        double textSimilarity = TextSimilarityUtils.calculateTextSimilarity(currentText, prevText);

        // If very low similarity to previous page
        if (textSimilarity < 0.2) {
            // Cross-check with next few pages to confirm this isn't just an anomaly
            int pagesAhead = Math.min(3, allTexts.size() - pageIndex - 1);
            if (pagesAhead > 0) {
                double avgSimilarityAhead = 0;
                for (int i = 1; i <= pagesAhead; i++) {
                    avgSimilarityAhead += TextSimilarityUtils.calculateTextSimilarity(
                            currentText, allTexts.get(pageIndex + i));
                }
                avgSimilarityAhead /= pagesAhead;

                // If current page is more similar to upcoming pages than to previous page,
                // it's likely a new document
                return avgSimilarityAhead > textSimilarity * 1.5;
            }
            return true;
        }

        return false;
    }
}