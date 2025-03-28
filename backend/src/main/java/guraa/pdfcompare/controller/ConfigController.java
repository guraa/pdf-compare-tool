package guraa.pdfcompare.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for application configuration endpoints
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    /**
     * Get application configuration settings
     * @return Application configuration
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAppConfiguration() {
        Map<String, Object> config = new HashMap<>();

        // Add application settings
        config.put("version", "1.0.0");
        config.put("maxFileSize", 50 * 1024 * 1024); // 50MB in bytes
        config.put("allowedFileTypes", new String[]{"application/pdf"});

        // Add comparison settings
        Map<String, Object> comparisonSettings = new HashMap<>();
        comparisonSettings.put("defaultTextComparisonMethod", "smart");
        comparisonSettings.put("defaultDifferenceThreshold", "normal");
        comparisonSettings.put("supportedExportFormats", new String[]{"pdf", "html", "json"});

        config.put("comparisonSettings", comparisonSettings);

        return ResponseEntity.ok(config);
    }
}