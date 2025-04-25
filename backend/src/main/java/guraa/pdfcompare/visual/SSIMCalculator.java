package guraa.pdfcompare.visual;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Optimized calculator for Structural Similarity Index (SSIM) between images.
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

    // Thresholds for early optimization
    private static final double QUICK_REJECT_THRESHOLD = 0.1;
    private static final double QUICK_ACCEPT_THRESHOLD = 0.95;

    // Maximum number of sample points for quick comparison
    private static final int MAX_SAMPLE_POINTS = 100;

    // Thread pool for parallel processing of large images
    private final ExecutorService executor;

    // Threshold for using parallel calculation
    private static final int PARALLEL_THRESHOLD = 1000 * 1000; // 1M pixels

    /**
     * Constructor.
     */
    public SSIMCalculator() {
        // Create a thread pool with the number of available processors
        this.executor = Executors.newFixedThreadPool(
                Math.max(1, Runtime.getRuntime().availableProcessors() - 1),
                r -> {
                    Thread t = new Thread(r);
                    t.setDaemon(true);
                    t.setName("ssim-worker-" + t.getId());
                    return t;
                }
        );
    }

    /**
     * Calculate the SSIM between two images with performance optimizations.
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

        // Quick check using sample points - can avoid expensive SSIM calculation
        double quickSimilarity = quickCompare(img1, img2);

        // If images are clearly different, return the quick result
        if (quickSimilarity < QUICK_REJECT_THRESHOLD) {
            return quickSimilarity;
        }

        // If images are almost identical, return the quick result
        if (quickSimilarity > QUICK_ACCEPT_THRESHOLD) {
            return quickSimilarity;
        }

        // For small images, or when we have very few processors, use sequential calculation
        if (img1.getWidth() * img1.getHeight() < PARALLEL_THRESHOLD ||
                Runtime.getRuntime().availableProcessors() <= 2) {
            return calculateSSIMSequential(img1, img2);
        } else {
            return calculateSSIMParallel(img1, img2);
        }
    }

    /**
     * Quick comparison of two images by sampling pixels.
     * This is much faster than full SSIM and can quickly identify obviously
     * similar or different images.
     *
     * @param img1 First image
     * @param img2 Second image
     * @return A rough similarity score (0.0 to 1.0)
     */
    private double quickCompare(BufferedImage img1, BufferedImage img2) {
        int width = img1.getWidth();
        int height = img1.getHeight();

        // Calculate step size to sample about MAX_SAMPLE_POINTS pixels
        int totalPixels = width * height;
        int step = (int) Math.max(1, Math.sqrt(totalPixels / (double) MAX_SAMPLE_POINTS));

        int matchCount = 0;
        int totalSamples = 0;

        // Sample pixels in a grid pattern
        for (int y = 0; y < height; y += step) {
            for (int x = 0; x < width; x += step) {
                if (x < width && y < height) {
                    totalSamples++;

                    int rgb1 = img1.getRGB(x, y);
                    int rgb2 = img2.getRGB(x, y);

                    // Calculate color similarity
                    if (colorDistance(rgb1, rgb2) < 50) {
                        matchCount++;
                    }
                }
            }
        }

        return totalSamples > 0 ? (double) matchCount / totalSamples : 0.0;
    }

    /**
     * Calculate distance between two RGB colors.
     *
     * @param rgb1 First color
     * @param rgb2 Second color
     * @return Simple Manhattan distance in RGB space (0-765)
     */
    private int colorDistance(int rgb1, int rgb2) {
        int r1 = (rgb1 >> 16) & 0xFF;
        int g1 = (rgb1 >> 8) & 0xFF;
        int b1 = rgb1 & 0xFF;

        int r2 = (rgb2 >> 16) & 0xFF;
        int g2 = (rgb2 >> 8) & 0xFF;
        int b2 = rgb2 & 0xFF;

        // Simple Manhattan distance
        return Math.abs(r1 - r2) + Math.abs(g1 - g2) + Math.abs(b1 - b2);
    }

    /**
     * Calculate SSIM sequentially.
     *
     * @param img1 The first image
     * @param img2 The second image
     * @return The SSIM value
     */
    private double calculateSSIMSequential(BufferedImage img1, BufferedImage img2) {
        // Convert images to luminance arrays for faster processing
        double[][] gray1 = imageToLuminanceArray(img1);
        double[][] gray2 = imageToLuminanceArray(img2);

        // Calculate SSIM
        return calculateSSIM(gray1, gray2);
    }

    /**
     * Calculate SSIM in parallel for large images.
     *
     * @param img1 The first image
     * @param img2 The second image
     * @return The SSIM value
     */
    private double calculateSSIMParallel(BufferedImage img1, BufferedImage img2) {
        int height = img1.getHeight();
        int width = img1.getWidth();

        // Convert images to luminance arrays
        double[][] gray1 = imageToLuminanceArray(img1);
        double[][] gray2 = imageToLuminanceArray(img2);

        // Calculate the number of rows per task
        int numProcessors = Runtime.getRuntime().availableProcessors();
        int rowsPerTask = Math.max(1, height / numProcessors);

        // Create tasks for parallel processing
        Future<Double>[] tasks = new Future[numProcessors];
        double[] results = new double[numProcessors];
        int[] rowCounts = new int[numProcessors];

        for (int i = 0; i < numProcessors; i++) {
            final int taskIndex = i;
            final int startRow = i * rowsPerTask;
            final int endRow = (i == numProcessors - 1) ? height : (i + 1) * rowsPerTask;

            // Submit task for this section of the image
            tasks[i] = executor.submit(() -> {
                double ssimSum = 0.0;
                int count = 0;

                // Calculate SSIM for this section
                for (int y = startRow; y < endRow; y++) {
                    if (y + WINDOW_SIZE > height) continue;

                    for (int x = 0; x < width; x++) {
                        if (x + WINDOW_SIZE > width) continue;

                        // Extract window
                        double[] window1 = extractWindow(gray1, x, y);
                        double[] window2 = extractWindow(gray2, x, y);

                        // Calculate SSIM for this window
                        double ssim = calculateWindowSSIM(window1, window2);
                        ssimSum += ssim;
                        count++;
                    }
                }

                // Store row count for weighted average
                rowCounts[taskIndex] = count;
                return ssimSum;
            });
        }

        // Collect results
        try {
            for (int i = 0; i < numProcessors; i++) {
                results[i] = tasks[i].get(2, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.error("Error in parallel SSIM calculation: {}", e.getMessage());
            return calculateSSIMSequential(img1, img2); // Fallback to sequential
        }

        // Calculate weighted average
        double ssimSum = 0.0;
        int totalCount = 0;

        for (int i = 0; i < numProcessors; i++) {
            ssimSum += results[i];
            totalCount += rowCounts[i];
        }

        return totalCount > 0 ? ssimSum / totalCount : 0.0;
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
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, 0, 0, width, height, null);
        g.dispose();
        return resized;
    }

    /**
     * Convert an image to a luminance (grayscale) array for faster processing.
     * This optimized version uses direct pixel access for better performance.
     *
     * @param img The image to convert
     * @return The luminance array
     */
    private double[][] imageToLuminanceArray(BufferedImage img) {
        int width = img.getWidth();
        int height = img.getHeight();
        double[][] result = new double[height][width];

        // Get image data for faster access
        Raster raster = img.getRaster();
        DataBuffer buffer = raster.getDataBuffer();

        // Check if the image is grayscale
        boolean isGray = img.getType() == BufferedImage.TYPE_BYTE_GRAY;

        // For faster conversion, process data based on image type
        if (isGray) {
            // Grayscale image - direct mapping
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    result[y][x] = raster.getSample(x, y, 0);
                }
            }
        } else {
            // RGB image - use optimized direct pixel access
            int[] pixels = img.getRGB(0, 0, width, height, null, 0, width);
            int pixel = 0;

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int rgb = pixels[pixel++];
                    int r = (rgb >> 16) & 0xFF;
                    int g = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;

                    // Luminance formula (optimize with bit shifts)
                    result[y][x] = (r * 76 + g * 150 + b * 29) >> 8; // Approximates 0.299R + 0.587G + 0.114B
                }
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

        // Skip some windows to speed up processing for large images
        int stride = 1;
        if (width * height > 1000000) { // > 1MP
            stride = 2; // Skip every other window
        }

        // Iterate over windows with stride
        for (int y = 0; y < numWindowsY; y += stride) {
            for (int x = 0; x < numWindowsX; x += stride) {
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
        return numWindows > 0 ? ssimSum / numWindows : 0.0;
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
     * Optimized implementation with better numerical stability.
     *
     * @param window1 The first window
     * @param window2 The second window
     * @return The SSIM value (0.0 to 1.0)
     */
    private double calculateWindowSSIM(double[] window1, double[] window2) {
        int n = window1.length;

        // Compute means
        double mean1 = 0, mean2 = 0;
        for (int i = 0; i < n; i++) {
            mean1 += window1[i];
            mean2 += window2[i];
        }
        mean1 /= n;
        mean2 /= n;

        // Quick shortcut for identical windows
        if (Math.abs(mean1 - mean2) < 1e-6) {
            boolean identical = true;
            for (int i = 0; i < n && identical; i++) {
                if (Math.abs(window1[i] - window2[i]) > 1e-6) {
                    identical = false;
                }
            }
            if (identical) return 1.0;
        }

        // Compute variances and covariance
        double variance1 = 0, variance2 = 0, covariance = 0;
        for (int i = 0; i < n; i++) {
            double diff1 = window1[i] - mean1;
            double diff2 = window2[i] - mean2;

            variance1 += diff1 * diff1;
            variance2 += diff2 * diff2;
            covariance += diff1 * diff2;
        }

        variance1 /= n;
        variance2 /= n;
        covariance /= n;

        // Compute standard deviations
        double stdDev1 = Math.sqrt(variance1);
        double stdDev2 = Math.sqrt(variance2);

        // Calculate SSIM
        double numerator = (2 * mean1 * mean2 + C1) * (2 * covariance + C2);
        double denominator = (mean1 * mean1 + mean2 * mean2 + C1) * (stdDev1 * stdDev1 + stdDev2 * stdDev2 + C2);

        double ssim = numerator / denominator;

        // Ensure the result is in valid range
        return Math.max(0.0, Math.min(1.0, ssim));
    }

    /**
     * Shut down the executor service.
     */
    public void shutdown() {
        executor.shutdown();
    }
}