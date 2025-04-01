package guraa.pdfcompare.core;

import java.util.HashSet;
import java.util.Set;
import java.util.Arrays;

/**
 * Utility class for calculating text similarity
 */
public class TextSimilarityCalculator {

    /**
     * Calculate Jaccard similarity between two text strings
     * Jaccard similarity = size of intersection / size of union
     */
    public static double calculateJaccardSimilarity(String text1, String text2) {
        // Handle null cases
        if (text1 == null || text2 == null) return 0.0;
        if (text1.isEmpty() || text2.isEmpty()) return 0.0;

        // Tokenize texts into word sets
        Set<String> set1 = new HashSet<>(Arrays.asList(text1.toLowerCase().split("\\W+")));
        Set<String> set2 = new HashSet<>(Arrays.asList(text2.toLowerCase().split("\\W+")));

        // Remove empty strings that might result from splitting
        set1.remove("");
        set2.remove("");

        // Calculate intersection and union
        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);

        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);

        // Calculate Jaccard similarity
        if (union.isEmpty()) return 0.0;
        return (double) intersection.size() / union.size();
    }

    /**
     * Calculate cosine similarity between two text strings
     * This is a more sophisticated measure that accounts for term frequency
     */
    public static double calculateCosineSimilarity(String text1, String text2) {
        // Handle null cases
        if (text1 == null || text2 == null) return 0.0;
        if (text1.isEmpty() || text2.isEmpty()) return 0.0;

        // Tokenize texts into word arrays
        String[] words1 = text1.toLowerCase().split("\\W+");
        String[] words2 = text2.toLowerCase().split("\\W+");

        // Get all unique words
        Set<String> uniqueWords = new HashSet<>();
        for (String word : words1) {
            if (!word.isEmpty()) uniqueWords.add(word);
        }
        for (String word : words2) {
            if (!word.isEmpty()) uniqueWords.add(word);
        }

        // Calculate term frequencies
        double[] vector1 = new double[uniqueWords.size()];
        double[] vector2 = new double[uniqueWords.size()];

        String[] uniqueWordsArray = uniqueWords.toArray(new String[0]);
        for (int i = 0; i < uniqueWordsArray.length; i++) {
            String word = uniqueWordsArray[i];
            vector1[i] = countOccurrences(words1, word);
            vector2[i] = countOccurrences(words2, word);
        }

        // Calculate cosine similarity
        return calculateCosineSimilarity(vector1, vector2);
    }

    /**
     * Count occurrences of a word in a word array
     */
    private static int countOccurrences(String[] words, String word) {
        int count = 0;
        for (String w : words) {
            if (w.equals(word)) count++;
        }
        return count;
    }

    /**
     * Calculate cosine similarity between two vectors
     */
    private static double calculateCosineSimilarity(double[] vector1, double[] vector2) {
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < vector1.length; i++) {
            dotProduct += vector1[i] * vector2[i];
            norm1 += vector1[i] * vector1[i];
            norm2 += vector2[i] * vector2[i];
        }

        if (norm1 == 0.0 || norm2 == 0.0) return 0.0;

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
}