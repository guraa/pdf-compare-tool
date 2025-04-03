package guraa.pdfcompare.model.difference;


import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ImageDifference extends Difference {

    private String imageName;
    private String baseImageHash;
    private String compareImageHash;

    // Image properties
    private String baseFormat;
    private String compareFormat;

    private Integer baseWidth;
    private Integer baseHeight;
    private Integer compareWidth;
    private Integer compareHeight;

    private Integer baseBitsPerPixel;
    private Integer compareBitsPerPixel;

    // Thumbnails paths
    private String baseThumbnailPath;
    private String compareThumbnailPath;

    // Image quality differences (if any)
    private Double compressionRatioDifference;
    private Double visualDifferenceScore; // 0-1, where 0 means identical

    // For tracking the source element IDs in the PDFs
    private String baseElementId;
    private String compareElementId;
}