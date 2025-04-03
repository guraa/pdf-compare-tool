package guraa.pdfcompare.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DocumentPair {

    private int pairIndex;
    private boolean matched;

    // Page ranges for the matched documents
    private int baseStartPage;
    private int baseEndPage;
    private int basePageCount;

    private int compareStartPage;
    private int compareEndPage;
    private int comparePageCount;

    // If only exists in one document
    private boolean hasBaseDocument;
    private boolean hasCompareDocument;

    // Similarity score between 0.0 and 1.0
    private double similarityScore;

    // Difference counts
    private int totalDifferences;
    private int textDifferences;
    private int imageDifferences;
    private int fontDifferences;
    private int styleDifferences;

    // Result path for this document pair
    private String resultFilePath;

    // Store page-to-page mappings for the matched documents
    @Builder.Default
    private List<PageMapping> pageMappings = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PageMapping {
        private int basePageNumber;
        private int comparePageNumber;
        private double similarityScore;
        private int differenceCount;
    }
}