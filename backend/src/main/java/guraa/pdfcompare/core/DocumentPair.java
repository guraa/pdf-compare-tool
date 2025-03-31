package guraa.pdfcompare.core;

/**
 * Represents a pair of matched documents across two PDFs
 */
public class DocumentPair {
    private final int baseStartPage;
    private final int baseEndPage;
    private final int compareStartPage;
    private final int compareEndPage;
    private final double similarityScore;

    /**
     * Constructor for DocumentPair
     * @param baseStartPage Starting page in the base document
     * @param baseEndPage Ending page in the base document
     * @param compareStartPage Starting page in the comparison document
     * @param compareEndPage Ending page in the comparison document
     * @param similarityScore Similarity score between the documents
     */
    public DocumentPair(
            int baseStartPage,
            int baseEndPage,
            int compareStartPage,
            int compareEndPage,
            double similarityScore) {
        this.baseStartPage = baseStartPage;
        this.baseEndPage = baseEndPage;
        this.compareStartPage = compareStartPage;
        this.compareEndPage = compareEndPage;
        this.similarityScore = similarityScore;
    }

    /**
     * Check if this pair represents a matched document
     * @return true if both base and compare documents are present
     */
    public boolean isMatched() {
        return hasBaseDocument() && hasCompareDocument();
    }

    /**
     * Check if base document is present
     * @return true if base document pages are valid
     */
    public boolean hasBaseDocument() {
        return baseStartPage >= 0 && baseEndPage >= 0;
    }

    /**
     * Check if compare document is present
     * @return true if compare document pages are valid
     */
    public boolean hasCompareDocument() {
        return compareStartPage >= 0 && compareEndPage >= 0;
    }

    // Getters
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

    @Override
    public String toString() {
        return String.format(
                "DocumentPair{basePages=%d-%d, comparePages=%d-%d, similarity=%.2f, matched=%b}",
                baseStartPage, baseEndPage,
                compareStartPage, compareEndPage,
                similarityScore,
                isMatched()
        );
    }
}