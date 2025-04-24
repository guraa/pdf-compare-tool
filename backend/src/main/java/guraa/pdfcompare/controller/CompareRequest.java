package guraa.pdfcompare.controller;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompareRequest {
    private String baseFileId;
    private String compareFileId;

    @Builder.Default
    private Map<String, Object> options = new HashMap<>();

    @Builder.Default
    private boolean smartMatching = true;

    @Builder.Default
    private String differenceThreshold = "normal";

    @Builder.Default
    private boolean includeMetadata = true;

    @Builder.Default
    private boolean includeText = true;

    @Builder.Default
    private boolean includeImages = true;

    @Builder.Default
    private boolean includeFonts = true;

    @Builder.Default
    private boolean includeStyles = true;
}