package guraa.pdfcompare.service;

import guraa.pdfcompare.model.Comparison;
import guraa.pdfcompare.model.ComparisonResult;
import guraa.pdfcompare.model.PdfDocument;
import guraa.pdfcompare.repository.ComparisonRepository;
import guraa.pdfcompare.repository.PdfRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * Service for generating comparison reports.
 * This service provides methods for generating HTML and PDF reports
 * for comparisons between PDF documents.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportGenerationService {

    private final ComparisonRepository comparisonRepository;
    private final PdfRepository pdfRepository;
    private final TemplateEngine templateEngine;
    private final ExecutorService executorService;

    @Value("${app.reports.directory:reports}")
    private String reportsDirectory;

    @Value("${app.reports.template:comparison-report}")
    private String reportTemplate;

    // Cache of report generation tasks
    private final ConcurrentHashMap<String, CompletableFuture<String>> reportTasks = new ConcurrentHashMap<>();

    /**
     * Generate a report for a comparison.
     *
     * @param comparisonId The comparison ID
     * @return The path to the generated report
     * @throws IOException If there is an error generating the report
     */
    public String generateReport(String comparisonId) throws IOException {
        // Check if we already have a report generation task for this comparison
        return reportTasks.computeIfAbsent(comparisonId, key -> {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return doGenerateReport(comparisonId);
                } catch (IOException e) {
                    log.error("Error generating report: {}", e.getMessage(), e);
                    throw new RuntimeException("Error generating report", e);
                }
            }, executorService);
        }).join(); // Wait for the task to complete
    }

    /**
     * Perform the actual report generation.
     *
     * @param comparisonId The comparison ID
     * @return The path to the generated report
     * @throws IOException If there is an error generating the report
     */
    private String doGenerateReport(String comparisonId) throws IOException {
        // Get the comparison
        Comparison comparison = comparisonRepository.findById(comparisonId)
                .orElseThrow(() -> new IllegalArgumentException("Comparison not found: " + comparisonId));
        
        // Get the documents
        PdfDocument baseDocument = pdfRepository.findById(comparison.getBaseDocumentId())
                .orElseThrow(() -> new IllegalArgumentException("Base document not found: " + comparison.getBaseDocumentId()));
        
        PdfDocument compareDocument = pdfRepository.findById(comparison.getCompareDocumentId())
                .orElseThrow(() -> new IllegalArgumentException("Compare document not found: " + comparison.getCompareDocumentId()));
        
        // Get the comparison result
        ComparisonResult result = comparison.getResult();
        if (result == null) {
            throw new IllegalStateException("Comparison result not available");
        }
        
        // Create the reports directory if it doesn't exist
        Path reportsDir = Paths.get(reportsDirectory);
        if (!Files.exists(reportsDir)) {
            Files.createDirectories(reportsDir);
        }
        
        // Generate a unique filename for the report
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String reportFilename = "report_" + comparisonId + "_" + timestamp + ".html";
        Path reportPath = reportsDir.resolve(reportFilename);
        
        // Create the Thymeleaf context
        Context context = new Context();
        context.setVariable("comparison", comparison);
        context.setVariable("baseDocument", baseDocument);
        context.setVariable("compareDocument", compareDocument);
        context.setVariable("result", result);
        context.setVariable("timestamp", timestamp);
        
        // Process the template
        String reportContent = templateEngine.process(reportTemplate, context);
        
        // Write the report to a file
        try (FileWriter writer = new FileWriter(reportPath.toFile())) {
            writer.write(reportContent);
        }
        
        return reportPath.toString();
    }

    /**
     * Get the path to a generated report.
     *
     * @param comparisonId The comparison ID
     * @return The path to the generated report, or null if no report exists
     */
    public String getReportPath(String comparisonId) {
        // Check if a report exists for this comparison
        Path reportsDir = Paths.get(reportsDirectory);
        if (!Files.exists(reportsDir)) {
            return null;
        }
        
        // Find the most recent report for this comparison
        try {
            Optional<Path> reportPath = Files.list(reportsDir)
                    .filter(path -> path.getFileName().toString().startsWith("report_" + comparisonId + "_"))
                    .sorted((p1, p2) -> -p1.getFileName().toString().compareTo(p2.getFileName().toString()))
                    .findFirst();
            
            return reportPath.map(Path::toString).orElse(null);
        } catch (IOException e) {
            log.error("Error finding report: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Delete all reports for a comparison.
     *
     * @param comparisonId The comparison ID
     * @throws IOException If there is an error deleting the reports
     */
    public void deleteReports(String comparisonId) throws IOException {
        // Check if reports exist for this comparison
        Path reportsDir = Paths.get(reportsDirectory);
        if (!Files.exists(reportsDir)) {
            return;
        }
        
        // Delete all reports for this comparison
        Files.list(reportsDir)
                .filter(path -> path.getFileName().toString().startsWith("report_" + comparisonId + "_"))
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        log.error("Error deleting report: {}", e.getMessage(), e);
                    }
                });
    }
}
