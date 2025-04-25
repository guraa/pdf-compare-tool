package guraa.pdfcompare.service;

import guraa.pdfcompare.model.PdfDocument;
import guraa.pdfcompare.model.difference.ImageDifference;
import guraa.pdfcompare.util.ImageInfo;
import guraa.pdfcompare.visual.SSIMCalculator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Enhanced service for comparing images between PDF documents
 * with fixed coordinate handling.
 */
@Slf4j
@Service
public class ImageComparisonService {

    private final ExecutorService executorService;
    private final SSIMCalculator ssimCalculator;
    private final PdfRenderingService pdfRenderingService;

    // For cancellation support
    private final ConcurrentHashMap<String, AtomicBoolean> cancellationTokens = new ConcurrentHashMap<>();

    // Fixed DPI values
    private static final float FIXED_RENDERING_DPI = 150f;
    private static final float FIXED_THUMBNAIL_DPI = 72f;

    /**
     * Constructor with qualifier to specify which executor service to use.
     *
     * @param executorService The executor service for comparison operations
     * @param ssimCalculator The SSIM calculator for image comparison
     * @param pdfRenderingService The PDF rendering service
     */
    public ImageComparisonService(
            @Qualifier("comparisonExecutor") ExecutorService executorService,
            SSIMCalculator ssimCalculator,
            PdfRenderingService pdfRenderingService) {
        this.executorService = executorService;
        this.ssimCalculator = ssimCalculator;
        this.pdfRenderingService = pdfRenderingService;
    }

    @Value("${app.comparison.image-similarity-threshold:0.95}")
    private double imageSimilarityThreshold;

    @Value("${app.comparison.image-comparison-timeout-seconds:30}")
    private int imageComparisonTimeoutSeconds = 30;

    // Cache of image comparison results
    private final ConcurrentHashMap<String, CompletableFuture<List<ImageDifference>>> comparisonTasks = new ConcurrentHashMap<>();

    /**
     * Compare images between two pages with improved coordinate handling.
     *
     * @param baseDocument The base document
     * @param compareDocument The document to compare against the base
     * @param basePageNumber The page number in the base document (1-based)
     * @param comparePageNumber The page number in the compare document (1-based)
     * @return A list of image differences with proper coordinates
     * @throws IOException If there is an error comparing the images
     */
    public List<ImageDifference> compareImages(
            PdfDocument baseDocument, PdfDocument compareDocument,
            int basePageNumber, int comparePageNumber) throws IOException {

        String cacheKey = baseDocument.getFileId() + "_" + basePageNumber + "_" +
                compareDocument.getFileId() + "_" + comparePageNumber;

        // Create cancellation token for this task
        AtomicBoolean cancellationToken = new AtomicBoolean(false);
        cancellationTokens.put(cacheKey, cancellationToken);

        try {
            // Check if we already have a comparison task for these pages
            CompletableFuture<List<ImageDifference>> task = comparisonTasks.computeIfAbsent(cacheKey, key -> {
                // Submit a new comparison task
                return CompletableFuture.supplyAsync(() -> {
                    try {
                        return doCompareImages(baseDocument, compareDocument, basePageNumber, comparePageNumber, cancellationToken);
                    } catch (IOException e) {
                        log.error("Error comparing images: {}", e.getMessage(), e);
                        return new ArrayList<>();
                    }
                }, executorService);
            });

            // Set a timeout for the comparison
            try {
                List<ImageDifference> results = task.get(imageComparisonTimeoutSeconds, TimeUnit.SECONDS);

                // Fix coordinates for all image differences
                for (ImageDifference diff : results) {
                    fixImageDifferenceCoordinates(diff);
                }

                return results;
            } catch (Exception e) {
                log.warn("Image comparison timed out after {} seconds for pages {}/{} in documents {}/{}",
                        imageComparisonTimeoutSeconds, basePageNumber, comparePageNumber,
                        baseDocument.getFileId(), compareDocument.getFileId());

                // Cancel the task
                cancellationToken.set(true);
                task.cancel(true);

                // Remove it from cache
                comparisonTasks.remove(cacheKey);

                // Return empty result instead of throwing
                return new ArrayList<>();
            }
        } finally {
            // Clean up the cancellation token
            cancellationTokens.remove(cacheKey);
        }
    }

