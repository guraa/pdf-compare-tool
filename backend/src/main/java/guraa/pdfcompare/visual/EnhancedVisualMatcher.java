package guraa.pdfcompare.visual;

import guraa.pdfcompare.model.PdfDocument;
import guraa.pdfcompare.service.PagePair;
import guraa.pdfcompare.service.PdfRenderingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/**
 * Enhanced visual matcher for PDF pages.
 * This class uses SSIM (Structural Similarity Index) to match pages
 * between two PDF documents based on visual similarity.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EnhancedVisualMatcher implements VisualMatcher {

    private final SSIMCalculator ssimCalculator;
    private final PdfRenderingService pdfRenderingService;
    private final ExecutorService executorService;

    @Value("${app.matching.visual-similarity-threshold:0.7}")
    private double visualSimilarityThreshold;

    @Value("${app.matching.max-candidates-per-page:5}")
    private int maxCandidatesPerPage;

    // Cache of similarity scores
    private final ConcurrentHashMap<String, Double> similarityCache = new ConcurrentHashMap<>();

    @Override
    public List<PagePair> matchPages(PdfDocument baseDocument, PdfDocument compareDocument) throws IOException {
        log.info("Starting visual matching between documents: {} and {}", 
                baseDocument.getFileId(), compareDocument.getFileId());

        // Pre-render all pages
        preRenderPages(baseDocument, compareDocument);

        // Calculate similarity scores for all page pairs
        Map<String, Double> similarityScores = calculateSimilarityScores(baseDocument, compareDocument);

        // Match pages using the Hungarian algorithm
        List<PagePair> pagePairs = matchPagesUsingHungarian(baseDocument, compareDocument, similarityScores);

        log.info("Completed visual matching between documents: {} and {}", 
                baseDocument.getFileId(), compareDocument.getFileId());

        return pagePairs;
    }

    /**
     * Pre-render all pages of both documents.
     *
     * @param baseDocument The base document
     * @param compareDocument The compare document
     * @throws IOException If there is an error rendering the pages
     */
    private void preRenderPages(PdfDocument baseDocument, PdfDocument compareDocument) throws IOException {
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

        // Wait for both tasks to complete
        CompletableFuture.allOf(baseRenderingFuture, compareRenderingFuture).join();
    }

    /**
     * Calculate similarity scores for all page pairs.
     *
     * @param baseDocument The base document
     * @param compareDocument The compare document
     * @return A map of page pair keys to similarity scores
     * @throws IOException If there is an error calculating the scores
     */
    private Map<String, Double> calculateSimilarityScores(PdfDocument baseDocument, PdfDocument compareDocument) throws IOException {
        Map<String, Double> similarityScores = new HashMap<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

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
                    continue;
                }

                // Submit a task to calculate the similarity score
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        // Get the rendered pages
                        File basePageFile = pdfRenderingService.renderPage(baseDocument, basePageNum);
                        File comparePageFile = pdfRenderingService.renderPage(compareDocument, comparePageNum);

                        // Load the images
                        BufferedImage baseImage = ImageIO.read(basePageFile);
                        BufferedImage compareImage = ImageIO.read(comparePageFile);

                        // Calculate the similarity score
                        double similarity = ssimCalculator.calculate(baseImage, compareImage);

                        // Cache the score
                        similarityCache.put(key, similarity);
                        similarityScores.put(key, similarity);
                    } catch (IOException e) {
                        log.error("Error calculating similarity score: {}", e.getMessage(), e);
                    }
                }, executorService);

                futures.add(future);
            }
        }

        // Wait for all tasks to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        return similarityScores;
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
