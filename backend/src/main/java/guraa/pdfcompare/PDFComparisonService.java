package guraa.pdfcompare;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import guraa.pdfcompare.comparison.*;
import guraa.pdfcompare.core.PDFDocumentModel;
import guraa.pdfcompare.core.PDFProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for PDF comparison operations
 */
@Service
public class PDFComparisonService {

    private static final Logger logger = LoggerFactory.getLogger(PDFComparisonService.class);


    // In-memory storage for comparison results
    // In a production environment, this should be replaced with a database
    private Map<String, PDFComparisonResult> comparisonResults = new ConcurrentHashMap<>();

    /**
     * Compare two PDF files
     *
     * @param baseFilePath    Path to the base PDF file
     * @param compareFilePath Path to the comparison PDF file
     * @return Comparison ID
     * @throws IOException If there's an error reading the files
     */
    public String compareFiles(String baseFilePath, String compareFilePath) throws IOException {
        // Create PDF processor
        PDFProcessor processor = new PDFProcessor();

        // Process both files
        PDFDocumentModel baseDocument = processor.processDocument(new File(baseFilePath));
        PDFDocumentModel compareDocument = processor.processDocument(new File(compareFilePath));

        // Create comparison engine
        PDFComparisonEngine engine = new PDFComparisonEngine();

        // Perform comparison
        PDFComparisonResult result = engine.compareDocuments(baseDocument, compareDocument);

        // Generate comparison ID
        String comparisonId = UUID.randomUUID().toString();

        // Store result
        comparisonResults.put(comparisonId, result);

        return comparisonId;
    }

    /**
     * Generate report for comparison results
     * @param comparisonId The comparison ID
     * @param format The report format (pdf, html, json)
     * @return Resource containing the report
     * @throws IOException If there's an error generating the report
     */
    public Resource generateReport(String comparisonId, String format) throws IOException {
        logger.info("Generating {} report for comparison {}", format, comparisonId);

        PDFComparisonResult result = getComparisonResult(comparisonId);

        if (result == null) {
            logger.error("Comparison result not found: {}", comparisonId);
            throw new RuntimeException("Comparison result not found: " + comparisonId);
        }

        switch (format.toLowerCase()) {
            case "pdf":
                return generatePdfReport(result);
            case "html":
                return generateHtmlReport(result);
            case "json":
                return generateJsonReport(result);
            default:
                logger.error("Unsupported report format: {}", format);
                throw new IllegalArgumentException("Unsupported format: " + format);
        }
    }

    /**
     * Generate PDF report
     * @param result The comparison result
     * @return Resource containing the PDF report
     * @throws IOException If there's an error generating the report
     */
    private Resource generatePdfReport(PDFComparisonResult result) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try {
            // Create PDF document
            Document document = new Document();
            PdfWriter writer = PdfWriter.getInstance(document, baos);
            document.open();

            // Add title
            Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD);
            Paragraph title = new Paragraph("PDF Comparison Report", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);
            document.add(Chunk.NEWLINE);

