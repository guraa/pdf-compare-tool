package guraa.pdfcompare.util;

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

import javax.imageio.ImageIO;
import java.awt.geom.Point2D;
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

    private final ThreadLocal<List<ImageInfo>> threadLocalImages = ThreadLocal.withInitial(CopyOnWriteArrayList::new);
    private PDPage currentPage;
    private PDResources currentResources;

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
                        pageIndex + 1, UUID.randomUUID(), imageInfo.getFormat());

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
            } catch (NoSuchAlgorithmException | IOException e) {
                log.error("Failed to process image", e);
            }
        }

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
                showForm(form);
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



    /**
     * Contains information about an extracted image.
     */
    public static class ImageInfo {
        private String id;
        private java.awt.image.BufferedImage image;
        private byte[] imageData;
        private String format;
        private int width;
        private int height;
        private Point2D position;
        private String imagePath;
        private String imageHash;

        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public java.awt.image.BufferedImage getImage() { return image; }
        public void setImage(java.awt.image.BufferedImage image) { this.image = image; }

        public byte[] getImageData() { return imageData; }
        public void setImageData(byte[] imageData) { this.imageData = imageData; }

        public String getFormat() { return format; }
        public void setFormat(String format) { this.format = format; }

        public int getWidth() { return width; }
        public void setWidth(int width) { this.width = width; }

        public int getHeight() { return height; }
        public void setHeight(int height) { this.height = height; }

        public Point2D getPosition() { return position; }
        public void setPosition(Point2D position) { this.position = position; }

        public String getImagePath() { return imagePath; }
        public void setImagePath(String imagePath) { this.imagePath = imagePath; }

        public String getImageHash() { return imageHash; }
        public void setImageHash(String imageHash) { this.imageHash = imageHash; }
    }
}