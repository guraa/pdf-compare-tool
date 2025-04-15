package guraa.pdfcompare.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Summary of a page-level comparison between two PDF documents.
 * This class stores information about the matching between pages,
 * including the similarity scores and any differences found.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageLevelComparisonSummary {

    /**
     * Unique identifier for this comparison summary.
     */
    private String id;

    /**
     * The ID of the base document.
     */
    private String baseDocumentId;

    /**
     * The ID of the compare document.
     */
    private String compareDocumentId;

    /**
     * The total number of pages in the base document.
     */
    private int baseTotalPages;

    /**
     * The total number of pages in the compare document.
     */
    private int compareTotalPages;

    /**
     * The matching strategy used.
     */
    private String matchingStrategy;

    /**
     * The confidence level of the matching.
     */
    private double confidenceLevel;

    /**
     * The overall similarity score between the documents (0.0 to 1.0).
     */
    private double overallSimilarityScore;

    /**
     * The page pairs.
     */
    @Builder.Default
    private List<PagePair> pagePairs = new ArrayList<>();

    /**
     * Add a page pair to this summary.
     *
     * @param pagePair The page pair to add
     */
    public void addPagePair(PagePair pagePair) {
        pagePairs.add(pagePair);
    }

    /**
     * Calculate the overall similarity score.
     */
    public void calculateOverallSimilarityScore() {
        // Count the number of matched pages
        int matchedPages = (int) pagePairs.stream()
                .filter(PagePair::isMatched)
                .count();
        
        // Calculate the average similarity score for matched pages
        double averageSimilarity = pagePairs.stream()
                .filter(PagePair::isMatched)
                .mapToDouble(PagePair::getSimilarityScore)
                .average()
                .orElse(0.0);
        
        // Calculate the overall similarity score
        // This takes into account both the number of matched pages and their similarity
        int maxPages = Math.max(baseTotalPages, compareTotalPages);
        double matchRatio = (double) matchedPages / maxPages;
        
        overallSimilarityScore = matchRatio * averageSimilarity;
    }

    /**
     * Get the number of matched pages.
     *
     * @return The number of matched pages
     */
    public int getMatchedPageCount() {
        return (int) pagePairs.stream()
                .filter(PagePair::isMatched)
                .count();
    }

    /**
     * Get the number of unmatched pages.
     *
     * @return The number of unmatched pages
     */
    public int getUnmatchedPageCount() {
        return pagePairs.size() - getMatchedPageCount();
    }

    /**
     * Get the number of pages with differences.
     *
     * @return The number of pages with differences
     */
    public int getPagesWithDifferencesCount() {
        return (int) pagePairs.stream()
                .filter(PagePair::hasDifferences)
                .count();
    }

    /**
     * Get the total number of differences.
     *
     * @return The total number of differences
     */
    public int getTotalDifferencesCount() {
        return pagePairs.stream()
                .mapToInt(PagePair::getDifferenceCount)
                .sum();
    }

    /**
     * Get the number of differences by type.
     *
     * @param type The type of difference
     * @return The count of differences of the specified type
     */
    public int getDifferenceCountByType(String type) {
        return pagePairs.stream()
                .mapToInt(pagePair -> pagePair.getDifferenceCountByType(type))
                .sum();
    }

    /**
     * Get the number of differences by severity.
     *
     * @param severity The severity level
     * @return The count of differences with the specified severity
     */
    public int getDifferenceCountBySeverity(String severity) {
        return pagePairs.stream()
                .mapToInt(pagePair -> pagePair.getDifferenceCountBySeverity(severity))
                .sum();
    }

    /**
     * Get the number of pages with differences by type.
     *
     * @param type The type of difference
     * @return The count of pages with differences of the specified type
     */
    public int getPagesWithDifferencesCountByType(String type) {
        return (int) pagePairs.stream()
                .filter(pagePair -> pagePair.hasDifferencesOfType(type))
                .count();
    }

    /**
     * Get the number of pages with differences by severity.
     *
     * @param severity The severity level
     * @return The count of pages with differences of the specified severity
     */
    public int getPagesWithDifferencesCountBySeverity(String severity) {
        return (int) pagePairs.stream()
                .filter(pagePair -> pagePair.hasDifferencesOfSeverity(severity))
                .count();
    }

    /**
     * Get a map of difference counts by type.
     *
     * @return A map of difference types to counts
     */
    public Map<String, Integer> getDifferenceCountsByType() {
        Map<String, Integer> counts = new HashMap<>();
        counts.put("text", getDifferenceCountByType("text"));
        counts.put("image", getDifferenceCountByType("image"));
        counts.put("font", getDifferenceCountByType("font"));
        counts.put("style", getDifferenceCountByType("style"));
        counts.put("metadata", getDifferenceCountByType("metadata"));
        return counts;
    }

    /**
     * Get a map of difference counts by severity.
     *
     * @return A map of severity levels to counts
     */
    public Map<String, Integer> getDifferenceCountsBySeverity() {
        Map<String, Integer> counts = new HashMap<>();
        counts.put("major", getDifferenceCountBySeverity("major"));
        counts.put("minor", getDifferenceCountBySeverity("minor"));
        counts.put("cosmetic", getDifferenceCountBySeverity("cosmetic"));
        return counts;
    }

    /**
     * Get a list of page pairs with differences.
     *
     * @return A list of page pairs with differences
     */
    public List<PagePair> getPagePairsWithDifferences() {
        return pagePairs.stream()
                .filter(PagePair::hasDifferences)
                .collect(Collectors.toList());
    }

    /**
     * Get a list of page pairs with differences of the specified type.
     *
     * @param type The type of difference
     * @return A list of page pairs with differences of the specified type
     */
    public List<PagePair> getPagePairsWithDifferencesOfType(String type) {
        return pagePairs.stream()
                .filter(pagePair -> pagePair.hasDifferencesOfType(type))
                .collect(Collectors.toList());
    }

    /**
     * Get a list of page pairs with differences of the specified severity.
     *
     * @param severity The severity level
     * @return A list of page pairs with differences of the specified severity
     */
    public List<PagePair> getPagePairsWithDifferencesOfSeverity(String severity) {
        return pagePairs.stream()
                .filter(pagePair -> pagePair.hasDifferencesOfSeverity(severity))
                .collect(Collectors.toList());
    }
}
