package guraa.pdfcompare.service;

import guraa.pdfcompare.model.Comparison;
import guraa.pdfcompare.model.ComparisonResult;
import guraa.pdfcompare.model.PdfDocument;
import guraa.pdfcompare.repository.ComparisonRepository;
import guraa.pdfcompare.repository.PdfRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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

    @Value("${app.reports.directory:uploads/reports}")
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
        return generateReportInternal(comparisonId, "html", new HashMap<>());
    }

    /**
     * Generate a report for a comparison with a specific format and options.
     *
     * @param comparisonId The comparison ID
     * @param format The format of the report (html, pdf, json)
     * @param options Additional options for report generation
     * @return The path to the generated report
     * @throws IOException If there is an error generating the report
     */
    public String generateReportInternal(String comparisonId, String format, Map<String, Object> options) throws IOException {
        // Check if we already have a report generation task for this comparison
        String taskKey = comparisonId + "_" + format + "_" + options.hashCode();
        return reportTasks.computeIfAbsent(taskKey, key -> {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return doGenerateReport(comparisonId, format, options);
                } catch (IOException e) {
                    log.error("Error generating report: {}", e.getMessage(), e);
                    throw new RuntimeException("Error generating report", e);
                }
            }, executorService);
        }).join(); // Wait for the task to complete
    }

    /**
     * Generate a report for a comparison and return it as a Resource.
     *
     * @param comparisonId The comparison ID
     * @param format The format of the report (html, pdf, json)
     * @param options Additional options for report generation
     * @return The generated report as a Resource
     * @throws IOException If there is an error generating the report
     */
    public Resource generateReport(String comparisonId, String format, Map<String, Object> options) throws IOException {
        String reportPath = generateReportInternal(comparisonId, format, options);
        if (reportPath == null) {
            return null;
        }
        return new FileSystemResource(reportPath);
    }

    /**
     * Perform the actual report generation.
     *
     * @param comparisonId The comparison ID
     * @param format The format of the report
     * @param options Additional options for report generation
     * @return The path to the generated report
     * @throws IOException If there is an error generating the report
     */
    private String doGenerateReport(String comparisonId, String format, Map<String, Object> options) throws IOException {
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
        String fileExtension = getFileExtensionForFormat(format);
        String reportFilename = "report_" + comparisonId + "_" + timestamp + fileExtension;
        Path reportPath = reportsDir.resolve(reportFilename);

        // Generate the report based on the requested format
        switch (format.toLowerCase()) {
            case "html":
                generateHtmlReport(reportPath, comparison, baseDocument, compareDocument, result, options);
                break;
            case "pdf":
                generatePdfReport(reportPath, comparison, baseDocument, compareDocument, result, options);
                break;
            case "json":
                generateJsonReport(reportPath, comparison, baseDocument, compareDocument, result, options);
                break;
            default:
                throw new IllegalArgumentException("Unsupported format: " + format);
        }

        return reportPath.toString();
    }

    /**
     * Generate an HTML report.
     *
     * @param reportPath The path to save the report
     * @param comparison The comparison
     * @param baseDocument The base document
     * @param compareDocument The compare document
     * @param result The comparison result
     * @param options Additional options for report generation
     * @throws IOException If there is an error generating the report
     */
    private void generateHtmlReport(Path reportPath, Comparison comparison, PdfDocument baseDocument,
                                    PdfDocument compareDocument, ComparisonResult result,
                                    Map<String, Object> options) throws IOException {
        // Create the Thymeleaf context
        Context context = new Context();
        context.setVariable("comparison", comparison);
        context.setVariable("baseDocument", baseDocument);
        context.setVariable("compareDocument", compareDocument);
        context.setVariable("result", result);
        context.setVariable("timestamp", LocalDateTime.now());
        context.setVariable("options", options);

        // Process the template
        String templateName = options.getOrDefault("template", reportTemplate).toString();
        String reportContent = templateEngine.process(templateName, context);

        // Write the report to a file
        try (FileWriter writer = new FileWriter(reportPath.toFile())) {
            writer.write(reportContent);
        }
    }

    /**
     * Generate a PDF report.
     *
     * @param reportPath The path to save the report
     * @param comparison The comparison
     * @param baseDocument The base document
     * @param compareDocument The compare document
     * @param result The comparison result
     * @param options Additional options for report generation
     * @throws IOException If there is an error generating the report
     */
    private void generatePdfReport(Path reportPath, Comparison comparison, PdfDocument baseDocument,
                                   PdfDocument compareDocument, ComparisonResult result,
                                   Map<String, Object> options) throws IOException {
        // First generate an HTML report
        Path htmlReportPath = Paths.get(reportPath.toString().replace(".pdf", ".html"));
        generateHtmlReport(htmlReportPath, comparison, baseDocument, compareDocument, result, options);

        // In a real implementation, this would convert the HTML to PDF using a library like Flying Saucer or iText
        // For now, we'll just copy the HTML file and rename it to .pdf
        Files.copy(htmlReportPath, reportPath);
        Files.delete(htmlReportPath);
    }

    /**
     * Generate a JSON report.
     *
     * @param reportPath The path to save the report
     * @param comparison The comparison
     * @param baseDocument The base document
     * @param compareDocument The compare document
     * @param result The comparison result
     * @param options Additional options for report generation
     * @throws IOException If there is an error generating the report
     */
    private void generateJsonReport(Path reportPath, Comparison comparison, PdfDocument baseDocument,
                                    PdfDocument compareDocument, ComparisonResult result,
                                    Map<String, Object> options) throws IOException {
        // In a real implementation, this would convert the comparison result to JSON
        // For now, we'll just create a simple JSON file
        Map<String, Object> jsonData = new HashMap<>();
        jsonData.put("comparisonId", comparison.getId());
        jsonData.put("baseDocumentId", comparison.getBaseDocumentId());
        jsonData.put("compareDocumentId", comparison.getCompareDocumentId());
        jsonData.put("status", comparison.getStatusAsString());
        jsonData.put("timestamp", LocalDateTime.now().toString());

        // Convert to JSON string (simplified)
        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{\n");
        for (Map.Entry<String, Object> entry : jsonData.entrySet()) {
            jsonBuilder.append("  \"").append(entry.getKey()).append("\": \"")
                    .append(entry.getValue()).append("\",\n");
        }
        // Remove the last comma and close the JSON object
        String jsonString = jsonBuilder.substring(0, jsonBuilder.length() - 2) + "\n}";

        // Write the JSON to a file
        Files.write(reportPath, jsonString.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Get the file extension for a report format.
     *
     * @param format The report format
     * @return The file extension
     */
    private String getFileExtensionForFormat(String format) {
        switch (format.toLowerCase()) {
            case "html":
                return ".html";
            case "pdf":
                return ".pdf";
            case "json":
                return ".json";
            default:
                return ".txt";
        }
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