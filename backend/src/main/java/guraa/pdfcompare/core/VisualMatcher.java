package guraa.pdfcompare.core;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * VisualMatcher provides page-level visual fingerprinting for smart matching.
 */
public class VisualMatcher {
    private static final Logger logger = LoggerFactory.getLogger(VisualMatcher.class);

    /**
     * Generate visual hashes (e.g. perceptual hashes) for all pages in a document
     */
    public static List<String> computeVisualHashes(File pdfFile) throws IOException {
        List<String> hashes = new ArrayList<>();

        try (PDDocument document = PDDocument.load(pdfFile)) {
            PDFRenderer renderer = new PDFRenderer(document);

            for (int i = 0; i < document.getNumberOfPages(); i++) {
                BufferedImage pageImage = renderer.renderImageWithDPI(i, 72); // Low-res to reduce processing
                BufferedImage resized = resizeImage(pageImage, 32, 32);
                String hash = averageHash(resized);
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
        resized.getGraphics().drawImage(original, 0, 0, width, height, null);
        return resized;
    }

    /**
     * Compute average hash (aHash) from a grayscale image
     */
    private static String averageHash(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        int sum = 0;
        int[] pixels = new int[width * height];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int gray = new Color(image.getRGB(x, y)).getRed();
                pixels[y * width + x] = gray;
                sum += gray;
            }
        }

        int avg = sum / pixels.length;
        StringBuilder hash = new StringBuilder();

        for (int px : pixels) {
            hash.append(px >= avg ? "1" : "0");
        }

        return hash.toString();
    }

    /**
     * Calculate Hamming distance between two binary hash strings
     */
    public static int hammingDistance(String hash1, String hash2) {
        int dist = 0;
        for (int i = 0; i < Math.min(hash1.length(), hash2.length()); i++) {
            if (hash1.charAt(i) != hash2.charAt(i)) {
                dist++;
            }
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
        for (int i = 0; i < minSize; i++) {
            totalDistance += hammingDistance(base.get(i), compare.get(i));
        }

        int maxDistance = minSize * base.get(0).length();
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
