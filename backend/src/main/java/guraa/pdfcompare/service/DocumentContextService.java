package guraa.pdfcompare.service;

import guraa.pdfcompare.model.PdfDocument;
import guraa.pdfcompare.util.TextExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for analyzing PDF document context and structures.
 * This helps with more accurate document matching and comparison.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentContextService {

    private final TextExtractor textExtractor;

    // Regular expressions for document structure detection
    private static final Pattern HEADING_PATTERN = Pattern.compile("(?m)^\\s*(?:(\\d+(\\.\\d+)*)\\s+)?([A-Z][A-Za-z0-9 \\-:]+)\\s*$");
    private static final Pattern TOC_ENTRY_PATTERN = Pattern.compile("(?m)^\\s*(?:(\\d+(\\.\\d+)*)\\s+)?([A-Za-z0-9 \\-:]+)\\s*\\.{2,}\\s*(\\d+)\\s*$");
    private static final Pattern PAGE_NUMBER_PATTERN = Pattern.compile("(?m)^\\s*(\\d+)\\s*$");

    /**
     * Analyze a PDF document to extract context information.
     *
     * @param document The PDF document
     * @return Map of context information
     */
    public Map<String, Object> analyzeDocumentContext(PdfDocument document) {
        Map<String, Object> context = new HashMap<>();

        try (PDDocument pdfDoc = PDDocument.load(new File(document.getFilePath()))) {
            // Extract document title and subject
            String title = extractDocumentTitle(pdfDoc);
            context.put("title", title);

            // Extract table of contents if available
            List<Map<String, Object>> toc = extractTableOfContents(pdfDoc);
            if (!toc.isEmpty()) {
                context.put("tableOfContents", toc);
            }

            // Extract headings
            List<Map<String, Object>> headings = extractHeadings(pdfDoc);
            if (!headings.isEmpty()) {
                context.put("headings", headings);
            }

            // Extract summary of content
            Map<String, Object> contentSummary = summarizeContent(pdfDoc);
            context.put("contentSummary", contentSummary);

            // Detect language
            String language = detectLanguage(pdfDoc);
            context.put("language", language);

            // Extract document structure information
            Map<String, Object> structure = analyzeDocumentStructure(pdfDoc);
            context.put("structure", structure);

        } catch (IOException e) {
            log.error("Error analyzing document context for {}: {}", document.getFileId(), e.getMessage());
        }

        return context;
    }

    /**
     * Extract the document title.
     *
     * @param document The PDF document
     * @return The document title
     */
    private String extractDocumentTitle(PDDocument document) {
        // First try to get title from metadata
        String title = document.getDocumentInformation().getTitle();

        // If no title in metadata, try to extract from first page
        if (title == null || title.isEmpty()) {
            try {
                String firstPageText = textExtractor.extractTextFromPage(document, 0);
                String[] lines = firstPageText.split("\n");

                // Look for a good title candidate in the first few lines
                for (int i = 0; i < Math.min(5, lines.length); i++) {
                    String line = lines[i].trim();
                    if (line.length() > 10 && line.length() < 100 &&
                            !line.matches(".*\\d{4}.*") && // Avoid lines with dates
                            Character.isUpperCase(line.charAt(0))) {
                        return line;
                    }
                }

                // If no good candidate, use first non-empty line
                for (String line : lines) {
                    if (!line.trim().isEmpty()) {
                        return line.trim();
                    }
                }
            } catch (IOException e) {
                log.error("Error extracting document title", e);
            }
        }

        return title != null ? title : "Untitled Document";
    }

    /**
     * Extract table of contents entries.
     *
     * @param document The PDF document
     * @return List of TOC entries
     */
    private List<Map<String, Object>> extractTableOfContents(PDDocument document) {
        List<Map<String, Object>> toc = new ArrayList<>();

        try {
            // Look for a TOC in the first few pages
            for (int i = 0; i < Math.min(5, document.getNumberOfPages()); i++) {
                String pageText = textExtractor.extractTextFromPage(document, i);

                // Check if this page looks like a TOC
                if (pageText.toLowerCase().contains("contents") ||
                        pageText.toLowerCase().contains("table of contents")) {

                    // Extract TOC entries using regex
                    Matcher matcher = TOC_ENTRY_PATTERN.matcher(pageText);
                    while (matcher.find()) {
                        Map<String, Object> entry = new HashMap<>();

                        String number = matcher.group(1);
                        String title = matcher.group(3);
                        String page = matcher.group(4);

                        entry.put("number", number != null ? number : "");
                        entry.put("title", title);
                        entry.put("page", page);

                        toc.add(entry);
                    }

                    // If we found entries, break
                    if (!toc.isEmpty()) {
                        break;
                    }
                }
            }
        } catch (IOException e) {
            log.error("Error extracting table of contents", e);
        }

        return toc;
    }

    /**
     * Extract headings from the document.
     *
     * @param document The PDF document
     * @return List of headings
     */
    private List<Map<String, Object>> extractHeadings(PDDocument document) {
        List<Map<String, Object>> headings = new ArrayList<>();

        try {
            // Extract text from each page
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                String pageText = textExtractor.extractTextFromPage(document, i);
                String[] lines = pageText.split("\n");

                for (String line : lines) {
                    // Skip empty lines
                    if (line.trim().isEmpty()) {
                        continue;
                    }

                    // Check if line looks like a heading
                    Matcher matcher = HEADING_PATTERN.matcher(line);
                    if (matcher.matches()) {
                        Map<String, Object> heading = new HashMap<>();

                        String number = matcher.group(1);
                        String title = matcher.group(3);

                        heading.put("number", number != null ? number : "");
                        heading.put("title", title);
                        heading.put("page", i + 1);

                        headings.add(heading);
                    }
                }
            }
        } catch (IOException e) {
            log.error("Error extracting headings", e);
        }

        return headings;
    }

    /**
     * Summarize document content.
     *
     * @param document The PDF document
     * @return Content summary
     */
    private Map<String, Object> summarizeContent(PDDocument document) {
        Map<String, Object> summary = new HashMap<>();

        // Count total pages
        summary.put("pageCount", document.getNumberOfPages());

        // Count characters, words, paragraphs
        int totalCharacters = 0;
        int totalWords = 0;
        int totalParagraphs = 0;

        try {
            // Process each page
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                String pageText = textExtractor.extractTextFromPage(document, i);

                // Count characters (excluding whitespace)
                totalCharacters += pageText.replaceAll("\\s", "").length();

                // Count words
                String[] words = pageText.split("\\s+");
                totalWords += words.length;

                // Count paragraphs (crudely by double line breaks)
                String[] paragraphs = pageText.split("\\n\\s*\\n");
                totalParagraphs += paragraphs.length;
            }

            summary.put("characters", totalCharacters);
            summary.put("words", totalWords);
            summary.put("paragraphs", totalParagraphs);

            // Calculate average words per page
            double avgWordsPerPage = (double) totalWords / document.getNumberOfPages();
            summary.put("averageWordsPerPage", avgWordsPerPage);

        } catch (IOException e) {
            log.error("Error summarizing content", e);
        }

        return summary;
    }

    /**
     * Detect the primary language of the document.
     *
     * @param document The PDF document
     * @return The detected language
     */
    private String detectLanguage(PDDocument document) {
        // Simple language detection - just check for common words
        // In a real implementation, you'd use a proper language detection library

        Map<String, Integer> languageScores = new HashMap<>();
        languageScores.put("en", 0); // English
        languageScores.put("es", 0); // Spanish
        languageScores.put("fr", 0); // French
        languageScores.put("de", 0); // German

        try {
            // Sample text from first few pages
            StringBuilder sampleText = new StringBuilder();

            for (int i = 0; i < Math.min(3, document.getNumberOfPages()); i++) {
                String pageText = textExtractor.extractTextFromPage(document, i);
                sampleText.append(pageText).append(" ");

                if (sampleText.length() > 1000) {
                    break;
                }
            }

            String text = sampleText.toString().toLowerCase();

            // Check for English words
            for (String word : new String[]{"the", "and", "for", "with"}) {
                languageScores.put("en", languageScores.get("en") + countOccurrences(text, word));
            }

            // Check for Spanish words
            for (String word : new String[]{"el", "la", "los", "para"}) {
                languageScores.put("es", languageScores.get("es") + countOccurrences(text, word));
            }

            // Check for French words
            for (String word : new String[]{"le", "la", "les", "pour"}) {
                languageScores.put("fr", languageScores.get("fr") + countOccurrences(text, word));
            }

            // Check for German words
            for (String word : new String[]{"der", "die", "das", "und"}) {
                languageScores.put("de", languageScores.get("de") + countOccurrences(text, word));
            }

            // Find language with highest score
            String detectedLanguage = "en"; // Default to English
            int highestScore = languageScores.get("en");

            for (Map.Entry<String, Integer> entry : languageScores.entrySet()) {
                if (entry.getValue() > highestScore) {
                    highestScore = entry.getValue();
                    detectedLanguage = entry.getKey();
                }
            }

            return detectedLanguage;

        } catch (IOException e) {
            log.error("Error detecting language", e);
            return "en"; // Default to English
        }
    }

    /**
     * Analyze document structure.
     *
     * @param document The PDF document
     * @return Document structure information
     */
    private Map<String, Object> analyzeDocumentStructure(PDDocument document) {
        Map<String, Object> structure = new HashMap<>();

        try {
            // Check if document has page numbers
            boolean hasPageNumbers = false;
            int pageNumberFormat = 0; // 0=none, 1=top, 2=bottom

            // Check a few pages
            for (int i = 1; i < Math.min(5, document.getNumberOfPages()); i++) {
                String pageText = textExtractor.extractTextFromPage(document, i);
                String[] lines = pageText.split("\n");

                if (lines.length > 0) {
                    // Check first line
                    if (PAGE_NUMBER_PATTERN.matcher(lines[0].trim()).matches()) {
                        hasPageNumbers = true;
                        pageNumberFormat = 1;
                        break;
                    }

                    // Check last line
                    if (PAGE_NUMBER_PATTERN.matcher(lines[lines.length - 1].trim()).matches()) {
                        hasPageNumbers = true;
                        pageNumberFormat = 2;
                        break;
                    }
                }
            }

            structure.put("hasPageNumbers", hasPageNumbers);
            structure.put("pageNumberFormat", pageNumberFormat);

            // Check if document has headers or footers
            boolean hasHeader = false;
            boolean hasFooter = false;

            // Check a few pages
            for (int i = 1; i < Math.min(5, document.getNumberOfPages()); i++) {
                String pageText = textExtractor.extractTextFromPage(document, i);
                String[] lines = pageText.split("\n");

                if (lines.length > 2) {
                    // Check for consistent text in header (first line of each page)
                    if (!lines[0].trim().isEmpty() && lines[0].equals(
                            textExtractor.extractTextFromPage(document, i-1).split("\n")[0])) {
                        hasHeader = true;
                    }

                    // Check for consistent text in footer (last line of each page)
                    if (!lines[lines.length - 1].trim().isEmpty() && lines[lines.length - 1].equals(
                            textExtractor.extractTextFromPage(document, i-1).split("\n")[lines.length - 1])) {
                        hasFooter = true;
                    }
                }
            }

            structure.put("hasHeader", hasHeader);
            structure.put("hasFooter", hasFooter);

            // Check document sections
            List<Map<String, Object>> sections = new ArrayList<>();

            // Use headings to identify sections
            List<Map<String, Object>> headings = extractHeadings(document);
            if (!headings.isEmpty()) {
                for (int i = 0; i < headings.size() - 1; i++) {
                    Map<String, Object> section = new HashMap<>();
                    section.put("title", headings.get(i).get("title"));
                    section.put("startPage", headings.get(i).get("page"));
                    section.put("endPage", headings.get(i+1).get("page"));
                    sections.add(section);
                }

                // Last section
                if (!headings.isEmpty()) {
                    Map<String, Object> lastSection = new HashMap<>();
                    Map<String, Object> lastHeading = headings.get(headings.size() - 1);
                    lastSection.put("title", lastHeading.get("title"));
                    lastSection.put("startPage", lastHeading.get("page"));
                    lastSection.put("endPage", document.getNumberOfPages());
                    sections.add(lastSection);
                }
            }

            structure.put("sections", sections);

        } catch (IOException e) {
            log.error("Error analyzing document structure", e);
        }

        return structure;
    }

    /**
     * Count occurrences of a word in text.
     *
     * @param text Text to search in
     * @param word Word to search for
     * @return Number of occurrences
     */
    private int countOccurrences(String text, String word) {
        int count = 0;
        int index = 0;

        while ((index = text.indexOf(word, index)) != -1) {
            count++;
            index += word.length();
        }

        return count;
    }
}