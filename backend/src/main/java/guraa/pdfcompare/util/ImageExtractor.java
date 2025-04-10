package guraa.pdfcompare.util;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.pdfbox.contentstream.PDFStreamEngine;
import org.apache.pdfbox.contentstream.operator.DrawObject;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.contentstream.operator.state.*;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Component;
import org.apache.commons.io.FileUtils;

import javax.imageio.ImageIO;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Component
public class ImageExtractor extends PDFStreamEngine {

    // ThreadLocal to store images for each thread
    private final ThreadLocal<List<ImageInfo>> threadLocalImages = ThreadLocal.withInitial(CopyOnWriteArrayList::new);
    private PDPage currentPage;
    private PDResources currentResources;
    private int pageIndex;
    private int imageCounter = 0;
    private Path outputDir;

    public ImageExtractor() {
        // Register operators for handling images
        addOperator(new Concatenate());
        addOperator(new DrawObject());
        addOperator(new SetGraphicsStateParameters());
        addOperator(new Save());
        addOperator(new Restore());
        addOperator(new SetMatrix());
    }

    /**
     * Extract images from a specific page in a PDF document.
     *
     * @param document The PDF document
     * @param pageIndex The zero-based page index
     * @param outputDir Output directory to save extracted images
     * @return List of information about extracted images
     * @throws IOException If there's an error extracting images
     */
    public List<ImageInfo> extractImagesFromPage(PDDocument document, int pageIndex, Path outputDir) throws IOException {
        // Clear the thread local list
        threadLocalImages.get().clear();

        // Set current page and its resources
        currentPage = document.getPage(pageIndex);
        currentResources = currentPage.getResources();
        this.pageIndex = pageIndex;
        this.outputDir = outputDir;
        this.imageCounter = 0;

        // Create output directory if it doesn't exist
        File outDir = outputDir.toFile();
        if (!outDir.exists()) {
            outDir.mkdirs();
        }

        try {
            // Extract images using PDFBox
            processPage(currentPage);
        } catch (EOFException e) {
            log.warn("EOF error encountered while extracting images from page {}: {}", pageIndex + 1, e.getMessage());
            // Return any partially extracted images
            return new ArrayList<>(threadLocalImages.get());
        } catch (IOException e) {
            log.error("Error processing page for image extraction", e);
            throw e;
        }

        // Save extracted images to files
        List<ImageInfo> savedImages = new ArrayList<>();
        for (ImageInfo imageInfo : threadLocalImages.get()) {
            try {
                // Generate a unique name for the image file
                String imageName = String.format("page_%d_image_%s.%s",
                        pageIndex + 1, UUID.randomUUID().toString(), imageInfo.getFormat());

                File outputFile = outputDir.resolve(imageName).toFile();
                ImageIO.write(imageInfo.getImage(), imageInfo.getFormat(), outputFile);

                // Generate MD5 hash for the image
                MessageDigest md = MessageDigest.getInstance("MD5");
                md.update(imageInfo.getImageData());
                byte[] digest = md.digest();

                // Convert to hex string
                StringBuilder sb = new StringBuilder();
                for (byte b : digest) {
                    sb.append(String.format("%02x", b));
                }

                imageInfo.setImagePath(outputFile.getAbsolutePath());
                imageInfo.setImageHash(sb.toString());

                savedImages.add(imageInfo);
                imageCounter++;
            } catch (NoSuchAlgorithmException | IOException e) {
                log.error("Failed to process image", e);
            }
        }

        log.debug("Extracted {} images from page {}", imageCounter, pageIndex + 1);
        return savedImages;
    }

    @Override
    protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
        String operation = operator.getName();

