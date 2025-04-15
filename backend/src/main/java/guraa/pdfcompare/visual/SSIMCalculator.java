package guraa.pdfcompare.visual;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.util.Arrays;

/**
 * Calculator for Structural Similarity Index (SSIM) between images.
 * SSIM is a perception-based model that considers image degradation as perceived change
 * in structural information, while also incorporating important perceptual phenomena,
 * including both luminance masking and contrast masking terms.
 */
@Slf4j
@Component
public class SSIMCalculator {

    // Constants for SSIM calculation
    private static final double K1 = 0.01;
    private static final double K2 = 0.03;
    private static final int WINDOW_SIZE = 8;
    private static final double C1 = Math.pow(255 * K1, 2);
    private static final double C2 = Math.pow(255 * K2, 2);

    /**
     * Calculate the SSIM between two images.
     *
     * @param img1 The first image
     * @param img2 The second image
     * @return The SSIM value (0.0 to 1.0)
     */
    public double calculate(BufferedImage img1, BufferedImage img2) {
        // Resize images to the same dimensions if needed
        if (img1.getWidth() != img2.getWidth() || img1.getHeight() != img2.getHeight()) {
            img2 = resizeImage(img2, img1.getWidth(), img1.getHeight());
        }

        // Convert images to grayscale
        double[][] gray1 = imageToGrayscaleMatrix(img1);
        double[][] gray2 = imageToGrayscaleMatrix(img2);

        // Calculate SSIM
        return calculateSSIM(gray1, gray2);
    }

    /**
     * Resize an image to the specified dimensions.
     *
     * @param img The image to resize
     * @param width The target width
     * @param height The target height
     * @return The resized image
     */
    private BufferedImage resizeImage(BufferedImage img, int width, int height) {
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        resized.createGraphics().drawImage(img, 0, 0, width, height, null);
        return resized;
    }

    /**
     * Convert an image to a grayscale matrix.
     *
     * @param img The image to convert
     * @return The grayscale matrix
     */
    private double[][] imageToGrayscaleMatrix(BufferedImage img) {
        int width = img.getWidth();
        int height = img.getHeight();
        double[][] result = new double[height][width];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                // Convert to grayscale using luminance formula
                result[y][x] = 0.299 * r + 0.587 * g + 0.114 * b;
            }
        }

        return result;
    }

    /**
     * Calculate the SSIM between two grayscale matrices.
     *
     * @param img1 The first grayscale matrix
     * @param img2 The second grayscale matrix
     * @return The SSIM value (0.0 to 1.0)
     */
    private double calculateSSIM(double[][] img1, double[][] img2) {
        int height = img1.length;
        int width = img1[0].length;

        // Calculate the number of windows
        int numWindowsY = height - WINDOW_SIZE + 1;
        int numWindowsX = width - WINDOW_SIZE + 1;

        if (numWindowsX <= 0 || numWindowsY <= 0) {
            // Images are too small for the window size
            return compareSmallImages(img1, img2);
        }

        double ssimSum = 0.0;
        int numWindows = 0;

        // Iterate over all windows
        for (int y = 0; y < numWindowsY; y++) {
            for (int x = 0; x < numWindowsX; x++) {
                // Extract window
                double[] window1 = extractWindow(img1, x, y);
                double[] window2 = extractWindow(img2, x, y);

                // Calculate SSIM for this window
                double ssim = calculateWindowSSIM(window1, window2);
                ssimSum += ssim;
                numWindows++;
            }
        }

        // Return the average SSIM
        return ssimSum / numWindows;
    }

    /**
     * Compare small images that are smaller than the window size.
     *
     * @param img1 The first grayscale matrix
     * @param img2 The second grayscale matrix
     * @return The similarity value (0.0 to 1.0)
     */
    private double compareSmallImages(double[][] img1, double[][] img2) {
        int height = img1.length;
        int width = img1[0].length;

        // Flatten the matrices
        double[] flat1 = new double[height * width];
        double[] flat2 = new double[height * width];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                flat1[y * width + x] = img1[y][x];
                flat2[y * width + x] = img2[y][x];
            }
        }

        // Calculate SSIM for the entire image
        return calculateWindowSSIM(flat1, flat2);
    }

    /**
     * Extract a window from a grayscale matrix.
     *
     * @param img The grayscale matrix
     * @param x The x-coordinate of the top-left corner of the window
     * @param y The y-coordinate of the top-left corner of the window
     * @return The window as a flattened array
     */
    private double[] extractWindow(double[][] img, int x, int y) {
        double[] window = new double[WINDOW_SIZE * WINDOW_SIZE];
        int index = 0;

        for (int j = y; j < y + WINDOW_SIZE; j++) {
            for (int i = x; i < x + WINDOW_SIZE; i++) {
                window[index++] = img[j][i];
            }
        }

        return window;
    }

    /**
     * Calculate the SSIM for a window.
     *
     * @param window1 The first window
     * @param window2 The second window
     * @return The SSIM value (0.0 to 1.0)
     */
    private double calculateWindowSSIM(double[] window1, double[] window2) {
        // Calculate mean
        double mean1 = Arrays.stream(window1).average().orElse(0);
        double mean2 = Arrays.stream(window2).average().orElse(0);

        // Calculate variance and covariance
        double variance1 = 0;
        double variance2 = 0;
        double covariance = 0;

        for (int i = 0; i < window1.length; i++) {
            double diff1 = window1[i] - mean1;
            double diff2 = window2[i] - mean2;
            variance1 += diff1 * diff1;
            variance2 += diff2 * diff2;
            covariance += diff1 * diff2;
        }

        variance1 /= window1.length;
        variance2 /= window1.length;
        covariance /= window1.length;

        // Calculate SSIM
        double numerator = (2 * mean1 * mean2 + C1) * (2 * covariance + C2);
        double denominator = (mean1 * mean1 + mean2 * mean2 + C1) * (variance1 + variance2 + C2);

        return numerator / denominator;
    }
}