    /**
     * Fixes image difference coordinates to ensure they are properly set.
     *
     * @param diff The image difference to fix
     */
    private void fixImageDifferenceCoordinates(ImageDifference diff) {
        if (diff == null) return;

        // First make sure we have non-zero values for all coordinates
        if (diff.getWidth() <= 0) {
            diff.setWidth(400); // Default width
        }

        if (diff.getHeight() <= 0) {
            diff.setHeight(300); // Default height
        }

        // If x,y are zero but base/compare coordinates exist, use those
        if (diff.getX() == 0 && diff.getY() == 0) {
            if (diff.isAddition() && diff.getCompareWidth() > 0) {
                // For additions, use compare coordinates
                diff.setX(diff.getCompareX() > 0 ? diff.getCompareX() : 0);
                diff.setY(diff.getCompareY() > 0 ? diff.getCompareY() : 0);
                diff.setWidth(diff.getCompareWidth() > 0 ? diff.getCompareWidth() : 400);
                diff.setHeight(diff.getCompareHeight() > 0 ? diff.getCompareHeight() : 300);
            } else if (diff.isDeletion() && diff.getBaseWidth() > 0) {
                // For deletions, use base coordinates
                diff.setX(diff.getBaseX() > 0 ? diff.getBaseX() : 0);
                diff.setY(diff.getBaseY() > 0 ? diff.getBaseY() : 0);
                diff.setWidth(diff.getBaseWidth() > 0 ? diff.getBaseWidth() : 400);
                diff.setHeight(diff.getBaseHeight() > 0 ? diff.getBaseHeight() : 300);
            } else if (diff.isModification()) {
                // For modifications, use average or whichever is available
                double x = 0, y = 0, width = 400, height = 300;

                if (diff.getBaseWidth() > 0 && diff.getCompareWidth() > 0) {
                    x = (diff.getBaseX() + diff.getCompareX()) / 2;
                    y = (diff.getBaseY() + diff.getCompareY()) / 2;
                    width = Math.max(diff.getBaseWidth(), diff.getCompareWidth());
                    height = Math.max(diff.getBaseHeight(), diff.getCompareHeight());
                } else if (diff.getBaseWidth() > 0) {
                    x = diff.getBaseX();
                    y = diff.getBaseY();
                    width = diff.getBaseWidth();
                    height = diff.getBaseHeight();
                } else if (diff.getCompareWidth() > 0) {
                    x = diff.getCompareX();
                    y = diff.getCompareY();
                    width = diff.getCompareWidth();
                    height = diff.getCompareHeight();
                }

                diff.setX(x);
                diff.setY(y);
                diff.setWidth(width);
                diff.setHeight(height);
            } else {
                // If nothing else works, set some default values
                diff.setX(50);
                diff.setY(50);
            }
        }
    }

