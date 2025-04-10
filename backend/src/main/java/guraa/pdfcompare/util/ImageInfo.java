package guraa.pdfcompare.util;

import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;

/**
 * Class representing image information extracted from a PDF document.
 */
public class ImageInfo {
    private String imageHash;
    private byte[] image;
    private BufferedImage bufferedImage;
    private String imagePath;
    private int width;
    private int height;
    private String format;
    private Point2D position;

    public ImageInfo() {
    }

    public ImageInfo(String imageHash, byte[] image) {
        this.imageHash = imageHash;
        this.image = image;
    }

    public String getImageHash() {
        return imageHash;
    }

    public void setImageHash(String imageHash) {
        this.imageHash = imageHash;
    }

    public byte[] getImage() {
        return image;
    }

    public void setImage(byte[] image) {
        this.image = image;
    }

    public BufferedImage getBufferedImage() {
        return bufferedImage;
    }

    public void setBufferedImage(BufferedImage bufferedImage) {
        this.bufferedImage = bufferedImage;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public Point2D getPosition() {
        return position;
    }

    public void setPosition(Point2D position) {
        this.position = position;
    }
}