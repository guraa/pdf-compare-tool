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
 * Service for comparing images between PDF documents.
 * This service uses SSIM (Structural Similarity Index) to compare images
 * and detect differences.
 */
@Slf4j
@Service
public class ImageComparisonService {

    private final ExecutorService executorService;
    private final SSIMCalculator ssimCalculator;
    private final PdfRenderingService pdfRenderingService;

    // For cancellation support
    private final ConcurrentHashMap<String, AtomicBoolean> cancellationTokens = new ConcurrentHashMap<>();

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
     * Compare images between two pages with improved timeout handling.
     *
     * @param baseDocument The base document
     * @param compareDocument The document to compare against the base
     * @param basePageNumber The page number in the base document (1-based)
     * @param comparePageNumber The page number in the compare document (1-based)
     * @return A list of image differences
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
                return task.get(imageComparisonTimeoutSeconds, TimeUnit.SECONDS);
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
     * Perform the actual image comparison with cancellation support.
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

        // Get the images from the pages
        List<ImageInfo> baseImages = extractImagesFromPage(baseDocument, basePageNumber);

        // Check cancellation
        if (cancellationToken.get()) {
            log.info("Image comparison cancelled for pages {}/{}", basePageNumber, comparePageNumber);
            return new ArrayList<>();
        }

        List<ImageInfo> compareImages = extractImagesFromPage(compareDocument, comparePageNumber);

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
                    differences.add(createImageDifference(
                            baseImage, bestMatch, bestSimilarity, "modified"));
                }
            } else {
                // No match found, image was deleted
                differences.add(createImageDifference(
                        baseImage, null, 0.0, "deleted"));
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
            differences.add(createImageDifference(
                    null, compareImage, 0.0, "added"));
        }

        return differences;
    }

    /**
     * Extract images from a page with improved error handling.
     *
     * @param document The document
     * @param pageNumber The page number (1-based)
     * @return A list of image information
     * @throws IOException If there is an error extracting the images
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

            // Create a dummy directory pattern to simulate image extraction
            Path dummyImagesDir = Paths.get(extractedImagesPath, "dummy");
            if (!Files.exists(dummyImagesDir)) {
                Files.createDirectories(dummyImagesDir);
            }

            // In a real implementation, this would extract actual images from the PDF
            // For now, return a small set of dummy image information
            List<ImageInfo> images = new ArrayList<>();

            // Use the rendered page as a source for image information
            try {
                File renderedPage = pdfRenderingService.renderPage(document, pageNumber);
                if (renderedPage.exists()) {
                    BufferedImage pageImage = ImageIO.read(renderedPage);
                    if (pageImage != null) {
                        // Create a dummy image info for the whole page
                        ImageInfo imageInfo = ImageInfo.builder()
                                .id(UUID.randomUUID().toString())
                                .path(renderedPage.getAbsolutePath())
                                .width(pageImage.getWidth())
                                .height(pageImage.getHeight())
                                .hash(calculateImageHash(pageImage))
                                .pageNumber(pageNumber)
                                .x(0)
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
     *
     * @param image The image
     * @return The hash
     */
    private String calculateImageHash(BufferedImage image) {
        // In a real implementation, this would calculate a perceptual hash
        // For now, use a simple hash based on width, height, and a sample of pixels
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
     *
     * @param baseImage The base image
     * @param compareImage The compare image
     * @return The similarity score (0.0 to 1.0)
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
     * Create an image difference.
     *
     * @param baseImage The base image
     * @param compareImage The compare image
     * @param similarityScore The similarity score
     * @param changeType The change type (added, deleted, modified)
     * @return The image difference
     */
    private ImageDifference createImageDifference(
            ImageInfo baseImage, ImageInfo compareImage,
            double similarityScore, String changeType) {

        ImageDifference.ImageDifferenceBuilder builder = ImageDifference.builder()
                .id(UUID.randomUUID().toString())
                .type("image")
                .changeType(changeType)
                .similarityScore(similarityScore);

        // Set severity based on similarity score
        if (similarityScore < 0.7) {
            builder.severity("major");
        } else if (similarityScore < 0.9) {
            builder.severity("minor");
        } else {
            builder.severity("cosmetic");
        }

        // Set base image properties
        if (baseImage != null) {
            builder.baseImageHash(baseImage.getHash())
                    .baseImagePath(baseImage.getPath())
                    .baseWidth(baseImage.getWidth())
                    .baseHeight(baseImage.getHeight())
                    .basePageNumber(baseImage.getPageNumber());

            // Set position based on base image
            builder.x(baseImage.getX())
                    .y(baseImage.getY())
                    .width(baseImage.getWidth())
                    .height(baseImage.getHeight());
        }

        // Set compare image properties
        if (compareImage != null) {
            builder.compareImageHash(compareImage.getHash())
                    .compareImagePath(compareImage.getPath())
                    .compareWidth(compareImage.getWidth())
                    .compareHeight(compareImage.getHeight())
                    .comparePageNumber(compareImage.getPageNumber());

            // If no base image, set position based on compare image
            if (baseImage == null) {
                builder.x(compareImage.getX())
                        .y(compareImage.getY())
                        .width(compareImage.getWidth())
                        .height(compareImage.getHeight());
            }
        }

        // Set description based on change type
        switch (changeType) {
            case "added":
                builder.description("Image added");
                break;
            case "deleted":
                builder.description("Image deleted");
                break;
            case "modified":
                builder.description(String.format("Image modified (%.1f%% similar)",
                        similarityScore * 100));
                break;
        }

        return builder.build();
    }

    /**
     * Cancel all ongoing image comparisons.
     */
    public void cancelAllComparisons() {
        log.info("Cancelling all image comparisons");
        cancellationTokens.forEach((key, token) -> token.set(true));
    }
}