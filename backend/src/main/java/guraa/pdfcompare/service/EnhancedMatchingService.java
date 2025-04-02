package guraa.pdfcompare.service;

import guraa.pdfcompare.PDFComparisonEngine;
import guraa.pdfcompare.comparison.PageComparisonResult;
import guraa.pdfcompare.core.PDFDocumentModel;
import guraa.pdfcompare.core.PDFProcessor;
import guraa.pdfcompare.util.PDFComparisonLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Enhanced service for document matching and comparison with improved algorithms
 */
public class EnhancedMatchingService {
    private static final Logger logger = LoggerFactory.getLogger(EnhancedMatchingService.class);

    // Configured components
    private final PDFComparisonEngine comparisonEngine;
    private final PDFProcessor pdfProcessor;
    private final EnhancedPageMatcher pageMatcher;

    // Thread pool for async operations
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);

    // Results storage
    private final Map<String, List<PagePair>> matchingResults = new ConcurrentHashMap<>();
    private final Map<String, Map<Integer, PageComparisonResult>> pageResults = new ConcurrentHashMap<>();
    private final Map<String, String> comparisonStatus = new ConcurrentHashMap<>();
    private final Map<String, String> comparisonErrors = new ConcurrentHashMap<>();

    /**
     * Constructor with required dependencies
     * @param comparisonEngine The PDF comparison engine
     */
    @Autowired
    public EnhancedMatchingService(PDFComparisonEngine comparisonEngine) {
        this.comparisonEngine = comparisonEngine;
        this.pdfProcessor = new PDFProcessor();
        this.pageMatcher = new EnhancedPageMatcher();
    }

    // Rest of the class implementation...

    /**
     * Compare two PDF files with enhanced matching
     * @param baseFilePath Base file path
     * @param compareFilePath Compare file path
     * @return Comparison ID for retrieving results
     * @throws IOException If there's an error reading the files
     */
    public String compareFiles(String baseFilePath, String compareFilePath) throws IOException {
        // Generate a unique ID for this comparison
        String comparisonId = UUID.randomUUID().toString();
        comparisonStatus.put(comparisonId, "processing");

        // Start asynchronous comparison
        startAsyncComparison(comparisonId, baseFilePath, compareFilePath);

        return comparisonId;
    }

    /**
     * Start asynchronous comparison
     */
    private void startAsyncComparison(String comparisonId, String baseFilePath, String compareFilePath) {
        executorService.submit(() -> {
            try {
                // Log the start of comparison
                logger.info("Starting enhanced comparison {} between {} and {}",
                        comparisonId, baseFilePath, compareFilePath);

                // Validate input files
                File baseFile = new File(baseFilePath);
                File compareFile = new File(compareFilePath);

                validateFiles(baseFile, compareFile);

                // Process the PDF documents
                PDFDocumentModel baseDocument = pdfProcessor.processDocument(baseFile);
                PDFDocumentModel compareDocument = pdfProcessor.processDocument(compareFile);

                // Log document info
                logger.info("Base document: {} pages, Compare document: {} pages",
                        baseDocument.getPageCount(), compareDocument.getPageCount());

                // Use page-level matching
                performPageLevelMatching(comparisonId, baseDocument, compareDocument);

                // Update status to completed
                comparisonStatus.put(comparisonId, "completed");
                logger.info("Enhanced comparison {} completed successfully", comparisonId);

            } catch (Exception e) {
                logger.error("Error in enhanced comparison {}: {}", comparisonId, e.getMessage(), e);
                comparisonStatus.put(comparisonId, "failed");
                comparisonErrors.put(comparisonId, e.getMessage());
            }
        });
    }

    /**
     * Validate input files
     */
    private void validateFiles(File baseFile, File compareFile) throws IOException {
        if (!baseFile.exists() || !baseFile.canRead()) {
            throw new IOException("Base file does not exist or cannot be read: " + baseFile.getAbsolutePath());
        }
        if (!compareFile.exists() || !compareFile.canRead()) {
            throw new IOException("Compare file does not exist or cannot be read: " + compareFile.getAbsolutePath());
        }

        // Log file information
        PDFComparisonLogger.logPdfFileInfo(baseFile);
        PDFComparisonLogger.logPdfFileInfo(compareFile);
    }

    /**
     * Perform page-level matching
     */
    private void performPageLevelMatching(String comparisonId, PDFDocumentModel baseDocument,
                                          PDFDocumentModel compareDocument) {
        logger.info("Using page-level matching strategy for comparison {}", comparisonId);

        // Match pages using the enhanced matcher
        List<PagePair> pagePairs = pageMatcher.matchPages(baseDocument, compareDocument);

        // Store the matching results
        matchingResults.put(comparisonId, pagePairs);

        // Compare matched pages
        compareMatchedPages(comparisonId, pagePairs, baseDocument, compareDocument);
    }

    /**
     * Compare matched pages
     */
    private void compareMatchedPages(String comparisonId, List<PagePair> pagePairs,
                                     PDFDocumentModel baseDocument, PDFDocumentModel compareDocument) {
        // Implementation details omitted for brevity
        // This would compare each matched page pair and store results
    }

    /**
     * Check if a comparison is ready
     */
    public boolean isComparisonReady(String comparisonId) {
        String status = comparisonStatus.get(comparisonId);
        return "completed".equals(status) || "failed".equals(status);
    }

    /**
     * Get comparison status
     */
    public String getComparisonStatus(String comparisonId) {
        return comparisonStatus.getOrDefault(comparisonId, "not_found");
    }

    /**
     * Get error message for a failed comparison
     */
    public String getComparisonError(String comparisonId) {
        return comparisonErrors.get(comparisonId);
    }

    /**
     * Get page matching results
     */
    public List<PagePair> getPagePairs(String comparisonId) {
        return matchingResults.getOrDefault(comparisonId, Collections.emptyList());
    }

    /**
     * Get page comparison result
     */
    public PageComparisonResult getPageResult(String comparisonId, int pairIndex) {
        Map<Integer, PageComparisonResult> results = pageResults.get(comparisonId);
        if (results == null) {
            return null;
        }
        return results.get(pairIndex);
    }

    /**
     * Get summary statistics for a comparison
     */
    public Map<String, Object> getComparisonSummary(String comparisonId) {
        Map<String, Object> summary = new HashMap<>();
        // Implementation details omitted for brevity
        return summary;
    }
}
