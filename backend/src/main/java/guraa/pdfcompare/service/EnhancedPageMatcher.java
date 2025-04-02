package guraa.pdfcompare.service;

import guraa.pdfcompare.core.PDFDocumentModel;
import guraa.pdfcompare.core.PDFPageModel;
import guraa.pdfcompare.core.PageFingerprint;
import guraa.pdfcompare.core.TextElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Enhanced page matching algorithm with multi-strategy approach
 * for more accurate matching between pages of different PDFs.
 */
public class EnhancedPageMatcher {
    private static final Logger logger = LoggerFactory.getLogger(EnhancedPageMatcher.class);

    // Configurable thresholds
    private double highSimilarityThreshold = 0.95;    // For direct matches - increased to 0.95 (95%)
    private double mediumSimilarityThreshold = 0.85;  // For probable matches - increased to 0.85 (85%)
    private double lowSimilarityThreshold = 0.75;     // For possible matches - increased to 0.75 (75%)

    // Weights for different matching strategies
    private double textContentWeight = 0.35;          // Pure text content similarity
    private double structuralSimilarityWeight = 0.25; // Structural features (layout, positioning)
    private double textStyleWeight = 0.15;            // Font styles, sizes
    private double imagePresenceWeight = 0.15;        // Image presence and positioning
    private double positionalWeight = 0.10;           // Page position in the document

    /**
     * Match pages between two PDF documents
     * @param baseDocument Base document
     * @param compareDocument Compare document
     * @return List of page pairs
     */
    public List<PagePair> matchPages(PDFDocumentModel baseDocument, PDFDocumentModel compareDocument) {
        logger.info("Starting enhanced page matching between documents");

        List<PDFPageModel> basePages = baseDocument.getPages();
        List<PDFPageModel> comparePages = compareDocument.getPages();

        // Generate fingerprints for all pages
        List<PageFingerprint> baseFingerprints = generateFingerprints(basePages, "base");
        List<PageFingerprint> compareFingerprints = generateFingerprints(comparePages, "compare");

        // Calculate similarity matrix
        double[][] similarityMatrix = calculateSimilarityMatrix(baseFingerprints, compareFingerprints);

        // Find optimal matching using the similarity matrix
        List<PagePair> pagePairs = findOptimalMatching(similarityMatrix, baseFingerprints, compareFingerprints);

        // Debug log matched pages
        logMatchingResults(pagePairs);

        return pagePairs;
    }





    /**
     * Generate fingerprints for pages
     */
    private List<PageFingerprint> generateFingerprints(List<PDFPageModel> pages, String sourceType) {
        List<PageFingerprint> fingerprints = new ArrayList<>();

        for (int i = 0; i < pages.size(); i++) {
            PDFPageModel page = pages.get(i);
            PageFingerprint fingerprint = new PageFingerprint(sourceType, i);
            fingerprint.setPage(page);

            // Extract and normalize text content
            String rawText = page.getText() != null ? page.getText() : "";
            String normalizedText = normalizeText(rawText);
            fingerprint.setText(normalizedText);

            // Calculate text hash for quick exact matches
            fingerprint.setTextHash(normalizedText.hashCode());

            // Extract significant words
            Set<String> significantWords = extractKeywords(normalizedText);
            fingerprint.setSignificantWords(significantWords);

            // Extract structural information if text elements exist
            if (page.getTextElements() != null && !page.getTextElements().isEmpty()) {
                // Process text elements to derive structural features
                Map<String, Integer> fontDistribution = new HashMap<>();
                List<Double> textPositions = new ArrayList<>();

                for (TextElement element : page.getTextElements()) {
                    // Track font usage
                    if (element.getFontName() != null) {
                        fontDistribution.merge(element.getFontName(), 1, Integer::sum);
                    }

                    // Track vertical positions to understand document structure
                    if (element.getY() > 0 && element.getText() != null && !element.getText().trim().isEmpty()) {
                        textPositions.add((double)element.getY());
                    }
                }

                fingerprint.setFontDistribution(fontDistribution);
                fingerprint.setElementCount(page.getTextElements().size());

                // Calculate text density distribution (useful for layout matching)
                if (!textPositions.isEmpty()) {
                    Collections.sort(textPositions);
                    fingerprint.setTextPositionDistribution(textPositions);
                }
            }

            // Process image information
            if (page.getImages() != null && !page.getImages().isEmpty()) {
                fingerprint.setHasImages(true);
                fingerprint.setImageCount(page.getImages().size());
            }

            fingerprints.add(fingerprint);
        }

        logger.debug("Generated {} fingerprints for {} pages", fingerprints.size(), pages.size());
        return fingerprints;
    }

