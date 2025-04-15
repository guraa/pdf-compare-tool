package guraa.pdfcompare.service;

import guraa.pdfcompare.comparison.TextElementDifference;
import guraa.pdfcompare.model.PdfDocument;
import guraa.pdfcompare.model.difference.TextDifference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import io.github.java_diff_utils.DiffUtils;
import io.github.java_diff_utils.patch.AbstractDelta;
import io.github.java_diff_utils.patch.Chunk;
import io.github.java_diff_utils.patch.DeltaType;
import io.github.java_diff_utils.patch.Patch;

/**
 * Service for comparing text elements between PDF documents.
 * This service extracts text from PDFs and uses diff algorithms to
 * detect differences.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TextElementComparisonService {

    private final ExecutorService executorService;
    private final PdfRenderingService pdfRenderingService;

    @Value("${app.comparison.text-similarity-threshold:0.8}")
    private double textSimilarityThreshold;

    // Cache of text comparison results
    private final ConcurrentHashMap<String, CompletableFuture<List<TextDifference>>> comparisonTasks = new ConcurrentHashMap<>();

    /**
     * Compare text elements between two pages.
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
        
        String cacheKey = baseDocument.getFileId() + "_" + basePageNumber + "_" +
                compareDocument.getFileId() + "_" + comparePageNumber;
        
        // Check if we already have a comparison task for these pages
        return comparisonTasks.computeIfAbsent(cacheKey, key -> {
            // Submit a new comparison task
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return doCompareText(baseDocument, compareDocument, basePageNumber, comparePageNumber);
                } catch (IOException e) {
                    log.error("Error comparing text: {}", e.getMessage(), e);
                    return new ArrayList<>();
                }
            }, executorService);
        }).join(); // Wait for the task to complete
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
        // In a real implementation, this would use iText to extract text from the PDF
        // For now, we'll check if there's an extracted text file and read it
        
        String extractedTextPath = document.getExtractedTextPath(pageNumber);
        File extractedTextFile = new File(extractedTextPath);
        
        if (extractedTextFile.exists()) {
            // Read the extracted text file
            try (BufferedReader reader = new BufferedReader(new FileReader(extractedTextFile))) {
                return reader.lines().collect(Collectors.toList());
            }
        } else {
            // No extracted text file, return an empty list
            // In a real implementation, this would extract text from the PDF
            return new ArrayList<>();
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
                .type("text");
        
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
                builder.description("Text added: " + targetChunk.getLines().get(0) + 
                        (targetChunk.size() > 1 ? "..." : ""));
                break;
            case DELETE:
                builder.description("Text deleted: " + sourceChunk.getLines().get(0) + 
                        (sourceChunk.size() > 1 ? "..." : ""));
                break;
            case CHANGE:
                builder.description("Text modified from: " + sourceChunk.getLines().get(0) + 
                        (sourceChunk.size() > 1 ? "..." : "") + 
                        " to: " + targetChunk.getLines().get(0) + 
                        (targetChunk.size() > 1 ? "..." : ""));
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
        // In a real implementation, this would use iText to extract text elements from the PDF
        // For now, we'll return an empty list
        return new ArrayList<>();
    }
}
