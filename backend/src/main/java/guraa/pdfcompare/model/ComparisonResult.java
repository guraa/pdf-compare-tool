package guraa.pdfcompare.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import guraa.pdfcompare.model.difference.Difference;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ComparisonResult {

    private String id;
    private String mode; // "standard" or "smart"

    private String baseFileId;
    private String baseFileName;
    private int basePageCount;

    private String compareFileId;
    private String compareFileName;
    private int comparePageCount;

    private boolean pageCountDifferent;
    private int totalDifferences;
    private int totalTextDifferences;
    private int totalImageDifferences;
    private int totalFontDifferences;
    private int totalStyleDifferences;

    private Map<String, Object> metadataDifferences = new HashMap<>();

    @Builder.Default
    private List<PageDifference> pageDifferences = new ArrayList<>();

    @Builder.Default
    private List<DocumentPair> documentPairs = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    @JsonIgnore
    private String resultFilePath;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PageDifference {
        private int pageNumber;
        private boolean onlyInBase;
        private boolean onlyInCompare;
        private boolean dimensionsDifferent;
        private TextDifferences textDifferences;
        private List<Difference> textElementDifferences;
        private List<Difference> imageDifferences;
        private List<Difference> fontDifferences;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TextDifferences {
        private String baseText;
        private String compareText;
        private List<Difference> differences;
    }
}