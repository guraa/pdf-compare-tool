package guraa.pdfcompare.core;

import guraa.pdfcompare.comparison.PDFComparisonResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.text.similarity.JaccardSimilarity;
import org.apache.commons.text.similarity.CosineSimilarity;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Smart document matcher that analyzes PDF contents to match similar documents
 * across two PDFs containing multiple documents each.
 */
public class SmartDocumentMatcher {
    private static final Logger logger = LoggerFactory.getLogger(SmartDocumentMatcher.class);

    // Configurable parameters
    private double similarityThreshold = 0.70; // Minimum similarity score to consider a match
    private int minDocumentPages = 1;          // Minimum pages to consider a document segment
    private int maxTitleDistance = 3;          // Max levenshtein distance for title matching

    // Feature extraction options
    private boolean useTextFeatures = true;
    private boolean useTitleMatching = true;
    private boolean useLayoutFeatures = true;
    private boolean useImageFeatures = true;

    /**
     * Match documents between two multi-document PDFs
     *
     * @param baseDocument The base PDF document model
     * @param compareDocument The comparison PDF document model
     * @return List of document pairs (indices in original PDFs)
     */
    public List<DocumentPair> matchDocuments(PDFDocumentModel baseDocument, PDFDocumentModel compareDocument) {
        logger.info("Starting smart document matching between {} and {}",
                baseDocument.getFileName(), compareDocument.getFileName());

        // Step 1: Identify document boundaries in both PDFs
        List<DocumentSegment> baseSegments = segmentDocuments(baseDocument);
        List<DocumentSegment> compareSegments = segmentDocuments(compareDocument);

        logger.info("Found {} document segments in base PDF and {} in comparison PDF",
                baseSegments.size(), compareSegments.size());

        // Step 2: Extract features for each document
        for (DocumentSegment segment : baseSegments) {
            extractFeatures(segment, baseDocument);
        }

        for (DocumentSegment segment : compareSegments) {
            extractFeatures(segment, compareDocument);
        }

        // Step 3: Calculate similarity scores between all document pairs
        List<SimilarityScore> allScores = new ArrayList<>();

        for (int i = 0; i < baseSegments.size(); i++) {
            DocumentSegment baseSegment = baseSegments.get(i);

            for (int j = 0; j < compareSegments.size(); j++) {
                DocumentSegment compareSegment = compareSegments.get(j);

                double similarity = calculateSimilarity(baseSegment, compareSegment);

                if (similarity >= similarityThreshold) {
                    allScores.add(new SimilarityScore(i, j, similarity));
                }
            }
        }

        // Step 4: Match documents using greedy approach (highest similarity first)
        Collections.sort(allScores, Comparator.comparingDouble(SimilarityScore::getScore).reversed());

        Set<Integer> matchedBase = new HashSet<>();
        Set<Integer> matchedCompare = new HashSet<>();
        List<DocumentPair> result = new ArrayList<>();

        for (SimilarityScore score : allScores) {
            int baseIndex = score.getBaseIndex();
            int compareIndex = score.getCompareIndex();

            if (!matchedBase.contains(baseIndex) && !matchedCompare.contains(compareIndex)) {
                // This is a valid match
                DocumentSegment baseSegment = baseSegments.get(baseIndex);
                DocumentSegment compareSegment = compareSegments.get(compareIndex);

                DocumentPair pair = new DocumentPair(
                        baseSegment.getStartPage(),
                        baseSegment.getEndPage(),
                        compareSegment.getStartPage(),
                        compareSegment.getEndPage(),
                        score.getScore()
                );

                result.add(pair);
                matchedBase.add(baseIndex);
                matchedCompare.add(compareIndex);

                logger.debug("Matched document: Base pages {}-{} to Compare pages {}-{} with similarity {}",
                        baseSegment.getStartPage(), baseSegment.getEndPage(),
                        compareSegment.getStartPage(), compareSegment.getEndPage(),
                        score.getScore());
            }
        }

        // Add unmatched documents with null pairs
        for (int i = 0; i < baseSegments.size(); i++) {
            if (!matchedBase.contains(i)) {
                DocumentSegment segment = baseSegments.get(i);
                result.add(new DocumentPair(
                        segment.getStartPage(),
                        segment.getEndPage(),
                        -1, -1, 0.0
                ));

                logger.debug("Unmatched base document: pages {}-{}",
                        segment.getStartPage(), segment.getEndPage());
            }
        }

        for (int i = 0; i < compareSegments.size(); i++) {
            if (!matchedCompare.contains(i)) {
                DocumentSegment segment = compareSegments.get(i);
                result.add(new DocumentPair(
                        -1, -1,
                        segment.getStartPage(),
                        segment.getEndPage(),
                        0.0
                ));

                logger.debug("Unmatched compare document: pages {}-{}",
                        segment.getStartPage(), segment.getEndPage());
            }
        }

        // Sort by base document page order
        result.sort(Comparator
                .comparingInt(DocumentPair::getBaseStartPage)
                .thenComparingInt(DocumentPair::getCompareStartPage));

        return result;
    }

