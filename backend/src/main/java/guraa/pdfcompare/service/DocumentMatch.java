package guraa.pdfcompare.service;

import lombok.Getter;
import lombok.ToString;

/**
 * Represents a match between two documents across different PDFs.
 * Contains information about matched documents and their similarity score.
 */
@Getter
@ToString
public class DocumentMatch {
    private final int baseDocumentIndex;
    private final int compareDocumentIndex;
    private final double similarityScore;

    /**
     * Constructor for a document match.
     *
     * @param baseDocumentIndex Index of the document in the base PDF
     * @param compareDocumentIndex Index of the document in the comparison PDF
     * @param similarityScore Similarity score between the documents (0.0 to 1.0)
     */
    public DocumentMatch(int baseDocumentIndex, int compareDocumentIndex, double similarityScore) {
        this.baseDocumentIndex = baseDocumentIndex;
        this.compareDocumentIndex = compareDocumentIndex;
        this.similarityScore = similarityScore;
    }

    /**
     * Check if this match has a high similarity score.
     *
     * @param threshold The threshold for considering a match as "high similarity"
     * @return true if the similarity score exceeds the threshold, false otherwise
     */
    public boolean isHighSimilarity(double threshold) {
        return similarityScore >= threshold;
    }

    /**
     * Compare this match to another match based on similarity score.
     *
     * @param other The other match to compare to
     * @return A negative integer, zero, or a positive integer as this match's
     *         similarity is less than, equal to, or greater than the other's
     */
    public int compareTo(DocumentMatch other) {
        return Double.compare(other.similarityScore, this.similarityScore);
    }
}