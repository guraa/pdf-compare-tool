package guraa.pdfcompare.core;

import org.apache.commons.text.similarity.JaccardSimilarity;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.apache.commons.text.similarity.CosineSimilarity;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Advanced text similarity calculator with multiple comparison techniques
 */
public class TextSimilarityCalculator {
    private final JaccardSimilarity jaccardSimilarity = new JaccardSimilarity();
    private final LevenshteinDistance levenshteinDistance = LevenshteinDistance.getDefaultInstance();
    private final CosineSimilarity cosineSimilarity = new CosineSimilarity();

    /**
     * Calculate text similarity using multiple techniques
     *
     * @param text1 First text to compare
     * @param text2 Second text to compare
     * @return Similarity score between 0 and 1
     */
    public double calculateTextSimilarity(String text1, String text2) {
        if (text1 == null || text2 == null) return 0.0;

        // Normalize texts
        text1 = normalizeText(text1);
        text2 = normalizeText(text2);

        // Calculate different similarity metrics
        double jaccardSim = calculateJaccardSimilarity(text1, text2);
        double editDistanceSim = calculateEditDistanceSimilarity(text1, text2);
        double cosineSim = calculateCosineSimilarity(text1, text2);
        double wordOverlapSim = calculateWordOverlapSimilarity(text1, text2);

        // Weighted combination of similarities
        return (
                jaccardSim * 0.3 +
                        editDistanceSim * 0.2 +
                        cosineSim * 0.3 +
                        wordOverlapSim * 0.2
        );
    }

    /**
     * Normalize text for comparison
     */
    private String normalizeText(String text) {
        return text.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", "")
                .trim();
    }

    /**
     * Calculate Jaccard similarity
     */
    private double calculateJaccardSimilarity(String text1, String text2) {
        return jaccardSimilarity.apply(text1, text2);
    }

    /**
     * Calculate edit distance similarity
     */
    private double calculateEditDistanceSimilarity(String text1, String text2) {
        int distance = levenshteinDistance.apply(text1, text2);
        int maxLength = Math.max(text1.length(), text2.length());
        return maxLength > 0 ? 1.0 - (double) distance / maxLength : 1.0;
    }

    /**
     * Calculate cosine similarity using word frequencies
     */
    private double calculateCosineSimilarity(String text1, String text2) {
        Map<CharSequence, Integer> freq1 = calculateWordFrequency(text1);
        Map<CharSequence, Integer> freq2 = calculateWordFrequency(text2);

        try {
            return cosineSimilarity.cosineSimilarity(freq1, freq2);
        } catch (Exception e) {
            return 0.0;
        }
    }

    /**
     * Calculate word overlap similarity
     */
    private double calculateWordOverlapSimilarity(String text1, String text2) {
        Set<String> words1 = Arrays.stream(text1.split("\\s+"))
                .collect(Collectors.toSet());
        Set<String> words2 = Arrays.stream(text2.split("\\s+"))
                .collect(Collectors.toSet());

        // Calculate Sørensen–Dice coefficient
        Set<String> intersection = new HashSet<>(words1);
        intersection.retainAll(words2);

        return 2.0 * intersection.size() / (words1.size() + words2.size());
    }

    /**
     * Calculate word frequency map
     */
    private Map<CharSequence, Integer> calculateWordFrequency(String text) {
        return Arrays.stream(text.split("\\s+"))
                .filter(word -> word.length() > 1)  // Ignore very short words
                .collect(Collectors.toMap(
                        word -> (CharSequence) word,
                        word -> 1,
                        Integer::sum
                ));
    }
}