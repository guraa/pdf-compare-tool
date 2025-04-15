package guraa.pdfcompare.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a pair of pages from two PDF documents.
 * This class stores information about the matching between pages,
 * including the similarity score and any differences found.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagePair {

    /**
     * Unique identifier for this page pair.
     */
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    /**
     * The ID of the base document.
     */
    private String baseDocumentId;

    /**
     * The ID of the compare document.
     */
    private String compareDocumentId;

    /**
     * The page number in the base document (1-based).
     */
    private int basePageNumber;

    /**
     * The page number in the compare document (1-based).
     */
    private int comparePageNumber;

    /**
     * Whether the pages are matched.
     */
    private boolean matched;

    /**
     * The similarity score between the pages (0.0 to 1.0).
     */
    private double similarityScore;

    /**
     * The differences found between the pages.
     */
    @Builder.Default
    private List<PageDifference> differences = new ArrayList<>();

    /**
     * Add a difference to this page pair.
     *
     * @param difference The difference to add
     */
    public void addDifference(PageDifference difference) {
        differences.add(difference);
    }

    /**
     * Get the number of differences.
     *
     * @return The number of differences
     */
    public int getDifferenceCount() {
        return differences.size();
    }

    /**
     * Get the number of differences by type.
     *
     * @param type The type of difference
     * @return The count of differences of the specified type
     */
    public int getDifferenceCountByType(String type) {
        return (int) differences.stream()
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
        return (int) differences.stream()
                .filter(diff -> severity.equals(diff.getSeverity()))
                .count();
    }

    /**
     * Check if this page pair has any differences.
     *
     * @return true if this page pair has differences, false otherwise
     */
    public boolean hasDifferences() {
        return !differences.isEmpty();
    }

    /**
     * Check if this page pair has any differences of the specified type.
     *
     * @param type The type of difference
     * @return true if this page pair has differences of the specified type, false otherwise
     */
    public boolean hasDifferencesOfType(String type) {
        return getDifferenceCountByType(type) > 0;
    }

    /**
     * Check if this page pair has any differences of the specified severity.
     *
     * @param severity The severity level
     * @return true if this page pair has differences of the specified severity, false otherwise
     */
    public boolean hasDifferencesOfSeverity(String severity) {
        return getDifferenceCountBySeverity(severity) > 0;
    }

    /**
     * Get the key for this page pair.
     *
     * @return The key
     */
    public String getKey() {
        return baseDocumentId + "_" + basePageNumber + "_" +
                compareDocumentId + "_" + comparePageNumber;
    }
}
