package guraa.pdfcompare.visual;

import guraa.pdfcompare.core.PDFPageModel;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

/**
 * Utility class for visual matching of PDF pages.
 * Provides methods for comparing pages based on visual features.
 */
public class VisualMatcher {
    private static final Logger logger = LoggerFactory.getLogger(VisualMatcher.class);
    
    // Similarity thresholds
    private static final double HIGH_SIMILARITY_THRESHOLD = 0.95;
    private static final double MEDIUM_SIMILARITY_THRESHOLD = 0.85;
    private static final double LOW_SIMILARITY_THRESHOLD = 0.75;
    
    /**
     * Calculate visual similarity between two pages
     * @param basePage Base page
     * @param comparePage Compare page
     * @return Similarity score (0.0-1.0)
     */
    public static double calculateVisualSimilarity(PDFPageModel basePage, PDFPageModel comparePage) {
        if (basePage == null || comparePage == null) {
            return 0.0;
        }
        
        // Calculate similarity based on dimensions
        double dimensionSimilarity = calculateDimensionSimilarity(basePage, comparePage);
        
        // Calculate similarity based on text layout
        double textLayoutSimilarity = calculateTextLayoutSimilarity(basePage, comparePage);
        
        // Calculate similarity based on image presence
        double imageSimilarity = calculateImageSimilarity(basePage, comparePage);
        
        // Weighted combination of similarities
        return (dimensionSimilarity * 0.3) + (textLayoutSimilarity * 0.5) + (imageSimilarity * 0.2);
    }
    
    /**
     * Calculate similarity based on page dimensions
     * @param basePage Base page
     * @param comparePage Compare page
     * @return Dimension similarity score
     */
    private static double calculateDimensionSimilarity(PDFPageModel basePage, PDFPageModel comparePage) {
        float baseWidth = basePage.getWidth();
        float baseHeight = basePage.getHeight();
        float compareWidth = comparePage.getWidth();
        float compareHeight = comparePage.getHeight();
        
        // Calculate ratio of dimensions
        double widthRatio = Math.min(baseWidth, compareWidth) / Math.max(baseWidth, compareWidth);
        double heightRatio = Math.min(baseHeight, compareHeight) / Math.max(baseHeight, compareHeight);
        
        return (widthRatio + heightRatio) / 2.0;
    }
    
    /**
     * Calculate similarity based on text layout
     * @param basePage Base page
     * @param comparePage Compare page
     * @return Text layout similarity score
     */
    private static double calculateTextLayoutSimilarity(PDFPageModel basePage, PDFPageModel comparePage) {
        // Simple implementation - compare text content length
        String baseText = basePage.getText();
        String compareText = comparePage.getText();
        
        if (baseText == null || compareText == null || baseText.isEmpty() || compareText.isEmpty()) {
            return 0.0;
        }
        
        double lengthRatio = (double) Math.min(baseText.length(), compareText.length()) / 
                             Math.max(baseText.length(), compareText.length());
        
        // TODO: Implement more sophisticated text layout comparison
        
        return lengthRatio;
    }
    
    /**
     * Calculate similarity based on image presence
     * @param basePage Base page
     * @param comparePage Compare page
     * @return Image similarity score
     */
    private static double calculateImageSimilarity(PDFPageModel basePage, PDFPageModel comparePage) {
        boolean baseHasImages = basePage.getImages() != null && !basePage.getImages().isEmpty();
        boolean compareHasImages = comparePage.getImages() != null && !comparePage.getImages().isEmpty();
        
        // Simple implementation - check if both have or don't have images
        if (baseHasImages == compareHasImages) {
            if (!baseHasImages) {
                return 1.0;  // Both have no images
            }
            
            // Both have images, compare counts
            int baseImageCount = basePage.getImages().size();
            int compareImageCount = comparePage.getImages().size();
            
            double countRatio = (double) Math.min(baseImageCount, compareImageCount) / 
                                Math.max(baseImageCount, compareImageCount);
            
            return 0.5 + (countRatio * 0.5);  // Scale from 0.5 to 1.0
        }
        
        return 0.0;  // One has images, the other doesn't
    }
    
