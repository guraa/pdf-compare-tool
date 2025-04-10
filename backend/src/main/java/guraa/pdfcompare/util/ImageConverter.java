package guraa.pdfcompare.util;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Utility class for converting between different image formats.
 */
@Slf4j
public class ImageConverter {

    /**
     * Convert BufferedImage to byte array.
     *
     * @param image The BufferedImage to convert
     * @param format Image format (e.g., "png", "jpg")
     * @return Byte array representation of the image
     */
    public static byte[] bufferedImageToByteArray(BufferedImage image, String format) {
        if (image == null) {
            return null;
        }

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, format != null ? format : "png", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("Error converting BufferedImage to byte array: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Convert byte array to BufferedImage.
     *
     * @param bytes The byte array to convert
     * @return BufferedImage created from the byte array
     */
    public static BufferedImage byteArrayToBufferedImage(byte[] bytes) {
        if (bytes == null) {
            return null;
        }

        try {
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            log.error("Error converting byte array to BufferedImage: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Fix the incompatible type error in CompareImages method by ensuring both inputs are byte arrays.
     *
     * @param image1 First image (can be BufferedImage or byte array)
     * @param image2 Second image (can be BufferedImage or byte array)
     * @return Array of two byte arrays
     */
    public static byte[][] ensureByteArrays(Object image1, Object image2) {
        byte[] bytes1 = null;
        byte[] bytes2 = null;

        // Convert first image if needed
        if (image1 instanceof BufferedImage) {
            bytes1 = bufferedImageToByteArray((BufferedImage) image1, "png");
        } else if (image1 instanceof byte[]) {
            bytes1 = (byte[]) image1;
        }

        // Convert second image if needed
        if (image2 instanceof BufferedImage) {
            bytes2 = bufferedImageToByteArray((BufferedImage) image2, "png");
        } else if (image2 instanceof byte[]) {
            bytes2 = (byte[]) image2;
        }

        return new byte[][] { bytes1, bytes2 };
    }
}