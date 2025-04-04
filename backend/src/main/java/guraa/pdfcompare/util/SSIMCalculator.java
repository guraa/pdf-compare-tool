package guraa.pdfcompare.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;

/**
 * Implementation of the Structural Similarity Index (SSIM) for image comparison.
 * SSIM provides a better measure of image similarity than simple pixel-by-pixel
 * comparison, taking into account luminance, contrast, and structure.
 */
@Slf4j
@Component
public class SSIMCalculator {

    private static final double K1 = 0.01;
    private static final double K2 = 0.03;
    private static final int WINDOW_SIZE = 8;

    /**
     * Calculate the SSIM between two images.
     *
     * @param image1 First image
     * @param image2 Second image
     * @return SSIM index between -1 and 1 (1 means identical)
     */
    public double calculateSSIM(BufferedImage image1, BufferedImage image2) {
        if (image1 == null || image2 == null) {
            return -1.0;
        }

        try {
            // Convert images to grayscale for SSIM computation
            BufferedImage img1Gray = convertToGrayscale(image1);
            BufferedImage img2Gray = convertToGrayscale(image2);

            // If images have different dimensions, resize the second image to match the first
            if (img1Gray.getWidth() != img2Gray.getWidth() || img1Gray.getHeight() != img2Gray.getHeight()) {
                img2Gray = resizeImage(img2Gray, img1Gray.getWidth(), img1Gray.getHeight());
            }

            // Extract data
            byte[] pixelsImg1 = ((DataBufferByte) img1Gray.getRaster().getDataBuffer()).getData();
            byte[] pixelsImg2 = ((DataBufferByte) img2Gray.getRaster().getDataBuffer()).getData();

            int width = img1Gray.getWidth();
            int height = img1Gray.getHeight();

            // Calculate SSIM for each window and average
            double ssimSum = 0;
            int windowCount = 0;

            // The L constant (dynamic range)
            double L = 255; // For 8-bit grayscale

            // Constants for stability
            double c1 = (K1 * L) * (K1 * L);
            double c2 = (K2 * L) * (K2 * L);

            // Process image in windows
            for (int y = 0; y <= height - WINDOW_SIZE; y += WINDOW_SIZE) {
                for (int x = 0; x <= width - WINDOW_SIZE; x += WINDOW_SIZE) {
                    double[] stats = calculateWindowStats(pixelsImg1, pixelsImg2, x, y, width, height);

                    double mean1 = stats[0];
                    double mean2 = stats[1];
                    double variance1 = stats[2];
                    double variance2 = stats[3];
                    double covariance = stats[4];

                    // Calculate SSIM for this window
                    double numerator = (2 * mean1 * mean2 + c1) * (2 * covariance + c2);
                    double denominator = (mean1 * mean1 + mean2 * mean2 + c1) *
                            (variance1 + variance2 + c2);

                    double ssim = numerator / denominator;
                    ssimSum += ssim;
                    windowCount++;
                }
            }

            // Average SSIM over all windows
            return windowCount > 0 ? ssimSum / windowCount : 0;
        } catch (Exception e) {
            log.error("Error calculating SSIM", e);
            return -1.0;
        }
    }

    /**
     * Convert an image to grayscale.
     *
     * @param image The color image
     * @return Grayscale version of the image
     */
    private BufferedImage convertToGrayscale(BufferedImage image) {
        BufferedImage result = new BufferedImage(
                image.getWidth(),
                image.getHeight(),
                BufferedImage.TYPE_BYTE_GRAY);

        ColorConvertOp op = new ColorConvertOp(
                ColorSpace.getInstance(ColorSpace.CS_GRAY), null);
        op.filter(image, result);

        return result;
    }

    /**
     * Resize an image to specified dimensions.
     *
     * @param image  Original image
     * @param width  Target width
     * @param height Target height
     * @return Resized image
     */
    private BufferedImage resizeImage(BufferedImage image, int width, int height) {
        BufferedImage resized = new BufferedImage(width, height, image.getType());
        java.awt.Graphics2D g = resized.createGraphics();
        g.drawImage(image, 0, 0, width, height, null);
        g.dispose();
        return resized;
    }

