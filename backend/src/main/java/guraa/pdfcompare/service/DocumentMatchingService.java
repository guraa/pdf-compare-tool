package guraa.pdfcompare.service;

import guraa.pdfcompare.model.DocumentPair;
import guraa.pdfcompare.model.PdfDocument;
import guraa.pdfcompare.util.TextExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentMatchingService {

    private final TextExtractor textExtractor;

    /**
     * Match documents between base and comparison PDFs.
     *
     * @param baseDocument The base document
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

            // Extract text contents for each page
            List<String> baseTexts = extractPageTexts(basePdf);
            List<String> compareTexts = extractPageTexts(comparePdf);

            // Identify document boundaries in each PDF
            List<DocumentBoundary> baseDocuments = identifyDocumentBoundaries(baseTexts);
            List<DocumentBoundary> compareDocuments = identifyDocumentBoundaries(compareTexts);

            // Match documents between base and compare PDFs
            List<DocumentMatch> matches = matchDocumentBoundaries(baseDocuments, compareDocuments,
                    baseTexts, compareTexts);

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
                createPageMappings(pair, baseBoundary, compareBoundary, baseTexts, compareTexts);

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

        // Algorithm to identify document boundaries
        // This is a simplified version that uses heuristics to detect document breaks

        int currentStart = 0;
        Map<String, Double> fingerprints = new HashMap<>();

        // Calculate fingerprint for first page
        fingerprints.put(getPageFingerprint(pageTexts.get(0)), 1.0);

        for (int i = 1; i < pageTexts.size(); i++) {
            String currentText = pageTexts.get(i);
            String prevText = pageTexts.get(i - 1);

            // Check if this page likely starts a new document
            if (isNewDocumentStart(currentText, prevText, pageTexts, i)) {
                // Create boundary for previous document
                boundaries.add(new DocumentBoundary(currentStart, i - 1));
                currentStart = i;

                // Add fingerprint for the new document's first page
                fingerprints.put(getPageFingerprint(currentText), 1.0);
            }
        }

        // Add the last document
        boundaries.add(new DocumentBoundary(currentStart, pageTexts.size() - 1));

        return boundaries;
    }

    /**
     * Check if a page is likely the start of a new document.
     *
     * @param currentText Text content of the current page
     * @param prevText Text content of the previous page
     * @param pageTexts List of text contents for all pages
     * @param pageIndex Index of the current page
     * @return True if the page likely starts a new document
     */
    private boolean isNewDocumentStart(String currentText, String prevText,
                                       List<String> pageTexts, int pageIndex) {
        // Heuristics to detect document breaks

        // 1. Check if the current page has title-like content
        boolean hasTitleFeatures = hasTitleFeatures(currentText);

        // 2. Check content similarity with previous page
        double similarity = calculateTextSimilarity(currentText, prevText);
        boolean lowSimilarity = similarity < 0.3;

        // 3. Check if page numbering resets
        boolean pageNumberReset = detectPageNumberReset(pageTexts, pageIndex);

        // 4. Check if the current page has a header/footer pattern different from previous pages
        boolean headerFooterChange = detectHeaderFooterChange(currentText, prevText);

        // Combine heuristics
        // If multiple indicators suggest a new document, consider it a break
        int indicators = 0;
        if (hasTitleFeatures) indicators++;
        if (lowSimilarity) indicators++;
        if (pageNumberReset) indicators++;
        if (headerFooterChange) indicators++;

        return indicators >= 2;
    }

    /**
     * Check if a page has features typically found in document titles.
     *
     * @param text The page text
     * @return True if the page has title-like features
     */
    private boolean hasTitleFeatures(String text) {
        // Simplified check for title-like features
        // Look for specific patterns like "Title:", "Chapter", etc. at the beginning
        String firstLine = text.trim().lines().findFirst().orElse("");

        return firstLine.contains("Title:") ||
                firstLine.contains("Chapter") ||
                firstLine.contains("Document") ||
                firstLine.contains("Report") ||
                firstLine.matches(".*\\d+\\.\\s+[A-Z].*"); // Numbered section pattern
    }

    /**
     * Detect if page numbering resets at a certain page.
     *
     * @param pageTexts List of text contents for all pages
     * @param pageIndex Index of the current page
     * @return True if page numbering likely resets
     */
    private boolean detectPageNumberReset(List<String> pageTexts, int pageIndex) {
        // This would require more complex analysis in a real implementation
        // Look for page numbers at the bottom of pages and check for resets

        // Simplified implementation for now
        return false;
    }

    /**
     * Detect if header/footer patterns change between pages.
     *
     * @param currentText Text of the current page
     * @param prevText Text of the previous page
     * @return True if header/footer likely changed
     */
    private boolean detectHeaderFooterChange(String currentText, String prevText) {
        // Extract first and last lines (potential headers/footers)
        String[] currentLines = currentText.split("\\n");
        String[] prevLines = prevText.split("\\n");

        if (currentLines.length == 0 || prevLines.length == 0) {
            return false;
        }

        String currentFirstLine = currentLines[0].trim();
        String prevFirstLine = prevLines[0].trim();

        String currentLastLine = currentLines[currentLines.length - 1].trim();
        String prevLastLine = prevLines[prevLines.length - 1].trim();

        // Check if both header and footer are different
        boolean headerDifferent = calculateTextSimilarity(currentFirstLine, prevFirstLine) < 0.5;
        boolean footerDifferent = calculateTextSimilarity(currentLastLine, prevLastLine) < 0.5;

        return headerDifferent && footerDifferent;
    }

    /**
     * Generate a fingerprint for a page to help identify similar pages.
     *
     * @param text The page text
     * @return A fingerprint string
     */
    private String getPageFingerprint(String text) {
        // Extract key features for fingerprinting
        // This is a simplified implementation

        // Get first line (potential title or header)
        String firstLine = text.trim().lines().findFirst().orElse("");

        // Extract potential keywords
        List<String> words = new ArrayList<>();
        for (String word : text.split("\\s+")) {
            if (word.length() > 5) {
                words.add(word.toLowerCase());
            }
        }

        // Sort and take top 10 words
        Collections.sort(words);
        String topWords = words.stream().limit(10).collect(Collectors.joining(","));

        return firstLine + "|" + topWords;
    }

    /**
     * Match document boundaries between base and comparison PDFs.
     *
     * @param baseDocuments List of document boundaries in the base PDF
     * @param compareDocuments List of document boundaries in the comparison PDF
     * @param baseTexts List of text contents for base PDF pages
     * @param compareTexts List of text contents for comparison PDF pages
     * @return List of document matches
     */
    private List<DocumentMatch> matchDocumentBoundaries(
            List<DocumentBoundary> baseDocuments,
            List<DocumentBoundary> compareDocuments,
            List<String> baseTexts,
            List<String> compareTexts) {

        List<DocumentMatch> matches = new ArrayList<>();

        // Calculate similarity for all possible document pairs
        List<PotentialMatch> potentialMatches = new ArrayList<>();

        for (int i = 0; i < baseDocuments.size(); i++) {
            DocumentBoundary baseBoundary = baseDocuments.get(i);

            for (int j = 0; j < compareDocuments.size(); j++) {
                DocumentBoundary compareBoundary = compareDocuments.get(j);

                // Calculate similarity between these two documents
                double similarity = calculateDocumentSimilarity(
                        baseBoundary, compareBoundary, baseTexts, compareTexts);

                potentialMatches.add(new PotentialMatch(i, j, similarity));
            }
        }

        // Sort potential matches by similarity (highest first)
        potentialMatches.sort(Comparator.comparing(PotentialMatch::getSimilarity).reversed());

        // Assign matches greedily
        boolean[] baseMatched = new boolean[baseDocuments.size()];
        boolean[] compareMatched = new boolean[compareDocuments.size()];

        for (PotentialMatch match : potentialMatches) {
            // Skip if similarity is too low
            if (match.getSimilarity() < 0.5) {
                continue;
            }

            // Skip if either document is already matched
            if (baseMatched[match.getBaseIndex()] || compareMatched[match.getCompareIndex()]) {
                continue;
            }

            // Create match
            matches.add(new DocumentMatch(
                    match.getBaseIndex(),
                    match.getCompareIndex(),
                    match.getSimilarity()));

            // Mark as matched
            baseMatched[match.getBaseIndex()] = true;
            compareMatched[match.getCompareIndex()] = true;
        }

        return matches;
    }

    /**
     * Calculate similarity between two documents.
     *
     * @param baseBoundary Document boundary in the base PDF
     * @param compareBoundary Document boundary in the comparison PDF
     * @param baseTexts List of text contents for base PDF pages
     * @param compareTexts List of text contents for comparison PDF pages
     * @return Similarity score between 0.0 and 1.0
     */
    private double calculateDocumentSimilarity(
            DocumentBoundary baseBoundary,
            DocumentBoundary compareBoundary,
            List<String> baseTexts,
            List<String> compareTexts) {

        // Extract text for both documents
        StringBuilder baseText = new StringBuilder();
        for (int i = baseBoundary.getStartPage(); i <= baseBoundary.getEndPage(); i++) {
            baseText.append(baseTexts.get(i)).append("\n");
        }

        StringBuilder compareText = new StringBuilder();
        for (int i = compareBoundary.getStartPage(); i <= compareBoundary.getEndPage(); i++) {
            compareText.append(compareTexts.get(i)).append("\n");
        }

        // Calculate text similarity
        return calculateTextSimilarity(baseText.toString(), compareText.toString());
    }

    /**
     * Calculate similarity between two text strings.
     * Uses cosine similarity on word frequencies.
     *
     * @param text1 First text
     * @param text2 Second text
     * @return Similarity score between 0.0 and 1.0
     */
    private double calculateTextSimilarity(String text1, String text2) {
        // Handle empty texts
        if (text1 == null || text2 == null || text1.isEmpty() || text2.isEmpty()) {
            return 0.0;
        }

        // Normalize and tokenize texts
        String[] words1 = text1.toLowerCase().replaceAll("[^a-z0-9\\s]", "").split("\\s+");
        String[] words2 = text2.toLowerCase().replaceAll("[^a-z0-9\\s]", "").split("\\s+");

        // Count word frequencies
        Map<String, Integer> freq1 = new HashMap<>();
        Map<String, Integer> freq2 = new HashMap<>();

        for (String word : words1) {
            if (word.length() > 1) { // Skip single-character words
                freq1.put(word, freq1.getOrDefault(word, 0) + 1);
            }
        }

        for (String word : words2) {
            if (word.length() > 1) { // Skip single-character words
                freq2.put(word, freq2.getOrDefault(word, 0) + 1);
            }
        }

        // Calculate cosine similarity
        // Get all unique words
        Set<String> allWords = new HashSet<>();
        allWords.addAll(freq1.keySet());
        allWords.addAll(freq2.keySet());

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (String word : allWords) {
            int f1 = freq1.getOrDefault(word, 0);
            int f2 = freq2.getOrDefault(word, 0);

            dotProduct += f1 * f2;
            norm1 += f1 * f1;
            norm2 += f2 * f2;
        }

        // Avoid division by zero
        if (norm1 == 0 || norm2 == 0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    /**
     * Create page mappings between base and comparison documents.
     *
     * @param pair The document pair to update
     * @param baseBoundary Document boundary in the base PDF
     * @param compareBoundary Document boundary in the comparison PDF
     * @param baseTexts List of text contents for base PDF pages
     * @param compareTexts List of text contents for comparison PDF pages
     */
    private void createPageMappings(
            DocumentPair pair,
            DocumentBoundary baseBoundary,
            DocumentBoundary compareBoundary,
            List<String> baseTexts,
            List<String> compareTexts) {

        List<DocumentPair.PageMapping> pageMappings = new ArrayList<>();

        // Get page ranges
        int baseStart = baseBoundary.getStartPage();
        int baseEnd = baseBoundary.getEndPage();
        int compareStart = compareBoundary.getStartPage();
        int compareEnd = compareBoundary.getEndPage();

        // Handle cases with unequal page counts
        int baseCount = baseEnd - baseStart + 1;
        int compareCount = compareEnd - compareStart + 1;

        if (baseCount == compareCount) {
            // One-to-one mapping
            for (int i = 0; i < baseCount; i++) {
                int basePage = baseStart + i;
                int comparePage = compareStart + i;

                // Calculate similarity between these pages
                double similarity = calculateTextSimilarity(
                        baseTexts.get(basePage),
                        compareTexts.get(comparePage));

                pageMappings.add(new DocumentPair.PageMapping(
                        basePage + 1, // Convert to 1-based
                        comparePage + 1, // Convert to 1-based
                        similarity,
                        0)); // Difference count will be updated later
            }
        } else {
            // Use dynamic programming to find optimal page matching
            // This is a simplified implementation

            // Create similarity matrix
            double[][] similarity = new double[baseCount][compareCount];

            for (int i = 0; i < baseCount; i++) {
                for (int j = 0; j < compareCount; j++) {
                    similarity[i][j] = calculateTextSimilarity(
                            baseTexts.get(baseStart + i),
                            compareTexts.get(compareStart + j));
                }
            }

            // Find best page matches greedily
            boolean[] baseMatched = new boolean[baseCount];
            boolean[] compareMatched = new boolean[compareCount];

            // First, find one-to-one matches with high similarity
            for (int threshold = 90; threshold >= 50; threshold -= 10) {
                double minSimilarity = threshold / 100.0;

                for (int i = 0; i < baseCount; i++) {
                    if (baseMatched[i]) continue;

                    int bestMatch = -1;
                    double bestSimilarity = minSimilarity;

                    for (int j = 0; j < compareCount; j++) {
                        if (compareMatched[j]) continue;

                        if (similarity[i][j] > bestSimilarity) {
                            bestSimilarity = similarity[i][j];
                            bestMatch = j;
                        }
                    }

                    if (bestMatch != -1) {
                        baseMatched[i] = true;
                        compareMatched[bestMatch] = true;

                        pageMappings.add(new DocumentPair.PageMapping(
                                baseStart + i + 1, // Convert to 1-based
                                compareStart + bestMatch + 1, // Convert to 1-based
                                bestSimilarity,
                                0)); // Difference count will be updated later
                    }
                }
            }

            // Handle unmatched pages
            for (int i = 0; i < baseCount; i++) {
                if (!baseMatched[i]) {
                    // Find the best available match for this base page
                    int bestMatch = -1;
                    double bestSimilarity = 0.0;

                    for (int j = 0; j < compareCount; j++) {
                        if (!compareMatched[j] && similarity[i][j] > bestSimilarity) {
                            bestSimilarity = similarity[i][j];
                            bestMatch = j;
                        }
                    }

                    if (bestMatch != -1) {
                        baseMatched[i] = true;
                        compareMatched[bestMatch] = true;

                        pageMappings.add(new DocumentPair.PageMapping(
                                baseStart + i + 1, // Convert to 1-based
                                compareStart + bestMatch + 1, // Convert to 1-based
                                bestSimilarity,
                                0)); // Difference count will be updated later
                    } else {
                        // No match found, map to a dummy page
                        pageMappings.add(new DocumentPair.PageMapping(
                                baseStart + i + 1, // Convert to 1-based
                                -1, // No match
                                0.0,
                                0)); // Difference count will be updated later
                    }
                }
            }

            // Handle any remaining unmatched compare pages
            for (int j = 0; j < compareCount; j++) {
                if (!compareMatched[j]) {
                    pageMappings.add(new DocumentPair.PageMapping(
                            -1, // No match
                            compareStart + j + 1, // Convert to 1-based
                            0.0,
                            0)); // Difference count will be updated later
                }
            }
        }

        pair.setPageMappings(pageMappings);
    }

    /**
     * Document boundary class.
     */
    private static class DocumentBoundary {
        private final int startPage;
        private final int endPage;
        private boolean matched = false;

        public DocumentBoundary(int startPage, int endPage) {
            this.startPage = startPage;
            this.endPage = endPage;
        }

        public int getStartPage() { return startPage; }
        public int getEndPage() { return endPage; }
        public boolean isMatched() { return matched; }
        public void setMatched(boolean matched) { this.matched = matched; }
    }

    /**
     * Potential match between base and comparison documents.
     */
    private static class PotentialMatch {
        private final int baseIndex;
        private final int compareIndex;
        private final double similarity;

        public PotentialMatch(int baseIndex, int compareIndex, double similarity) {
            this.baseIndex = baseIndex;
            this.compareIndex = compareIndex;
            this.similarity = similarity;
        }

        public int getBaseIndex() { return baseIndex; }
        public int getCompareIndex() { return compareIndex; }
        public double getSimilarity() { return similarity; }
    }

    /**
     * Document match between base and comparison PDFs.
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

        public int getBaseDocumentIndex() { return baseDocumentIndex; }
        public int getCompareDocumentIndex() { return compareDocumentIndex; }
        public double getSimilarityScore() { return similarityScore; }
    }

    /**
     * Java Set interface for word frequencies.
     */
    private static class Set<T> extends java.util.HashSet<T> {
    }
}