    /**
     * Calculate similarity matrix between all page pairs
     */
    private double[][] calculateSimilarityMatrix(List<PageFingerprint> baseFingerprints,
                                                 List<PageFingerprint> compareFingerprints) {
        int baseSize = baseFingerprints.size();
        int compareSize = compareFingerprints.size();
        double[][] matrix = new double[baseSize][compareSize];

        // Calculate similarity for every possible page pair
        for (int i = 0; i < baseSize; i++) {
            for (int j = 0; j < compareSize; j++) {
                // Fast path: if text hashes match exactly, they are identical
                if (baseFingerprints.get(i).getTextHash() != 0 &&
                        baseFingerprints.get(i).getTextHash() == compareFingerprints.get(j).getTextHash()) {
                    matrix[i][j] = 1.0;
                    continue;
                }

                // Calculate composite similarity score
                matrix[i][j] = calculateCompositeSimilarity(baseFingerprints.get(i), compareFingerprints.get(j));

                // Apply positional bias - pages at similar positions are more likely to match
                // Store the i and j values so they don't cause lambda issues
                final int baseIdx = i;
                final int compareIdx = j;
                matrix[i][j] = applyPositionalBias(matrix[i][j], baseIdx, compareIdx, baseSize, compareSize);

                logger.debug("Similarity between base page {} and compare page {}: {}",
                        i+1, j+1, String.format("%.4f", matrix[i][j]));
            }
        }

        return matrix;
    }

    /**
     * Calculate comprehensive similarity between two page fingerprints
     */
    private double calculateCompositeSimilarity(PageFingerprint base, PageFingerprint compare) {
        // Calculate text content similarity
        double textSimilarity = calculateTextSimilarity(base, compare);

        // Calculate structural similarity
        double structuralSimilarity = calculateStructuralSimilarity(base, compare);

        // Calculate text style similarity
        double styleSimilarity = calculateStyleSimilarity(base, compare);

        // Calculate image similarity
        double imageSimilarity = calculateImageSimilarity(base, compare);

        // Combine scores with weights
        return (textSimilarity * textContentWeight) +
                (structuralSimilarity * structuralSimilarityWeight) +
                (styleSimilarity * textStyleWeight) +
                (imageSimilarity * imagePresenceWeight);
    }

    /**
     * Apply positional bias to similarity score
     * Pages at similar positions in both documents have a higher chance of matching
     */
    private double applyPositionalBias(double currentSimilarity, int baseIndex, int compareIndex,
                                       int baseSize, int compareSize) {
        // Calculate normalized positions (0-1 range)
        double basePosition = (double)baseIndex / baseSize;
        double comparePosition = (double)compareIndex / compareSize;

        // Calculate position similarity (closer to 1 if positions are similar)
        double positionSimilarity = 1.0 - Math.abs(basePosition - comparePosition);

        // Apply positional weight
        return (currentSimilarity * (1.0 - positionalWeight)) +
                (positionSimilarity * positionalWeight);
    }

    /**
     * Calculate text similarity using multiple techniques
     */
    private double calculateTextSimilarity(PageFingerprint base, PageFingerprint compare) {
        String baseText = base.getText();
        String compareText = compare.getText();

        // Handle empty text
        if (baseText.isEmpty() && compareText.isEmpty()) {
            return 1.0; // Both empty = perfect match
        }
        if (baseText.isEmpty() || compareText.isEmpty()) {
            return 0.0; // Only one empty = no match
        }

        // 1. Calculate Jaccard similarity with significant words
        double jaccardSimilarity = calculateJaccardSimilarity(
                base.getSignificantWords(), compare.getSignificantWords());

        // 2. Calculate n-gram similarity for more context
        double ngramSimilarity = calculateNgramSimilarity(baseText, compareText);

        // 3. Calculate edit distance similarity
        double editDistanceSimilarity = calculateEditDistanceSimilarity(baseText, compareText);

        // Combine text similarity scores (with potential for fine-tuning weights)
        return (jaccardSimilarity * 0.5) + (ngramSimilarity * 0.3) + (editDistanceSimilarity * 0.2);
    }

