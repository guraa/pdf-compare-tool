package guraa.pdfcompare.visual;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Advanced Visual Matcher using perceptual hashing and advanced comparison techniques
 */
public class EnhancedVisualMatcher {
    private static final Logger logger = LoggerFactory.getLogger(EnhancedVisualMatcher.class);

    // Configuration parameters
    private static final int HASH_SIZE = 64; // Larger hash for more precision
    private static final double SIMILARITY_THRESHOLD = 0.7; // 70% similarity threshold

    /**
     * Compute perceptual hashes for all pages in a document
     * @param pdfFile PDF file to process
     * @return List of perceptual hashes for each page
     * @throws IOException If there's an error reading the PDF
     */
    public List<String> computePerceptualHashes(File pdfFile) throws IOException {
        List<String> perceptualHashes = new ArrayList<>();

        try (PDDocument document = PDDocument.load(pdfFile)) {
            PDFRenderer renderer = new PDFRenderer(document);

            for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
                BufferedImage image = renderer.renderImageWithDPI(pageIndex, 150); // Higher DPI for more detail
                String hash = computePerceptualHash(image);
                perceptualHashes.add(hash);
            }
        }

        return perceptualHashes;
    }

    /**
     * Compute perceptual hash using pHash (Perception Hash) algorithm
     * @param image Input image
     * @return Perceptual hash as a binary string
     */
    private String computePerceptualHash(BufferedImage image) {
        // Convert to grayscale
        BufferedImage grayImage = convertToGrayscale(image);

        // Resize to standard size
        BufferedImage resizedImage = resizeImage(grayImage, HASH_SIZE, HASH_SIZE);

        // Compute DCT (Discrete Cosine Transform)
        double[][] dctMatrix = computeDCT(resizedImage);

        // Compute median
        double median = computeMedian(dctMatrix);

        // Generate hash based on DCT values compared to median
        return generateHash(dctMatrix, median);
    }

    /**
     * Convert image to grayscale
     * @param image Input image
     * @return Grayscale image
     */
    private BufferedImage convertToGrayscale(BufferedImage image) {
        BufferedImage grayImage = new BufferedImage(
                image.getWidth(),
                image.getHeight(),
                BufferedImage.TYPE_BYTE_GRAY
        );
        grayImage.getGraphics().drawImage(image, 0, 0, null);
        return grayImage;
    }

    /**
     * Resize image to specified dimensions
     * @param image Input image
     * @param width Target width
     * @param height Target height
     * @return Resized image
     */
    private BufferedImage resizeImage(BufferedImage image, int width, int height) {
        BufferedImage resizedImage = new BufferedImage(width, height, image.getType());
        resizedImage.getGraphics().drawImage(
                image.getScaledInstance(width, height, java.awt.Image.SCALE_SMOOTH),
                0, 0, null
        );
        return resizedImage;
    }

    /**
     * Compute Discrete Cosine Transform
     * @param image Input image
     * @return DCT matrix
     */
    private double[][] computeDCT(BufferedImage image) {
        int size = image.getWidth();
        double[][] dctMatrix = new double[size][size];

        for (int u = 0; u < size; u++) {
            for (int v = 0; v < size; v++) {
                double sum = 0.0;
                for (int x = 0; x < size; x++) {
                    for (int y = 0; y < size; y++) {
                        int pixel = image.getRGB(x, y) & 0xFF; // Grayscale
                        sum += pixel *
                                Math.cos(((2 * x + 1) * u * Math.PI) / (2.0 * size)) *
                                Math.cos(((2 * y + 1) * v * Math.PI) / (2.0 * size));
                    }
                }

                // DCT coefficients
                double cu = (u == 0) ? 1.0 / Math.sqrt(2) : 1.0;
                double cv = (v == 0) ? 1.0 / Math.sqrt(2) : 1.0;

                dctMatrix[u][v] = cu * cv * sum * 0.25;
            }
        }

        return dctMatrix;
    }

    /**
     * Compute median of DCT matrix
     * @param dctMatrix DCT matrix
     * @return Median value
     */
    private double computeMedian(double[][] dctMatrix) {
        List<Double> flattenedDCT = new ArrayList<>();
        for (double[] row : dctMatrix) {
            for (double val : row) {
                flattenedDCT.add(val);
            }
        }

        Collections.sort(flattenedDCT);
        int midIndex = flattenedDCT.size() / 2;
        return flattenedDCT.get(midIndex);
    }

    /**
     * Generate hash from DCT matrix
     * @param dctMatrix DCT matrix
     * @param median Median value
     * @return Binary hash string
     */
    private String generateHash(double[][] dctMatrix, double median) {
        StringBuilder hash = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                hash.append(dctMatrix[i][j] > median ? '1' : '0');
            }
        }
        return hash.toString();
    }

    /**
     * Compare two perceptual hashes
     * @param hash1 First hash
     * @param hash2 Second hash
     * @return Similarity score (0.0 to 1.0)
     */
    public double comparePerceptualHashes(String hash1, String hash2) {
        if (hash1.length() != hash2.length()) {
            throw new IllegalArgumentException("Hashes must be of equal length");
        }

        int differences = 0;
        for (int i = 0; i < hash1.length(); i++) {
            if (hash1.charAt(i) != hash2.charAt(i)) {
                differences++;
            }
        }

        // Convert differences to similarity
        return 1.0 - (differences / (double) hash1.length());
    }

    /**
     * Compare lists of hashes
     * @param baseHashes Base document hashes
     * @param compareHashes Compare document hashes
     * @return Similarity score (0.0 to 1.0)
     */
    public double compareHashLists(List<String> baseHashes, List<String> compareHashes) {
        // Ensure same length or truncate to shorter list
        int minLength = Math.min(baseHashes.size(), compareHashes.size());

        List<Double> similarities = new ArrayList<>();

        for (int i = 0; i < minLength; i++) {
            double similarity = comparePerceptualHashes(baseHashes.get(i), compareHashes.get(i));
            similarities.add(similarity);
        }

        // Average similarity across pages
        return similarities.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }

    /**
     * Determine if documents are visually similar
     * @param baseHashes Base document hashes
     * @param compareHashes Compare document hashes
     * @return true if documents are visually similar
     */
    public boolean areDocumentsVisuallySimilar(List<String> baseHashes, List<String> compareHashes) {
        double similarity = compareHashLists(baseHashes, compareHashes);
        return similarity >= SIMILARITY_THRESHOLD;
    }

    /**
     * Find visually similar segments between documents
     * @param baseHashes Base document hashes
     * @param compareHashes Compare document hashes
     * @return List of matching segment pairs
     */
    public List<DocumentSegmentMatch> findVisuallyMatchedSegments(
            List<String> baseHashes,
            List<String> compareHashes) {
        List<DocumentSegmentMatch> matches = new ArrayList<>();

        // Sliding window approach
        int windowSize = 3; // Match 3-page segments

        for (int baseStart = 0; baseStart <= baseHashes.size() - windowSize; baseStart++) {
            for (int compareStart = 0; compareStart <= compareHashes.size() - windowSize; compareStart++) {
                // Compare window of hashes
                List<String> baseWindow = baseHashes.subList(baseStart, baseStart + windowSize);
                List<String> compareWindow = compareHashes.subList(compareStart, compareStart + windowSize);

                double similarity = compareHashLists(baseWindow, compareWindow);

                if (similarity >= SIMILARITY_THRESHOLD) {
                    matches.add(new DocumentSegmentMatch(
                            baseStart,
                            baseStart + windowSize - 1,
                            compareStart,
                            compareStart + windowSize - 1,
                            similarity
                    ));
                }
            }
        }

        return matches;
    }

    /**
     * Inner class to represent matched document segments
     */
    public static class DocumentSegmentMatch {
        private final int baseStartPage;
        private final int baseEndPage;
        private final int compareStartPage;
        private final int compareEndPage;
        private final double similarity;

        public DocumentSegmentMatch(
                int baseStartPage,
                int baseEndPage,
                int compareStartPage,
                int compareEndPage,
                double similarity) {
            this.baseStartPage = baseStartPage;
            this.baseEndPage = baseEndPage;
            this.compareStartPage = compareStartPage;
            this.compareEndPage = compareEndPage;
            this.similarity = similarity;
        }

        // Getters
        public int getBaseStartPage() { return baseStartPage; }
        public int getBaseEndPage() { return baseEndPage; }
        public int getCompareStartPage() { return compareStartPage; }
        public int getCompareEndPage() { return compareEndPage; }
        public double getSimilarity() { return similarity; }
    }

    /**
     * Configure similarity threshold
     * @param threshold New similarity threshold
     */
    public void setSimilarityThreshold(double threshold) {
        // This would ideally modify the class's static final threshold
        // For now, this is a placeholder
        logger.warn("Dynamic threshold setting not fully implemented");
    }
}