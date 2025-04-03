package guraa.pdfcompare.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import guraa.pdfcompare.model.Comparison;
import guraa.pdfcompare.model.ComparisonResult;
import guraa.pdfcompare.model.DocumentPair;
import guraa.pdfcompare.model.PageDetails;
import guraa.pdfcompare.model.PdfDocument;
import guraa.pdfcompare.model.difference.Difference;
import guraa.pdfcompare.model.difference.FontDifference;
import guraa.pdfcompare.model.difference.ImageDifference;
import guraa.pdfcompare.model.difference.MetadataDifference;
import guraa.pdfcompare.model.difference.StyleDifference;
import guraa.pdfcompare.model.difference.TextDifference;
import guraa.pdfcompare.repository.ComparisonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportGenerationService {

    private final ComparisonRepository comparisonRepository;
    private final ComparisonService comparisonService;
    private final ObjectMapper objectMapper;
    private final TemplateEngine templateEngine;

    /**
     * Generate a report of the comparison results.
     *
     * @param comparisonId The comparison ID
     * @param format The report format ("pdf", "html", "json")
     * @param options Additional options for report generation
     * @return The generated report as a Resource
     * @throws Exception If there's an error generating the report
     */
    public Resource generateReport(String comparisonId, String format, Map<String, Object> options) throws Exception {
        // Get comparison and result
        Comparison comparison = comparisonRepository.findByComparisonId(comparisonId)
                .orElseThrow(() -> new IllegalArgumentException("Comparison not found: " + comparisonId));

        ComparisonResult result = comparisonService.getComparisonResult(comparisonId);

        if (result == null) {
            throw new IllegalStateException("Comparison result not found for ID: " + comparisonId);
        }

        // Generate report based on format
        switch (format.toLowerCase()) {
            case "pdf":
                return generatePdfReport(comparison, result, options);
            case "html":
                return generateHtmlReport(comparison, result, options);
            case "json":
                return generateJsonReport(comparison, result, options);
            default:
                throw new IllegalArgumentException("Unsupported report format: " + format);
        }
    }

    /**
     * Generate a PDF report of the comparison results.
     *
     * @param comparison The comparison entity
     * @param result The comparison result
     * @param options Additional options for report generation
     * @return The generated PDF report as a Resource
     * @throws Exception If there's an error generating the report
     */
    private Resource generatePdfReport(Comparison comparison, ComparisonResult result, Map<String, Object> options) throws Exception {
        // Create a temporary file for the PDF report
        Path tempDir = Files.createTempDirectory("pdf-compare-reports");
        File reportFile = tempDir.resolve("report_" + UUID.randomUUID() + ".pdf").toFile();

        // Create PDF document
        Document document = new Document();
        PdfWriter.getInstance(document, new FileOutputStream(reportFile));

        document.open();

        // Add report header
        addReportHeader(document, comparison, result);

        // Add summary section
        addSummarySection(document, comparison, result);

        // Add document pairs section if in smart mode
        if (result.getMode() != null && result.getMode().equals("smart") && result.getDocumentPairs() != null) {
            addDocumentPairsSection(document, result);
        }

        // Add differences section
        addDifferencesSection(document, comparison, result, options);

        document.close();

        // Read the PDF file and return as a resource
        ByteArrayResource resource = new ByteArrayResource(Files.readAllBytes(reportFile.toPath()));

        // Clean up temporary file
        reportFile.delete();
        tempDir.toFile().delete();

        return resource;
    }

    /**
     * Add report header to the PDF document.
     *
     * @param document The PDF document
     * @param comparison The comparison entity
     * @param result The comparison result
     */
    private void addReportHeader(Document document, Comparison comparison, ComparisonResult result) throws Exception {
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Color.BLACK);
        Font subtitleFont = FontFactory.getFont(FontFactory.HELVETICA, 12, Color.DARK_GRAY);

        Paragraph title = new Paragraph("PDF Comparison Report", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(10);
        document.add(title);

        Paragraph dateTime = new Paragraph(
                "Generated on: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                subtitleFont);
        dateTime.setAlignment(Element.ALIGN_CENTER);
        dateTime.setSpacingAfter(20);
        document.add(dateTime);

        // Add document info
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);

        addTableCell(table, "Base Document:", result.getBaseFileName(), true);
        addTableCell(table, "Comparison Document:", result.getCompareFileName(), false);
        addTableCell(table, "Page Count:", result.getBasePageCount() + " / " + result.getComparePageCount(), true);
        addTableCell(table, "Total Differences:", String.valueOf(result.getTotalDifferences()), false);
        addTableCell(table, "Comparison Mode:", result.getMode(), true);

        document.add(table);
        document.add(new Paragraph(" ")); // Add spacing
    }

    /**
     * Add summary section to the PDF document.
     *
     * @param document The PDF document
     * @param comparison The comparison entity
     * @param result The comparison result
     */
    private void addSummarySection(Document document, Comparison comparison, ComparisonResult result) throws Exception {
        Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, Color.BLACK);

        Paragraph section = new Paragraph("Summary", sectionFont);
        section.setSpacingBefore(15);
        section.setSpacingAfter(10);
        document.add(section);

        // Create summary table
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);

        // Add difference counts by type
        addTableCell(table, "Text Differences:", String.valueOf(result.getTotalTextDifferences()), true);
        addTableCell(table, "Image Differences:", String.valueOf(result.getTotalImageDifferences()), false);
        addTableCell(table, "Font Differences:", String.valueOf(result.getTotalFontDifferences()), true);
        addTableCell(table, "Style Differences:", String.valueOf(result.getTotalStyleDifferences()), false);
        addTableCell(table, "Metadata Differences:", String.valueOf(result.getMetadataDifferences().size()), true);

        document.add(table);

        // Add metadata differences if any
        if (!result.getMetadataDifferences().isEmpty()) {
            Font subsectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.BLACK);

            Paragraph subsection = new Paragraph("Metadata Differences", subsectionFont);
            subsection.setSpacingBefore(10);
            subsection.setSpacingAfter(5);
            document.add(subsection);

            PdfPTable metadataTable = new PdfPTable(3);
            metadataTable.setWidthPercentage(100);

            // Add header row
            PdfPCell keyHeader = new PdfPCell(new Phrase("Property"));
            keyHeader.setBackgroundColor(Color.LIGHT_GRAY);
            metadataTable.addCell(keyHeader);

            PdfPCell baseHeader = new PdfPCell(new Phrase("Base Document"));
            baseHeader.setBackgroundColor(Color.LIGHT_GRAY);
            metadataTable.addCell(baseHeader);

            PdfPCell compareHeader = new PdfPCell(new Phrase("Comparison Document"));
            compareHeader.setBackgroundColor(Color.LIGHT_GRAY);
            metadataTable.addCell(compareHeader);

            // Add metadata differences
            for (Object obj : result.getMetadataDifferences().values()) {
                if (obj instanceof MetadataDifference) {
                    MetadataDifference diff = (MetadataDifference) obj;

                    metadataTable.addCell(diff.getKey());
                    metadataTable.addCell(diff.getBaseValue() != null ? diff.getBaseValue() : "Not present");
                    metadataTable.addCell(diff.getCompareValue() != null ? diff.getCompareValue() : "Not present");
                }
            }

            document.add(metadataTable);
        }

        document.add(new Paragraph(" ")); // Add spacing
    }

    /**
     * Add document pairs section to the PDF document.
     *
     * @param document The PDF document
     * @param result The comparison result
     */
    private void addDocumentPairsSection(Document document, ComparisonResult result) throws Exception {
        Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, Color.BLACK);

        Paragraph section = new Paragraph("Document Pairs", sectionFont);
        section.setSpacingBefore(15);
        section.setSpacingAfter(10);
        document.add(section);

        // Create document pairs table
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);

        // Add header row
        PdfPCell indexHeader = new PdfPCell(new Phrase("Pair"));
        indexHeader.setBackgroundColor(Color.LIGHT_GRAY);
        table.addCell(indexHeader);

        PdfPCell matchedHeader = new PdfPCell(new Phrase("Matched"));
        matchedHeader.setBackgroundColor(Color.LIGHT_GRAY);
        table.addCell(matchedHeader);

        PdfPCell baseHeader = new PdfPCell(new Phrase("Base Pages"));
        baseHeader.setBackgroundColor(Color.LIGHT_GRAY);
        table.addCell(baseHeader);

        PdfPCell compareHeader = new PdfPCell(new Phrase("Compare Pages"));
        compareHeader.setBackgroundColor(Color.LIGHT_GRAY);
        table.addCell(compareHeader);

        PdfPCell diffsHeader = new PdfPCell(new Phrase("Differences"));
        diffsHeader.setBackgroundColor(Color.LIGHT_GRAY);
        table.addCell(diffsHeader);

        // Add document pairs
        for (DocumentPair pair : result.getDocumentPairs()) {
            table.addCell(String.valueOf(pair.getPairIndex() + 1));
            table.addCell(pair.isMatched() ? "Yes" : "No");

            if (pair.isHasBaseDocument()) {
                table.addCell(pair.getBaseStartPage() + "-" + pair.getBaseEndPage() +
                        " (" + pair.getBasePageCount() + " pages)");
            } else {
                table.addCell("N/A");
            }

            if (pair.isHasCompareDocument()) {
                table.addCell(pair.getCompareStartPage() + "-" + pair.getCompareEndPage() +
                        " (" + pair.getComparePageCount() + " pages)");
            } else {
                table.addCell("N/A");
            }

            table.addCell(String.valueOf(pair.getTotalDifferences()));
        }

        document.add(table);
        document.add(new Paragraph(" ")); // Add spacing
    }

    /**
     * Add differences section to the PDF document.
     *
     * @param document The PDF document
     * @param comparison The comparison entity
     * @param result The comparison result
     * @param options Additional options for report generation
     */
    private void addDifferencesSection(Document document, Comparison comparison, ComparisonResult result, Map<String, Object> options) throws Exception {
        Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, Color.BLACK);
        Font subsectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.BLACK);

        Paragraph section = new Paragraph("Differences", sectionFont);
        section.setSpacingBefore(15);
        section.setSpacingAfter(10);
        document.add(section);

        // Check if we should include page differences
        boolean includePageDiffs = options == null ||
                !options.containsKey("includePageDiffs") ||
                Boolean.TRUE.equals(options.get("includePageDiffs"));

        // Standard mode
        if (result.getMode() == null || result.getMode().equals("standard")) {
            if (result.getPageDifferences() != null && includePageDiffs) {
                // Add page differences
                for (ComparisonResult.PageDifference pageDiff : result.getPageDifferences()) {
                    // Skip pages with no differences
                    if ((pageDiff.getTextDifferences() == null || pageDiff.getTextDifferences().getDifferences() == null ||
                            pageDiff.getTextDifferences().getDifferences().isEmpty()) &&
                            (pageDiff.getTextElementDifferences() == null || pageDiff.getTextElementDifferences().isEmpty()) &&
                            (pageDiff.getImageDifferences() == null || pageDiff.getImageDifferences().isEmpty()) &&
                            (pageDiff.getFontDifferences() == null || pageDiff.getFontDifferences().isEmpty()) &&
                            !pageDiff.isOnlyInBase() && !pageDiff.isOnlyInCompare() && !pageDiff.isDimensionsDifferent()) {
                        continue;
                    }

                    // Add page header
                    Paragraph pageHeader = new Paragraph("Page " + pageDiff.getPageNumber(), subsectionFont);
                    pageHeader.setSpacingBefore(10);
                    pageHeader.setSpacingAfter(5);
                    document.add(pageHeader);

                    // Add page info
                    if (pageDiff.isOnlyInBase()) {
                        document.add(new Paragraph("This page exists only in the base document."));
                    } else if (pageDiff.isOnlyInCompare()) {
                        document.add(new Paragraph("This page exists only in the comparison document."));
                    }

                    if (pageDiff.isDimensionsDifferent()) {
                        document.add(new Paragraph("Page dimensions are different."));
                    }

                    // Add text differences
                    if (pageDiff.getTextDifferences() != null && pageDiff.getTextDifferences().getDifferences() != null &&
                            !pageDiff.getTextDifferences().getDifferences().isEmpty()) {

                        document.add(new Paragraph("Text Differences:", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11)));

                        PdfPTable textTable = new PdfPTable(3);
                        textTable.setWidthPercentage(100);

                        // Add header row
                        PdfPCell typeHeader = new PdfPCell(new Phrase("Type"));
                        typeHeader.setBackgroundColor(Color.LIGHT_GRAY);
                        textTable.addCell(typeHeader);

                        PdfPCell baseTextHeader = new PdfPCell(new Phrase("Base Text"));
                        baseTextHeader.setBackgroundColor(Color.LIGHT_GRAY);
                        textTable.addCell(baseTextHeader);

                        PdfPCell compareTextHeader = new PdfPCell(new Phrase("Compare Text"));
                        compareTextHeader.setBackgroundColor(Color.LIGHT_GRAY);
                        textTable.addCell(compareTextHeader);

                        // Add text differences
                        for (Difference diff : pageDiff.getTextDifferences().getDifferences()) {
                            if (diff instanceof TextDifference) {
                                TextDifference textDiff = (TextDifference) diff;

                                textTable.addCell(textDiff.getChangeType());
                                textTable.addCell(textDiff.getBaseText() != null ? textDiff.getBaseText() : "");
                                textTable.addCell(textDiff.getCompareText() != null ? textDiff.getCompareText() : "");
                            }
                        }

                        document.add(textTable);
                    }

                    // Add image differences
                    if (pageDiff.getImageDifferences() != null && !pageDiff.getImageDifferences().isEmpty()) {
                        document.add(new Paragraph("Image Differences:", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11)));

                        for (Difference diff : pageDiff.getImageDifferences()) {
                            if (diff instanceof ImageDifference) {
                                ImageDifference imageDiff = (ImageDifference) diff;
                                document.add(new Paragraph(imageDiff.getDescription()));
                            }
                        }
                    }

                    // Add font differences
                    if (pageDiff.getFontDifferences() != null && !pageDiff.getFontDifferences().isEmpty()) {
                        document.add(new Paragraph("Font Differences:", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11)));

                        for (Difference diff : pageDiff.getFontDifferences()) {
                            if (diff instanceof FontDifference) {
                                FontDifference fontDiff = (FontDifference) diff;
                                document.add(new Paragraph(fontDiff.getDescription()));
                            }
                        }
                    }

                    // Add text element (style) differences
                    if (pageDiff.getTextElementDifferences() != null && !pageDiff.getTextElementDifferences().isEmpty()) {
                        document.add(new Paragraph("Style Differences:", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11)));

                        for (Difference diff : pageDiff.getTextElementDifferences()) {
                            if (diff instanceof StyleDifference) {
                                StyleDifference styleDiff = (StyleDifference) diff;
                                document.add(new Paragraph(styleDiff.getDescription()));
                            }
                        }
                    }
                }
            } else {
                document.add(new Paragraph("No page differences included in this report."));
            }
        } else {
            // Smart mode
            document.add(new Paragraph("Document pairs comparison results are included in the summary section."));
            document.add(new Paragraph("For detailed differences, please use the web interface."));
        }
    }

    /**
     * Add a cell to a PDF table.
     *
     * @param table The PDF table
     * @param label The cell label
     * @param value The cell value
     * @param highlight Whether to highlight the cell
     */
    private void addTableCell(PdfPTable table, String label, String value, boolean highlight) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label));
        if (highlight) {
            labelCell.setBackgroundColor(new Color(240, 240, 240));
        }
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value));
        if (highlight) {
            valueCell.setBackgroundColor(new Color(240, 240, 240));
        }
        table.addCell(valueCell);
    }

    /**
     * Generate an HTML report of the comparison results.
     *
     * @param comparison The comparison entity
     * @param result The comparison result
     * @param options Additional options for report generation
     * @return The generated HTML report as a Resource
     * @throws Exception If there's an error generating the report
     */
    private Resource generateHtmlReport(Comparison comparison, ComparisonResult result, Map<String, Object> options) throws Exception {
        // Create Thymeleaf context
        Context context = new Context();
        context.setVariable("comparison", comparison);
        context.setVariable("result", result);
        context.setVariable("options", options);
        context.setVariable("now", LocalDateTime.now());

        // Process template
        String html = templateEngine.process("comparison-report", context);

        // Return as resource
        return new ByteArrayResource(html.getBytes());
    }

    /**
     * Generate a JSON report of the comparison results.
     *
     * @param comparison The comparison entity
     * @param result The comparison result
     * @param options Additional options for report generation
     * @return The generated JSON report as a Resource
     * @throws Exception If there's an error generating the report
     */
    private Resource generateJsonReport(Comparison comparison, ComparisonResult result, Map<String, Object> options) throws Exception {
        // Create a simplified result for export
        ComparisonExportDTO exportDTO = new ComparisonExportDTO();
        exportDTO.setComparisonId(comparison.getComparisonId());
        exportDTO.setBaseFileName(result.getBaseFileName());
        exportDTO.setCompareFileName(result.getCompareFileName());
        exportDTO.setBasePageCount(result.getBasePageCount());
        exportDTO.setComparePageCount(result.getComparePageCount());
        exportDTO.setTotalDifferences(result.getTotalDifferences());
        exportDTO.setTextDifferences(result.getTotalTextDifferences());
        exportDTO.setImageDifferences(result.getTotalImageDifferences());
        exportDTO.setFontDifferences(result.getTotalFontDifferences());
        exportDTO.setStyleDifferences(result.getTotalStyleDifferences());
        exportDTO.setMode(result.getMode());
        exportDTO.setMetadataDifferences(result.getMetadataDifferences());

        if (result.getMode() != null && result.getMode().equals("smart")) {
            exportDTO.setDocumentPairs(result.getDocumentPairs());
        } else {
            // Include detailed page differences based on options
            boolean includePageDiffs = options == null ||
                    !options.containsKey("includePageDiffs") ||
                    Boolean.TRUE.equals(options.get("includePageDiffs"));

            if (includePageDiffs) {
                exportDTO.setPageDifferences(result.getPageDifferences());
            }
        }

        // Convert to JSON
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(baos, exportDTO);

        // Return as resource
        return new ByteArrayResource(baos.toByteArray());
    }

    /**
     * DTO for exporting comparison results.
     */
    private static class ComparisonExportDTO {
        private String comparisonId;
        private String baseFileName;
        private String compareFileName;
        private int basePageCount;
        private int comparePageCount;
        private int totalDifferences;
        private int textDifferences;
        private int imageDifferences;
        private int fontDifferences;
        private int styleDifferences;
        private String mode;
        private Map<String, Object> metadataDifferences;
        private List<DocumentPair> documentPairs;
        private List<ComparisonResult.PageDifference> pageDifferences;

        // Getters and setters
        public String getComparisonId() { return comparisonId; }
        public void setComparisonId(String comparisonId) { this.comparisonId = comparisonId; }

        public String getBaseFileName() { return baseFileName; }
        public void setBaseFileName(String baseFileName) { this.baseFileName = baseFileName; }

        public String getCompareFileName() { return compareFileName; }
        public void setCompareFileName(String compareFileName) { this.compareFileName = compareFileName; }

        public int getBasePageCount() { return basePageCount; }
        public void setBasePageCount(int basePageCount) { this.basePageCount = basePageCount; }

        public int getComparePageCount() { return comparePageCount; }
        public void setComparePageCount(int comparePageCount) { this.comparePageCount = comparePageCount; }

        public int getTotalDifferences() { return totalDifferences; }
        public void setTotalDifferences(int totalDifferences) { this.totalDifferences = totalDifferences; }

        public int getTextDifferences() { return textDifferences; }
        public void setTextDifferences(int textDifferences) { this.textDifferences = textDifferences; }

        public int getImageDifferences() { return imageDifferences; }
        public void setImageDifferences(int imageDifferences) { this.imageDifferences = imageDifferences; }

        public int getFontDifferences() { return fontDifferences; }
        public void setFontDifferences(int fontDifferences) { this.fontDifferences = fontDifferences; }

        public int getStyleDifferences() { return styleDifferences; }
        public void setStyleDifferences(int styleDifferences) { this.styleDifferences = styleDifferences; }

        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }

        public Map<String, Object> getMetadataDifferences() { return metadataDifferences; }
        public void setMetadataDifferences(Map<String, Object> metadataDifferences) { this.metadataDifferences = metadataDifferences; }

        public List<DocumentPair> getDocumentPairs() { return documentPairs; }
        public void setDocumentPairs(List<DocumentPair> documentPairs) { this.documentPairs = documentPairs; }

        public List<ComparisonResult.PageDifference> getPageDifferences() { return pageDifferences; }
        public void setPageDifferences(List<ComparisonResult.PageDifference> pageDifferences) { this.pageDifferences = pageDifferences; }
    }
}