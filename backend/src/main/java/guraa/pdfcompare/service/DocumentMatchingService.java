package guraa.pdfcompare.service;

import guraa.pdfcompare.model.DocumentPair;
import guraa.pdfcompare.model.PdfDocument;
import guraa.pdfcompare.util.DifferenceCalculator;
import guraa.pdfcompare.util.TextExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentMatchingService {

    private final TextExtractor textExtractor;
    private final DifferenceCalculator differenceCalculator;

    // Constants for document matching
    private static final double TEXT_SIMILARITY_THRESHOLD = 0.5;
    private static final double VISUAL_SIMILARITY_THRESHOLD = 0.6;
    private static final int MIN_PAGES_FOR_DOCUMENT = 1;
    private static final int MAX_SAMPLE_PAGES = 3; // For performance when comparing large documents

    /**
     * Match documents between base and comparison PDFs.
     *
     * @param baseDocument    The base document
     * @param compareDocument The comparison document
     * @return List of document pairs
     * @throws IOException If there's an error processing the documents
     */
    public List<DocumentPair> matchDocuments(PdfDocument baseDocument, PdfDocument compareDocument)
            throws IOException {
        // Load PDF documents
        PDDocument basePdf = PDDocument.load(new File(baseDocument.getFilePath()));
        PDDocument comparePdf = PDDocument.load(new File(compareDocument.getFilePath()));

        try {
            List<DocumentPair> documentPairs = new ArrayList<>();

            // Extract text contents for each page and render sample pages for visual comparison
            List<String> baseTexts = extractPageTexts(basePdf);
            List<String> compareTexts = extractPageTexts(comparePdf);

            // Create page renderers
            PDFRenderer baseRenderer = new PDFRenderer(basePdf);
            PDFRenderer compareRenderer = new PDFRenderer(comparePdf);

            // Identify document boundaries in each PDF
            List<DocumentBoundary> baseDocuments = identifyDocumentBoundaries(baseTexts);
            List<DocumentBoundary> compareDocuments = identifyDocumentBoundaries(compareTexts);

            log.info("Identified {} documents in base PDF and {} in comparison PDF",
                    baseDocuments.size(), compareDocuments.size());

            // Match documents between base and compare PDFs using combined text and visual features
            List<DocumentMatch> matches = matchDocumentBoundaries(baseDocuments, compareDocuments,
                    baseTexts, compareTexts, basePdf, comparePdf, baseRenderer, compareRenderer);

            // Create document pairs from matches
            int pairIndex = 0;

            // First, handle matched documents
            for (DocumentMatch match : matches) {
                DocumentBoundary baseBoundary = baseDocuments.get(match.getBaseDocumentIndex());
                DocumentBoundary compareBoundary = compareDocuments.get(match.getCompareDocumentIndex());

                // Create document pair for matched documents
                DocumentPair pair = DocumentPair.builder()
                        .pairIndex(pairIndex++)
                        .matched(true)
                        .baseStartPage(baseBoundary.getStartPage() + 1) // Convert to 1-based
                        .baseEndPage(baseBoundary.getEndPage() + 1)     // Convert to 1-based
                        .basePageCount(baseBoundary.getEndPage() - baseBoundary.getStartPage() + 1)
                        .compareStartPage(compareBoundary.getStartPage() + 1) // Convert to 1-based
                        .compareEndPage(compareBoundary.getEndPage() + 1)     // Convert to 1-based
                        .comparePageCount(compareBoundary.getEndPage() - compareBoundary.getStartPage() + 1)
                        .similarityScore(match.getSimilarityScore())
                        .hasBaseDocument(true)
                        .hasCompareDocument(true)
                        .build();

                // Create page mappings
                createPageMappings(pair, baseBoundary, compareBoundary, baseTexts, compareTexts,
                        basePdf, comparePdf, baseRenderer, compareRenderer);

                documentPairs.add(pair);

                // Mark these documents as processed
                baseBoundary.setMatched(true);
                compareBoundary.setMatched(true);
            }

            // Handle unmatched base documents
            for (int i = 0; i < baseDocuments.size(); i++) {
                DocumentBoundary boundary = baseDocuments.get(i);
                if (!boundary.isMatched()) {
                    DocumentPair pair = DocumentPair.builder()
                            .pairIndex(pairIndex++)
                            .matched(false)
                            .baseStartPage(boundary.getStartPage() + 1) // Convert to 1-based
                            .baseEndPage(boundary.getEndPage() + 1)     // Convert to 1-based
                            .basePageCount(boundary.getEndPage() - boundary.getStartPage() + 1)
                            .hasBaseDocument(true)
                            .hasCompareDocument(false)
                            .similarityScore(0.0)
                            .build();

                    documentPairs.add(pair);
                }
            }

            // Handle unmatched compare documents
            for (int i = 0; i < compareDocuments.size(); i++) {
                DocumentBoundary boundary = compareDocuments.get(i);
                if (!boundary.isMatched()) {
                    DocumentPair pair = DocumentPair.builder()
                            .pairIndex(pairIndex++)
                            .matched(false)
                            .compareStartPage(boundary.getStartPage() + 1) // Convert to 1-based
                            .compareEndPage(boundary.getEndPage() + 1)     // Convert to 1-based
                            .comparePageCount(boundary.getEndPage() - boundary.getStartPage() + 1)
                            .hasBaseDocument(false)
                            .hasCompareDocument(true)
                            .similarityScore(0.0)
                            .build();

                    documentPairs.add(pair);
                }
            }

            return documentPairs;
        } finally {
            // Close PDFs
            basePdf.close();
            comparePdf.close();
        }
    }

    /**
     * Extract text from each page of a PDF document.
     *
     * @param document The PDF document
     * @return List of text contents for each page
     * @throws IOException If there's an error extracting text
     */
    private List<String> extractPageTexts(PDDocument document) throws IOException {
        List<String> pageTexts = new ArrayList<>();

        for (int i = 0; i < document.getNumberOfPages(); i++) {
            String text = textExtractor.extractTextFromPage(document, i);
            pageTexts.add(text);
        }

        return pageTexts;
    }

    /**
     * Identify document boundaries within a PDF.
     *
     * @param pageTexts List of text contents for each page
     * @return List of document boundaries
     */
    private List<DocumentBoundary> identifyDocumentBoundaries(List<String> pageTexts) {
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

        // Algorithm to identify document boundaries
        int currentStart = 0;
        Map<String, Double> documentFingerprints = new HashMap<>();

        // Calculate fingerprint for first page
        documentFingerprints.put(getPageFingerprint(pageTexts.get(0)), 1.0);

        for (int i = 1; i < pageTexts.size(); i++) {
            String currentText = pageTexts.get(i);
            String prevText = pageTexts.get(i - 1);

            // Check if this page likely starts a new document
            if (isNewDocumentStart(currentText, prevText, pageTexts, i)) {
                // Create boundary for previous document
                boundaries.add(new DocumentBoundary(currentStart, i - 1));
                currentStart = i;

                // Add fingerprint for the new document's first page
                documentFingerprints.put(getPageFingerprint(currentText), 1.0);
            }
        }

        // Add the last document
        boundaries.add(new DocumentBoundary(currentStart, pageTexts.size() - 1));

        return boundaries;
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
        double textSimilarity = calculateTextSimilarity(currentText, prevText);

        // If similarity is very low, it might be a new document
        if (textSimilarity < 0.2) {
            // Cross-check with a few pages ahead to confirm this isn't just an anomaly
            int pagesAhead = Math.min(3, allTexts.size() - pageIndex - 1);
            if (pagesAhead > 0) {
                double avgSimilarityAhead = 0;
                for (int i = 1; i <= pagesAhead; i++) {
                    avgSimilarityAhead += calculateTextSimilarity(
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

    /**
     * Create a fingerprint for a page to use in document identification.
     *
     * @param pageText Text content of the page
     * @return Fingerprint string
     */
    private String getPageFingerprint(String pageText) {
        // Simple fingerprint: first 100 chars (or less if shorter) with only alphanumeric chars
        String normalized = pageText.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        return normalized.substring(0, Math.min(normalized.length(), 100));
    }

    /**
     * Calculate similarity between two text strings.
     *
     * @param text1 First text
     * @param text2 Second text
     * @return Similarity score between 0.0 and 1.0
     */
    private double calculateTextSimilarity(String text1, String text2) {
        if (text1 == null || text2 == null || text1.isEmpty() || text2.isEmpty()) {
            return 0.0;
        }

        // Normalize texts by removing extra whitespace and converting to lowercase
        String normalizedText1 = text1.replaceAll("\\s+", " ").trim().toLowerCase();
        String normalizedText2 = text2.replaceAll("\\s+", " ").trim().toLowerCase();

        // Use Jaccard similarity on word sets
        Set<String> words1 = new HashSet<>(Arrays.asList(normalizedText1.split(" ")));
        Set<String> words2 = new HashSet<>(Arrays.asList(normalizedText2.split(" ")));

        // Calculate intersection size
        Set<String> intersection = new HashSet<>(words1);
        intersection.retainAll(words2);

        // Calculate union size
        Set<String> union = new HashSet<>(words1);
        union.addAll(words2);

        // Return Jaccard index
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    /**
     * Match document boundaries between base and comparison PDFs.
     *
     * @param baseDocuments    List of document boundaries in base PDF
     * @param compareDocuments List of document boundaries in comparison PDF
     * @param baseTexts        List of page texts in base PDF
     * @param compareTexts     List of page texts in comparison PDF
     * @param basePdf          Base PDF document
     * @param comparePdf       Comparison PDF document
     * @param baseRenderer     Renderer for base PDF
     * @param compareRenderer  Renderer for comparison PDF
     * @return List of document matches
     * @throws IOException If there's an error processing the documents
     */
    private List<DocumentMatch> matchDocumentBoundaries(
            List<DocumentBoundary> baseDocuments,
            List<DocumentBoundary> compareDocuments,
            List<String> baseTexts,
            List<String> compareTexts,
            PDDocument basePdf,
            PDDocument comparePdf,
            PDFRenderer baseRenderer,
            PDFRenderer compareRenderer) throws IOException {

        List<DocumentMatch> matches = new ArrayList<>();

        // Calculate similarity scores for all possible document pairs
        List<PotentialMatch> potentialMatches = new ArrayList<>();

        for (int i = 0; i < baseDocuments.size(); i++) {
            DocumentBoundary baseBoundary = baseDocuments.get(i);

            for (int j = 0; j < compareDocuments.size(); j++) {
                DocumentBoundary compareBoundary = compareDocuments.get(j);

                // Calculate similarity using both text and visual features
                double similarity = calculateDocumentSimilarity(
                        baseBoundary, compareBoundary,
                        baseTexts, compareTexts,
                        basePdf, comparePdf,
                        baseRenderer, compareRenderer);

                // If similarity exceeds threshold, consider it as a potential match
                if (similarity > TEXT_SIMILARITY_THRESHOLD) {
                    potentialMatches.add(new PotentialMatch(i, j, similarity));
                }
            }
        }

        // Sort potential matches by similarity (highest first)
        potentialMatches.sort((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()));

        // Create matches, ensuring each document is matched at most once
        Set<Integer> matchedBaseIndices = new HashSet<>();
        Set<Integer> matchedCompareIndices = new HashSet<>();

        for (PotentialMatch potentialMatch : potentialMatches) {
            int baseIndex = potentialMatch.getBaseIndex();
            int compareIndex = potentialMatch.getCompareIndex();

            // Only create match if neither document has been matched yet
            if (!matchedBaseIndices.contains(baseIndex) &&
                    !matchedCompareIndices.contains(compareIndex)) {

                matches.add(new DocumentMatch(baseIndex, compareIndex, potentialMatch.getSimilarity()));

                // Mark these documents as matched
                matchedBaseIndices.add(baseIndex);
                matchedCompareIndices.add(compareIndex);
            }
        }

        return matches;
    }

    /**
     * Calculate similarity between two documents using both text and visual features.
     *
     * @param baseBoundary    Document boundary in base PDF
     * @param compareBoundary Document boundary in comparison PDF
     * @param baseTexts       List of page texts in base PDF
     * @param compareTexts    List of page texts in comparison PDF
     * @param basePdf         Base PDF document
     * @param comparePdf      Comparison PDF document
     * @param baseRenderer    Renderer for base PDF
     * @param compareRenderer Renderer for comparison PDF
     * @return Similarity score between 0.0 and 1.0
     * @throws IOException If there's an error processing the documents
     */
    private double calculateDocumentSimilarity(
            DocumentBoundary baseBoundary,
            DocumentBoundary compareBoundary,
            List<String> baseTexts,
            List<String> compareTexts,
            PDDocument basePdf,
            PDDocument comparePdf,
            PDFRenderer baseRenderer,
            PDFRenderer compareRenderer) throws IOException {

        // Calculate text similarity
        double textSimilarity = calculateTextSimilarityForDocuments(
                baseBoundary, compareBoundary, baseTexts, compareTexts);

        // If text similarity is very low, no need to check visual similarity
        if (textSimilarity < TEXT_SIMILARITY_THRESHOLD / 2) {
            return textSimilarity;
        }

        // Calculate visual similarity for a sample of pages
        double visualSimilarity = calculateVisualSimilarityForDocuments(
                baseBoundary, compareBoundary, basePdf, comparePdf, baseRenderer, compareRenderer);

        // Combined similarity score (70% text, 30% visual)
        return 0.7 * textSimilarity + 0.3 * visualSimilarity;
    }

    /**
     * Calculate text similarity between two documents.
     *
     * @param baseBoundary    Document boundary in base PDF
     * @param compareBoundary Document boundary in comparison PDF
     * @param baseTexts       List of page texts in base PDF
     * @param compareTexts    List of page texts in comparison PDF
     * @return Text similarity score between 0.0 and 1.0
     */
    private double calculateTextSimilarityForDocuments(
            DocumentBoundary baseBoundary,
            DocumentBoundary compareBoundary,
            List<String> baseTexts,
            List<String> compareTexts) {

        // Concatenate all text in each document
        StringBuilder baseText = new StringBuilder();
        for (int i = baseBoundary.getStartPage(); i <= baseBoundary.getEndPage(); i++) {
            if (i < baseTexts.size()) {
                baseText.append(baseTexts.get(i)).append(" ");
            }
        }

        StringBuilder compareText = new StringBuilder();
        for (int i = compareBoundary.getStartPage(); i <= compareBoundary.getEndPage(); i++) {
            if (i < compareTexts.size()) {
                compareText.append(compareTexts.get(i)).append(" ");
            }
        }

        // Calculate similarity between the concatenated texts
        return calculateTextSimilarity(baseText.toString(), compareText.toString());
    }

    /**
     * Calculate visual similarity between two documents using SSIM.
     *
     * @param baseBoundary    Document boundary in base PDF
     * @param compareBoundary Document boundary in comparison PDF
     * @param basePdf         Base PDF document
     * @param comparePdf      Comparison PDF document
     * @param baseRenderer    Renderer for base PDF
     * @param compareRenderer Renderer for comparison PDF
     * @return Visual similarity score between 0.0 and 1.0
     * @throws IOException If there's an error processing the documents
     */
    private double calculateVisualSimilarityForDocuments(
            DocumentBoundary baseBoundary,
            DocumentBoundary compareBoundary,
            PDDocument basePdf,
            PDDocument comparePdf,
            PDFRenderer baseRenderer,
            PDFRenderer compareRenderer) throws IOException {

        int basePageCount = baseBoundary.getEndPage() - baseBoundary.getStartPage() + 1;
        int comparePageCount = compareBoundary.getEndPage() - compareBoundary.getStartPage() + 1;

        // Determine how many pages to sample
        int samplePageCount = Math.min(
                Math.min(basePageCount, comparePageCount),
                MAX_SAMPLE_PAGES);

        // No pages to compare
        if (samplePageCount <= 0) {
            return 0.0;
        }

        double totalSimilarity = 0.0;

        // Sample evenly distributed pages
        for (int i = 0; i < samplePageCount; i++) {
            // Calculate page indices for even distribution
            int basePageIndex = baseBoundary.getStartPage() +
                    (i * basePageCount / samplePageCount);

            int comparePageIndex = compareBoundary.getStartPage() +
                    (i * comparePageCount / samplePageCount);

            // Ensure we're within bounds
            if (basePageIndex < basePdf.getNumberOfPages() &&
                    comparePageIndex < comparePdf.getNumberOfPages()) {

                // Render pages to images
                BufferedImage baseImage = baseRenderer.renderImageWithDPI(
                        basePageIndex, 72, ImageType.RGB);

                BufferedImage compareImage = compareRenderer.renderImageWithDPI(
                        comparePageIndex, 72, ImageType.RGB);

                // Calculate SSIM
                double pageSimilarity = 1.0 - differenceCalculator.compareImages(
                        baseImage, compareImage);

                totalSimilarity += pageSimilarity;
            }
        }

        // Return average similarity
        return totalSimilarity / samplePageCount;
    }

    /**
     * Create page mappings between documents for more detailed comparison.
     *
     * @param pair            Document pair to update with mappings
     * @param baseBoundary    Document boundary in base PDF
     * @param compareBoundary Document boundary in comparison PDF
     * @param baseTexts       List of page texts in base PDF
     * @param compareTexts    List of page texts in comparison PDF
     * @param basePdf         Base PDF document
     * @param comparePdf      Comparison PDF document
     * @param baseRenderer    Renderer for base PDF
     * @param compareRenderer Renderer for comparison PDF
     * @throws IOException If there's an error processing the documents
     */
    private void createPageMappings(
            DocumentPair pair,
            DocumentBoundary baseBoundary,
            DocumentBoundary compareBoundary,
            List<String> baseTexts,
            List<String> compareTexts,
            PDDocument basePdf,
            PDDocument comparePdf,
            PDFRenderer baseRenderer,
            PDFRenderer compareRenderer) throws IOException {

        int basePageCount = baseBoundary.getEndPage() - baseBoundary.getStartPage() + 1;
        int comparePageCount = compareBoundary.getEndPage() - compareBoundary.getStartPage() + 1;

        // Simple case: equal number of pages
        if (basePageCount == comparePageCount) {
            // Create one-to-one mappings
            for (int i = 0; i < basePageCount; i++) {
                int baseIndex = baseBoundary.getStartPage() + i;
                int compareIndex = compareBoundary.getStartPage() + i;

                DocumentPair.PageMapping mapping = DocumentPair.PageMapping.builder()
                        .basePageNumber(baseIndex + 1) // Convert to 1-based
                        .comparePageNumber(compareIndex + 1) // Convert to 1-based
                        // Calculate page similarity
                        .similarityScore(calculatePageSimilarity(
                                baseIndex, compareIndex, baseTexts, compareTexts,
                                basePdf, comparePdf, baseRenderer, compareRenderer))
                        .build();

                pair.getPageMappings().add(mapping);
            }
        } else {
            // Complex case: different number of pages
            // Use dynamic programming to find optimal page mappings
            createOptimalPageMappings(
                    pair, baseBoundary, compareBoundary,
                    baseTexts, compareTexts,
                    basePdf, comparePdf,
                    baseRenderer, compareRenderer);
        }
    }

    /**
     * Calculate similarity between two pages using a combination of text and visual similarity.
     *
     * @param basePageIndex    Page index in base PDF
     * @param comparePageIndex Page index in comparison PDF
     * @param baseTexts        List of page texts in base PDF
     * @param compareTexts     List of page texts in comparison PDF
     * @param basePdf          Base PDF document
     * @param comparePdf       Comparison PDF document
     * @param baseRenderer     Renderer for base PDF
     * @param compareRenderer  Renderer for comparison PDF
     * @return Similarity score between 0.0 and 1.0
     * @throws IOException If there's an error processing the pages
     */
    private double calculatePageSimilarity(
            int basePageIndex,
            int comparePageIndex,
            List<String> baseTexts,
            List<String> compareTexts,
            PDDocument basePdf,
            PDDocument comparePdf,
            PDFRenderer baseRenderer,
            PDFRenderer compareRenderer) throws IOException {

        // Ensure page indices are valid
        if (basePageIndex >= baseTexts.size() || comparePageIndex >= compareTexts.size()) {
            return 0.0;
        }

        // Calculate text similarity
        double textSimilarity = calculateTextSimilarity(
                baseTexts.get(basePageIndex), compareTexts.get(comparePageIndex));

        // If text similarity is very low, no need to check visual similarity
        if (textSimilarity < TEXT_SIMILARITY_THRESHOLD / 2) {
            return textSimilarity;
        }

        // Calculate visual similarity
        double visualSimilarity = 0.0;
        try {
            BufferedImage baseImage = baseRenderer.renderImageWithDPI(
                    basePageIndex, 72, ImageType.RGB);

            BufferedImage compareImage = compareRenderer.renderImageWithDPI(
                    comparePageIndex, 72, ImageType.RGB);

            visualSimilarity = 1.0 - differenceCalculator.compareImages(
                    baseImage, compareImage);
        } catch (Exception e) {
            log.warn("Error calculating visual similarity for pages: {} and {}",
                    basePageIndex, comparePageIndex, e);
        }

        // Combined similarity score (60% text, 40% visual)
        return 0.6 * textSimilarity + 0.4 * visualSimilarity;
    }

    /**
     * Create optimal page mappings between documents with different page counts.
     *
     * @param pair            Document pair to update with mappings
     * @param baseBoundary    Document boundary in base PDF
     * @param compareBoundary Document boundary in comparison PDF
     * @param baseTexts       List of page texts in base PDF
     * @param compareTexts    List of page texts in comparison PDF
     * @param basePdf         Base PDF document
     * @param comparePdf      Comparison PDF document
     * @param baseRenderer    Renderer for base PDF
     * @param compareRenderer Renderer for comparison PDF
     * @throws IOException If there's an error processing the documents
     */
    private void createOptimalPageMappings(
            DocumentPair pair,
            DocumentBoundary baseBoundary,
            DocumentBoundary compareBoundary,
            List<String> baseTexts,
            List<String> compareTexts,
            PDDocument basePdf,
            PDDocument comparePdf,
            PDFRenderer baseRenderer,
            PDFRenderer compareRenderer) throws IOException {

        int basePageCount = baseBoundary.getEndPage() - baseBoundary.getStartPage() + 1;
        int comparePageCount = compareBoundary.getEndPage() - compareBoundary.getStartPage() + 1;

        // Calculate similarity matrix
        double[][] similarityMatrix = new double[basePageCount][comparePageCount];

        for (int i = 0; i < basePageCount; i++) {
            int baseIndex = baseBoundary.getStartPage() + i;

            for (int j = 0; j < comparePageCount; j++) {
                int compareIndex = compareBoundary.getStartPage() + j;

                similarityMatrix[i][j] = calculatePageSimilarity(
                        baseIndex, compareIndex, baseTexts, compareTexts,
                        basePdf, comparePdf, baseRenderer, compareRenderer);
            }
        }

        // Find optimal page mappings using greedy approach
        // For each base page, find the best matching compare page that hasn't been matched yet
        boolean[] matchedComparePages = new boolean[comparePageCount];

        for (int i = 0; i < basePageCount; i++) {
            double maxSimilarity = 0.0;
            int bestMatch = -1;

            // Find best unmatched compare page for this base page
            for (int j = 0; j < comparePageCount; j++) {
                if (!matchedComparePages[j] && similarityMatrix[i][j] > maxSimilarity) {
                    maxSimilarity = similarityMatrix[i][j];
                    bestMatch = j;
                }
            }

            // If we found a good match
            if (bestMatch >= 0 && maxSimilarity > TEXT_SIMILARITY_THRESHOLD) {
                // Create mapping
                DocumentPair.PageMapping mapping = DocumentPair.PageMapping.builder()
                        .basePageNumber(baseBoundary.getStartPage() + i + 1) // Convert to 1-based
                        .comparePageNumber(compareBoundary.getStartPage() + bestMatch + 1) // Convert to 1-based
                        .similarityScore(maxSimilarity)
                        .build();

                pair.getPageMappings().add(mapping);

                // Mark this compare page as matched
                matchedComparePages[bestMatch] = true;
            } else {
                // No good match found for this base page
                DocumentPair.PageMapping mapping = DocumentPair.PageMapping.builder()
                        .basePageNumber(baseBoundary.getStartPage() + i + 1) // Convert to 1-based
                        .comparePageNumber(-1) // No match
                        .similarityScore(0.0)
                        .build();

                pair.getPageMappings().add(mapping);
            }
        }

        // Find any unmatched compare pages
        for (int j = 0; j < comparePageCount; j++) {
            if (!matchedComparePages[j]) {
                // Create mapping for unmatched compare page
                DocumentPair.PageMapping mapping = DocumentPair.PageMapping.builder()
                        .basePageNumber(-1) // No match
                        .comparePageNumber(compareBoundary.getStartPage() + j + 1) // Convert to 1-based
                        .similarityScore(0.0)
                        .build();

                pair.getPageMappings().add(mapping);
            }
        }
    }

    /**
     * DocumentMatch inner class to track document matches.
     */
    private static class DocumentMatch {
        private final int baseDocumentIndex;
        private final int compareDocumentIndex;
        private final double similarityScore;

        public DocumentMatch(int baseDocumentIndex, int compareDocumentIndex, double similarityScore) {
            this.baseDocumentIndex = baseDocumentIndex;
            this.compareDocumentIndex = compareDocumentIndex;
            this.similarityScore = similarityScore;
        }

        public int getBaseDocumentIndex() {
            return baseDocumentIndex;
        }

        public int getCompareDocumentIndex() {
            return compareDocumentIndex;
        }

        public double getSimilarityScore() {
            return similarityScore;
        }
    }
}