    /**
     * Identify document segments in a PDF based on document boundaries
     */
    private List<DocumentSegment> segmentDocuments(PDFDocumentModel document) {
        List<DocumentSegment> segments = new ArrayList<>();
        int currentStart = 0;
        String currentTitle = null;

        for (int i = 0; i < document.getPageCount(); i++) {
            PDFPageModel page = document.getPages().get(i);

            // Detect if this page could be the start of a new document
            if (isDocumentStart(page)) {
                // If we've accumulated enough pages, save the previous segment
                if (i - currentStart >= minDocumentPages) {
                    segments.add(new DocumentSegment(currentStart, i - 1, currentTitle));
                }

                // Start a new segment
                currentStart = i;
                currentTitle = extractDocumentTitle(page);
            }
        }

        // Add the final segment
        if (document.getPageCount() - currentStart >= minDocumentPages) {
            segments.add(new DocumentSegment(currentStart, document.getPageCount() - 1, currentTitle));
        }

        // If no segments were detected or only one large document, treat the whole PDF as one document
        if (segments.isEmpty()) {
            segments.add(new DocumentSegment(0, document.getPageCount() - 1, extractDocumentTitle(document.getPages().get(0))));
        }

        return segments;
    }

    /**
     * Check if a page is likely the start of a new document
     */
    private boolean isDocumentStart(PDFPageModel page) {
        // Simple heuristic: First page or page with large text at the top could be a document start
        // More sophisticated approaches might look for title patterns, page numbers restarting, etc.

        // Check if this is the first page
        if (page.getPageNumber() == 1) {
            return true;
        }

        // Check for title-like text at the top of the page
        if (page.getTextElements() != null && !page.getTextElements().isEmpty()) {
            // Sort text elements by Y position (top to bottom)
            List<TextElement> topElements = page.getTextElements().stream()
                    .sorted(Comparator.comparingDouble(TextElement::getY))
                    .limit(3)  // Look at top 3 elements
                    .collect(Collectors.toList());

            for (TextElement element : topElements) {
                // Check if it looks like a title (large font, centered, short text)
                if (element.getFontSize() > 12 && element.getText().length() < 100) {
                    return true;
                }
            }
        }

        // More document boundary detection logic can be added here
        return false;
    }

    /**
     * Try to extract a document title from a page
     */
    private String extractDocumentTitle(PDFPageModel page) {
        if (page.getTextElements() == null || page.getTextElements().isEmpty()) {
            return null;
        }

        // Find potential title elements (large text near the top)
        List<TextElement> candidates = page.getTextElements().stream()
                .filter(el -> el.getY() < page.getHeight() * 0.3) // Top 30% of page
                .filter(el -> el.getFontSize() > 12)              // Larger than normal text
                .sorted(Comparator.comparingDouble(TextElement::getY))
                .collect(Collectors.toList());

        if (!candidates.isEmpty()) {
            // Take the first candidate as the title
            return candidates.get(0).getText();
        }

        // Fallback: use first text on page as title
        return page.getTextElements().stream()
                .min(Comparator.comparingDouble(TextElement::getY))
                .map(TextElement::getText)
                .orElse(null);
    }

