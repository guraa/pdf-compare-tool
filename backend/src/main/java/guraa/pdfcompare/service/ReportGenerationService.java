package guraa.pdfcompare.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import guraa.pdfcompare.comparison.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for generating reports from PDF comparison results
 */
@Service
public class ReportGenerationService {

    private static final Logger logger = LoggerFactory.getLogger(ReportGenerationService.class);

    /**
     * Generate PDF report
     * @param result The comparison result
     * @return Resource containing the PDF report
     * @throws IOException If there's an error generating the report
     */
    public Resource generatePdfReport(PDFComparisonResult result) throws IOException {
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

            // Add metadata differences - but only if less than 100 to avoid memory issues
            if (result.getMetadataDifferences() != null && !result.getMetadataDifferences().isEmpty()
                    && result.getMetadataDifferences().size() < 100) {
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

                // Add data rows - limit to top 50 differences to avoid memory issues
                int count = 0;
                int maxEntries = 50;
                for (MetadataDifference diff : result.getMetadataDifferences().values()) {
                    if (count++ >= maxEntries) {
                        break;
                    }
                    metadataTable.addCell(diff.getKey());
                    metadataTable.addCell(diff.getBaseValue() != null ? diff.getBaseValue() : "(not present)");
                    metadataTable.addCell(diff.getCompareValue() != null ? diff.getCompareValue() : "(not present)");
                }

                if (result.getMetadataDifferences().size() > maxEntries) {
                    metadataTable.addCell("...");
                    metadataTable.addCell("(additional differences not shown)");
                    metadataTable.addCell("...");
                }

                document.add(metadataTable);
                document.add(Chunk.NEWLINE);
            }

            // Add page differences - limit to 20 most important pages to avoid memory issues
            if (result.getPageDifferences() != null && !result.getPageDifferences().isEmpty()) {
                Paragraph pagesTitle = new Paragraph("Page Differences", sectionFont);
                document.add(pagesTitle);
                document.add(Chunk.NEWLINE);

                // Sort pages by most significant differences
                List<guraa.pdfcompare.comparison.PageComparisonResult> significantPages = findMostSignificantPages(result.getPageDifferences(), 20);

                for (guraa.pdfcompare.comparison.PageComparisonResult page : significantPages) {
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

                    // Text differences - limit to top 20 differences per page
                    if (page.getTextDifferences() != null && page.getTextDifferences().getDifferenceCount() > 0) {
                        document.add(new Paragraph("Text Differences:"));

                        int diffCount = 0;
                        int maxDiffs = 20;
                        for (TextDifferenceItem textDiff : page.getTextDifferences().getDifferences()) {
                            if (diffCount++ >= maxDiffs) {
                                document.add(new Paragraph("(additional text differences not shown)"));
                                break;
                            }

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

                    // Image differences - limit to 10 per page
                    if (page.getImageDifferences() != null && !page.getImageDifferences().isEmpty()) {
                        document.add(new Paragraph("Image Differences:"));

                        int imgCount = 0;
                        int maxImgs = 10;
                        for (ImageDifference imageDiff : page.getImageDifferences()) {
                            if (imgCount++ >= maxImgs) {
                                document.add(new Paragraph("(additional image differences not shown)"));
                                break;
                            }

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

                    // Font differences - limit to 10 per page
                    if (page.getFontDifferences() != null && !page.getFontDifferences().isEmpty()) {
                        document.add(new Paragraph("Font Differences:"));

                        int fontCount = 0;
                        int maxFonts = 10;
                        for (FontDifference fontDiff : page.getFontDifferences()) {
                            if (fontCount++ >= maxFonts) {
                                document.add(new Paragraph("(additional font differences not shown)"));
                                break;
                            }

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
    public Resource generateHtmlReport(PDFComparisonResult result) {
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

        // Metadata differences - only show if less than 100 to avoid memory issues
        if (result.getMetadataDifferences() != null && !result.getMetadataDifferences().isEmpty()
                && result.getMetadataDifferences().size() < 100) {
            html.append("  <h2>Metadata Differences</h2>\n")
                    .append("  <table>\n")
                    .append("    <tr><th>Property</th><th>Base Value</th><th>Compare Value</th></tr>\n");

            // Limit to top 50 differences to avoid memory issues
            int count = 0;
            int maxEntries = 50;
            for (MetadataDifference diff : result.getMetadataDifferences().values()) {
                if (count++ >= maxEntries) {
                    html.append("    <tr><td colspan=\"3\">Additional differences not shown...</td></tr>\n");
                    break;
                }

                html.append("    <tr>")
                        .append("<td>").append(diff.getKey()).append("</td>")
                        .append("<td>").append(diff.getBaseValue() != null ? escapeHtml(diff.getBaseValue()) : "(not present)").append("</td>")
                        .append("<td>").append(diff.getCompareValue() != null ? escapeHtml(diff.getCompareValue()) : "(not present)").append("</td>")
                        .append("</tr>\n");
            }

            html.append("  </table>\n");
        }

        // Page differences - limit to 20 most significant pages
        if (result.getPageDifferences() != null && !result.getPageDifferences().isEmpty()) {
            html.append("  <h2>Page Differences</h2>\n");

            // Find most significant pages
            List<guraa.pdfcompare.comparison.PageComparisonResult> significantPages = findMostSignificantPages(result.getPageDifferences(), 20);

            for (PageComparisonResult page : significantPages) {
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

                // Text differences - limit to 20 most important differences
                if (page.getTextDifferences() != null && page.getTextDifferences().getDifferenceCount() > 0) {
                    html.append("    <h4>Text Differences</h4>\n")
                            .append("    <ul>\n");

                    List<TextDifferenceItem> textDiffs = page.getTextDifferences().getDifferences();
                    int limit = Math.min(20, textDiffs.size());

                    for (int i = 0; i < limit; i++) {
                        TextDifferenceItem textDiff = textDiffs.get(i);
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

                    if (textDiffs.size() > limit) {
                        html.append("      <li>(").append(textDiffs.size() - limit).append(" more differences not shown)</li>\n");
                    }

                    html.append("    </ul>\n");
                }

                // Image differences - with limit
                if (page.getImageDifferences() != null && !page.getImageDifferences().isEmpty()) {
                    html.append("    <h4>Image Differences</h4>\n")
                            .append("    <ul>\n");

                    List<ImageDifference> imageDiffs = page.getImageDifferences();
                    int limit = Math.min(10, imageDiffs.size());

                    for (int i = 0; i < limit; i++) {
                        ImageDifference imageDiff = imageDiffs.get(i);
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

                    if (imageDiffs.size() > limit) {
                        html.append("      <li>(").append(imageDiffs.size() - limit).append(" more image differences not shown)</li>\n");
                    }

                    html.append("    </ul>\n");
                }

                // Font differences - with limit
                if (page.getFontDifferences() != null && !page.getFontDifferences().isEmpty()) {
                    html.append("    <h4>Font Differences</h4>\n")
                            .append("    <ul>\n");

                    List<FontDifference> fontDiffs = page.getFontDifferences();
                    int limit = Math.min(10, fontDiffs.size());

                    for (int i = 0; i < limit; i++) {
                        FontDifference fontDiff = fontDiffs.get(i);
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

                    if (fontDiffs.size() > limit) {
                        html.append("      <li>(").append(fontDiffs.size() - limit).append(" more font differences not shown)</li>\n");
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
        if (text == null) return "";
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
    public Resource generateJsonReport(PDFComparisonResult result) throws IOException {
        // Create a lightweight version of the result to avoid memory issues
        PDFComparisonResult lightweight = createLightweightResult(result);

        // Use Jackson to serialize the result to JSON
        ObjectMapper mapper = new ObjectMapper();
        byte[] jsonData = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(lightweight);

        return new ByteArrayResource(jsonData);
    }

    /**
     * Create a lightweight version of the comparison result
     * @param result The full comparison result
     * @return A lightweight version with limited details
     */
    private PDFComparisonResult createLightweightResult(PDFComparisonResult result) {
        PDFComparisonResult lightweight = new PDFComparisonResult();

        // Copy summary data
        lightweight.setBasePageCount(result.getBasePageCount());
        lightweight.setComparePageCount(result.getComparePageCount());
        lightweight.setPageCountDifferent(result.isPageCountDifferent());
        lightweight.setTotalDifferences(result.getTotalDifferences());
        lightweight.setTotalTextDifferences(result.getTotalTextDifferences());
        lightweight.setTotalImageDifferences(result.getTotalImageDifferences());
        lightweight.setTotalFontDifferences(result.getTotalFontDifferences());
        lightweight.setTotalStyleDifferences(result.getTotalStyleDifferences());

        // Copy limited metadata differences
        if (result.getMetadataDifferences() != null && !result.getMetadataDifferences().isEmpty()) {
            Map<String, MetadataDifference> limitedMetadata = new HashMap<>();
            int count = 0;
            int limit = 50; // Limit metadata entries

            for (Map.Entry<String, MetadataDifference> entry : result.getMetadataDifferences().entrySet()) {
                if (count++ < limit) {
                    limitedMetadata.put(entry.getKey(), entry.getValue());
                } else {
                    break;
                }
            }

            lightweight.setMetadataDifferences(limitedMetadata);
        }

        // Copy limited page differences
        if (result.getPageDifferences() != null && !result.getPageDifferences().isEmpty()) {
            // Find most significant pages
            List<PageComparisonResult> significantPages = findMostSignificantPages(result.getPageDifferences(), 20);

            // Create lightweight versions of these pages
            List<PageComparisonResult> lightweightPages = new ArrayList<>();
            for (PageComparisonResult page : significantPages) {
                PageComparisonResult lightPage = new PageComparisonResult();

                // Copy basic info
                lightPage.setPageNumber(page.getPageNumber());
                lightPage.setOnlyInBase(page.isOnlyInBase());
                lightPage.setOnlyInCompare(page.isOnlyInCompare());
                lightPage.setDimensionsDifferent(page.isDimensionsDifferent());

                if (page.getBaseDimensions() != null) {
                    lightPage.setBaseDimensions(page.getBaseDimensions().clone());
                }

                if (page.getCompareDimensions() != null) {
                    lightPage.setCompareDimensions(page.getCompareDimensions().clone());
                }

                // Create limited text differences
                if (page.getTextDifferences() != null && page.getTextDifferences().getDifferences() != null) {
                    TextComparisonResult lightTextResult = new TextComparisonResult();
                    lightTextResult.setDifferenceCount(page.getTextDifferences().getDifferenceCount());

                    // Limit to 20 most important text differences
                    List<TextDifferenceItem> lightDiffs = new ArrayList<>();
                    int textLimit = Math.min(20, page.getTextDifferences().getDifferences().size());

                    for (int i = 0; i < textLimit; i++) {
                        lightDiffs.add(page.getTextDifferences().getDifferences().get(i));
                    }

                    lightTextResult.setDifferences(lightDiffs);
                    lightPage.setTextDifferences(lightTextResult);
                }

                // Create limited text element differences
                if (page.getTextElementDifferences() != null && !page.getTextElementDifferences().isEmpty()) {
                    int elemLimit = Math.min(20, page.getTextElementDifferences().size());
                    lightPage.setTextElementDifferences(
                            new ArrayList<>(page.getTextElementDifferences().subList(0, elemLimit))
                    );
                }

                // Create limited image differences
                if (page.getImageDifferences() != null && !page.getImageDifferences().isEmpty()) {
                    int imgLimit = Math.min(10, page.getImageDifferences().size());
                    lightPage.setImageDifferences(
                            new ArrayList<>(page.getImageDifferences().subList(0, imgLimit))
                    );
                }

                // Create limited font differences
                if (page.getFontDifferences() != null && !page.getFontDifferences().isEmpty()) {
                    int fontLimit = Math.min(10, page.getFontDifferences().size());
                    lightPage.setFontDifferences(
                            new ArrayList<>(page.getFontDifferences().subList(0, fontLimit))
                    );
                }

                lightweightPages.add(lightPage);
            }

            lightweight.setPageDifferences(lightweightPages);
        }

        return lightweight;
    }

    /**
     * Find most significant pages with differences
     * @param pages List of all page comparison results
     * @param maxPages Maximum number of pages to return
     * @return List of most significant pages
     */
    public List<guraa.pdfcompare.comparison.PageComparisonResult> findMostSignificantPages(List<?> pages, int maxPages) {
        // Convert the list to the correct type
        List<guraa.pdfcompare.comparison.PageComparisonResult> typedPages = new ArrayList<>();
        for (Object page : pages) {
            if (page instanceof guraa.pdfcompare.comparison.PageComparisonResult) {
                typedPages.add((guraa.pdfcompare.comparison.PageComparisonResult) page);
            } else if (page instanceof PageComparisonResult) {
                // Convert service.PageComparisonResult to comparison.PageComparisonResult
                PageComparisonResult servicePage = (PageComparisonResult) page;
                
                guraa.pdfcompare.comparison.PageComparisonResult comparisonPage = 
                        new guraa.pdfcompare.comparison.PageComparisonResult();
                comparisonPage.setPageNumber(servicePage.getPageNumber());
                comparisonPage.setOnlyInBase(servicePage.isOnlyInBase());
                comparisonPage.setOnlyInCompare(servicePage.isOnlyInCompare());
                comparisonPage.setDimensionsDifferent(servicePage.isDimensionsDifferent());
                comparisonPage.setBaseDimensions(servicePage.getBaseDimensions());
                comparisonPage.setCompareDimensions(servicePage.getCompareDimensions());
                comparisonPage.setTextDifferences(servicePage.getTextDifferences());
                comparisonPage.setTextElementDifferences(servicePage.getTextElementDifferences());
                comparisonPage.setImageDifferences(servicePage.getImageDifferences());
                comparisonPage.setFontDifferences(servicePage.getFontDifferences());
                
                typedPages.add(comparisonPage);
            }
        }
        
        // Sort pages by significance (number and type of differences)
        return typedPages.stream()
                .filter(page ->
                        page.isOnlyInBase() ||
                                page.isOnlyInCompare() ||
                                page.isDimensionsDifferent() ||
                                (page.getTextDifferences() != null && page.getTextDifferences().getDifferenceCount() > 0) ||
                                (page.getTextElementDifferences() != null && !page.getTextElementDifferences().isEmpty()) ||
                                (page.getImageDifferences() != null && !page.getImageDifferences().isEmpty()) ||
                                (page.getFontDifferences() != null && !page.getFontDifferences().isEmpty())
                )
                .sorted((p1, p2) -> {
                    // Calculate significance scores
                    int score1 = calculatePageSignificance(p1);
                    int score2 = calculatePageSignificance(p2);
                    return Integer.compare(score2, score1); // Descending order
                })
                .limit(maxPages)
                .collect(Collectors.toList());
    }

    /**
     * Calculate a significance score for a page
     * @param page The page comparison result
     * @return Significance score
     */
    private int calculatePageSignificance(guraa.pdfcompare.comparison.PageComparisonResult page) {
        int score = 0;

        // Pages that exist in only one document are highly significant
        if (page.isOnlyInBase() || page.isOnlyInCompare()) {
            score += 1000;
        }

        // Different dimensions are significant
        if (page.isDimensionsDifferent()) {
            score += 500;
        }

        // Count different types of differences with weights
        if (page.getTextDifferences() != null) {
            score += page.getTextDifferences().getDifferenceCount() * 10;
        }

        if (page.getTextElementDifferences() != null) {
            score += page.getTextElementDifferences().size() * 8;
        }

        if (page.getImageDifferences() != null) {
            score += page.getImageDifferences().size() * 20;
        }

        if (page.getFontDifferences() != null) {
            score += page.getFontDifferences().size() * 5;
        }

        return score;
    }
}
