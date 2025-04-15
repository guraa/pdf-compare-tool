package guraa.pdfcompare.model;

import guraa.pdfcompare.model.difference.Difference;
import guraa.pdfcompare.service.PageLevelComparisonSummary;
import guraa.pdfcompare.service.PagePair;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Result of a PDF comparison.
 * This class contains all the information about the comparison,
 * including the matched pages and the differences found.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComparisonResult {

    /**
     * Unique identifier for this comparison result.
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
     * The list of page pairs.
     */
    private List<PagePair> pagePairs;

    /**
     * The comparison summary.
     */
    private PageLevelComparisonSummary summary;

    /**
     * The differences by page.
     */
    private Map<String, List<Difference>> differencesByPage;

    /**
     * Get the total number of differences.
     *
     * @return The total number of differences
     */
    public int getTotalDifferences() {
        return differencesByPage.values().stream()
                .mapToInt(List::size)
                .sum();
    }

    /**
     * Get the number of differences by type.
     *
     * @param type The type of difference
     * @return The count of differences of the specified type
     */
    public int getDifferenceCountByType(String type) {
        return (int) differencesByPage.values().stream()
                .flatMap(List::stream)
                .filter(diff -> type.equals(diff.getType()))
                .count();
    }

    /**
     * Get the number of differences by severity.
     *
     * @param severity The severity level
     * @return The count of differences with the specified severity
     */
    public int getDifferenceCountBySeverity(String severity) {
        return (int) differencesByPage.values().stream()
                .flatMap(List::stream)
                .filter(diff -> severity.equals(diff.getSeverity()))
                .count();
    }

    /**
     * Get the number of differences by change type.
     *
     * @param changeType The change type
     * @return The count of differences with the specified change type
     */
    public int getDifferenceCountByChangeType(String changeType) {
        return (int) differencesByPage.values().stream()
                .flatMap(List::stream)
                .filter(diff -> changeType.equals(diff.getChangeType()))
                .count();
    }

    /**
     * Get the number of differences by page.
     *
     * @param pageId The page ID
     * @return The count of differences on the specified page
     */
    public int getDifferenceCountByPage(String pageId) {
        List<Difference> differences = differencesByPage.get(pageId);
        return differences != null ? differences.size() : 0;
    }

    /**
     * Get the overall similarity score.
     *
     * @return The overall similarity score
     */
    public double getOverallSimilarityScore() {
        return summary != null ? summary.getOverallSimilarityScore() : 0.0;
    }

    /**
     * Check if the documents are identical.
     *
     * @return true if the documents are identical, false otherwise
     */
    public boolean areDocumentsIdentical() {
        return getTotalDifferences() == 0;
    }

    /**
     * Check if the documents are similar.
     *
     * @param threshold The similarity threshold
     * @return true if the documents are similar, false otherwise
     */
    public boolean areDocumentsSimilar(double threshold) {
        return getOverallSimilarityScore() >= threshold;
    }
}
