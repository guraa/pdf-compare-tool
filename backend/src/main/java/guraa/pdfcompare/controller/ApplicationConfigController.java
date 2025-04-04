package guraa.pdfcompare.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/config")
public class ApplicationConfigController {

    @Value("${app.version:1.0.0}")
    private String appVersion;

    @Value("${app.comparison.smart-matching-enabled:true}")
    private boolean smartMatchingEnabled;

    @Value("${app.comparison.default-difference-threshold:normal}")
    private String defaultDifferenceThreshold;

    @Value("${spring.servlet.multipart.max-file-size:100MB}")
    private String maxFileSize;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getApplicationConfig() {
        Map<String, Object> config = new HashMap<>();

        config.put("version", appVersion);
        config.put("smartMatchingEnabled", smartMatchingEnabled);
        config.put("defaultDifferenceThreshold", defaultDifferenceThreshold);
        config.put("maxFileSize", maxFileSize);

        // Add any other configuration details your frontend might need
        config.put("supportedComparisonModes", new String[]{"standard", "smart"});
        config.put("supportedDifferenceThresholds", new String[]{"low", "normal", "high"});

        return ResponseEntity.ok(config);
    }

    @GetMapping("/features")
    public ResponseEntity<Map<String, Boolean>> getFeatureFlags() {
        Map<String, Boolean> features = new HashMap<>();

        features.put("smartMatching", smartMatchingEnabled);
        features.put("metadataComparison", true);
        features.put("imageComparison", true);
        features.put("textComparison", true);
        features.put("fontComparison", true);
        features.put("styleComparison", true);

        return ResponseEntity.ok(features);
    }

    @GetMapping("/comparison-options")
    public ResponseEntity<Map<String, Object>> getComparisonOptions() {
        Map<String, Object> options = new HashMap<>();

        options.put("textComparisonMethods", new String[]{"exact", "smart", "fuzzy"});
        options.put("differenceThresholds", new String[]{"low", "normal", "high"});
        options.put("comparisonModes", new String[]{"standard", "smart"});

        return ResponseEntity.ok(options);
    }
}