    /**
     * Extract feature vectors for a document segment
     */
    private void extractFeatures(DocumentSegment segment, PDFDocumentModel document) {
        Map<String, Object> features = new HashMap<>();

        // Calculate text features
        if (useTextFeatures) {
            StringBuilder fullText = new StringBuilder();
            List<String> keywords = new ArrayList<>();

            // Extract text and keywords from each page
            for (int i = segment.getStartPage(); i <= segment.getEndPage(); i++) {
                PDFPageModel page = document.getPages().get(i);

                // Add full text
                if (page.getText() != null) {
                    fullText.append(page.getText()).append(" ");
                }

                // Extract potential keywords (e.g., words in bold or larger font)
                if (page.getTextElements() != null) {
                    for (TextElement element : page.getTextElements()) {
                        if (element.getFontSize() > 12 || "bold".equalsIgnoreCase(element.getFontStyle())) {
                            keywords.add(element.getText());
                        }
                    }
                }
            }

            // Store text features
            features.put("fullText", fullText.toString());
            features.put("keywords", keywords);

            // Calculate TF-IDF or other text features
            Map<String, Integer> wordFrequency = calculateWordFrequency(fullText.toString());
            features.put("wordFrequency", wordFrequency);
        }

        // Title feature
        if (useTitleMatching) {
            features.put("title", segment.getTitle());
        }

        // Layout features
        if (useLayoutFeatures) {
            // Extract layout patterns, page sizes, margins, etc.
            List<float[]> pageDimensions = new ArrayList<>();

            for (int i = segment.getStartPage(); i <= segment.getEndPage(); i++) {
                PDFPageModel page = document.getPages().get(i);
                pageDimensions.add(new float[]{page.getWidth(), page.getHeight()});
            }

            features.put("pageDimensions", pageDimensions);
        }

        // Image features
        if (useImageFeatures) {
            // Count images, extract image hashes, etc.
            int imageCount = 0;
            for (int i = segment.getStartPage(); i <= segment.getEndPage(); i++) {
                PDFPageModel page = document.getPages().get(i);
                if (page.getImages() != null) {
                    imageCount += page.getImages().size();
                }
            }

            features.put("imageCount", imageCount);
        }

        segment.setFeatures(features);
    }

