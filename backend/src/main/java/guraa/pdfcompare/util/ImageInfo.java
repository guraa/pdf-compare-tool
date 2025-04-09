package guraa.pdfcompare.util;

import lombok.Data;

/**
 * Class to store image information.
 */
@Data
public class ImageInfo {
    private String imageName;
    private int pageNumber;
    private int width;
    private int height;
    private String format;
    private String colorSpace;
    private int bitsPerComponent;
    private boolean interpolate;
}