    /**
     * Perform the actual image comparison with improved coordinate handling.
     *
     * @param baseDocument The base document
     * @param compareDocument The document to compare against the base
     * @param basePageNumber The page number in the base document (1-based)
     * @param comparePageNumber The page number in the compare document (1-based)
     * @param cancellationToken Token to check for cancellation
     * @return A list of image differences
     * @throws IOException If there is an error comparing the images
     */
    private List<ImageDifference> doCompareImages(
            PdfDocument baseDocument, PdfDocument compareDocument,
            int basePageNumber, int comparePageNumber,
            AtomicBoolean cancellationToken) throws IOException {

        // Extract images from the pages with proper coordinates
        List<ImageInfo> baseImages = new ArrayList<>();
        if (basePageNumber > 0) {
            baseImages = extractImagesFromPage(baseDocument, basePageNumber);
        }

        // Check cancellation
        if (cancellationToken.get()) {
            log.info("Image comparison cancelled for pages {}/{}", basePageNumber, comparePageNumber);
            return new ArrayList<>();
        }

        List<ImageInfo> compareImages = new ArrayList<>();
        if (comparePageNumber > 0) {
            compareImages = extractImagesFromPage(compareDocument, comparePageNumber);
        }

        // Check cancellation
        if (cancellationToken.get()) {
            log.info("Image comparison cancelled for pages {}/{}", basePageNumber, comparePageNumber);
            return new ArrayList<>();
        }

        // Quick short-circuit if both pages have no images
        if (baseImages.isEmpty() && compareImages.isEmpty()) {
            return new ArrayList<>();
        }

        // Create a map of image hashes to images for quick lookup
        Map<String, ImageInfo> baseImageMap = new HashMap<>();
        for (ImageInfo img : baseImages) {
            baseImageMap.put(img.getHash(), img);
            // Check cancellation
            if (cancellationToken.get()) return new ArrayList<>();
        }

        Map<String, ImageInfo> compareImageMap = new HashMap<>();
        for (ImageInfo img : compareImages) {
            compareImageMap.put(img.getHash(), img);
            // Check cancellation
            if (cancellationToken.get()) return new ArrayList<>();
        }

        // Track which images have been matched
        Map<String, Boolean> baseMatched = new HashMap<>();
        Map<String, Boolean> compareMatched = new HashMap<>();

        // List to store the differences
        List<ImageDifference> differences = new ArrayList<>();

        // First pass: Find exact matches by hash
        for (ImageInfo baseImage : baseImages) {
            // Check cancellation
            if (cancellationToken.get()) return new ArrayList<>();

            String baseHash = baseImage.getHash();

            if (compareImageMap.containsKey(baseHash)) {
                // Exact match found
                baseMatched.put(baseHash, true);
                compareMatched.put(baseHash, true);
            }
        }

        // Second pass: Find similar images using SSIM
        for (ImageInfo baseImage : baseImages) {
            // Check cancellation
            if (cancellationToken.get()) return new ArrayList<>();

            String baseHash = baseImage.getHash();

            // Skip if already matched
            if (baseMatched.containsKey(baseHash)) {
                continue;
            }

            // Find the best match among unmatched compare images
            ImageInfo bestMatch = null;
            double bestSimilarity = imageSimilarityThreshold;

            for (ImageInfo compareImage : compareImages) {
                // Check cancellation
                if (cancellationToken.get()) return new ArrayList<>();

                String compareHash = compareImage.getHash();

                // Skip if already matched
                if (compareMatched.containsKey(compareHash)) {
                    continue;
                }

                // Calculate similarity
                double similarity = calculateImageSimilarity(baseImage, compareImage);

                if (similarity > bestSimilarity) {
                    bestSimilarity = similarity;
                    bestMatch = compareImage;
                }
            }

            if (bestMatch != null) {
                // Similar match found
                baseMatched.put(baseHash, true);
                compareMatched.put(bestMatch.getHash(), true);

                // If similarity is not perfect, add a difference
                if (bestSimilarity < 1.0) {
                    // Create a modified image difference with coordinates from both images
                    ImageDifference diff = createImageDifference(
                            baseImage, bestMatch, bestSimilarity, "modified",
                            basePageNumber, comparePageNumber);

                    differences.add(diff);
                }
            } else {
                // No match found, image was deleted
                // Create a deleted image difference with coordinates from base image
                ImageDifference diff = createImageDifference(
                        baseImage, null, 0.0, "deleted",
                        basePageNumber, 0);

                differences.add(diff);
            }
        }

        // Check cancellation
        if (cancellationToken.get()) return new ArrayList<>();

        // Third pass: Find unmatched compare images (added)
        for (ImageInfo compareImage : compareImages) {
            String compareHash = compareImage.getHash();

            // Skip if already matched
            if (compareMatched.containsKey(compareHash)) {
                continue;
            }

            // No match found, image was added
            // Create an added image difference with coordinates from compare image
            ImageDifference diff = createImageDifference(
                    null, compareImage, 0.0, "added",
                    0, comparePageNumber);

            differences.add(diff);
        }

        return differences;
    }

    /**
     * Create an ImageDifference with proper coordinates.
     */
    private ImageDifference createImageDifference(
            ImageInfo baseImage, ImageInfo compareImage,
            double similarityScore, String changeType,
            int basePageNumber, int comparePageNumber) {

        ImageDifference.ImageDifferenceBuilder builder = ImageDifference.builder()
                .id(UUID.randomUUID().toString())
                .type("image")
                .changeType(changeType)
                .basePageNumber(basePageNumber)
                .comparePageNumber(comparePageNumber)
                .similarityScore(similarityScore);

        // Set width and height to ensure we have default values
        double width = 400;
        double height = 300;
        double x = 50;
        double y = 50;

        // Determine severity
        if (changeType.equals("added") || changeType.equals("deleted")) {
            builder.severity("major");
        } else {
            // For modifications, use similarity score
            if (similarityScore < 0.7) {
                builder.severity("major");
            } else if (similarityScore < 0.9) {
                builder.severity("minor");
            } else {
                builder.severity("cosmetic");
            }
        }

        // Set properties based on available images
        if (baseImage != null) {
            builder.baseImagePath(baseImage.getPath())
                    .baseImageHash(baseImage.getHash())
                    .baseWidth((int)baseImage.getWidth())
                    .baseHeight((int)baseImage.getHeight())
                    .baseX(baseImage.getX())
                    .baseY(baseImage.getY());

            // For deletions, use base image properties for display
            if (changeType.equals("deleted")) {
                width = baseImage.getWidth();
                height = baseImage.getHeight();
                x = baseImage.getX();
                y = baseImage.getY();
            }
        }

        if (compareImage != null) {
            builder.compareImagePath(compareImage.getPath())
                    .compareImageHash(compareImage.getHash())
                    .compareWidth((int)compareImage.getWidth())
                    .compareHeight((int)compareImage.getHeight())
                    .compareX(compareImage.getX())
                    .compareY(compareImage.getY());

            // For additions, use compare image properties for display
            if (changeType.equals("added")) {
                width = compareImage.getWidth();
                height = compareImage.getHeight();
                x = compareImage.getX();
                y = compareImage.getY();
            }
        }

        // For modifications, use average/max dimensions
        if (changeType.equals("modified") && baseImage != null && compareImage != null) {
            width = Math.max(baseImage.getWidth(), compareImage.getWidth());
            height = Math.max(baseImage.getHeight(), compareImage.getHeight());
            x = (baseImage.getX() + compareImage.getX()) / 2;
            y = (baseImage.getY() + compareImage.getY()) / 2;
        }

        // Make sure we have positive values
        if (width <= 0) width = 400;
        if (height <= 0) height = 300;

        // Set display coordinates
        builder.width(width)
                .height(height)
                .x(x)
                .y(y);

        // Build the difference
        return builder.build();
    }