    /**
     * Calculate structural similarity based on page layout and elements
     */
    private double calculateStructuralSimilarity(PageFingerprint base, PageFingerprint compare) {
        double similarity = 0.0;
        double componentCount = 0.0;

        // Compare font distributions
        if (base.getFontDistribution() != null && compare.getFontDistribution() != null) {
            similarity += calculateFontDistributionSimilarity(
                    base.getFontDistribution(), compare.getFontDistribution());
            componentCount++;
        }

        // Compare element counts
        if (base.getElementCount() > 0 && compare.getElementCount() > 0) {
            double countRatio = (double)Math.min(base.getElementCount(), compare.getElementCount()) /
                    Math.max(base.getElementCount(), compare.getElementCount());
            similarity += countRatio;
            componentCount++;
        }

        // Compare text position distributions if available
        if (base.getTextPositionDistribution() != null && compare.getTextPositionDistribution() != null &&
                !base.getTextPositionDistribution().isEmpty() && !compare.getTextPositionDistribution().isEmpty()) {
            similarity += calculateTextPositionSimilarity(
                    base.getTextPositionDistribution(), compare.getTextPositionDistribution());
            componentCount++;
        }

        // Return average if we have components, otherwise 0
        return componentCount > 0 ? similarity / componentCount : 0.0;
    }

    /**
     * Calculate similarity of text style features
     */
    private double calculateStyleSimilarity(PageFingerprint base, PageFingerprint compare) {
        // This could be expanded to analyze font sizes, styles, etc.
        // For now use font distribution as proxy
        if (base.getFontDistribution() != null && compare.getFontDistribution() != null) {
            return calculateFontDistributionSimilarity(base.getFontDistribution(), compare.getFontDistribution());
        }
        return 0.0;
    }

    /**
     * Calculate image feature similarity
     */
    private double calculateImageSimilarity(PageFingerprint base, PageFingerprint compare) {
        // Basic image presence comparison
        if (base.isHasImages() == compare.isHasImages()) {
            if (!base.isHasImages()) {
                return 1.0; // Both have no images = match
            }

            // Both have images, compare counts
            double countRatio = (double)Math.min(base.getImageCount(), compare.getImageCount()) /
                    Math.max(base.getImageCount(), compare.getImageCount());
            return countRatio;
        }

        return 0.0; // One has images, one doesn't
    }

    /**
     * Calculate font distribution similarity
     */
    private double calculateFontDistributionSimilarity(
            Map<String, Integer> baseFonts, Map<String, Integer> compareFonts) {
        // Create a set of all font names
        Set<String> allFonts = new HashSet<>(baseFonts.keySet());
        allFonts.addAll(compareFonts.keySet());

        // For each font, calculate the difference in usage
        int totalDifference = 0;
        int totalElements = 0;

        for (String font : allFonts) {
            int baseCount = baseFonts.getOrDefault(font, 0);
            int compareCount = compareFonts.getOrDefault(font, 0);

            totalDifference += Math.abs(baseCount - compareCount);
            totalElements += Math.max(baseCount, compareCount);
        }

        // Return similarity (1 - normalized difference)
        return totalElements > 0 ? 1.0 - ((double)totalDifference / totalElements) : 0.0;
    }

    /**
     * Calculate similarity of text positions (for layout comparison)
     */
    private double calculateTextPositionSimilarity(
            List<Double> basePositions, List<Double> comparePositions) {
        // Normalize positions to 0-1 range
        List<Double> normalizedBase = normalizePositions(basePositions);
        List<Double> normalizedCompare = normalizePositions(comparePositions);

        // Get distribution information (how text is spread across the page)
        double[] baseDistribution = calculateDistributionFeatures(normalizedBase);
        double[] compareDistribution = calculateDistributionFeatures(normalizedCompare);

        // Calculate Euclidean distance between distribution features
        double distance = 0.0;
        for (int i = 0; i < baseDistribution.length; i++) {
            distance += Math.pow(baseDistribution[i] - compareDistribution[i], 2);
        }
        distance = Math.sqrt(distance);

        // Convert distance to similarity (1 for identical, 0 for maximally different)
        return Math.max(0.0, 1.0 - distance);
    }

