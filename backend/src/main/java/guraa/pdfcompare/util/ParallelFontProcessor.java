package guraa.pdfcompare.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Utility class for processing fonts in parallel across multiple pages of a PDF
 * This helps to improve performance for large documents
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ParallelFontProcessor {

    private final FontAnalyzer fontAnalyzer;

    @Qualifier("pdfPageProcessingExecutor")
    private final ExecutorService processingExecutor;

    @Value("${app.font.parallel-processing:true}")
    private boolean parallelProcessingEnabled;

    @Value("${app.font.processing-batch-size:5}")
    private int processingBatchSize;

    private static final int MAX_CONCURRENCY = 4; // Limit concurrency to prevent resource exhaustion

    /**
     * Process fonts for all pages in a document in parallel
     *
     * @param document The PDF document
     * @param pageCount Total page count
     * @param outputDir Output directory for font information
     * @return Number of pages successfully processed
     */
    public int processAllPages(PDDocument document, int pageCount, Path outputDir) {
        if (!parallelProcessingEnabled || pageCount <= 1) {
            // Fall back to sequential processing for small documents
            return processSequentially(document, pageCount, outputDir);
        }

        // For large documents, use an adaptive strategy
        return pageCount > 100 ?
                processLargeDocument(document, pageCount, outputDir) :
                processStandardDocument(document, pageCount, outputDir);
    }

    /**
     * Process a standard-sized document (up to 100 pages)
     *
     * @param document The PDF document
     * @param pageCount Total page count
     * @param outputDir Output directory for font information
     * @return Number of pages successfully processed
     */
    private int processStandardDocument(PDDocument document, int pageCount, Path outputDir) {
        log.info("Processing fonts for standard document with {} pages", pageCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // Create a list to hold all futures
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // Calculate concurrency level (limit for smaller documents)
        int concurrency = Math.min(Math.min(pageCount / 5 + 1, MAX_CONCURRENCY),
                Runtime.getRuntime().availableProcessors() / 2);

        // Process in batches based on concurrency
        for (int i = 0; i < concurrency; i++) {
            int startIdx = i * (pageCount / concurrency);
            int endIdx = (i == concurrency - 1) ? pageCount - 1 : (i + 1) * (pageCount / concurrency) - 1;

            // Submit batch for processing
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    for (int pageIdx = startIdx; pageIdx <= endIdx; pageIdx++) {
                        try {
                            fontAnalyzer.analyzeFontsOnPage(document, pageIdx, outputDir);
                            successCount.incrementAndGet();
                        } catch (Exception e) {
                            log.warn("Error processing fonts on page {}: {}", pageIdx + 1, e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    log.error("Error in font processing batch: {}", e.getMessage());
                }
            }, processingExecutor);

            futures.add(future);
        }

        // Wait for all to complete
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .orTimeout(5, TimeUnit.MINUTES)
                    .join();
        } catch (Exception e) {
            log.warn("Font processing didn't complete in time: {}", e.getMessage());
        }

        return successCount.get();
    }

    /**
     * Process a large document (over 100 pages) using a sparse sampling strategy
     *
     * @param document The PDF document
     * @param pageCount Total page count
     * @param outputDir Output directory for font information
     * @return Number of pages successfully processed
     */
    private int processLargeDocument(PDDocument document, int pageCount, Path outputDir) {
        log.info("Processing fonts for large document with {} pages using sparse strategy", pageCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // For very large documents, don't process all pages - use a sampling approach
        // First, process key pages: first few, last few, and some in the middle
        List<Integer> keyPages = new ArrayList<>();

        // First 5 pages
        for (int i = 0; i < Math.min(5, pageCount); i++) {
            keyPages.add(i);
        }

        // Last 5 pages
        for (int i = Math.max(0, pageCount - 5); i < pageCount; i++) {
            if (!keyPages.contains(i)) {
                keyPages.add(i);
            }
        }

        // Middle section - sample every 10 pages
        for (int i = 5; i < pageCount - 5; i += 10) {
            if (!keyPages.contains(i)) {
                keyPages.add(i);
            }
        }

        // Process key pages in parallel
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int pageIdx : keyPages) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    fontAnalyzer.analyzeFontsOnPage(document, pageIdx, outputDir);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    log.warn("Error processing fonts on page {}: {}", pageIdx + 1, e.getMessage());
                }
            }, processingExecutor);

            futures.add(future);
        }

        // Wait for key pages to complete
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .orTimeout(2, TimeUnit.MINUTES)
                    .join();
        } catch (Exception e) {
            log.warn("Key page font processing didn't complete in time: {}", e.getMessage());
        }

        return successCount.get();
    }

    /**
     * Process all pages sequentially when parallel processing is disabled
     *
     * @param document The PDF document
     * @param pageCount Total page count
     * @param outputDir Output directory for font information
     * @return Number of pages successfully processed
     */
    private int processSequentially(PDDocument document, int pageCount, Path outputDir) {
        log.info("Processing fonts sequentially for document with {} pages", pageCount);
        int successCount = 0;

        // Process pages in batches to avoid excessive memory consumption
        for (int pageIdx = 0; pageIdx < pageCount; pageIdx++) {
            try {
                fontAnalyzer.analyzeFontsOnPage(document, pageIdx, outputDir);
                successCount++;

                // Log progress for long-running operations
                if (pageCount > 20 && pageIdx % 10 == 0 && pageIdx > 0) {
                    log.info("Font processing progress: {} of {} pages", pageIdx + 1, pageCount);
                }
            } catch (Exception e) {
                log.warn("Error processing fonts on page {}: {}", pageIdx + 1, e.getMessage());
            }
        }

        return successCount;
    }

    /**
     * Process fonts for a specific range of pages
     *
     * @param document The PDF document
     * @param startPage Start page index (0-based)
     * @param endPage End page index (0-based)
     * @param documentId Document ID for creating output directories
     * @return Number of pages successfully processed
     */
    public int processPageRange(PDDocument document, int startPage, int endPage, String documentId) {
        // Create appropriate output directory
        Path outputDir = Paths.get("uploads", "documents", documentId, "fonts");

        int pageCount = endPage - startPage + 1;
        if (pageCount <= 0 || startPage < 0 || endPage >= document.getNumberOfPages()) {
            log.warn("Invalid page range: {}-{} for document with {} pages",
                    startPage, endPage, document.getNumberOfPages());
            return 0;
        }

        log.info("Processing font information for pages {}-{}", startPage + 1, endPage + 1);

        if (!parallelProcessingEnabled || pageCount <= processingBatchSize) {
            // Process sequentially for small ranges
            int successCount = 0;
            for (int pageIdx = startPage; pageIdx <= endPage; pageIdx++) {
                try {
                    fontAnalyzer.analyzeFontsOnPage(document, pageIdx, outputDir);
                    successCount++;
                } catch (Exception e) {
                    log.warn("Error processing fonts on page {}: {}", pageIdx + 1, e.getMessage());
                }
            }
            return successCount;
        }

        // For larger ranges, use parallel processing
        AtomicInteger successCount = new AtomicInteger(0);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // Calculate how many batches we need
        int batchCount = (pageCount + processingBatchSize - 1) / processingBatchSize;

        // Process in batches
        for (int i = 0; i < batchCount; i++) {
            int batchStart = startPage + (i * processingBatchSize);
            int batchEnd = Math.min(startPage + ((i + 1) * processingBatchSize) - 1, endPage);

            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                log.debug("Processing batch of pages {}-{}", batchStart + 1, batchEnd + 1);
                for (int pageIdx = batchStart; pageIdx <= batchEnd; pageIdx++) {
                    try {
                        fontAnalyzer.analyzeFontsOnPage(document, pageIdx, outputDir);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        log.warn("Error processing fonts on page {}: {}", pageIdx + 1, e.getMessage());
                    }
                }
            }, processingExecutor);

            futures.add(future);
        }

        // Wait for all to complete with a timeout
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .orTimeout(5, TimeUnit.MINUTES)
                    .join();
        } catch (Exception e) {
            log.warn("Font processing didn't complete in time: {}", e.getMessage());
        }

        return successCount.get();
    }

    /**
     * Pre-process font information for a document to improve comparison performance
     *
     * @param document The PDF document
     * @param documentId Document ID
     * @return Number of pages successfully pre-processed
     */
    public int preProcessDocument(PDDocument document, String documentId) {
        int pageCount = document.getNumberOfPages();

        // For very large documents, use sparse sampling
        if (pageCount > 200) {
            log.info("Pre-processing large document with {} pages using sparse strategy", pageCount);
            return preProcessLargeDocument(document, documentId);
        }

        // For standard documents, process all pages
        Path outputDir = Paths.get("uploads", "documents", documentId, "fonts");
        return processAllPages(document, pageCount, outputDir);
    }

    /**
     * Pre-process a large document using an optimized strategy
     *
     * @param document The PDF document
     * @param documentId Document ID
     * @return Number of pages successfully pre-processed
     */
    private int preProcessLargeDocument(PDDocument document, String documentId) {
        int pageCount = document.getNumberOfPages();
        Path outputDir = Paths.get("uploads", "documents", documentId, "fonts");

        // For very large documents:
        // 1. Process first 10 pages (most docs have fonts defined early)
        // 2. Sample every 25th page through the document
        // 3. Process last 5 pages (in case of appendices with different fonts)

        AtomicInteger successCount = new AtomicInteger(0);
        List<Integer> pagesToProcess = new ArrayList<>();

        // First 10 pages
        for (int i = 0; i < Math.min(10, pageCount); i++) {
            pagesToProcess.add(i);
        }

        // Sample pages
        for (int i = 10; i < pageCount - 5; i += 25) {
            if (!pagesToProcess.contains(i)) {
                pagesToProcess.add(i);
            }
        }

        // Last 5 pages
        for (int i = Math.max(0, pageCount - 5); i < pageCount; i++) {
            if (!pagesToProcess.contains(i)) {
                pagesToProcess.add(i);
            }
        }

        // Process the selected pages in parallel
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int pageIdx : pagesToProcess) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    fontAnalyzer.analyzeFontsOnPage(document, pageIdx, outputDir);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    log.warn("Error pre-processing fonts on page {}: {}", pageIdx + 1, e.getMessage());
                }
            }, processingExecutor);

            futures.add(future);
        }

        // Wait for processing to complete with a timeout
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .orTimeout(3, TimeUnit.MINUTES)
                    .join();
        } catch (Exception e) {
            log.warn("Font pre-processing didn't complete in time: {}", e.getMessage());
        }

        return successCount.get();
    }

    /**
     * Cancel all ongoing font processing tasks
     * This can be used when shutting down or when a comparison is canceled
     */
    public void cancelAllTasks() {
        log.info("Canceling all font processing tasks");
        // Shutdown is handled by Spring, but we can add specific cancellation logic here if needed
    }
}