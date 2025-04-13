package guraa.pdfcompare.service;

import guraa.pdfcompare.model.difference.Difference;
import guraa.pdfcompare.model.difference.ImageDifference;
import guraa.pdfcompare.util.DifferenceCalculator;
import guraa.pdfcompare.util.ImageConverter;
import guraa.pdfcompare.util.ImageExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/**
 * Service for comparing images between PDF documents.
 * Simplified version that doesn't rely on getter methods.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageComparisonService {

    private final ImageExtractor imageExtractor;
    private final DifferenceCalculator differenceCalculator;

    /**
     * Compare images on a page between two PDF documents.
     *
     * @param basePdf Base PDF document
     * @param comparePdf Compare PDF document
     * @param pageIndex Page index to compare
     * @param outputDir Directory for extracted images
     * @return List of image differences
     * @throws IOException If there's an error processing the images
     */
    public List<Difference> compareImagesOnPage(
            PDDocument basePdf,
            PDDocument comparePdf,
            int pageIndex,
            Path outputDir) throws IOException {

        // Extract images from both pages
        List<ImageExtractor.ImageInfo> baseImages = imageExtractor.extractImagesFromPage(
                basePdf, pageIndex, outputDir.resolve("base_images"));
        List<ImageExtractor.ImageInfo> compareImages = imageExtractor.extractImagesFromPage(
                comparePdf, pageIndex, outputDir.resolve("compare_images"));

        return compareImages(baseImages, compareImages);
    }

    /**
     * Compare images on a page between two PDF documents in document pair mode.
     *
     * @param basePdf Base PDF document
     * @param comparePdf Compare PDF document
     * @param basePageIndex Base page index
     * @param comparePageIndex Compare page index
     * @param outputDir Directory for extracted images
     * @return List of image differences
     * @throws IOException If there's an error processing the images
     */
    public List<Difference> compareImagesOnPage(
            PDDocument basePdf,
            PDDocument comparePdf,
            int basePageIndex,
            int comparePageIndex,
            Path outputDir) throws IOException {

        // Extract images from both pages
        List<ImageExtractor.ImageInfo> baseImages = imageExtractor.extractImagesFromPage(
                basePdf, basePageIndex, outputDir.resolve("base_images"));
        List<ImageExtractor.ImageInfo> compareImages = imageExtractor.extractImagesFromPage(
                comparePdf, comparePageIndex, outputDir.resolve("compare_images"));

        return compareImages(baseImages, compareImages);
    }

    /**
     * Compare images between two pages.
     *
     * @param baseImages Images from base page
     * @param compareImages Images from comparison page
     * @return List of differences
     */
    private List<Difference> compareImages(
            List<ImageExtractor.ImageInfo> baseImages,
            List<ImageExtractor.ImageInfo> compareImages) {

        List<Difference> differences = new ArrayList<>();

        // Match images based on hash and content
        Map<ImageExtractor.ImageInfo, ImageExtractor.ImageInfo> matches =
                matchImages(baseImages, compareImages);

        // Track images that have been matched
        Set<ImageExtractor.ImageInfo> matchedBaseImages = new HashSet<>(matches.keySet());
        Set<ImageExtractor.ImageInfo> matchedCompareImages = new HashSet<>(matches.values());

        // Find differences in matched images
        int imageIndex = 0;
        for (Map.Entry<ImageExtractor.ImageInfo, ImageExtractor.ImageInfo> match : matches.entrySet()) {
            ImageExtractor.ImageInfo baseImage = match.getKey();
            ImageExtractor.ImageInfo compareImage = match.getValue();

            // Since we can't access the properties, we'll use the presence of a match
            // to determine that there's at least some difference
            String diffId = UUID.randomUUID().toString();
            String description = "Image content differs";

            // Create image difference
            ImageDifference diff = ImageDifference.builder()
                    .id(diffId)
                    .type("image")
                    .changeType("modified")
                    .severity("minor")
                    .description(description)
                    .baseImageHash(baseImage.getImageHash())
                    .compareImageHash(compareImage.getImageHash())
                    .build();

            // Use default positioning for image differences
            setDefaultImagePosition(diff, pageIndex(imageIndex));

            differences.add(diff);
            imageIndex++;
        }

        // Images only in base document (deleted)
        for (ImageExtractor.ImageInfo image : baseImages) {
            if (!matchedBaseImages.contains(image)) {
                // Create image difference for deleted image
                String diffId = UUID.randomUUID().toString();

                ImageDifference diff = ImageDifference.builder()
                        .id(diffId)
                        .type("image")
                        .changeType("deleted")
                        .severity("major")
                        .description("Image removed")
                        .baseImageHash(image.getImageHash())
                        .build();

                // Use default positioning for deleted images
                setDefaultImagePosition(diff, pageIndex(imageIndex));

                differences.add(diff);
                imageIndex++;
            }
        }

        // Images only in compare document (added)
        for (ImageExtractor.ImageInfo image : compareImages) {
            if (!matchedCompareImages.contains(image)) {
                // Create image difference for added image
                String diffId = UUID.randomUUID().toString();

                ImageDifference diff = ImageDifference.builder()
                        .id(diffId)
                        .type("image")
                        .changeType("added")
                        .severity("major")
                        .description("Image added")
                        .compareImageHash(image.getImageHash())
                        .build();

                // Use default positioning for added images
                setDefaultImagePosition(diff, pageIndex(imageIndex));

                differences.add(diff);
                imageIndex++;
            }
        }

        return differences;
    }

    /**
     * Calculate a y-position on the page based on image index
     * to visually separate multiple images.
     *
     * @param imageIndex The index of the image
     * @return Relative Y position (0.0-1.0)
     */
    private double pageIndex(int imageIndex) {
        // Position images at different spots on the page
        // First image at 0.3 (30% from top), then space them out
        return 0.3 + (imageIndex % 5) * 0.1;
    }

    /**
     * Set position for an image difference with proper coordinate handling.
     * This method ensures image differences are positioned correctly in display coordinates.
     *
     * @param diff The image difference
     * @param pageWidth Width of the page
     * @param pageHeight Height of the page
     * @param relativeY Relative Y position (0.0-1.0)
     */
    private void setImageDifferencePosition(ImageDifference diff, double pageWidth, double pageHeight, double relativeY) {
        // Calculate absolute Y position from relative Y
        double y = pageHeight * relativeY;

        // Position at 20% from left margin
        double x = pageWidth * 0.2;

        // Use 60% of page width and 25% of page height for image differences
        double width = pageWidth * 0.6;
        double height = pageHeight * 0.25;

        // Ensure we don't exceed page bounds
        if (y + height > pageHeight) {
            y = pageHeight - height - 10; // Keep 10 units from bottom edge
        }

        // These coordinates are in display space (top-left origin)
        // so no additional transformation is needed
        differenceCalculator.setPositionAndBounds(diff, x, y, width, height);
    }

    /**
     * Calculate a y-position on the page based on image index
     * to visually separate multiple images.
     *
     * @param imageIndex The index of the image
     * @return Relative Y position (0.0-1.0)
     */
    private double pageYPositionForIndex(int imageIndex) {
        // Position images at different spots on the page
        // First image at 0.3 (30% from top), then space them out
        return 0.3 + (imageIndex % 5) * 0.1;
    }

    /**
     * Compare images between two pages with proper coordinate handling.
     * This method ensures all image differences have appropriate coordinates.
     *
     * @param baseImages Images from base page
     * @param compareImages Images from comparison page
     * @param pageWidth Width of the page
     * @param pageHeight Height of the page
     * @return List of differences
     */
    private List<Difference> compareImages(
            List<ImageExtractor.ImageInfo> baseImages,
            List<ImageExtractor.ImageInfo> compareImages,
            double pageWidth,
            double pageHeight) {

        List<Difference> differences = new ArrayList<>();

        // Match images based on hash and content
        Map<ImageExtractor.ImageInfo, ImageExtractor.ImageInfo> matches =
                matchImages(baseImages, compareImages);

        // Track images that have been matched
        Set<ImageExtractor.ImageInfo> matchedBaseImages = new HashSet<>(matches.keySet());
        Set<ImageExtractor.ImageInfo> matchedCompareImages = new HashSet<>(matches.values());

        // Find differences in matched images
        int imageIndex = 0;
        for (Map.Entry<ImageExtractor.ImageInfo, ImageExtractor.ImageInfo> match : matches.entrySet()) {
            ImageExtractor.ImageInfo baseImage = match.getKey();
            ImageExtractor.ImageInfo compareImage = match.getValue();

            // Check for content differences
            double contentSimilarity = compareImageContent(baseImage, compareImage);

            // If there's a significant difference in content
            if (contentSimilarity < 0.95) { // Threshold for considering images different
                String diffId = UUID.randomUUID().toString();
                String description = "Image content differs";

                // Create image difference
                ImageDifference diff = ImageDifference.builder()
                        .id(diffId)
                        .type("image")
                        .changeType("modified")
                        .severity("minor") // Can be determined based on contentSimilarity
                        .description(description)
                        .baseImageHash(baseImage.getImageHash())
                        .compareImageHash(compareImage.getImageHash())
                        .similarityScore(contentSimilarity)
                        .build();

                // Set position with proper coordinate handling
                setImageDifferencePosition(diff, pageWidth, pageHeight, pageYPositionForIndex(imageIndex));

                differences.add(diff);
            }

            imageIndex++;
        }

        // Images only in base document (deleted)
        for (ImageExtractor.ImageInfo image : baseImages) {
            if (!matchedBaseImages.contains(image)) {
                // Create image difference for deleted image
                String diffId = UUID.randomUUID().toString();

                ImageDifference diff = ImageDifference.builder()
                        .id(diffId)
                        .type("image")
                        .changeType("deleted")
                        .severity("major")
                        .description("Image removed")
                        .baseImageHash(image.getImageHash())
                        .build();

                // Set position with proper coordinate handling
                setImageDifferencePosition(diff, pageWidth, pageHeight, pageYPositionForIndex(imageIndex));

                differences.add(diff);
                imageIndex++;
            }
        }

        // Images only in compare document (added)
        for (ImageExtractor.ImageInfo image : compareImages) {
            if (!matchedCompareImages.contains(image)) {
                // Create image difference for added image
                String diffId = UUID.randomUUID().toString();

                ImageDifference diff = ImageDifference.builder()
                        .id(diffId)
                        .type("image")
                        .changeType("added")
                        .severity("major")
                        .description("Image added")
                        .compareImageHash(image.getImageHash())
                        .build();

                // Set position with proper coordinate handling
                setImageDifferencePosition(diff, pageWidth, pageHeight, pageYPositionForIndex(imageIndex));

                differences.add(diff);
                imageIndex++;
            }
        }

        return differences;
    }

    /**
     * Compare image content to determine similarity.
     * This is a placeholder method - actual implementation would depend on available methods.
     *
     * @param baseImage Base image info
     * @param compareImage Compare image info
     * @return Similarity score between 0.0 and 1.0
     */
    private double compareImageContent(ImageExtractor.ImageInfo baseImage, ImageExtractor.ImageInfo compareImage) {
        // This is a placeholder - in a real implementation, you would use:
        // 1. Hash comparison (fast but less accurate)
        // 2. Pixel-by-pixel comparison (more accurate but slower)
        // 3. SSIM or other image similarity metrics

        // For this example, we'll return a high similarity if the hashes match
        if (baseImage.getImageHash() != null && baseImage.getImageHash().equals(compareImage.getImageHash())) {
            return 1.0;
        }

        // Use differenceCalculator.compareImages if available
        try {
            if (baseImage.getImage() != null && compareImage.getImage() != null) {
                byte[] baseImageData = ImageConverter.bufferedImageToByteArray(baseImage.getImage(), "png");
                byte[] compareImageData = ImageConverter.bufferedImageToByteArray(compareImage.getImage(), "png");

                // Get difference score (0.0-1.0 where 0.0 means no difference)
                double differenceScore = differenceCalculator.compareImages(baseImageData, compareImageData);

                // Convert to similarity score (1.0-0.0 where 1.0 means identical)
                return 1.0 - differenceScore;
            }
        } catch (Exception e) {
            log.warn("Error comparing image content: {}", e.getMessage());
        }

        // Default to a medium similarity when comparison fails
        return 0.5;
    }

    /**
     * Set default position for an image difference.
     *
     * @param diff The image difference
     * @param relativeY The relative Y position (0.0-1.0)
     */
    private void setDefaultImagePosition(ImageDifference diff, double relativeY) {
        // Using standard letter size
        double pageWidth = 612;
        double pageHeight = 792;

        // Use 60% of page width and 25% of page height for default image size
        double width = pageWidth * 0.6;
        double height = pageHeight * 0.25;

        // Position at 20% from left margin
        double x = pageWidth * 0.2;
        double y = pageHeight * relativeY;

        // Set position and bounds
        differenceCalculator.setPositionAndBounds(diff, x, y, width, height);
    }

    /**
     * Match images between base and comparison pages.
     *
     * @param baseImages Images from base page
     * @param compareImages Images from comparison page
     * @return Map of matched images
     */
    private Map<ImageExtractor.ImageInfo, ImageExtractor.ImageInfo> matchImages(
            List<ImageExtractor.ImageInfo> baseImages,
            List<ImageExtractor.ImageInfo> compareImages) {

        Map<ImageExtractor.ImageInfo, ImageExtractor.ImageInfo> matches = new HashMap<>();

        // First match by hash (exact matches)
        for (ImageExtractor.ImageInfo baseImage : baseImages) {
            for (ImageExtractor.ImageInfo compareImage : compareImages) {
                if (baseImage.getImageHash() != null &&
                        baseImage.getImageHash().equals(compareImage.getImageHash())) {
                    matches.put(baseImage, compareImage);
                    break;
                }
            }
        }

        // We'll skip the similarity-based matching since we can't access the images directly
        // This will just identify exact hash matches and assume any others are different

        return matches;
    }
}