    /**
     * Find optimal matching between pages using similarity matrix
     */
    private List<PagePair> findOptimalMatching(double[][] similarityMatrix,
                                               List<PageFingerprint> baseFingerprints,
                                               List<PageFingerprint> compareFingerprints) {
        int baseSize = baseFingerprints.size();
        int compareSize = compareFingerprints.size();

        List<PagePair> pagePairs = new ArrayList<>();

        // Mark which pages are matched
        boolean[] baseMatched = new boolean[baseSize];
        boolean[] compareMatched = new boolean[compareSize];

        // Phase 1: Match pages with high similarity (above highSimilarityThreshold)
        matchPagesAboveThreshold(similarityMatrix, baseFingerprints, compareFingerprints,
                baseMatched, compareMatched, pagePairs, highSimilarityThreshold);

        // Phase 2: Match remaining pages with medium similarity
        matchPagesAboveThreshold(similarityMatrix, baseFingerprints, compareFingerprints,
                baseMatched, compareMatched, pagePairs, mediumSimilarityThreshold);

        // Phase 3: Match remaining pages with low similarity
        matchPagesAboveThreshold(similarityMatrix, baseFingerprints, compareFingerprints,
                baseMatched, compareMatched, pagePairs, lowSimilarityThreshold);

        // Add unmatched base pages
        for (int i = 0; i < baseSize; i++) {
            if (!baseMatched[i]) {
                pagePairs.add(new PagePair(baseFingerprints.get(i), null, 0.0));
            }
        }

        // Add unmatched compare pages
        for (int j = 0; j < compareSize; j++) {
            if (!compareMatched[j]) {
                pagePairs.add(new PagePair(null, compareFingerprints.get(j), 0.0));
            }
        }

        // Sort the pairs for better presentation
        sortPagePairs(pagePairs);

        return pagePairs;
    }

    /**
     * Match pages above a given threshold
     */
    private void matchPagesAboveThreshold(double[][] similarityMatrix,
                                          List<PageFingerprint> baseFingerprints,
                                          List<PageFingerprint> compareFingerprints,
                                          boolean[] baseMatched,
                                          boolean[] compareMatched,
                                          List<PagePair> pagePairs,
                                          double threshold) {
        int baseSize = baseFingerprints.size();
        int compareSize = compareFingerprints.size();

        // Create prioritized list of potential matches
        List<PotentialMatch> potentialMatches = new ArrayList<>();

        for (int i = 0; i < baseSize; i++) {
            if (baseMatched[i]) continue;

            for (int j = 0; j < compareSize; j++) {
                if (compareMatched[j]) continue;

                if (similarityMatrix[i][j] >= threshold) {
                    potentialMatches.add(new PotentialMatch(i, j, similarityMatrix[i][j]));
                }
            }
        }

        // Sort by similarity score (highest first)
        potentialMatches.sort((a, b) -> Double.compare(b.similarity, a.similarity));

        // Match pages greedily by highest similarity first
        for (PotentialMatch match : potentialMatches) {
            if (!baseMatched[match.baseIndex] && !compareMatched[match.compareIndex]) {
                pagePairs.add(new PagePair(
                        baseFingerprints.get(match.baseIndex),
                        compareFingerprints.get(match.compareIndex),
                        match.similarity
                ));
                baseMatched[match.baseIndex] = true;
                compareMatched[match.compareIndex] = true;
            }
        }
    }

    /**
     * Sort page pairs for better presentation and reporting
     */
    private void sortPagePairs(List<PagePair> pagePairs) {
        pagePairs.sort((a, b) -> {
            // Put matched pairs first, then by base page index
            if (a.getBaseFingerprint() != null && b.getBaseFingerprint() != null) {
                return Integer.compare(a.getBaseFingerprint().getPageIndex(),
                        b.getBaseFingerprint().getPageIndex());
            } else if (a.getBaseFingerprint() != null) {
                return -1; // a has base page, b doesn't
            } else if (b.getBaseFingerprint() != null) {
                return 1;  // b has base page, a doesn't
            } else {
                // Both are compare-only pages, sort by compare page index
                return Integer.compare(a.getCompareFingerprint().getPageIndex(),
                        b.getCompareFingerprint().getPageIndex());
            }
        });
    }