    /**
     * Calculate similarity score between two document segments
     */
    private double calculateSimilarity(DocumentSegment baseSegment, DocumentSegment compareSegment) {
        double score = 0;
        double weight = 0;

        // Text similarity
        if (useTextFeatures) {
            String baseText = (String) baseSegment.getFeatures().get("fullText");
            String compareText = (String) compareSegment.getFeatures().get("fullText");

            if (baseText != null && compareText != null) {
                // Use Jaccard similarity for text comparison
                JaccardSimilarity jaccard = new JaccardSimilarity();
                double textSimilarity = jaccard.apply(baseText, compareText);

                // Use TF-IDF cosine similarity for more nuanced text comparison
                @SuppressWarnings("unchecked")
                Map<String, Integer> baseFreq = (Map<String, Integer>) baseSegment.getFeatures().get("wordFrequency");
                @SuppressWarnings("unchecked")
                Map<String, Integer> compareFreq = (Map<String, Integer>) compareSegment.getFeatures().get("wordFrequency");

                double cosineSim = calculateCosineSimilarity(baseFreq, compareFreq);

                // Combine text similarity scores
                double textScore = (textSimilarity + cosineSim) / 2;
                score += textScore * 0.7; // Text gets highest weight
                weight += 0.7;
            }
        }

        // Title similarity
        if (useTitleMatching) {
            String baseTitle = baseSegment.getTitle();
            String compareTitle = compareSegment.getTitle();

            if (baseTitle != null && compareTitle != null) {
                double titleSimilarity = calculateTitleSimilarity(baseTitle, compareTitle);
                score += titleSimilarity * 0.15;
                weight += 0.15;
            }
        }

        // Layout similarity
        if (useLayoutFeatures) {
            @SuppressWarnings("unchecked")
            List<float[]> baseDims = (List<float[]>) baseSegment.getFeatures().get("pageDimensions");
            @SuppressWarnings("unchecked")
            List<float[]> compareDims = (List<float[]>) compareSegment.getFeatures().get("pageDimensions");

            if (baseDims != null && compareDims != null) {
                double layoutSimilarity = calculateLayoutSimilarity(baseDims, compareDims);
                score += layoutSimilarity * 0.1;
                weight += 0.1;
            }
        }

        // Image count similarity
        if (useImageFeatures) {
            Integer baseImageCount = (Integer) baseSegment.getFeatures().get("imageCount");
            Integer compareImageCount = (Integer) compareSegment.getFeatures().get("imageCount");

            if (baseImageCount != null && compareImageCount != null) {
                // Simple ratio of image counts (capped at 1.0)
                double countRatio = Math.min(baseImageCount, compareImageCount) /
                        (double) Math.max(baseImageCount, compareImageCount);

                // If both zero, they're similar
                if (baseImageCount == 0 && compareImageCount == 0) {
                    countRatio = 1.0;
                }

                score += countRatio * 0.05;
                weight += 0.05;
            }
        }

        // Return normalized score
        return weight > 0 ? score / weight : 0;
    }

    /**
     * Calculate word frequency map for text
     */
    private Map<String, Integer> calculateWordFrequency(String text) {
        Map<String, Integer> frequency = new HashMap<>();

        if (text == null || text.isEmpty()) {
            return frequency;
        }

        // Normalize and tokenize text
        String[] words = text.toLowerCase()
                .replaceAll("[^\\p{Alnum}\\s]", "")
                .split("\\s+");

        // Count frequencies
        for (String word : words) {
            if (word.length() > 2) { // Skip very short words
                frequency.put(word, frequency.getOrDefault(word, 0) + 1);
            }
        }

        return frequency;
    }

    /**
     * Calculate cosine similarity between word frequency maps
     */
    private double calculateCosineSimilarity(Map<String, Integer> map1, Map<String, Integer> map2) {
        if (map1 == null || map2 == null || map1.isEmpty() || map2.isEmpty()) {
            return 0;
        }

        CosineSimilarity cosineSimilarity = new CosineSimilarity();
        return cosineSimilarity.cosineSimilarity(map1, map2);
    }

    /**
     * Calculate title similarity
     */
    private double calculateTitleSimilarity(String title1, String title2) {
        if (title1 == null || title2 == null) {
            return 0;
        }

        // Normalize titles for comparison
        title1 = title1.toLowerCase().trim();
        title2 = title2.toLowerCase().trim();

        // Calculate Levenshtein distance
        int distance = org.apache.commons.text.similarity.LevenshteinDistance.getDefaultInstance()
                .apply(title1, title2);

        // Normalize by max length
        int maxLength = Math.max(title1.length(), title2.length());
        if (maxLength == 0) return 1.0; // Both empty strings

        double normalizedDistance = 1.0 - (double) distance / maxLength;

        // Consider exact match or very close match as high similarity
        if (distance <= maxTitleDistance || normalizedDistance > 0.8) {
            return 1.0;
        }

        return normalizedDistance;
    }

    /**
     * Calculate layout similarity based on page dimensions
     */
    private double calculateLayoutSimilarity(List<float[]> dims1, List<float[]> dims2) {
        if (dims1.isEmpty() || dims2.isEmpty()) {
            return 0;
        }

        // Compare dimensions of first pages as a proxy for layout similarity
        float[] firstDim1 = dims1.get(0);
        float[] firstDim2 = dims2.get(0);

        // Calculate normalized dimension similarity
        double widthRatio = Math.min(firstDim1[0], firstDim2[0]) / Math.max(firstDim1[0], firstDim2[0]);
        double heightRatio = Math.min(firstDim1[1], firstDim2[1]) / Math.max(firstDim1[1], firstDim2[1]);

        return (widthRatio + heightRatio) / 2;
    }

