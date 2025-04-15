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
import java.util.stream.Collectors;

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

    // Cache of image comparison results
    private final ConcurrentHashMap<String, CompletableFuture<List<ImageDifference>>> comparisonTasks = new ConcurrentHashMap<>();

    /**
     * Compare images between two pages.
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
        
        // Check if we already have a comparison task for these pages
        return comparisonTasks.computeIfAbsent(cacheKey, key -> {
            // Submit a new comparison task
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return doCompareImages(baseDocument, compareDocument, basePageNumber, comparePageNumber);
                } catch (IOException e) {
                    log.error("Error comparing images: {}", e.getMessage(), e);
                    return new ArrayList<>();
                }
            }, executorService);
        }).join(); // Wait for the task to complete
    }

    /**
     * Perform the actual image comparison.
     *
     * @param baseDocument The base document
     * @param compareDocument The document to compare against the base
     * @param basePageNumber The page number in the base document (1-based)
     * @param comparePageNumber The page number in the compare document (1-based)
     * @return A list of image differences
     * @throws IOException If there is an error comparing the images
     */
    private List<ImageDifference> doCompareImages(
            PdfDocument baseDocument, PdfDocument compareDocument,
            int basePageNumber, int comparePageNumber) throws IOException {
        
        // Get the images from the pages
        List<ImageInfo> baseImages = extractImagesFromPage(baseDocument, basePageNumber);
        List<ImageInfo> compareImages = extractImagesFromPage(compareDocument, comparePageNumber);
        
        // Create a map of image hashes to images for quick lookup
        Map<String, ImageInfo> baseImageMap = baseImages.stream()
                .collect(Collectors.toMap(ImageInfo::getHash, img -> img));
        Map<String, ImageInfo> compareImageMap = compareImages.stream()
                .collect(Collectors.toMap(ImageInfo::getHash, img -> img));
        
        // Track which images have been matched
        Map<String, Boolean> baseMatched = new HashMap<>();
        Map<String, Boolean> compareMatched = new HashMap<>();
        
        // List to store the differences
        List<ImageDifference> differences = new ArrayList<>();
        
        // First pass: Find exact matches by hash
        for (ImageInfo baseImage : baseImages) {
            String baseHash = baseImage.getHash();
            
            if (compareImageMap.containsKey(baseHash)) {
                // Exact match found
                baseMatched.put(baseHash, true);
                compareMatched.put(baseHash, true);
            }
        }
        
        // Second pass: Find similar images using SSIM
        for (ImageInfo baseImage : baseImages) {
            String baseHash = baseImage.getHash();
            
            // Skip if already matched
            if (baseMatched.containsKey(baseHash)) {
                continue;
            }
            
            // Find the best match among unmatched compare images
            ImageInfo bestMatch = null;
            double bestSimilarity = imageSimilarityThreshold;
            
            for (ImageInfo compareImage : compareImages) {
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
     * Extract images from a page.
     *
     * @param document The document
     * @param pageNumber The page number (1-based)
     * @return A list of image information
     * @throws IOException If there is an error extracting the images
     */
    private List<ImageInfo> extractImagesFromPage(PdfDocument document, int pageNumber) throws IOException {
        // In a real implementation, this would use iText to extract images from the PDF
        // For now, we'll just use a placeholder implementation
        
        // Create a directory for extracted images if it doesn't exist
        String extractedImagesPath = document.getExtractedImagesPath(pageNumber);
        Path extractedImagesDir = Paths.get(extractedImagesPath);
        if (!Files.exists(extractedImagesDir)) {
            Files.createDirectories(extractedImagesDir);
        }
        
        // Check if we've already extracted images for this page
        File[] extractedImageFiles = new File(extractedImagesPath).listFiles(
                file -> file.isFile() && file.getName().toLowerCase().endsWith(".png"));
        
        if (extractedImageFiles != null && extractedImageFiles.length > 0) {
            // Images already extracted, load them
            List<ImageInfo> images = new ArrayList<>();
            
            for (File imageFile : extractedImageFiles) {
                BufferedImage image = ImageIO.read(imageFile);
                
                ImageInfo imageInfo = ImageInfo.builder()
                        .id(imageFile.getName().replace(".png", ""))
                        .path(imageFile.getAbsolutePath())
                        .width(image.getWidth())
                        .height(image.getHeight())
                        .hash(calculateImageHash(image))
                        .pageNumber(pageNumber)
                        .build();
                
                images.add(imageInfo);
            }
            
            return images;
        } else {
            // No images extracted yet, use a placeholder
            // In a real implementation, this would extract images from the PDF
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
        // For now, we'll just use a placeholder
        return UUID.randomUUID().toString();
    }

    /**
     * Calculate the similarity between two images.
     *
     * @param baseImage The base image
     * @param compareImage The compare image
     * @return The similarity score (0.0 to 1.0)
     */
    private double calculateImageSimilarity(ImageInfo baseImage, ImageInfo compareImage) {
        try {
            // Load the images
            BufferedImage baseImg = ImageIO.read(new File(baseImage.getPath()));
            BufferedImage compareImg = ImageIO.read(new File(compareImage.getPath()));
            
            // Calculate SSIM
            return ssimCalculator.calculate(baseImg, compareImg);
        } catch (IOException e) {
            log.error("Error calculating image similarity: {}", e.getMessage(), e);
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
                   .baseHeight(baseImage.getHeight());
            
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
                   .compareHeight(compareImage.getHeight());
            
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
}