    /**
     * Normalize text for more consistent comparisons
     */
    private String normalizeText(String text) {
        if (text == null) return "";

        // Convert to lowercase
        text = text.toLowerCase();

        // Remove excessive whitespace
        text = text.replaceAll("\\s+", " ").trim();

        // Remove common punctuation
        text = text.replaceAll("[.,;:!?()\\[\\]{}\"']", "");

        return text;
    }

    /**
     * Extract significant words (keywords) from text
     */
    private Set<String> extractKeywords(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptySet();
        }

        // List of common stop words to filter out
        Set<String> stopWords = new HashSet<>(Arrays.asList(
                "a", "an", "and", "are", "as", "at", "be", "but", "by", "for", "if", "in", "into",
                "is", "it", "no", "not", "of", "on", "or", "such", "that", "the", "their", "then",
                "there", "these", "they", "this", "to", "was", "will", "with"
        ));

        // Extract words, convert to lowercase, and filter stop words
        return Arrays.stream(text.toLowerCase().split("\\s+"))
                .filter(word -> word.length() > 2)  // Only words with 3+ characters
                .filter(word -> !stopWords.contains(word))
                .collect(Collectors.toSet());
    }

    /**
     * Calculate Jaccard similarity between two sets
     */
    private double calculateJaccardSimilarity(Set<String> set1, Set<String> set2) {
        if (set1.isEmpty() && set2.isEmpty()) {
            return 1.0; // Both empty = perfect match
        }

        // Find intersection and union
        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);

        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);

        // Jaccard similarity = |intersection| / |union|
        return (double)intersection.size() / union.size();
    }

    /**
     * Calculate n-gram similarity between two strings
     */
    private double calculateNgramSimilarity(String str1, String str2) {
        // Use trigrams (3-character sequences)
        int n = 3;

        // If either string is shorter than n characters, fall back to character-level comparison
        if (str1.length() < n || str2.length() < n) {
            n = 1;
        }

        // Generate n-grams for both strings
        Set<String> ngrams1 = generateNgrams(str1, n);
        Set<String> ngrams2 = generateNgrams(str2, n);

        // Calculate Jaccard similarity of n-gram sets
        return calculateJaccardSimilarity(ngrams1, ngrams2);
    }

    /**
     * Generate n-grams from a string
     */
    private Set<String> generateNgrams(String str, int n) {
        Set<String> ngrams = new HashSet<>();
        for (int i = 0; i <= str.length() - n; i++) {
            ngrams.add(str.substring(i, i + n));
        }
        return ngrams;
    }

    /**
     * Calculate similarity based on edit distance
     */
    private double calculateEditDistanceSimilarity(String str1, String str2) {
        // For very long strings, use sampling
        if (str1.length() > 1000 || str2.length() > 1000) {
            return calculateEditDistanceSimilaritySampled(str1, str2);
        }

        // Calculate Levenshtein distance
        int distance = levenshteinDistance(str1, str2);

        // Convert to similarity score (1 for identical, 0 for maximally different)
        int maxLength = Math.max(str1.length(), str2.length());
        return maxLength > 0 ? 1.0 - ((double)distance / maxLength) : 1.0;
    }

    /**
     * Calculate edit distance similarity for very long strings using sampling
     */
    private double calculateEditDistanceSimilaritySampled(String str1, String str2) {
        // Sample beginning, middle, and end of each string
        int sampleSize = 300;

        // Beginning samples
        String beginning1 = str1.substring(0, Math.min(sampleSize, str1.length()));
        String beginning2 = str2.substring(0, Math.min(sampleSize, str2.length()));

        // Middle samples
        String middle1 = "";
        String middle2 = "";
        if (str1.length() > sampleSize * 2) {
            int startPos = (str1.length() / 2) - (sampleSize / 2);
            middle1 = str1.substring(startPos, startPos + sampleSize);
        }
        if (str2.length() > sampleSize * 2) {
            int startPos = (str2.length() / 2) - (sampleSize / 2);
            middle2 = str2.substring(startPos, startPos + sampleSize);
        }

        // End samples
        String end1 = "";
        String end2 = "";
        if (str1.length() > sampleSize) {
            end1 = str1.substring(str1.length() - sampleSize);
        }
        if (str2.length() > sampleSize) {
            end2 = str2.substring(str2.length() - sampleSize);
        }

        // Calculate similarities for each section
        double beginningSimilarity = calculateEditDistanceSimilarity(beginning1, beginning2);
        double middleSimilarity = middle1.isEmpty() || middle2.isEmpty() ? 0.0 :
                calculateEditDistanceSimilarity(middle1, middle2);
        double endSimilarity = end1.isEmpty() || end2.isEmpty() ? 0.0 :
                calculateEditDistanceSimilarity(end1, end2);

        // Combine similarities with weights
        double totalWeight = 1.0;
        double weightedSum = beginningSimilarity * 0.5;

        if (!middle1.isEmpty() && !middle2.isEmpty()) {
            weightedSum += middleSimilarity * 0.3;
            totalWeight += 0.3;
        }

        if (!end1.isEmpty() && !end2.isEmpty()) {
            weightedSum += endSimilarity * 0.2;
            totalWeight += 0.2;
        }

        return weightedSum / totalWeight;
    }

    /**
     * Calculate Levenshtein distance between two strings
     */
    private int levenshteinDistance(String s1, String s2) {
        int m = s1.length();
        int n = s2.length();

        // Special cases for efficiency
        if (m == 0) return n;
        if (n == 0) return m;
        if (s1.equals(s2)) return 0;

        // Use two rows instead of a full matrix for space efficiency
        int[] prevRow = new int[n + 1];
        int[] currRow = new int[n + 1];

        // Initialize the previous row
        for (int j = 0; j <= n; j++) {
            prevRow[j] = j;
        }

        // Fill in the DP table row by row
        for (int i = 1; i <= m; i++) {
            currRow[0] = i;

            for (int j = 1; j <= n; j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;

                currRow[j] = Math.min(
                        currRow[j - 1] + 1,        // insertion
                        Math.min(
                                prevRow[j] + 1,        // deletion
                                prevRow[j - 1] + cost  // substitution/match
                        )
                );
            }

            // Swap rows for next iteration
            int[] temp = prevRow;
            prevRow = currRow;
            currRow = temp;
        }

        return prevRow[n];
    }

    /**
     * Normalize a list of positions to 0-1 range
     */
    private List<Double> normalizePositions(List<Double> positions) {
        if (positions.isEmpty()) return positions;

        // Find min and max
        double min = positions.get(0);
        double max = positions.get(0);

        for (Double pos : positions) {
            min = Math.min(min, pos);
            max = Math.max(max, pos);
        }

        // Apply normalization
        final double finalMin = min; // Create final copy
        final double finalRange = (max - min < 0.001) ? 1.0 : max - min; // Create final copy with check

        return positions.stream()
                .map(pos -> (pos - finalMin) / finalRange) // Use final variables
                .collect(Collectors.toList());
    }
    /**
     * Calculate distribution features for normalized positions
     */
    private double[] calculateDistributionFeatures(List<Double> normalizedPositions) {
        // Create a histogram of positions
        int numBins = 10;
        int[] histogram = new int[numBins];

        for (Double pos : normalizedPositions) {
            int bin = Math.min(numBins - 1, (int)(pos * numBins));
            histogram[bin]++;
        }

        // Calculate feature vector (normalized histogram)
        double[] features = new double[numBins];

        for (int i = 0; i < numBins; i++) {
            features[i] = normalizedPositions.isEmpty() ? 0.0 :
                    (double)histogram[i] / normalizedPositions.size();
        }

        return features;
    }

    /**
     * Log matching results for debugging
     */
    private void logMatchingResults(List<PagePair> pagePairs) {
        int matched = 0;
        int baseOnly = 0;
        int compareOnly = 0;

        for (PagePair pair : pagePairs) {
            if (pair.getBaseFingerprint() != null && pair.getCompareFingerprint() != null) {
                matched++;
            } else if (pair.getBaseFingerprint() != null) {
                baseOnly++;
            } else if (pair.getCompareFingerprint() != null) {
                compareOnly++;
            }
        }

        logger.info("Matching results: {} matched pairs, {} base-only pages, {} compare-only pages",
                matched, baseOnly, compareOnly);
    }

    /**
     * Helper class to store potential match information during matching
     */
    private static class PotentialMatch {
        final int baseIndex;
        final int compareIndex;
        final double similarity;

        PotentialMatch(int baseIndex, int compareIndex, double similarity) {
            this.baseIndex = baseIndex;
            this.compareIndex = compareIndex;
            this.similarity = similarity;
        }
    }
}