    /**
     * Class representing a document segment within a PDF
     */
    public static class DocumentSegment {
        private final int startPage;
        private final int endPage;
        private final String title;
        private Map<String, Object> features;

        public DocumentSegment(int startPage, int endPage, String title) {
            this.startPage = startPage;
            this.endPage = endPage;
            this.title = title;
            this.features = new HashMap<>();
        }

        public int getStartPage() {
            return startPage;
        }

        public int getEndPage() {
            return endPage;
        }

        public String getTitle() {
            return title;
        }

        public Map<String, Object> getFeatures() {
            return features;
        }

        public void setFeatures(Map<String, Object> features) {
            this.features = features;
        }

        public int getPageCount() {
            return endPage - startPage + 1;
        }
    }

    /**
     * Class representing a similarity score between two document segments
     */
    private static class SimilarityScore {
        private final int baseIndex;
        private final int compareIndex;
        private final double score;

        public SimilarityScore(int baseIndex, int compareIndex, double score) {
            this.baseIndex = baseIndex;
            this.compareIndex = compareIndex;
            this.score = score;
        }

        public int getBaseIndex() {
            return baseIndex;
        }

        public int getCompareIndex() {
            return compareIndex;
        }

        public double getScore() {
            return score;
        }
    }

    /**
     * Class representing a matched pair of documents
     */
    public static class DocumentPair {
        private final int baseStartPage;
        private final int baseEndPage;
        private final int compareStartPage;
        private final int compareEndPage;
        private final double similarityScore;

        public DocumentPair(int baseStartPage, int baseEndPage, int compareStartPage, int compareEndPage, double similarityScore) {
            this.baseStartPage = baseStartPage;
            this.baseEndPage = baseEndPage;
            this.compareStartPage = compareStartPage;
            this.compareEndPage = compareEndPage;
            this.similarityScore = similarityScore;
        }

        public int getBaseStartPage() {
            return baseStartPage;
        }

        public int getBaseEndPage() {
            return baseEndPage;
        }

        public int getCompareStartPage() {
            return compareStartPage;
        }

        public int getCompareEndPage() {
            return compareEndPage;
        }

        public double getSimilarityScore() {
            return similarityScore;
        }

        public boolean hasBaseDocument() {
            return baseStartPage >= 0 && baseEndPage >= 0;
        }

        public boolean hasCompareDocument() {
            return compareStartPage >= 0 && compareEndPage >= 0;
        }

        public boolean isMatched() {
            return hasBaseDocument() && hasCompareDocument();
        }
    }

    // Getters and setters for configuration
    public double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public void setSimilarityThreshold(double similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }

    public int getMinDocumentPages() {
        return minDocumentPages;
    }

    public void setMinDocumentPages(int minDocumentPages) {
        this.minDocumentPages = minDocumentPages;
    }

    public boolean isUseTextFeatures() {
        return useTextFeatures;
    }

    public void setUseTextFeatures(boolean useTextFeatures) {
        this.useTextFeatures = useTextFeatures;
    }

    public boolean isUseTitleMatching() {
        return useTitleMatching;
    }

    public void setUseTitleMatching(boolean useTitleMatching) {
        this.useTitleMatching = useTitleMatching;
    }

    public boolean isUseLayoutFeatures() {
        return useLayoutFeatures;
    }

    public void setUseLayoutFeatures(boolean useLayoutFeatures) {
        this.useLayoutFeatures = useLayoutFeatures;
    }

    public boolean isUseImageFeatures() {
        return useImageFeatures;
    }

    public void setUseImageFeatures(boolean useImageFeatures) {
        this.useImageFeatures = useImageFeatures;
    }
}