package guraa.pdfcompare.service;

import guraa.pdfcompare.model.difference.Difference;
import guraa.pdfcompare.model.difference.ImageDifference;
import guraa.pdfcompare.util.CoordinateTransformer;
import guraa.pdfcompare.util.DifferenceCalculator;
import guraa.pdfcompare.util.ImageConverter;
import guraa.pdfcompare.util.ImageExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.springframework.stereotype.Service;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/**
 * Service for comparing images between PDF documents.
 * Updated to use consistent coordinate transformations across all difference types.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageComparisonService {

    private final ImageExtractor imageExtractor;
    private final DifferenceCalculator differenceCalculator;
    private final CoordinateTransformer coordinateTransformer;

    /**
     * Compare images on a page between two PDF documents.
     *
     * @param basePdf    Base PDF document
     * @param comparePdf Compare PDF document
     * @param pageIndex  Page index to compare
     * @param outputDir  Directory for extracted images
     * @return List of image differences
     * @throws IOException If there's an error processing the images
     */
    public List<Difference> compareImagesOnPage(
            PDDocument basePdf,
            PDDocument comparePdf,
            int pageIndex,
            Path outputDir) throws IOException {

        PDPage basePage = basePdf.getPage(pageIndex);
        float pageHeight = coordinateTransformer.getPageHeight(basePage);

        // Extract images from both pages
        List<ImageExtractor.ImageInfo> baseImages = imageExtractor.extractImagesFromPage(
                basePdf, pageIndex, outputDir.resolve("base_images"));
        List<ImageExtractor.ImageInfo> compareImages = imageExtractor.extractImagesFromPage(
                comparePdf, pageIndex, outputDir.resolve("compare_images"));

        return compareImages(baseImages, compareImages, basePage.getMediaBox().getWidth(), pageHeight);
    }

    /**
     * Compare images on a page between two PDF documents in document pair mode.
     *
     * @param basePdf          Base PDF document
     * @param comparePdf       Compare PDF document
     * @param basePageIndex    Base page index
     * @param comparePageIndex Compare page index
     * @param outputDir        Directory for extracted images
     * @return List of image differences
     * @throws IOException If there's an error processing the images
     */
    public List<Difference> compareImagesOnPage(
            PDDocument basePdf,
            PDDocument comparePdf,
            int basePageIndex,
            int comparePageIndex,
            Path outputDir) throws IOException {

        PDPage basePage = basePdf.getPage(basePageIndex);
        float pageHeight = coordinateTransformer.getPageHeight(basePage);

        // Extract images from both pages
        List<ImageExtractor.ImageInfo> baseImages = imageExtractor.extractImagesFromPage(
                basePdf, basePageIndex, outputDir.resolve("base_images"));
        List<ImageExtractor.ImageInfo> compareImages = imageExtractor.extractImagesFromPage(
                comparePdf, comparePageIndex, outputDir.resolve("compare_images"));

        return compareImages(baseImages, compareImages, basePage.getMediaBox().getWidth(), pageHeight);
    }

    /**
     * Compare images between two pages with proper coordinate handling.
     *
     * @param baseImages    Images from base page
     * @param compareImages Images from comparison page
     * @param pageWidth     Width of the page
     * @param pageHeight    Height of the page
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

            // If there's a significant difference in content (less than 95% similar)
            if (contentSimilarity < 0.95) {
                String diffId = UUID.randomUUID().toString();
                String description = "Image content differs";

                // Create image difference
                ImageDifference diff = ImageDifference.builder()
                        .id(diffId)
                        .type("image")
                        .changeType("modified")
                        .severity(determineSeverity(contentSimilarity))
                        .description(description)
                        .baseImageHash(baseImage.getImageHash())
                        .compareImageHash(compareImage.getImageHash())
                        .baseWidth(baseImage.getImage() != null ? baseImage.getImage().getWidth() : null)
                        .baseHeight(baseImage.getImage() != null ? baseImage.getImage().getHeight() : null)
                        .compareWidth(compareImage.getImage() != null ? compareImage.getImage().getWidth() : null)
                        .compareHeight(compareImage.getImage() != null ? compareImage.getImage().getHeight() : null)
                        .similarityScore(contentSimilarity)
                        .build();

                // Set position with proper coordinate transformation
                setImageDifferencePosition(diff, baseImage, pageWidth, pageHeight);

                differences.add(diff);
                imageIndex++;
            }
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
                        .baseWidth(image.getImage() != null ? image.getImage().getWidth() : null)
                        .baseHeight(image.getImage() != null ? image.getImage().getHeight() : null)
                        .build();

                // Set position with proper coordinate transformation
                setImageDifferencePosition(diff, image, pageWidth, pageHeight);

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
                        .compareWidth(image.getImage() != null ? image.getImage().getWidth() : null)
                        .compareHeight(image.getImage() != null ? image.getImage().getHeight() : null)
                        .build();

                // Set position with proper coordinate transformation
                setImageDifferencePosition(diff, image, pageWidth, pageHeight);

                differences.add(diff);
                imageIndex++;
            }
        }

        return differences;
    }

    /**
     * Determine severity based on image similarity score.
     *
     * @param similarityScore Similarity score between 0.0 and 1.0
     * @return Severity classification string
     */
    private String determineSeverity(double similarityScore) {
        if (similarityScore < 0.5) {
            return "critical";
        } else if (similarityScore < 0.8) {
            return "major";
        } else {
            return "minor";
        }
    }

    /**
     * Set position for an image difference with proper coordinate transformation.
     * Uses the actual image position when available, or estimates a position when not.
     *
     * @param diff       The image difference
     * @param imageInfo  The image information
     * @param pageWidth  Width of the page
     * @param pageHeight Height of the page
     */
    private void setImageDifferencePosition(ImageDifference diff, ImageExtractor.ImageInfo imageInfo,
                                            double pageWidth, double pageHeight) {
        // Try to use actual image position if available
        Point2D position = imageInfo.getPosition();

        if (position != null) {
            // Get image dimensions
            int width = imageInfo.getWidth();
            int height = imageInfo.getHeight();

            // If dimensions are missing, use reasonable defaults
            if (width <= 0 || height <= 0) {
                width = Math.max(50, (int)(pageWidth * 0.3));
                height = Math.max(50, (int)(pageHeight * 0.2));
            }

            // Transform from PDF coordinates to display coordinates
            CoordinateTransformer.Rectangle displayRect = coordinateTransformer.pdfRectToDisplay(
                    position.getX(), position.getY(), width, height, pageHeight);

            // Set the position and bounds
            differenceCalculator.setPositionAndBounds(
                    diff,
                    displayRect.getX(),
                    displayRect.getY(),
                    displayRect.getWidth(),
                    displayRect.getHeight());

            log.debug("Image difference positioned at actual coordinates: {}x{} @ ({},{})",
                    displayRect.getWidth(), displayRect.getHeight(),
                    displayRect.getX(), displayRect.getY());
        } else {
            // If no position information, use a default positioning strategy
            useDefaultImagePosition(diff, pageWidth, pageHeight);
        }
    }

    /**
     * Use default positioning when actual image position is not available.
     *
     * @param diff       The image difference
     * @param pageWidth  Width of the page
     * @param pageHeight Height of the page
     */
    private void useDefaultImagePosition(ImageDifference diff, double pageWidth, double pageHeight) {
        // Calculate a pseudo-random but deterministic position based on the image hash
        // This keeps the same image at the same position across different runs
        double yRatio;
        String imageHash = diff.getBaseImageHash() != null ? diff.getBaseImageHash() : diff.getCompareImageHash();

        if (imageHash != null && !imageHash.isEmpty()) {
            // Use the first 4 characters of the hash to generate a position between 0.2 and 0.8
            int hashCode = imageHash.substring(0, Math.min(4, imageHash.length())).hashCode();
            yRatio = 0.2 + (Math.abs(hashCode) % 60) / 100.0; // Gives a value between 0.2 and 0.8
        } else {
            // Fallback if no hash
            yRatio = 0.4;
        }

        // Use 60% of page width and 25% of page height for default image size
        double width = pageWidth * 0.6;
        double height = pageHeight * 0.25;

        // Position at 20% from left margin and calculated Y position
        double x = pageWidth * 0.2;
        double y = pageHeight * yRatio;

        // Ensure the image doesn't go off the page
        if (y + height > pageHeight) {
            y = pageHeight - height - 10; // Keep 10 units from bottom edge
        }

        // Set position and bounds directly (already in display coordinates)
        differenceCalculator.setPositionAndBounds(diff, x, y, width, height);

        log.debug("Image difference positioned using default strategy: {}x{} @ ({},{})",
                width, height, x, y);
    }

    /**
     * Compare image content to determine similarity.
     *
     * @param baseImage    Base image info
     * @param compareImage Compare image info
     * @return Similarity score between 0.0 and 1.0
     */
    private double compareImageContent(ImageExtractor.ImageInfo baseImage, ImageExtractor.ImageInfo compareImage) {
        // If hashes match exactly, images are identical
        if (baseImage.getImageHash() != null && baseImage.getImageHash().equals(compareImage.getImageHash())) {
            return 1.0;
        }

        // Use visual comparison when possible
        try {
            if (baseImage.getImage() != null && compareImage.getImage() != null) {
                byte[] baseImageData = ImageConverter.bufferedImageToByteArray(baseImage.getImage(), "png");
                byte[] compareImageData = ImageConverter.bufferedImageToByteArray(compareImage.getImage(), "png");

                if (baseImageData != null && compareImageData != null) {
                    // Get difference score and convert to similarity score
                    double differenceScore = differenceCalculator.compareImages(baseImageData, compareImageData);
                    return 1.0 - differenceScore;
                }
            }
        } catch (Exception e) {
            log.warn("Error comparing image content: {}", e.getMessage());
        }

        // Default to a medium similarity when comparison fails
        return 0.5;
    }

    /**
     * Match images between base and comparison pages.
     *
     * @param baseImages    Images from base page
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

        // For unmatched images, try to match based on position and size
        Set<ImageExtractor.ImageInfo> matchedBaseImages = new HashSet<>(matches.keySet());
        Set<ImageExtractor.ImageInfo> matchedCompareImages = new HashSet<>(matches.values());

        for (ImageExtractor.ImageInfo baseImage : baseImages) {
            if (matchedBaseImages.contains(baseImage)) continue;

            Point2D basePos = baseImage.getPosition();
            if (basePos == null) continue;

            for (ImageExtractor.ImageInfo compareImage : compareImages) {
                if (matchedCompareImages.contains(compareImage)) continue;

                Point2D comparePos = compareImage.getPosition();
                if (comparePos == null) continue;

                // Check if the images are in similar positions (within 10% of page size)
                double xDiff = Math.abs(basePos.getX() - comparePos.getX());
                double yDiff = Math.abs(basePos.getY() - comparePos.getY());

                if (xDiff < 50 && yDiff < 50) {
                    // Also check if sizes are similar
                    double sizeDiff = Math.abs(baseImage.getWidth() * baseImage.getHeight() -
                            compareImage.getWidth() * compareImage.getHeight());
                    double maxSize = Math.max(baseImage.getWidth() * baseImage.getHeight(),
                            compareImage.getWidth() * compareImage.getHeight());

                    if (maxSize > 0 && sizeDiff / maxSize < 0.2) { // Size difference less than 20%
                        matches.put(baseImage, compareImage);
                        matchedBaseImages.add(baseImage);
                        matchedCompareImages.add(compareImage);
                        break;
                    }
                }
            }
        }

        return matches;
    }
}