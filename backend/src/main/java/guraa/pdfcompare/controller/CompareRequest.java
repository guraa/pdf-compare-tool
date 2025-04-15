package guraa.pdfcompare.controller;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Request object for PDF comparisons.
 * This class represents the request to compare two PDF documents.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompareRequest {

    /**
     * The ID of the base document.
     */
    private String baseFileId;

    /**
     * The ID of the document to compare against the base.
     */
    private String compareFileId;

    /**
     * Additional options for the comparison.
     */
    @Builder.Default
    private Map<String, Object> options = new HashMap<>();

    /**
     * A name for the comparison (optional).
     */
    private String comparisonName;

    /**
     * Whether to use smart matching (optional).
     */
    @Builder.Default
    private boolean smartMatching = true;

    /**
     * The difference threshold (optional).
     * Possible values: "low", "normal", "high"
     */
    @Builder.Default
    private String differenceThreshold = "normal";

    /**
     * Whether to include metadata in the comparison (optional).
     */
    @Builder.Default
    private boolean includeMetadata = true;

    /**
     * Whether to include text in the comparison (optional).
     */
    @Builder.Default
    private boolean includeText = true;

    /**
     * Whether to include images in the comparison (optional).
     */
    @Builder.Default
    private boolean includeImages = true;

    /**
     * Whether to include fonts in the comparison (optional).
     */
    @Builder.Default
    private boolean includeFonts = true;

    /**
     * Whether to include styles in the comparison (optional).
     */
    @Builder.Default
    private boolean includeStyles = true;
}