    /**
     * Check if two pages are visually similar
     * @param basePage Base page
     * @param comparePage Compare page
     * @param threshold Similarity threshold
     * @return true if pages are similar
     */
    public static boolean arePagesVisuallyMatched(PDFPageModel basePage, PDFPageModel comparePage, double threshold) {
        double similarity = calculateVisualSimilarity(basePage, comparePage);
        return similarity >= threshold;
    }
    
    /**
     * Get the similarity level description
     * @param similarity Similarity score
     * @return Similarity level description
     */
    public static String getSimilarityLevel(double similarity) {
        if (similarity >= HIGH_SIMILARITY_THRESHOLD) {
            return "HIGH";
        } else if (similarity >= MEDIUM_SIMILARITY_THRESHOLD) {
            return "MEDIUM";
        } else if (similarity >= LOW_SIMILARITY_THRESHOLD) {
            return "LOW";
        } else {
            return "NONE";
        }
    }
    
    /**
     * Create a visual signature for a page
     * @param page Page model
     * @return Visual signature map
     */
    public static Map<String, Object> createVisualSignature(PDFPageModel page) {
        Map<String, Object> signature = new HashMap<>();
        
        if (page == null) {
            return signature;
        }
        
        // Add basic dimensions
        signature.put("width", page.getWidth());
        signature.put("height", page.getHeight());
        
        // Add text information
        if (page.getText() != null) {
            signature.put("textLength", page.getText().length());
        }
        
        // Add element counts
        if (page.getTextElements() != null) {
            signature.put("textElementCount", page.getTextElements().size());
        }
        
        if (page.getImages() != null) {
            signature.put("imageCount", page.getImages().size());
        }
        
        if (page.getFonts() != null) {
            signature.put("fontCount", page.getFonts().size());
        }
        
        return signature;
    }
    
    /**
     * Generate visual hashes (dHash) for all pages in a document
     * @param pdfFile PDF file
     * @return List of visual hashes
     * @throws IOException If there's an error processing the file
     */
    public static List<String> generateVisualHashes(File pdfFile) throws IOException {
        List<String> hashes = new ArrayList<>();

        try (PDDocument document = PDDocument.load(pdfFile)) {
            PDFRenderer renderer = new PDFRenderer(document);

            for (int i = 0; i < document.getNumberOfPages(); i++) {
                BufferedImage pageImage = renderer.renderImageWithDPI(i, 72, ImageType.RGB);
                String hash = computeDHash(pageImage);
                hashes.add(hash);
            }
        }

        return hashes;
    }
    
    /**
     * Compute visual hashes for a PDF file
     * @param file PDF file
     * @return Map of visual hashes
     */
    public static Map<String, String> computeVisualHashes(File file) {
        Map<String, String> hashes = new HashMap<>();
        
        if (file == null || !file.exists()) {
            logger.warn("Cannot compute visual hashes for null or non-existent file");
            return hashes;
        }
        
        logger.info("Computing visual hashes for file: {}", file.getName());
        
        try {
            List<String> hashList = generateVisualHashes(file);
            for (int i = 0; i < hashList.size(); i++) {
                hashes.put("page_" + i, hashList.get(i));
            }
            hashes.put("file_hash", "hash_" + file.getName().hashCode());
            hashes.put("page_count_hash", "hash_" + hashList.size());
        } catch (IOException e) {
            logger.error("Error computing visual hashes: {}", e.getMessage());
            // Fallback to simple hashing
            hashes.put("file_hash", "hash_" + file.getName().hashCode());
            hashes.put("page_count_hash", "hash_" + file.length() % 100);
        }
        
        return hashes;
    }
    
