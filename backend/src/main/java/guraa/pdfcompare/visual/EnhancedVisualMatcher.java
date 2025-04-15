package guraa.pdfcompare.visual;

import guraa.pdfcompare.model.PdfDocument;
import guraa.pdfcompare.service.PagePair;
import guraa.pdfcompare.service.PdfRenderingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Enhanced visual matcher for PDF pages with improved error handling, threading, and caching.
 * This class uses SSIM (Structural Similarity Index) to match pages
 * between two PDF documents based on visual similarity.
 */
@Slf4j
@Component
public class EnhancedVisualMatcher implements VisualMatcher {

    private final SSIMCalculator ssimCalculator;
    private final PdfRenderingService pdfRenderingService;
    private final ExecutorService executorService;

    // Cache of rendered pages to avoid repeated file I/O
    private final ConcurrentHashMap<String, BufferedImage> imageCache = new ConcurrentHashMap<>();

    // Set a maximum size for the cache to avoid memory issues
    private static final int MAX_CACHE_SIZE = 50;

    /**
     * Constructor with qualifiers to specify which beans to use.
     *
     * @param ssimCalculator The SSIM calculator for image comparison
     * @param pdfRenderingService The PDF rendering service
     * @param executorService The executor service for comparison operations
     */
    public EnhancedVisualMatcher(
            SSIMCalculator ssimCalculator,
            PdfRenderingService pdfRenderingService,
            @Qualifier("comparisonExecutor") ExecutorService executorService) {
        this.ssimCalculator = ssimCalculator;
        this.pdfRenderingService = pdfRenderingService;
        this.executorService = executorService;
    }

    @Value("${app.matching.visual-similarity-threshold:0.7}")
    private double visualSimilarityThreshold;

    @Value("${app.matching.max-candidates-per-page:5}")
    private int maxCandidatesPerPage;

    @Value("${app.matching.max-concurrent-comparisons:8}")
    private int maxConcurrentComparisons;

    @Value("${app.matching.retry-count:3}")
    private int retryCount;

    @Value("${app.matching.retry-delay-ms:50}")
    private int retryDelayMs;

    // Cache of similarity scores
    private final ConcurrentHashMap<String, Double> similarityCache = new ConcurrentHashMap<>();

