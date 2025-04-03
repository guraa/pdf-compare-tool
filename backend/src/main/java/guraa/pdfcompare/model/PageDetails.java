package guraa.pdfcompare.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import guraa.pdfcompare.model.difference.Difference;
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
public class PageDetails {

    private int pageNumber;
    private String pageId;

    // Lists of differences on this page
    @Builder.Default
    private List<Difference> baseDifferences = new ArrayList<>();

    @Builder.Default
    private List<Difference> compareDifferences = new ArrayList<>();

    // Page dimensions
    private double baseWidth;
    private double baseHeight;
    private double compareWidth;
    private double compareHeight;

    // Page existence
    private boolean pageExistsInBase;
    private boolean pageExistsInCompare;

    // Content summary
    private int textDifferenceCount;
    private int imageDifferenceCount;
    private int fontDifferenceCount;
    private int styleDifferenceCount;

    // Text extraction for this page
    private String baseExtractedText;
    private String compareExtractedText;

    // Rendered page images paths (for API to serve)
    private String baseRenderedImagePath;
    private String compareRenderedImagePath;
}