    /**
     * Resize image to fixed size
     */
    private static BufferedImage resizeImage(BufferedImage original, int width, int height) {
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = resized.createGraphics();
        g.drawImage(original, 0, 0, width, height, null);
        g.dispose();
        return resized;
    }

    /**
     * Compute dHash from a grayscale image
     */
    private static String computeDHash(BufferedImage image) {
        final int width = 9; // dHash uses width+1
        final int height = 8;

        BufferedImage resized = resizeImage(image, width, height);

        StringBuilder hashBuilder = new StringBuilder();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width - 1; x++) {
                int leftPixel = resized.getRGB(x, y) & 0xFF;
                int rightPixel = resized.getRGB(x + 1, y) & 0xFF;
                hashBuilder.append(leftPixel < rightPixel ? "1" : "0");
            }
        }

        // Convert binary string to base64
        byte[] hashBytes = new byte[hashBuilder.length() / 8];
        for (int i = 0; i < hashBytes.length; i++) {
            String byteStr = hashBuilder.substring(i * 8, i * 8 + 8);
            hashBytes[i] = (byte) Integer.parseInt(byteStr, 2);
        }

        return Base64.getEncoder().encodeToString(hashBytes);
    }
    
    /**
     * Compare two visual hashes
     * @param hash1 First hash
     * @param hash2 Second hash
     * @return Similarity score (0.0-1.0)
     */
    public static double compareHashes(String hash1, String hash2) {
        if (hash1 == null || hash2 == null) {
            return 0.0;
        }
        
        if (hash1.equals(hash2)) {
            return 1.0;
        }
        
        try {
            // Calculate Hamming distance
            int distance = hammingDistance(hash1, hash2);

            // Convert to similarity score (1.0 = identical, 0.0 = completely different)
            byte[] b1 = Base64.getDecoder().decode(hash1);
            int maxDistance = 8 * b1.length; // Maximum possible Hamming distance (all bits different)

            return 1.0 - ((double) distance / maxDistance);
        } catch (Exception e) {
            logger.warn("Error comparing hashes: {}", e.getMessage());
            
            // Fallback to simple comparison
            int commonPrefix = 0;
            int minLength = Math.min(hash1.length(), hash2.length());
            
            for (int i = 0; i < minLength; i++) {
                if (hash1.charAt(i) == hash2.charAt(i)) {
                    commonPrefix++;
                } else {
                    break;
                }
            }
            
            return (double) commonPrefix / minLength;
        }
    }
    
    /**
     * Calculate Hamming distance between two binary hash strings
     */
    private static int hammingDistance(String hash1, String hash2) {
        byte[] b1 = Base64.getDecoder().decode(hash1);
        byte[] b2 = Base64.getDecoder().decode(hash2);
        int dist = 0;
        for (int i = 0; i < Math.min(b1.length, b2.length); i++) {
            dist += Integer.bitCount(b1[i] ^ b2[i]);
        }
        return dist;
    }

    /**
     * Compare two sets of visual hashes and return a similarity score (0.0 - 1.0)
     */
    public static double compareHashLists(List<String> base, List<String> compare) {
        int minSize = Math.min(base.size(), compare.size());
        if (minSize == 0) return 0;

        int totalDistance = 0;
        int maxDistance = 0;
        for (int i = 0; i < minSize; i++) {
            totalDistance += hammingDistance(base.get(i), compare.get(i));
            maxDistance += 8 * Base64.getDecoder().decode(base.get(i)).length;
        }

        return 1.0 - ((double) totalDistance / maxDistance);
    }

    /**
     * Slice a visual hash list by page range
     */
    public static List<String> sliceHashes(List<String> hashes, int startPage, int endPage) {
        if (startPage >= 0 && endPage < hashes.size()) {
            return new ArrayList<>(hashes.subList(startPage, endPage + 1));
        }
        return Collections.emptyList();
    }
}
