package guraa.pdfcompare.controller;

import guraa.pdfcompare.repository.ComparisonRepository;
import guraa.pdfcompare.repository.PdfRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller for health and status checks.
 */
@Slf4j
@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final PdfRepository pdfRepository;
    private final ComparisonRepository comparisonRepository;

    /**
     * Basic health check endpoint.
     *
     * @return Simple health status
     */
    @GetMapping
    public ResponseEntity<Map<String, String>> healthCheck() {
        Map<String, String> status = new HashMap<>();
        status.put("status", "UP");
        return ResponseEntity.ok(status);
    }

    /**
     * Detailed status check endpoint.
     *
     * @return Detailed application status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> detailedStatus() {
        Map<String, Object> status = new HashMap<>();

        try {
            // Application status
            status.put("status", "UP");

            // Database counts
            status.put("documentCount", pdfRepository.count());
            status.put("comparisonCount", comparisonRepository.count());

            // Directory information
            Map<String, Object> directories = new HashMap<>();

            File uploadsDir = new File("uploads");
            directories.put("uploads", getDirInfo(uploadsDir));

            File documentsDir = new File("uploads/documents");
            directories.put("documents", getDirInfo(documentsDir));

            File comparisonsDir = new File("uploads/comparisons");
            directories.put("comparisons", getDirInfo(comparisonsDir));

            File reportsDir = new File("uploads/reports");
            directories.put("reports", getDirInfo(reportsDir));

            File tempDir = new File("uploads/temp");
            directories.put("temp", getDirInfo(tempDir));

            status.put("directories", directories);

            // Runtime info
            Map<String, Object> runtime = new HashMap<>();
            runtime.put("maxMemory", Runtime.getRuntime().maxMemory() / (1024 * 1024) + " MB");
            runtime.put("totalMemory", Runtime.getRuntime().totalMemory() / (1024 * 1024) + " MB");
            runtime.put("freeMemory", Runtime.getRuntime().freeMemory() / (1024 * 1024) + " MB");
            runtime.put("processors", Runtime.getRuntime().availableProcessors());

            status.put("runtime", runtime);

            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Error getting detailed status", e);

            status.put("status", "ERROR");
            status.put("error", e.getMessage());

            return ResponseEntity.internalServerError().body(status);
        }
    }

    /**
     * Get directory information.
     *
     * @param dir The directory
     * @return Map with directory information
     */
    private Map<String, Object> getDirInfo(File dir) {
        Map<String, Object> info = new HashMap<>();

        info.put("exists", dir.exists());

        if (dir.exists()) {
            info.put("size", getDirSize(dir) / (1024 * 1024) + " MB");
            info.put("fileCount", countFiles(dir));
            info.put("lastModified", new java.util.Date(dir.lastModified()));
        }

        return info;
    }

    /**
     * Calculate directory size recursively.
     *
     * @param dir The directory
     * @return Size in bytes
     */
    private long getDirSize(File dir) {
        long size = 0;

        if (dir.isFile()) {
            return dir.length();
        }

        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    size += file.length();
                } else {
                    size += getDirSize(file);
                }
            }
        }

        return size;
    }

    /**
     * Count files in a directory recursively.
     *
     * @param dir The directory
     * @return Number of files
     */
    private int countFiles(File dir) {
        int count = 0;

        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    count++;
                } else {
                    count += countFiles(file);
                }
            }
        }

        return count;
    }
}