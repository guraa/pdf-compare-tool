
package guraa.pdfcompare.model.difference;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class ImageDifference extends Difference {
    private String baseImageHash;
    private String compareImageHash;
    private String baseFormat;
    private String compareFormat;
    private int baseWidth;
    private int baseHeight;
    private int compareWidth;
    private int compareHeight;
    private String baseThumbnailPath;
    private String compareThumbnailPath;
    private double visualDifferenceScore;
}