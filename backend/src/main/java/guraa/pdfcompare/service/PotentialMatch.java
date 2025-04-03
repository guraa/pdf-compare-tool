package guraa.pdfcompare.service;

import lombok.Getter;
import lombok.ToString;

/**
 * Represents a potential match between two documents during the document matching process.
 * This class tracks the indices of the documents in their respective collections
 * along with a similarity score.
 */
@Getter
@ToString
public class PotentialMatch {
    private final int baseIndex;
    private final int compareIndex;
    private final double similarity;

    /**
     * Constructor for a potential match.
     *
     * @param baseIndex The index of the document in the base PDF
     * @param compareIndex The index of the document in the comparison PDF
     * @param similarity The similarity score between the documents (0.0 to 1.0)
     */
    public PotentialMatch(int baseIndex, int compareIndex, double similarity) {
        this.baseIndex = baseIndex;
        this.compareIndex = compareIndex;
        this.similarity = similarity;
    }

    /**
     * Compare this potential match to another based on similarity score.
     *
     * @param other The other potential match to compare to
     * @return A negative integer, zero, or a positive integer as this match's
     *         similarity is less than, equal to, or greater than the other's
     */
    public int compareTo(PotentialMatch other) {
        return Double.compare(other.similarity, this.similarity);
    }

    /**
     * Check if this match has sufficient similarity to be considered valid.
     *
     * @param threshold The minimum similarity threshold
     * @return true if the match's similarity exceeds the threshold, false otherwise
     */
    public boolean isAboveThreshold(double threshold) {
        return similarity >= threshold;
    }
}