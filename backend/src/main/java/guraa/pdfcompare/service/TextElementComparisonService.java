package guraa.pdfcompare.service;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Chunk;
import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Chunk;
import com.github.difflib.patch.DeltaType;
import com.github.difflib.patch.Patch;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.itextpdf.kernel.pdf.canvas.parser.listener.LocationTextExtractionStrategy;
import guraa.pdfcompare.comparison.TextElementDifference;
import guraa.pdfcompare.model.PdfDocument;
import guraa.pdfcompare.model.difference.TextDifference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
// Removed: import java.util.concurrent.CompletableFuture;
// Removed: import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/**
 * Service for comparing text elements between PDF documents.
 * This service extracts text from PDFs and uses diff algorithms to
 * detect differences.
 */
@Slf4j
@Service
public class TextElementComparisonService {

    private final ExecutorService executorService;
    private final PdfRenderingService pdfRenderingService;
    
    /**
     * Constructor with qualifier to specify which executor service to use.
     * 
     * @param executorService The executor service for comparison operations
     * @param pdfRenderingService The PDF rendering service
     */
    public TextElementComparisonService(
            @Qualifier("comparisonExecutor") ExecutorService executorService, // Keep executor if needed elsewhere, or remove if only used for removed cache
            PdfRenderingService pdfRenderingService) {
        this.executorService = executorService; // Keep or remove based on usage
        this.pdfRenderingService = pdfRenderingService;
    }

    @Value("${app.comparison.text-similarity-threshold:0.8}")
    private double textSimilarityThreshold;

    // Removed cache: private final ConcurrentHashMap<String, CompletableFuture<List<TextDifference>>> comparisonTasks = new ConcurrentHashMap<>();

    /**
     * Compare text elements between two pages synchronously.
     * The PDFComparisonEngine handles the asynchronous execution.
     *
     * @param baseDocument The base document
     * @param compareDocument The document to compare against the base
     * @param basePageNumber The page number in the base document (1-based)
     * @param comparePageNumber The page number in the compare document (1-based)
     * @return A list of text differences
     * @throws IOException If there is an error comparing the text
     */
    public List<TextDifference> compareText(
            PdfDocument baseDocument, PdfDocument compareDocument,
            int basePageNumber, int comparePageNumber) throws IOException {

        // Directly perform the comparison without internal caching or async execution
        // The PDFComparisonEngine is responsible for calling this asynchronously.
        try {
            return doCompareText(baseDocument, compareDocument, basePageNumber, comparePageNumber);
        } catch (IOException e) {
            log.error("Error comparing text for pages {} and {}: {}", basePageNumber, comparePageNumber, e.getMessage(), e);
            // Re-throw or return empty list based on how PDFComparisonEngine handles exceptions
            throw e; 
        }
    }

    /**
     * Perform the actual text comparison.
     *
     * @param baseDocument The base document
     * @param compareDocument The document to compare against the base
     * @param basePageNumber The page number in the base document (1-based)
     * @param comparePageNumber The page number in the compare document (1-based)
     * @return A list of text differences
     * @throws IOException If there is an error comparing the text
     */
    private List<TextDifference> doCompareText(
            PdfDocument baseDocument, PdfDocument compareDocument,
            int basePageNumber, int comparePageNumber) throws IOException {

        // Extract text from the pages
        List<String> baseLines = extractTextLines(baseDocument, basePageNumber);
        List<String> compareLines = extractTextLines(compareDocument, comparePageNumber);

        // Calculate the diff
        Patch<String> patch = DiffUtils.diff(baseLines, compareLines);

        // Convert the diff to text differences
        List<TextDifference> differences = new ArrayList<>();

        for (AbstractDelta<String> delta : patch.getDeltas()) {
            TextDifference difference = createTextDifference(delta, basePageNumber, comparePageNumber);
            differences.add(difference);
        }

        return differences;
    }

    /**
     * Extract text lines from a page.
     *
     * @param document The document
     * @param pageNumber The page number (1-based)
     * @return A list of text lines
     * @throws IOException If there is an error extracting the text
     */
    private List<String> extractTextLines(PdfDocument document, int pageNumber) throws IOException {
        // In a real implementation, this would use iText or PDFBox to extract text from the PDF
        // For now, we'll check if there's an extracted text file and read it

        String extractedTextPath = document.getExtractedTextPath(pageNumber);
        File extractedTextFile = new File(extractedTextPath);

        if (extractedTextFile.exists()) {
            // Read the extracted text file
            try (BufferedReader reader = new BufferedReader(new FileReader(extractedTextFile))) {
                return reader.lines().collect(Collectors.toList());
            }
        } else {
            // No extracted text file, ensure the directories exist
            Path textDir = Paths.get(extractedTextPath).getParent();
            if (textDir != null && !Files.exists(textDir)) {
                Files.createDirectories(textDir);
            }

            // --- iText Extraction Logic ---
            log.debug("Extracted text file not found for document {} page {}. Extracting using iText.",
                    document.getFileId(), pageNumber);
            try {
                PdfReader reader = new PdfReader(document.getFilePath());
                // Use try-with-resources for the iText PdfDocument
                try (com.itextpdf.kernel.pdf.PdfDocument pdfDoc = new com.itextpdf.kernel.pdf.PdfDocument(reader)) {
                    if (pageNumber > 0 && pageNumber <= pdfDoc.getNumberOfPages()) {
                        PdfPage page = pdfDoc.getPage(pageNumber);
                        String text = PdfTextExtractor.getTextFromPage(page, new LocationTextExtractionStrategy());
                        
                        // Split text into lines using a regex that handles different line endings
                        List<String> lines = Arrays.asList(text.split("\\R")); 
                        
                        // Optional: Save the extracted text to the file for future use
                        // try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(extractedTextPath))) {
                        //     writer.write(text);
                        // } catch (IOException writeEx) {
                        //     log.warn("Could not write extracted text to cache file {}: {}", extractedTextPath, writeEx.getMessage());
                        // }

                        return lines;
                    } else {
                        log.warn("Invalid page number {} requested for document {} with {} pages.",
                                pageNumber, document.getFileId(), pdfDoc.getNumberOfPages());
                        return new ArrayList<>(); // Return empty list for invalid page number
                    }
                }
            } catch (IOException e) {
                log.error("Error extracting text using iText for document {} page {}: {}",
                        document.getFileId(), pageNumber, e.getMessage(), e);
                // Re-throw or return empty list depending on desired error handling
                throw new IOException("Failed to extract text using iText", e); 
            }
            // --- End iText Extraction Logic ---
        }
    }

