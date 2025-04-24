package guraa.pdfcompare.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentPair {
    private int pairIndex;
    private boolean matched;

    // Document IDs
    private String baseDocumentId;
    private String compareDocumentId;

    // Page ranges
    private int baseStartPage;
    private int baseEndPage;
    private int basePageCount;

    private int compareStartPage;
    private int compareEndPage;
    private int comparePageCount;

    // Document existence flags
    private boolean hasBaseDocument;
    private boolean hasCompareDocument;

    // Comparison metadata
    private Double similarityScore;
    private Integer totalDifferences;
}