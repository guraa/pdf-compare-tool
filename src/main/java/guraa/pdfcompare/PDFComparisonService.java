package guraa.pdfcompare;

import guraa.pdfcompare.core.*;
import guraa.pdfcompare.comparison.*;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for PDF comparison operations
 */
@Service
public class PDFComparisonService {

    // In-memory storage for comparison results
    // In a production environment, this should be replaced with a database
    private Map<String, PDFComparisonResult> comparisonResults = new ConcurrentHashMap<>();

    /**
     * Compare two PDF files
     * @param baseFilePath Path to the base PDF file
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
     * Get comparison result by ID
     * @param comparisonId The comparison ID
     * @return Comparison result, or null if not found
     */
    public PDFComparisonResult getComparisonResult(String comparisonId) {
        return comparisonResults.get(comparisonId);
    }
}