    /**
     * Create a text difference from a diff delta.
     *
     * @param delta The diff delta
     * @param basePageNumber The page number in the base document (1-based)
     * @param comparePageNumber The page number in the compare document (1-based)
     * @return The text difference
     */
    private TextDifference createTextDifference(
            AbstractDelta<String> delta, int basePageNumber, int comparePageNumber) {

        TextDifference.TextDifferenceBuilder builder = TextDifference.builder()
                .id(UUID.randomUUID().toString())
                .type("text")
                .basePageNumber(basePageNumber)
                .comparePageNumber(comparePageNumber);

        // Set change type based on delta type
        DeltaType deltaType = delta.getType();
        switch (deltaType) {
            case INSERT:
                builder.changeType("added");
                break;
            case DELETE:
                builder.changeType("deleted");
                break;
            case CHANGE:
                builder.changeType("modified");
                break;
        }

        // Set text content
        Chunk<String> sourceChunk = delta.getSource();
        Chunk<String> targetChunk = delta.getTarget();

        if (deltaType == DeltaType.DELETE || deltaType == DeltaType.CHANGE) {
            // Set base text
            String baseText = String.join("\n", sourceChunk.getLines());
            builder.baseText(baseText);
        }

        if (deltaType == DeltaType.INSERT || deltaType == DeltaType.CHANGE) {
            // Set compare text
            String compareText = String.join("\n", targetChunk.getLines());
            builder.compareText(compareText);
        }

        // Set position (in a real implementation, this would be more accurate)
        builder.startIndex(sourceChunk.getPosition());
        builder.endIndex(sourceChunk.getPosition() + sourceChunk.size());
        builder.length(sourceChunk.size());

        // Set severity based on the amount of text changed
        int baseLength = sourceChunk.getLines().size();
        int compareLength = targetChunk.getLines().size();
        int maxLength = Math.max(baseLength, compareLength);

        if (maxLength > 10) {
            builder.severity("major");
        } else if (maxLength > 3) {
            builder.severity("minor");
        } else {
            builder.severity("cosmetic");
        }

        // Set description based on change type
        switch (deltaType) {
            case INSERT:
                builder.description("Text added: " + (targetChunk.getLines().isEmpty() ? "" :
                        targetChunk.getLines().get(0) + (targetChunk.size() > 1 ? "..." : "")));
                break;
            case DELETE:
                builder.description("Text deleted: " + (sourceChunk.getLines().isEmpty() ? "" :
                        sourceChunk.getLines().get(0) + (sourceChunk.size() > 1 ? "..." : "")));
                break;
            case CHANGE:
                builder.description("Text modified from: " + (sourceChunk.getLines().isEmpty() ? "" :
                        sourceChunk.getLines().get(0) + (sourceChunk.size() > 1 ? "..." : "")) +
                        " to: " + (targetChunk.getLines().isEmpty() ? "" :
                        targetChunk.getLines().get(0) + (targetChunk.size() > 1 ? "..." : "")));
                break;
        }

        return builder.build();
    }

    /**
     * Compare text elements between two documents.
     *
     * @param baseDocument The base document
     * @param compareDocument The document to compare against the base
     * @param pagePairs The page pairs to compare
     * @return A map of page pairs to text differences
     * @throws IOException If there is an error comparing the text
     */
    public Map<PagePair, List<TextDifference>> compareText(
            PdfDocument baseDocument, PdfDocument compareDocument,
            List<PagePair> pagePairs) throws IOException {

        Map<PagePair, List<TextDifference>> result = new HashMap<>();

        for (PagePair pagePair : pagePairs) {
            if (pagePair.isMatched()) {
                // Compare the matched pages
                List<TextDifference> differences = compareText(
                        baseDocument, compareDocument,
                        pagePair.getBasePageNumber(), pagePair.getComparePageNumber());

                result.put(pagePair, differences);
            }
        }

        return result;
    }

    /**
     * Get text elements from a page.
     *
     * @param document The document
     * @param pageNumber The page number (1-based)
     * @return A list of text elements
     * @throws IOException If there is an error extracting the text elements
     */
    public List<TextElementDifference> getTextElements(PdfDocument document, int pageNumber) throws IOException {
        // In a real implementation, this would use iText or PDFBox to extract text elements from the PDF
        // For now, we'll return an empty list
        return new ArrayList<>();
    }
}