        // Handle the "Do" operation which draws objects like images
        if ("Do".equals(operation)) {
            // Defensive null checks
            if (operands == null || operands.isEmpty()) {
                return;
            }

            COSName objectName = (COSName) operands.get(0);

            // Defensive checks for resources and object name
            if (currentResources == null || objectName == null) {
                log.warn("Skipping image extraction due to null resources or object name");
                return;
            }

            PDXObject xobject;
            try {
                xobject = currentResources.getXObject(objectName);
            } catch (Exception e) {
                log.warn("Could not retrieve XObject: {}", e.getMessage());
                return;
            }

            // Handle images
            if (xobject instanceof PDImageXObject) {
                PDImageXObject image = (PDImageXObject) xobject;

                // Get image position (in user space)
                Point2D position = getCurrentPosition();

                // Extract image data bytes
                byte[] imageData;
                try (InputStream stream = image.getCOSObject().createRawInputStream()) {
                    // Convert stream to byte array
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    IOUtils.copy(stream, buffer);
                    imageData = buffer.toByteArray();
                } catch (IOException e) {
                    log.error("Error reading image data", e);
                    return;
                }

                // Create image info object
                ImageInfo info = new ImageInfo();
                info.setId(UUID.randomUUID().toString());
                info.setImage(image.getImage());
                info.setImageData(imageData);
                info.setFormat(getImageFormat(image));
                info.setWidth(image.getWidth());
                info.setHeight(image.getHeight());
                info.setPosition(position);

                // Add to our thread-local list of images
                threadLocalImages.get().add(info);
            }
            // Handle forms which might contain images
            else if (xobject instanceof PDFormXObject) {
                PDFormXObject form = (PDFormXObject) xobject;
                try {
                    // Attempt to process the form, but it's complex and might not work
                    // A proper implementation would require a deeper understanding of form XObjects
                    // and their resources.
                    // For now, we just log a message.
                    log.warn("Skipping processing of form XObject. Complex forms may not be processed correctly.");
                } catch (Exception e) {
                    log.warn("Error processing form XObject: {}", e.getMessage());
                }
            }
        } else {
            // Process all non-image operators
            super.processOperator(operator, operands);
        }
    }

    private String getImageFormat(PDImageXObject image) {
        // Try to determine the image format, fallback to PNG
        try {
            String suffix = image.getSuffix();
            if (suffix != null && !suffix.isEmpty()) {
                return suffix.toLowerCase();
            }
        } catch (Exception e) {
            log.warn("Could not determine image format", e);
        }

        return "png"; // Default format
    }

    private Point2D getCurrentPosition() {
        // Get the current transformation matrix
        return new Point2D.Float(
                getGraphicsState().getCurrentTransformationMatrix().getTranslateX(),
                getGraphicsState().getCurrentTransformationMatrix().getTranslateY()
        );
    }

    private void saveImageMetadata(PDImageXObject image, String imageName, int width, int height) {
        try {
            ImageInfo imageInfo = new ImageInfo();
            imageInfo.setImageName(imageName);
            imageInfo.setPageNumber(pageIndex + 1);
            imageInfo.setWidth(width);
            imageInfo.setHeight(height);
            if (image.getSuffix() != null) {
                imageInfo.setFormat(image.getSuffix());
            }
            if (image.getColorSpace() != null) {
                imageInfo.setColorSpace(image.getColorSpace().getName());
            }
            imageInfo.setBitsPerComponent(image.getBitsPerComponent());
            imageInfo.setInterpolate(image.getInterpolate());

            // Create a JSON metadata file
            StringBuilder json = new StringBuilder();
            json.append("{\n");
            json.append("  \"imageName\": \"").append(imageInfo.getImageName()).append("\",\n");
            json.append("  \"pageNumber\": \"").append(imageInfo.getPageNumber()).append("\",\n");
            json.append("  \"width\": \"").append(imageInfo.getWidth()).append("\",\n");
            json.append("  \"height\": \"").append(imageInfo.getHeight()).append("\",\n");

            // Add image format if available
            if (imageInfo.getFormat() != null) {
                json.append("  \"format\": \"").append(imageInfo.getFormat()).append("\",\n");
            }

            // Add color space if available
            if (imageInfo.getColorSpace() != null) {
                json.append("  \"colorSpace\": \"").append(imageInfo.getColorSpace()).append("\",\n");
            }

            // Add bits per component if available
            json.append("  \"bitsPerComponent\": \"").append(imageInfo.getBitsPerComponent()).append("\",\n");

            // Add interpolate flag
            json.append("  \"interpolate\": \"").append(imageInfo.isInterpolate()).append("\"\n");

            json.append("}");

            // Save the metadata file
            String metadataFilename = imageName.replace(".png", "_metadata.json");
            File metadataFile = new File(outputDir.toFile(), metadataFilename);
            FileUtils.writeStringToFile(metadataFile, json.toString(), "UTF-8");
        } catch (Exception e) {
            log.warn("Error saving image metadata: {}", e.getMessage());
        }
    }

    /**
     * Contains information about an extracted image.
     */
    @Data
    public static class ImageInfo {
        private String id;
        private String imageName;
        private int pageNumber;
        private BufferedImage image;
        private byte[] imageData;
        private String imageHash;
        private String imagePath;
        private int width;
        private int height;
        private String format;
        private Point2D position;
        private String colorSpace;
        private int bitsPerComponent;
        private boolean interpolate;
    }
}