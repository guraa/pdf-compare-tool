package guraa.pdfcompare.service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Utility class for calculating text similarity measures.
 * Provides various algorithms for comparing text content.
 */
public class TextSimilarityUtils {

    /**
     * Calculate Jaccard similarity between two texts.
     * Jaccard similarity is defined as the size of the intersection
     * divided by the size of the union of the sample sets.
     *
     * @param text1 First text
     * @param text2 Second text
     * @return Similarity score between 0.0 and 1.0
     */
    public static double calculateTextSimilarity(String text1, String text2) {
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
     * Calculate cosine similarity between two texts.
     * Cosine similarity measures the cosine of the angle between two vectors.
     *
     * @param text1 First text
     * @param text2 Second text
     * @return Similarity score between 0.0 and 1.0
     */
    public static double calculateCosineSimilarity(String text1, String text2) {
        if (text1 == null || text2 == null || text1.isEmpty() || text2.isEmpty()) {
            return 0.0;
        }

        // Normalize texts and split into words
        String[] words1 = text1.replaceAll("\\s+", " ").trim().toLowerCase().split(" ");
        String[] words2 = text2.replaceAll("\\s+", " ").trim().toLowerCase().split(" ");

        // Create a set of all unique words
        Set<String> uniqueWords = new HashSet<>();
        for (String word : words1) uniqueWords.add(word);
        for (String word : words2) uniqueWords.add(word);

        // Create term frequency vectors
        double[] vector1 = new double[uniqueWords.size()];
        double[] vector2 = new double[uniqueWords.size()];

        // Fill the vectors
        int i = 0;
        for (String word : uniqueWords) {
            vector1[i] = countOccurrences(words1, word);
            vector2[i] = countOccurrences(words2, word);
            i++;
        }

        // Calculate cosine similarity
        return cosine(vector1, vector2);
    }

    /**
     * Calculate Levenshtein (edit) distance between two strings.
     * The Levenshtein distance is the minimum number of single-character edits
     * (insertions, deletions, or substitutions) required to change one string into the other.
     *
     * @param s1 First string
     * @param s2 Second string
     * @return The edit distance
     */
    public static int levenshteinDistance(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return s1 == s2 ? 0 : (s1 == null ? s2.length() : s1.length());
        }

        int[] prev = new int[s2.length() + 1];
        int[] curr = new int[s2.length() + 1];

        // Initialize the previous row
        for (int j = 0; j <= s2.length(); j++) {
            prev[j] = j;
        }

        for (int i = 0; i < s1.length(); i++) {
            curr[0] = i + 1;

            for (int j = 0; j < s2.length(); j++) {
                int cost = (s1.charAt(i) == s2.charAt(j)) ? 0 : 1;
                curr[j + 1] = Math.min(Math.min(curr[j] + 1, prev[j + 1] + 1), prev[j] + cost);
            }

            // Swap arrays
            int[] temp = prev;
            prev = curr;
            curr = temp;
        }

        return prev[s2.length()];
    }

    /**
     * Calculate normalized Levenshtein similarity (0-1 range).
     *
     * @param s1 First string
     * @param s2 Second string
     * @return Similarity score between 0.0 and 1.0
     */
    public static double levenshteinSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return s1 == s2 ? 1.0 : 0.0;
        }

        int maxLength = Math.max(s1.length(), s2.length());
        if (maxLength == 0) return 1.0;

        int distance = levenshteinDistance(s1, s2);
        return 1.0 - ((double) distance / maxLength);
    }

    /**
     * Helper method to count occurrences of a word in an array.
     */
    private static int countOccurrences(String[] words, String targetWord) {
        int count = 0;
        for (String word : words) {
            if (word.equals(targetWord)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Calculate cosine between two vectors.
     */
    private static double cosine(double[] v1, double[] v2) {
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < v1.length; i++) {
            dotProduct += v1[i] * v2[i];
            norm1 += v1[i] * v1[i];
            norm2 += v2[i] * v2[i];
        }

        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
}