    @Override
    public List<PagePair> matchPages(PdfDocument baseDocument, PdfDocument compareDocument) throws IOException {
        log.info("Starting visual matching between documents: {} and {}",
                baseDocument.getFileId(), compareDocument.getFileId());

        long startTime = System.currentTimeMillis();

        try {
            // Pre-render all pages - this is done in parallel for both documents
            CompletableFuture<Void> preRenderingFuture = preRenderPages(baseDocument, compareDocument);

            // Calculate similarity scores for all page pairs - start this while pages are still rendering
            Map<String, Double> similarityScores = calculateSimilarityScores(baseDocument, compareDocument);

            // Wait for pre-rendering to complete if it hasn't already
            try {
                preRenderingFuture.get(60, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("Timeout waiting for page pre-rendering. Continuing with partial results.");
            }

            // Match pages using the Hungarian algorithm
            List<PagePair> pagePairs = matchPagesUsingHungarian(baseDocument, compareDocument, similarityScores);

            long endTime = System.currentTimeMillis();
            log.info("Completed visual matching between documents: {} and {} in {}ms",
                    baseDocument.getFileId(), compareDocument.getFileId(), (endTime - startTime));

            // Clear the image cache after matching to free memory
            clearImageCache();

            return pagePairs;
        } catch (Exception e) {
            log.error("Error during visual matching: {}", e.getMessage(), e);
            throw new IOException("Visual matching failed", e);
        }
    }

    /**
     * Pre-render all pages of both documents.
     *
     * @param baseDocument The base document
     * @param compareDocument The compare document
     * @return A CompletableFuture representing the pre-rendering task
     */
    private CompletableFuture<Void> preRenderPages(PdfDocument baseDocument, PdfDocument compareDocument) {
        // Submit tasks to pre-render all pages
        CompletableFuture<Void> baseRenderingFuture = CompletableFuture.runAsync(() -> {
            try {
                pdfRenderingService.preRenderAllPages(baseDocument);
            } catch (IOException e) {
                log.error("Error pre-rendering base document: {}", e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }, executorService);

        CompletableFuture<Void> compareRenderingFuture = CompletableFuture.runAsync(() -> {
            try {
                pdfRenderingService.preRenderAllPages(compareDocument);
            } catch (IOException e) {
                log.error("Error pre-rendering compare document: {}", e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }, executorService);

        // Combine and return both futures
        return CompletableFuture.allOf(baseRenderingFuture, compareRenderingFuture);
    }

    /**
     * Calculate similarity scores for all page pairs with improved error handling.
     *
     * @param baseDocument The base document
     * @param compareDocument The compare document
     * @return A map of page pair keys to similarity scores
     */
    private Map<String, Double> calculateSimilarityScores(PdfDocument baseDocument, PdfDocument compareDocument) {
        Map<String, Double> similarityScores = new ConcurrentHashMap<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // Use a semaphore to limit concurrent comparisons
        Semaphore semaphore = new Semaphore(maxConcurrentComparisons);

        // Track progress for logging
        AtomicInteger completedComparisons = new AtomicInteger(0);
        int totalComparisons = baseDocument.getPageCount() * compareDocument.getPageCount();

        // For each page in the base document
        for (int basePageNumber = 1; basePageNumber <= baseDocument.getPageCount(); basePageNumber++) {
            final int basePageNum = basePageNumber;

            // For each page in the compare document
            for (int comparePageNumber = 1; comparePageNumber <= compareDocument.getPageCount(); comparePageNumber++) {
                final int comparePageNum = comparePageNumber;

                // Create a key for this page pair
                String key = baseDocument.getFileId() + "_" + basePageNum + "_" +
                        compareDocument.getFileId() + "_" + comparePageNum;

                // Check if we already have a similarity score for this page pair
                if (similarityCache.containsKey(key)) {
                    similarityScores.put(key, similarityCache.get(key));
                    completedComparisons.incrementAndGet();
                    continue;
                }

                // Submit a task to calculate the similarity score
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        // Acquire permit from semaphore to limit concurrent operations
                        semaphore.acquire();

                        try {
                            double similarity = calculateSimilarityWithRetry(baseDocument, compareDocument, basePageNum, comparePageNum);

                            // Cache the score
                            similarityCache.put(key, similarity);
                            similarityScores.put(key, similarity);

                            // Log progress periodically
                            int completed = completedComparisons.incrementAndGet();
                            if (completed % 10 == 0 || completed == totalComparisons) {
                                log.info("Comparison progress: {}/{} ({} %)",
                                        completed, totalComparisons, (completed * 100) / totalComparisons);
                            }
                        } finally {
                            // Always release the permit
                            semaphore.release();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.error("Thread interrupted while waiting for semaphore", e);
                    } catch (Exception e) {
                        log.error("Error calculating similarity for pages {} and {}: {}",
                                basePageNum, comparePageNum, e.getMessage());
                        // Use a low similarity score as fallback
                        similarityScores.put(key, 0.0);
                        completedComparisons.incrementAndGet();
                    }
                }, executorService);

                futures.add(future);
            }
        }

        // Wait for all tasks to complete with timeout handling
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(2, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("Error or timeout waiting for similarity calculations to complete", e);
            // Continue with partial results rather than failing completely
        }

        return similarityScores;
    }

    /**
     * Calculate similarity with retry mechanism to handle transient errors.
     *
     * @param baseDocument The base document
     * @param compareDocument The compare document
     * @param basePageNum The base page number
     * @param comparePageNum The compare page number
     * @return The similarity score
     * @throws IOException If all retry attempts fail
     */
    private double calculateSimilarityWithRetry(
            PdfDocument baseDocument, PdfDocument compareDocument,
            int basePageNum, int comparePageNum) throws IOException {

        IOException lastException = null;

        for (int attempt = 0; attempt < retryCount; attempt++) {
            try {
                // Get the rendered page images
                BufferedImage baseImage = getPageImage(baseDocument, basePageNum);
                BufferedImage compareImage = getPageImage(compareDocument, comparePageNum);

                // Check if images were loaded successfully
                if (baseImage == null) {
                    throw new IOException("Failed to load base image for page " + basePageNum);
                }

                if (compareImage == null) {
                    throw new IOException("Failed to load compare image for page " + comparePageNum);
                }

                // Calculate the similarity score
                return ssimCalculator.calculate(baseImage, compareImage);
            } catch (IOException e) {
                lastException = e;
                log.warn("Attempt {} failed for pages {} and {}: {}",
                        attempt + 1, basePageNum, comparePageNum, e.getMessage());

                if (attempt < retryCount - 1) {
                    // Sleep before retrying
                    try {
                        Thread.sleep(retryDelayMs * (attempt + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Thread interrupted during retry delay", ie);
                    }
                }
            }
        }

        // All retries failed
        throw new IOException("Failed to calculate similarity after " + retryCount + " attempts", lastException);
    }

    /**
     * Get the rendered page image with caching.
     *
     * @param document The document
     * @param pageNumber The page number
     * @return The rendered page image
     * @throws IOException If there is an error loading the image
     */
    private BufferedImage getPageImage(PdfDocument document, int pageNumber) throws IOException {
        String cacheKey = document.getFileId() + "_" + pageNumber;

        // Check if the image is already in the cache
        BufferedImage cachedImage = imageCache.get(cacheKey);
        if (cachedImage != null) {
            return cachedImage;
        }

        // Get the rendered page
        File pageFile = pdfRenderingService.renderPage(document, pageNumber);

        // Ensure file exists and is readable
        if (!pageFile.exists() || !pageFile.canRead()) {
            throw new IOException("Page file does not exist or is not readable: " + pageFile.getPath());
        }

        // Check file size to avoid empty files
        if (pageFile.length() == 0) {
            throw new IOException("Page file is empty: " + pageFile.getPath());
        }

        // Load the image
        BufferedImage image = ImageIO.read(pageFile);

        // Check if the image was loaded successfully
        if (image == null) {
            throw new IOException("Failed to load image: " + pageFile.getPath());
        }

        // Cache the image if there's room in the cache
        if (imageCache.size() < MAX_CACHE_SIZE) {
            imageCache.put(cacheKey, image);
        }

        return image;
    }

    /**
     * Clear the image cache to free memory.
     */
    private void clearImageCache() {
        imageCache.clear();
    }

    /**
     * Match pages using the Hungarian algorithm.
     *
     * @param baseDocument The base document
     * @param compareDocument The compare document
     * @param similarityScores The similarity scores for all page pairs
     * @return A list of page pairs
     */
    private List<PagePair> matchPagesUsingHungarian(
            PdfDocument baseDocument, PdfDocument compareDocument,
            Map<String, Double> similarityScores) {

        int basePageCount = baseDocument.getPageCount();
        int comparePageCount = compareDocument.getPageCount();

        // Create a cost matrix for the Hungarian algorithm
        double[][] costMatrix = new double[basePageCount][comparePageCount];

        // Fill the cost matrix with the negative similarity scores
        // (Hungarian algorithm minimizes cost, but we want to maximize similarity)
        for (int i = 0; i < basePageCount; i++) {
            for (int j = 0; j < comparePageCount; j++) {
                String key = baseDocument.getFileId() + "_" + (i + 1) + "_" +
                        compareDocument.getFileId() + "_" + (j + 1);

                double similarity = similarityScores.getOrDefault(key, 0.0);

                // If the similarity is below the threshold, set a high cost
                if (similarity < visualSimilarityThreshold) {
                    costMatrix[i][j] = 1.0;
                } else {
                    costMatrix[i][j] = 1.0 - similarity;
                }
            }
        }

        // Run the Hungarian algorithm
        HungarianAlgorithm hungarian = new HungarianAlgorithm(costMatrix);
        int[] assignments = hungarian.execute();

        // Create page pairs based on the assignments
        List<PagePair> pagePairs = new ArrayList<>();

        for (int i = 0; i < basePageCount; i++) {
            int j = assignments[i];

            // Create a page pair
            PagePair.PagePairBuilder builder = PagePair.builder()
                    .baseDocumentId(baseDocument.getFileId())
                    .compareDocumentId(compareDocument.getFileId())
                    .basePageNumber(i + 1);

            // If the page is matched
            if (j != -1 && j < comparePageCount) {
                String key = baseDocument.getFileId() + "_" + (i + 1) + "_" +
                        compareDocument.getFileId() + "_" + (j + 1);

                double similarity = similarityScores.getOrDefault(key, 0.0);

                // If the similarity is above the threshold, mark as matched
                if (similarity >= visualSimilarityThreshold) {
                    builder.comparePageNumber(j + 1)
                            .matched(true)
                            .similarityScore(similarity);
                } else {
                    builder.matched(false);
                }
            } else {
                builder.matched(false);
            }

            pagePairs.add(builder.build());
        }

        // Add unmatched pages from the compare document
        for (int j = 0; j < comparePageCount; j++) {
            boolean matched = false;

            for (int i = 0; i < basePageCount; i++) {
                if (assignments[i] == j) {
                    matched = true;
                    break;
                }
            }

            if (!matched) {
                pagePairs.add(PagePair.builder()
                        .baseDocumentId(baseDocument.getFileId())
                        .compareDocumentId(compareDocument.getFileId())
                        .comparePageNumber(j + 1)
                        .matched(false)
                        .build());
            }
        }

        return pagePairs;
    }

    /**
     * Implementation of the Hungarian algorithm for solving the assignment problem.
     */
    private static class HungarianAlgorithm {
        private final double[][] costMatrix;
        private final int rows, cols;
        private final int[] colAssignment;
        private final int[] rowAssignment;
        private final double[] rowCover;
        private final double[] colCover;

        /**
         * Constructor.
         *
         * @param costMatrix The cost matrix
         */
        public HungarianAlgorithm(double[][] costMatrix) {
            this.costMatrix = costMatrix;
            this.rows = costMatrix.length;
            this.cols = costMatrix[0].length;
            this.colAssignment = new int[rows];
            this.rowAssignment = new int[cols];
            this.rowCover = new double[rows];
            this.colCover = new double[cols];
        }

        /**
         * Execute the Hungarian algorithm.
         *
         * @return An array of assignments (column indices for each row)
         */
        public int[] execute() {
            // Initialize assignments
            for (int i = 0; i < rows; i++) {
                colAssignment[i] = -1;
            }
            for (int j = 0; j < cols; j++) {
                rowAssignment[j] = -1;
            }

            // Step 1: Subtract the smallest value in each row from all values in that row
            for (int i = 0; i < rows; i++) {
                double minValue = Double.MAX_VALUE;
                for (int j = 0; j < cols; j++) {
                    minValue = Math.min(minValue, costMatrix[i][j]);
                }
                for (int j = 0; j < cols; j++) {
                    costMatrix[i][j] -= minValue;
                }
            }

            // Step 2: Subtract the smallest value in each column from all values in that column
            for (int j = 0; j < cols; j++) {
                double minValue = Double.MAX_VALUE;
                for (int i = 0; i < rows; i++) {
                    minValue = Math.min(minValue, costMatrix[i][j]);
                }
                for (int i = 0; i < rows; i++) {
                    costMatrix[i][j] -= minValue;
                }
            }

            // Step 3: Cover all zeros with a minimum number of lines
            int[] rowsCovered = new int[rows];
            int[] colsCovered = new int[cols];
            int numLines = 0;

            // Find a maximal matching
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < cols; j++) {
                    if (costMatrix[i][j] == 0 && rowsCovered[i] == 0 && colsCovered[j] == 0) {
                        rowsCovered[i] = 1;
                        colsCovered[j] = 1;
                        colAssignment[i] = j;
                        rowAssignment[j] = i;
                        numLines++;
                        break;
                    }
                }
            }

            // If the number of lines equals the number of rows, we're done
            if (numLines == Math.min(rows, cols)) {
                return colAssignment;
            }

            // Step 4: Create additional zeros
            // Find the minimum uncovered value
            double minUncovered = Double.MAX_VALUE;
            for (int i = 0; i < rows; i++) {
                if (rowsCovered[i] == 0) {
                    for (int j = 0; j < cols; j++) {
                        if (colsCovered[j] == 0) {
                            minUncovered = Math.min(minUncovered, costMatrix[i][j]);
                        }
                    }
                }
            }

            // Subtract the minimum uncovered value from all uncovered elements
            // and add it to all elements covered by two lines
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < cols; j++) {
                    if (rowsCovered[i] == 0 && colsCovered[j] == 0) {
                        costMatrix[i][j] -= minUncovered;
                    } else if (rowsCovered[i] == 1 && colsCovered[j] == 1) {
                        costMatrix[i][j] += minUncovered;
                    }
                }
            }

            // Recursively call the algorithm
            return execute();
        }
    }
}