    /**
     * Calculate statistics for a window in the images.
     *
     * @param pixels1 Pixels from first image
     * @param pixels2 Pixels from second image
     * @param startX  X coordinate of window start
     * @param startY  Y coordinate of window start
     * @param width   Image width
     * @param height  Image height
     * @return Array with [mean1, mean2, variance1, variance2, covariance]
     */
    private double[] calculateWindowStats(byte[] pixels1, byte[] pixels2,
                                          int startX, int startY, int width, int height) {
        double mean1 = 0, mean2 = 0;
        double variance1 = 0, variance2 = 0;
        double covariance = 0;

        // First pass: calculate means
        for (int y = startY; y < startY + WINDOW_SIZE && y < height; y++) {
            for (int x = startX; x < startX + WINDOW_SIZE && x < width; x++) {
                int pos = y * width + x;
                // Convert to unsigned [0,255]
                int val1 = pixels1[pos] & 0xFF;
                int val2 = pixels2[pos] & 0xFF;

                mean1 += val1;
                mean2 += val2;
            }
        }

        mean1 /= (WINDOW_SIZE * WINDOW_SIZE);
        mean2 /= (WINDOW_SIZE * WINDOW_SIZE);

        // Second pass: calculate variances and covariance
        for (int y = startY; y < startY + WINDOW_SIZE && y < height; y++) {
            for (int x = startX; x < startX + WINDOW_SIZE && x < width; x++) {
                int pos = y * width + x;
                int val1 = pixels1[pos] & 0xFF;
                int val2 = pixels2[pos] & 0xFF;

                variance1 += (val1 - mean1) * (val1 - mean1);
                variance2 += (val2 - mean2) * (val2 - mean2);
                covariance += (val1 - mean1) * (val2 - mean2);
            }
        }

        variance1 /= (WINDOW_SIZE * WINDOW_SIZE - 1);
        variance2 /= (WINDOW_SIZE * WINDOW_SIZE - 1);
        covariance /= (WINDOW_SIZE * WINDOW_SIZE - 1);

        return new double[]{mean1, mean2, variance1, variance2, covariance};
    }

    /**
     * Get a similarity score normalized to 0-1 range (1 = identical).
     * This converts the SSIM (-1 to 1) to a value that can be used with other metrics.
     *
     * @param image1 First image
     * @param image2 Second image
     * @return Normalized similarity score (0-1)
     */
    public double getSimilarityScore(BufferedImage image1, BufferedImage image2) {
        double ssim = calculateSSIM(image1, image2);
        // Map [-1,1] to [0,1]
        return (ssim + 1) / 2.0;
    }

    /**
     * Calculate a perceptual hash for an image.
     * This provides a fast way to compare images based on their visual content.
     *
     * @param image The image to hash
     * @return A 64-bit perceptual hash as a long
     */
    public long calculatePerceptualHash(BufferedImage image) {
        try {
            // Resize to 8x8 pixels
            BufferedImage resized = resizeImage(image, 8, 8);

            // Convert to grayscale
            BufferedImage grayscale = convertToGrayscale(resized);

            // Get pixel data
            Raster raster = grayscale.getRaster();

            // Calculate average value
            double sum = 0;
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    sum += raster.getSample(x, y, 0);
                }
            }
            double avg = sum / 64.0;

            // Calculate hash (1 for values above average, 0 for below)
            long hash = 0;
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    if (raster.getSample(x, y, 0) > avg) {
                        hash |= 1;
                    }
                    hash = hash << 1;
                }
            }

            return hash;
        } catch (Exception e) {
            log.error("Error calculating perceptual hash", e);
            return 0;
        }
    }

    /**
     * Calculate the Hamming distance between two hashes.
     *
     * @param hash1 First hash
     * @param hash2 Second hash
     * @return Hamming distance (number of differing bits)
     */
    public int hammingDistance(long hash1, long hash2) {
        long xor = hash1 ^ hash2;
        int distance = 0;

        while (xor != 0) {
            distance += (xor & 1);
            xor >>= 1;
        }

        return distance;
    }

    /**
     * Calculate hash-based similarity between two images.
     *
     * @param image1 First image
     * @param image2 Second image
     * @return Similarity score (0-1)
     */
    public double getHashSimilarity(BufferedImage image1, BufferedImage image2) {
        long hash1 = calculatePerceptualHash(image1);
        long hash2 = calculatePerceptualHash(image2);

        int distance = hammingDistance(hash1, hash2);

        // Convert distance to similarity (0-1)
        return 1.0 - (distance / 64.0);
    }
}