            // Add timestamp
            Font timestampFont = new Font(Font.HELVETICA, 10, Font.ITALIC);
            Paragraph timestamp = new Paragraph("Generated: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()), timestampFont);
            timestamp.setAlignment(Element.ALIGN_CENTER);
            document.add(timestamp);
            document.add(Chunk.NEWLINE);
            document.add(Chunk.NEWLINE);

            // Add summary
            Font sectionFont = new Font(Font.HELVETICA, 14, Font.BOLD);
            Paragraph summaryTitle = new Paragraph("Summary", sectionFont);
            document.add(summaryTitle);
            document.add(Chunk.NEWLINE);

            PdfPTable summaryTable = new PdfPTable(2);
            summaryTable.setWidthPercentage(100);

            addTableRow(summaryTable, "Base Document Page Count:", String.valueOf(result.getBasePageCount()));
            addTableRow(summaryTable, "Compare Document Page Count:", String.valueOf(result.getComparePageCount()));
            addTableRow(summaryTable, "Page Count Different:", result.isPageCountDifferent() ? "Yes" : "No");
            addTableRow(summaryTable, "Total Differences:", String.valueOf(result.getTotalDifferences()));
            addTableRow(summaryTable, "Text Differences:", String.valueOf(result.getTotalTextDifferences()));
            addTableRow(summaryTable, "Image Differences:", String.valueOf(result.getTotalImageDifferences()));
            addTableRow(summaryTable, "Font Differences:", String.valueOf(result.getTotalFontDifferences()));
            addTableRow(summaryTable, "Style Differences:", String.valueOf(result.getTotalStyleDifferences()));

            document.add(summaryTable);
            document.add(Chunk.NEWLINE);

            // Add metadata differences
            if (result.getMetadataDifferences() != null && !result.getMetadataDifferences().isEmpty()) {
                Paragraph metadataTitle = new Paragraph("Metadata Differences", sectionFont);
                document.add(metadataTitle);
                document.add(Chunk.NEWLINE);

                PdfPTable metadataTable = new PdfPTable(3);
                metadataTable.setWidthPercentage(100);

                // Add header row
                Font headerFont = new Font(Font.HELVETICA, 12, Font.BOLD);
                PdfPCell keyCell = new PdfPCell(new Phrase("Property", headerFont));
                PdfPCell baseCell = new PdfPCell(new Phrase("Base Value", headerFont));
                PdfPCell compareCell = new PdfPCell(new Phrase("Compare Value", headerFont));

                keyCell.setBackgroundColor(new java.awt.Color(230, 230, 230));
                baseCell.setBackgroundColor(new java.awt.Color(230, 230, 230));
                compareCell.setBackgroundColor(new java.awt.Color(230, 230, 230));

                metadataTable.addCell(keyCell);
                metadataTable.addCell(baseCell);
                metadataTable.addCell(compareCell);

                // Add data rows
                for (MetadataDifference diff : result.getMetadataDifferences().values()) {
                    metadataTable.addCell(diff.getKey());
                    metadataTable.addCell(diff.getBaseValue() != null ? diff.getBaseValue() : "(not present)");
                    metadataTable.addCell(diff.getCompareValue() != null ? diff.getCompareValue() : "(not present)");
                }

                document.add(metadataTable);
                document.add(Chunk.NEWLINE);
            }

            // Add page differences
            if (result.getPageDifferences() != null && !result.getPageDifferences().isEmpty()) {
                Paragraph pagesTitle = new Paragraph("Page Differences", sectionFont);
                document.add(pagesTitle);
                document.add(Chunk.NEWLINE);

                for (PageComparisonResult page : result.getPageDifferences()) {
                    // Skip pages with no differences
                    if ((page.getTextDifferences() == null || page.getTextDifferences().getDifferenceCount() == 0) &&
                            (page.getTextElementDifferences() == null || page.getTextElementDifferences().isEmpty()) &&
                            (page.getImageDifferences() == null || page.getImageDifferences().isEmpty()) &&
                            (page.getFontDifferences() == null || page.getFontDifferences().isEmpty()) &&
                            !page.isOnlyInBase() && !page.isOnlyInCompare() && !page.isDimensionsDifferent()) {
                        continue;
                    }

                    Font pageFont = new Font(Font.HELVETICA, 12, Font.BOLD);
                    Paragraph pageTitle = new Paragraph("Page " + page.getPageNumber(), pageFont);
                    document.add(pageTitle);

                    if (page.isOnlyInBase()) {
                        document.add(new Paragraph("This page exists only in the base document."));
                        continue;
                    }

                    if (page.isOnlyInCompare()) {
                        document.add(new Paragraph("This page exists only in the comparison document."));
                        continue;
                    }

                    if (page.isDimensionsDifferent()) {
                        document.add(new Paragraph(String.format(
                                "Page dimensions differ: Base [%.2f x %.2f], Compare [%.2f x %.2f]",
                                page.getBaseDimensions()[0], page.getBaseDimensions()[1],
                                page.getCompareDimensions()[0], page.getCompareDimensions()[1])));
                    }

                    // Text differences
                    if (page.getTextDifferences() != null && page.getTextDifferences().getDifferenceCount() > 0) {
                        document.add(new Paragraph("Text Differences:"));

                        for (TextDifferenceItem textDiff : page.getTextDifferences().getDifferences()) {
                            Paragraph diffPara = new Paragraph();

                            // Line number
                            Chunk lineChunk = new Chunk("Line " + textDiff.getLineNumber() + ": ");
                            diffPara.add(lineChunk);

                            // Difference type
                            switch (textDiff.getDifferenceType()) {
                                case ADDED:
                                    diffPara.add(new Chunk("Added: ", new Font(Font.HELVETICA, 10, Font.BOLD, new java.awt.Color(0, 128, 0))));
                                    diffPara.add(new Chunk(textDiff.getCompareText()));
                                    break;
                                case DELETED:
                                    diffPara.add(new Chunk("Deleted: ", new Font(Font.HELVETICA, 10, Font.BOLD, new java.awt.Color(255, 0, 0))));
                                    diffPara.add(new Chunk(textDiff.getBaseText()));
                                    break;
                                case MODIFIED:
                                    diffPara.add(new Chunk("Modified: ", new Font(Font.HELVETICA, 10, Font.BOLD, new java.awt.Color(0, 0, 255))));
                                    diffPara.add(new Chunk("From \"" + textDiff.getBaseText() + "\" to \"" + textDiff.getCompareText() + "\""));
                                    break;
                            }

                            document.add(diffPara);
                        }

                        document.add(Chunk.NEWLINE);
                    }

                    // Image differences
                    if (page.getImageDifferences() != null && !page.getImageDifferences().isEmpty()) {
                        document.add(new Paragraph("Image Differences:"));

                        for (ImageDifference imageDiff : page.getImageDifferences()) {
                            if (imageDiff.isOnlyInBase()) {
                                document.add(new Paragraph("- Image only in base document"));
                            } else if (imageDiff.isOnlyInCompare()) {
                                document.add(new Paragraph("- Image only in comparison document"));
                            } else {
                                List<String> changes = new ArrayList<>();
                                if (imageDiff.isDimensionsDifferent()) {
                                    changes.add("Dimensions differ");
                                }
                                if (imageDiff.isPositionDifferent()) {
                                    changes.add("Position differs");
                                }
                                if (imageDiff.isFormatDifferent()) {
                                    changes.add("Format differs");
                                }

                                document.add(new Paragraph("- Image modified: " + String.join(", ", changes)));
                            }
                        }

                        document.add(Chunk.NEWLINE);
                    }

                    // Font differences
                    if (page.getFontDifferences() != null && !page.getFontDifferences().isEmpty()) {
                        document.add(new Paragraph("Font Differences:"));

                        for (FontDifference fontDiff : page.getFontDifferences()) {
                            if (fontDiff.isOnlyInBase()) {
                                document.add(new Paragraph("- Font \"" + fontDiff.getBaseFont().getName() + "\" only in base document"));
                            } else if (fontDiff.isOnlyInCompare()) {
                                document.add(new Paragraph("- Font \"" + fontDiff.getCompareFont().getName() + "\" only in comparison document"));
                            } else {
                                List<String> changes = new ArrayList<>();
                                if (fontDiff.isEmbeddingDifferent()) {
                                    changes.add("Embedding differs");
                                }
                                if (fontDiff.isSubsetDifferent()) {
                                    changes.add("Subsetting differs");
                                }

                                document.add(new Paragraph("- Font \"" + fontDiff.getBaseFont().getName() + "\" modified: " + String.join(", ", changes)));
                            }
                        }

                        document.add(Chunk.NEWLINE);
                    }

                    document.add(Chunk.NEWLINE);
                }
            }

            document.close();
            return new ByteArrayResource(baos.toByteArray());
        } catch (DocumentException e) {
            logger.error("Error generating PDF report", e);
            throw new IOException("Error generating PDF report", e);
        }
    }

    /**
     * Add a row to a PDF table
     * @param table The table
     * @param label The label
     * @param value The value
     */
    private void addTableRow(PdfPTable table, String label, String value) {
        table.addCell(label);
        table.addCell(value);
    }

    /**
     * Generate HTML report
     * @param result The comparison result
     * @return Resource containing the HTML report
     */
    private Resource generateHtmlReport(PDFComparisonResult result) {
        StringBuilder html = new StringBuilder();

        // HTML header
        html.append("<!DOCTYPE html>\n")
                .append("<html lang=\"en\">\n")
                .append("<head>\n")
                .append("  <meta charset=\"UTF-8\">\n")
                .append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
                .append("  <title>PDF Comparison Report</title>\n")
                .append("  <style>\n")
                .append("    body { font-family: Arial, sans-serif; margin: 0; padding: 20px; }\n")
                .append("    h1, h2, h3 { color: #2c6dbd; }\n")
                .append("    table { border-collapse: collapse; width: 100%; margin-bottom: 20px; }\n")
                .append("    th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }\n")
                .append("    th { background-color: #f2f2f2; }\n")
                .append("    .page { border: 1px solid #ddd; padding: 15px; margin-bottom: 20px; }\n")
                .append("    .text-added { color: green; }\n")
                .append("    .text-deleted { color: red; }\n")
                .append("    .text-modified { color: blue; }\n")
                .append("  </style>\n")
                .append("</head>\n")
                .append("<body>\n")
                .append("  <h1>PDF Comparison Report</h1>\n")
                .append("  <p>Generated: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())).append("</p>\n");

        // Summary section
        html.append("  <h2>Summary</h2>\n")
                .append("  <table>\n")
                .append("    <tr><td>Base Document Page Count</td><td>").append(result.getBasePageCount()).append("</td></tr>\n")
                .append("    <tr><td>Compare Document Page Count</td><td>").append(result.getComparePageCount()).append("</td></tr>\n")
                .append("    <tr><td>Page Count Different</td><td>").append(result.isPageCountDifferent() ? "Yes" : "No").append("</td></tr>\n")
                .append("    <tr><td>Total Differences</td><td>").append(result.getTotalDifferences()).append("</td></tr>\n")
                .append("    <tr><td>Text Differences</td><td>").append(result.getTotalTextDifferences()).append("</td></tr>\n")
                .append("    <tr><td>Image Differences</td><td>").append(result.getTotalImageDifferences()).append("</td></tr>\n")
                .append("    <tr><td>Font Differences</td><td>").append(result.getTotalFontDifferences()).append("</td></tr>\n")
                .append("    <tr><td>Style Differences</td><td>").append(result.getTotalStyleDifferences()).append("</td></tr>\n")
                .append("  </table>\n");

        // Metadata differences
        if (result.getMetadataDifferences() != null && !result.getMetadataDifferences().isEmpty()) {
            html.append("  <h2>Metadata Differences</h2>\n")
                    .append("  <table>\n")
                    .append("    <tr><th>Property</th><th>Base Value</th><th>Compare Value</th></tr>\n");

            for (MetadataDifference diff : result.getMetadataDifferences().values()) {
                html.append("    <tr>")
                        .append("<td>").append(diff.getKey()).append("</td>")
                        .append("<td>").append(diff.getBaseValue() != null ? escapeHtml(diff.getBaseValue()) : "(not present)").append("</td>")
                        .append("<td>").append(diff.getCompareValue() != null ? escapeHtml(diff.getCompareValue()) : "(not present)").append("</td>")
                        .append("</tr>\n");
            }

            html.append("  </table>\n");
        }

        // Page differences
        if (result.getPageDifferences() != null && !result.getPageDifferences().isEmpty()) {
            html.append("  <h2>Page Differences</h2>\n");

            for (PageComparisonResult page : result.getPageDifferences()) {
                // Skip pages with no differences
                if ((page.getTextDifferences() == null || page.getTextDifferences().getDifferenceCount() == 0) &&
                        (page.getTextElementDifferences() == null || page.getTextElementDifferences().isEmpty()) &&
                        (page.getImageDifferences() == null || page.getImageDifferences().isEmpty()) &&
                        (page.getFontDifferences() == null || page.getFontDifferences().isEmpty()) &&
                        !page.isOnlyInBase() && !page.isOnlyInCompare() && !page.isDimensionsDifferent()) {
                    continue;
                }

                html.append("  <div class=\"page\">\n")
                        .append("    <h3>Page ").append(page.getPageNumber()).append("</h3>\n");

                if (page.isOnlyInBase()) {
                    html.append("    <p>This page exists only in the base document.</p>\n");
                    html.append("  </div>\n");
                    continue;
                }

                if (page.isOnlyInCompare()) {
                    html.append("    <p>This page exists only in the comparison document.</p>\n");
                    html.append("  </div>\n");
                    continue;
                }

                if (page.isDimensionsDifferent()) {
                    html.append("    <p>Page dimensions differ: Base [")
                            .append(String.format("%.2f x %.2f", page.getBaseDimensions()[0], page.getBaseDimensions()[1]))
                            .append("], Compare [")
                            .append(String.format("%.2f x %.2f", page.getCompareDimensions()[0], page.getCompareDimensions()[1]))
                            .append("]</p>\n");
                }

                // Text differences
                if (page.getTextDifferences() != null && page.getTextDifferences().getDifferenceCount() > 0) {
                    html.append("    <h4>Text Differences</h4>\n")
                            .append("    <ul>\n");

                    for (TextDifferenceItem textDiff : page.getTextDifferences().getDifferences()) {
                        html.append("      <li>Line ").append(textDiff.getLineNumber()).append(": ");

                        switch (textDiff.getDifferenceType()) {
                            case ADDED:
                                html.append("<span class=\"text-added\">Added: \"")
                                        .append(escapeHtml(textDiff.getCompareText()))
                                        .append("\"</span>");
                                break;
                            case DELETED:
                                html.append("<span class=\"text-deleted\">Deleted: \"")
                                        .append(escapeHtml(textDiff.getBaseText()))
                                        .append("\"</span>");
                                break;
                            case MODIFIED:
                                html.append("<span class=\"text-modified\">Modified: From \"")
                                        .append(escapeHtml(textDiff.getBaseText()))
                                        .append("\" to \"")
                                        .append(escapeHtml(textDiff.getCompareText()))
                                        .append("\"</span>");
                                break;
                        }

                        html.append("</li>\n");
                    }

                    html.append("    </ul>\n");
                }

                // Image differences
                if (page.getImageDifferences() != null && !page.getImageDifferences().isEmpty()) {
                    html.append("    <h4>Image Differences</h4>\n")
                            .append("    <ul>\n");

                    for (ImageDifference imageDiff : page.getImageDifferences()) {
                        html.append("      <li>");

                        if (imageDiff.isOnlyInBase()) {
                            html.append("Image only in base document");
                        } else if (imageDiff.isOnlyInCompare()) {
                            html.append("Image only in comparison document");
                        } else {
                            html.append("Image modified: ");

                            List<String> changes = new ArrayList<>();
                            if (imageDiff.isDimensionsDifferent()) {
                                changes.add("Dimensions differ");
                            }
                            if (imageDiff.isPositionDifferent()) {
                                changes.add("Position differs");
                            }
                            if (imageDiff.isFormatDifferent()) {
                                changes.add("Format differs");
                            }

                            html.append(String.join(", ", changes));
                        }

                        html.append("</li>\n");
                    }

                    html.append("    </ul>\n");
                }

                // Font differences
                if (page.getFontDifferences() != null && !page.getFontDifferences().isEmpty()) {
                    html.append("    <h4>Font Differences</h4>\n")
                            .append("    <ul>\n");

                    for (FontDifference fontDiff : page.getFontDifferences()) {
                        html.append("      <li>");

                        if (fontDiff.isOnlyInBase()) {
                            html.append("Font \"").append(escapeHtml(fontDiff.getBaseFont().getName())).append("\" only in base document");
                        } else if (fontDiff.isOnlyInCompare()) {
                            html.append("Font \"").append(escapeHtml(fontDiff.getCompareFont().getName())).append("\" only in comparison document");
                        } else {
                            html.append("Font \"").append(escapeHtml(fontDiff.getBaseFont().getName())).append("\" modified: ");

                            List<String> changes = new ArrayList<>();
                            if (fontDiff.isEmbeddingDifferent()) {
                                changes.add("Embedding differs");
                            }
                            if (fontDiff.isSubsetDifferent()) {
                                changes.add("Subsetting differs");
                            }

                            html.append(String.join(", ", changes));
                        }

                        html.append("</li>\n");
                    }

                    html.append("    </ul>\n");
                }

                html.append("  </div>\n");
            }
        }

        // HTML footer
        html.append("</body>\n")
                .append("</html>");

        return new ByteArrayResource(html.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Escape HTML special characters
     * @param text The text to escape
     * @return Escaped text
     */
    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /**
     * Generate JSON report
     * @param result The comparison result
     * @return Resource containing the JSON report
     * @throws IOException If there's an error generating the report
     */
    private Resource generateJsonReport(PDFComparisonResult result) throws IOException {
        // Use Jackson to serialize the result to JSON
        ObjectMapper mapper = new ObjectMapper();
        byte[] jsonData = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(result);

        return new ByteArrayResource(jsonData);
    }

    /**
     * Get comparison result by ID
     * @param comparisonId The comparison ID
     * @return Comparison result, or null if not found
     */
    public PDFComparisonResult getComparisonResult(String comparisonId) {
        return comparisonResults.get(comparisonId);
    }
}