    /**
     * Extract images from a page with explicit coordinate information.
     */
    private List<ImageInfo> extractImagesFromPage(PdfDocument document, int pageNumber) throws IOException {
        log.debug("Extracting images from document {} page {}", document.getFileId(), pageNumber);

        try {
            // Create a directory for extracted images if it doesn't exist
            String extractedImagesPath = document.getExtractedImagesPath(pageNumber);
            Path extractedImagesDir = Paths.get(extractedImagesPath);
            if (!Files.exists(extractedImagesDir)) {
                Files.createDirectories(extractedImagesDir);
            }

            // Use the rendered page as a source for image information
            List<ImageInfo> images = new ArrayList<>();

            try {
                File renderedPage = pdfRenderingService.renderPage(document, pageNumber);
                if (renderedPage.exists()) {
                    BufferedImage pageImage = ImageIO.read(renderedPage);
                    if (pageImage != null) {
                        // Create whole page image info with proper coordinates
                        ImageInfo imageInfo = ImageInfo.builder()
                                .id(UUID.randomUUID().toString())
                                .path(renderedPage.getAbsolutePath())
                                .width(pageImage.getWidth())
                                .height(pageImage.getHeight())
                                .hash(calculateImageHash(pageImage))
                                .pageNumber(pageNumber)
                                .x(0)  // Start at origin
                                .y(0)
                                .format("PNG")
                                .colorSpace("RGB")
                                .bitsPerComponent(24)
                                .build();

                        images.add(imageInfo);
                    }
                }
            } catch (Exception e) {
                log.warn("Error creating image info from rendered page: {}", e.getMessage());
                // Continue and return empty list instead of failing
            }

            return images;
        } catch (Exception e) {
            log.error("Error extracting images from page: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Calculate a hash for an image.
     */
    private String calculateImageHash(BufferedImage image) {
        // Simple hash based on image dimensions and sampling
        try {
            int width = image.getWidth();
            int height = image.getHeight();

            int sampleSize = 5;
            StringBuilder sb = new StringBuilder();

            sb.append(width).append("x").append(height).append("_");

            // Sample pixels in a grid
            for (int y = 0; y < height; y += height / sampleSize) {
                for (int x = 0; x < width; x += width / sampleSize) {
                    if (x < width && y < height) {
                        int rgb = image.getRGB(x, y);
                        sb.append(rgb & 0xFFFFFF).append("_");
                    }
                }
            }

            return sb.toString();
        } catch (Exception e) {
            log.warn("Error calculating image hash: {}", e.getMessage());
            return UUID.randomUUID().toString();
        }
    }

    /**
     * Calculate the similarity between two images with safety checks.
     */
    private double calculateImageSimilarity(ImageInfo baseImage, ImageInfo compareImage) {
        try {
            // Safety checks
            if (baseImage == null || compareImage == null) {
                return 0.0;
            }

            File baseFile = new File(baseImage.getPath());
            File compareFile = new File(compareImage.getPath());

            if (!baseFile.exists() || !compareFile.exists()) {
                return 0.0;
            }

            // Load the images
            BufferedImage baseImg = ImageIO.read(baseFile);
            BufferedImage compareImg = ImageIO.read(compareFile);

            if (baseImg == null || compareImg == null) {
                return 0.0;
            }

            // Calculate SSIM
            return ssimCalculator.calculate(baseImg, compareImg);
        } catch (IOException e) {
            log.error("Error calculating image similarity: {}", e.getMessage(), e);
            return 0.0;
        } catch (Exception e) {
            log.error("Unexpected error in image similarity calculation: {}", e.getMessage(), e);
            return 0.0;
        }
    }

    /**
     * Cancel all ongoing image comparisons.
     */
    public void cancelAllComparisons() {
        log.info("Cancelling all image comparisons");
        cancellationTokens.forEach((key, token) -> token.set(true));
    }
}