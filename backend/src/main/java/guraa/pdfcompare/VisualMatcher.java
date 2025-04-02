package guraa.pdfcompare.visual;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

/**
 * Unified VisualMatcher: generates visual hashes and compares them using perceptual similarity.
 */
public class VisualMatcher {
    private static final Logger logger = LoggerFactory.getLogger(VisualMatcher.class);

    /**
     * Generate visual hashes (dHash) for all pages in a document
     */
    public static List<String> computeVisualHashes(File pdfFile) throws IOException {
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
     * Compare two hash strings and calculate similarity
     * @param hash1 First hash string
     * @param hash2 Second hash string
     * @return Similarity score (0.0-1.0)
     */
    public static double compareHashes(String hash1, String hash2) {
        // Calculate Hamming distance
        int distance = hammingDistance(hash1, hash2);

        // Convert to similarity score (1.0 = identical, 0.0 = completely different)
        byte[] b1 = Base64.getDecoder().decode(hash1);
        int maxDistance = 8 * b1.length; // Maximum possible Hamming distance (all bits different)

        return 1.0 - ((double) distance / maxDistance);
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