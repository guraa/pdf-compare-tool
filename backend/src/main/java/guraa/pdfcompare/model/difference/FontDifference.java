package guraa.pdfcompare.model.difference;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Represents a font difference between documents.
 * This class extends the base Difference class with font-specific properties.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FontDifference extends Difference {

    private String baseFontName;
    private String compareFontName;
    private String baseFontFamily;
    private String compareFontFamily;
    private Boolean baseEmbedded;
    private Boolean compareEmbedded;
    private Boolean baseBold;
    private Boolean compareBold;
    private Boolean baseItalic;
    private Boolean compareItalic;
    private String baseEncoding;
    private String compareEncoding;
    private String sampleText;

    /**
     * Get the font name in the base document.
     *
     * @return The base font name
     */
    public String getBaseFontName() {
        return baseFontName;
    }

    /**
     * Set the font name in the base document.
     *
     * @param baseFontName The base font name
     */
    public void setBaseFontName(String baseFontName) {
        this.baseFontName = baseFontName;
    }

    /**
     * Get the font name in the compare document.
     *
     * @return The compare font name
     */
    public String getCompareFontName() {
        return compareFontName;
    }

    /**
     * Set the font name in the compare document.
     *
     * @param compareFontName The compare font name
     */
    public void setCompareFontName(String compareFontName) {
        this.compareFontName = compareFontName;
    }

    /**
     * Get the font family in the base document.
     *
     * @return The base font family
     */
    public String getBaseFontFamily() {
        return baseFontFamily;
    }

    /**
     * Set the font family in the base document.
     *
     * @param baseFontFamily The base font family
     */
    public void setBaseFontFamily(String baseFontFamily) {
        this.baseFontFamily = baseFontFamily;
    }

    /**
     * Get the font family in the compare document.
     *
     * @return The compare font family
     */
    public String getCompareFontFamily() {
        return compareFontFamily;
    }

    /**
     * Set the font family in the compare document.
     *
     * @param compareFontFamily The compare font family
     */
    public void setCompareFontFamily(String compareFontFamily) {
        this.compareFontFamily = compareFontFamily;
    }

    /**
     * Check if the font is embedded in the base document.
     *
     * @return true if embedded, false otherwise
     */
    public Boolean getBaseEmbedded() {
        return baseEmbedded;
    }

    /**
     * Set whether the font is embedded in the base document.
     *
     * @param baseEmbedded Whether the font is embedded
     */
    public void setBaseEmbedded(Boolean baseEmbedded) {
        this.baseEmbedded = baseEmbedded;
    }

    /**
     * Check if the font is embedded in the compare document.
     *
     * @return true if embedded, false otherwise
     */
    public Boolean getCompareEmbedded() {
        return compareEmbedded;
    }

    /**
     * Set whether the font is embedded in the compare document.
     *
     * @param compareEmbedded Whether the font is embedded
     */
    public void setCompareEmbedded(Boolean compareEmbedded) {
        this.compareEmbedded = compareEmbedded;
    }

    /**
     * Check if the font is bold in the base document.
     *
     * @return true if bold, false otherwise
     */
    public Boolean getBaseBold() {
        return baseBold;
    }

    /**
     * Set whether the font is bold in the base document.
     *
     * @param baseBold Whether the font is bold
     */
    public void setBaseBold(Boolean baseBold) {
        this.baseBold = baseBold;
    }

    /**
     * Check if the font is bold in the compare document.
     *
     * @return true if bold, false otherwise
     */
    public Boolean getCompareBold() {
        return compareBold;
    }

    /**
     * Set whether the font is bold in the compare document.
     *
     * @param compareBold Whether the font is bold
     */
    public void setCompareBold(Boolean compareBold) {
        this.compareBold = compareBold;
    }

    /**
     * Check if the font is italic in the base document.
     *
     * @return true if italic, false otherwise
     */
    public Boolean getBaseItalic() {
        return baseItalic;
    }

    /**
     * Set whether the font is italic in the base document.
     *
     * @param baseItalic Whether the font is italic
     */
    public void setBaseItalic(Boolean baseItalic) {
        this.baseItalic = baseItalic;
    }

    /**
     * Check if the font is italic in the compare document.
     *
     * @return true if italic, false otherwise
     */
    public Boolean getCompareItalic() {
        return compareItalic;
    }

    /**
     * Set whether the font is italic in the compare document.
     *
     * @param compareItalic Whether the font is italic
     */
    public void setCompareItalic(Boolean compareItalic) {
        this.compareItalic = compareItalic;
    }

    /**
     * Get the encoding of the font in the base document.
     *
     * @return The base encoding
     */
    public String getBaseEncoding() {
        return baseEncoding;
    }

    /**
     * Set the encoding of the font in the base document.
     *
     * @param baseEncoding The base encoding
     */
    public void setBaseEncoding(String baseEncoding) {
        this.baseEncoding = baseEncoding;
    }

    /**
     * Get the encoding of the font in the compare document.
     *
     * @return The compare encoding
     */
    public String getCompareEncoding() {
        return compareEncoding;
    }

    /**
     * Set the encoding of the font in the compare document.
     *
     * @param compareEncoding The compare encoding
     */
    public void setCompareEncoding(String compareEncoding) {
        this.compareEncoding = compareEncoding;
    }

    /**
     * Get the sample text for this font difference.
     *
     * @return The sample text
     */
    public String getSampleText() {
        return sampleText;
    }

    /**
     * Set the sample text for this font difference.
     *
     * @param sampleText The sample text
     */
    public void setSampleText(String sampleText) {
        this.sampleText = sampleText;
    }
}