package guraa.pdfcompare.model.difference;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * Represents an image difference between documents.
 * This class extends the base Difference class with image-specific properties.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ImageDifference extends Difference {

    /**
     * -- GETTER --
     * Get the hash of the image in the base document.
     *
     * @return The base image hash
     */
    @Getter
    private String baseImageHash;
    private String compareImageHash;
    private String baseImagePath;
    private String compareImagePath;
    private Integer baseWidth;
    private Integer baseHeight;
    private Integer compareWidth;
    private Integer compareHeight;
    private Double similarityScore;

    /**
     * Set the hash of the image in the base document.
     *
     * @param baseImageHash The base image hash
     */
    public void setBaseImageHash(String baseImageHash) {
        this.baseImageHash = baseImageHash;
    }

    /**
     * Get the hash of the image in the compare document.
     *
     * @return The compare image hash
     */
    public String getCompareImageHash() {
        return compareImageHash;
    }

    /**
     * Set the hash of the image in the compare document.
     *
     * @param compareImageHash The compare image hash
     */
    public void setCompareImageHash(String compareImageHash) {
        this.compareImageHash = compareImageHash;
    }

    /**
     * Get the path to the image in the base document.
     *
     * @return The base image path
     */
    public String getBaseImagePath() {
        return baseImagePath;
    }

    /**
     * Set the path to the image in the base document.
     *
     * @param baseImagePath The base image path
     */
    public void setBaseImagePath(String baseImagePath) {
        this.baseImagePath = baseImagePath;
    }

    /**
     * Get the path to the image in the compare document.
     *
     * @return The compare image path
     */
    public String getCompareImagePath() {
        return compareImagePath;
    }

    /**
     * Set the path to the image in the compare document.
     *
     * @param compareImagePath The compare image path
     */
    public void setCompareImagePath(String compareImagePath) {
        this.compareImagePath = compareImagePath;
    }
}
