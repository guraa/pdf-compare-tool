package guraa.pdfcompare.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.awt.geom.Point2D;

/**
 * Class representing font information extracted from a PDF document.
 * This version includes all the required methods for the FontComparisonService class.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FontInfo {
    private String fontName;
    private String fontFamily;
    private boolean isEmbedded;
    private boolean isBold;
    private boolean isItalic;

    // Sample information for positioning
    private Point2D samplePosition;
    /**
     * -- GETTER --
     *  Get the sample width.
     *
     *
     * -- SETTER --
     *  Set the sample width.
     *
     @return The width of the sample text
      * @param sampleWidth The sample width to set
     */
    @Setter
    @Getter
    private int sampleWidth;
    /**
     * -- GETTER --
     *  Get the sample height.
     *
     * @return The height of the sample text
     */
    @Getter
    private int sampleHeight;
    private String sampleText;

    /**
     * Default constructor.
     */
    public FontInfo() {
    }

    /**
     * Constructor with basic font properties.
     *
     * @param fontName The name of the font
     * @param fontFamily The font family
     * @param isEmbedded Whether the font is embedded in the PDF
     * @param isBold Whether the font is bold
     * @param isItalic Whether the font is italic
     */
    public FontInfo(String fontName, String fontFamily, boolean isEmbedded, boolean isBold, boolean isItalic) {
        this.fontName = fontName;
        this.fontFamily = fontFamily;
        this.isEmbedded = isEmbedded;
        this.isBold = isBold;
        this.isItalic = isItalic;
    }

    /**
     * Get the font name.
     *
     * @return The font name
     */
    public String getFontName() {
        return fontName;
    }

    /**
     * Set the font name.
     *
     * @param fontName The font name to set
     */
    public void setFontName(String fontName) {
        this.fontName = fontName;
    }

    /**
     * Get the font family.
     *
     * @return The font family
     */
    public String getFontFamily() {
        return fontFamily;
    }

    /**
     * Set the font family.
     *
     * @param fontFamily The font family to set
     */
    public void setFontFamily(String fontFamily) {
        this.fontFamily = fontFamily;
    }

    /**
     * Check if the font is embedded.
     *
     * @return True if embedded, false otherwise
     */
    public boolean isEmbedded() {
        return isEmbedded;
    }

    /**
     * Set whether the font is embedded.
     *
     * @param embedded Whether the font is embedded
     */
    public void setEmbedded(boolean embedded) {
        this.isEmbedded = embedded;
    }

    /**
     * Check if the font is bold.
     *
     * @return True if bold, false otherwise
     */
    public boolean isBold() {
        return isBold;
    }

    /**
     * Set whether the font is bold.
     *
     * @param bold Whether the font is bold
     */
    public void setBold(boolean bold) {
        this.isBold = bold;
    }

    /**
     * Check if the font is italic.
     *
     * @return True if italic, false otherwise
     */
    public boolean isItalic() {
        return isItalic;
    }

    /**
     * Set whether the font is italic.
     *
     * @param italic Whether the font is italic
     */
    public void setItalic(boolean italic) {
        this.isItalic = italic;
    }

    /**
     * Get the sample position.
     *
     * @return The position of a sample text using this font
     */
    public Point2D getSamplePosition() {
        return samplePosition;
    }

    /**
     * Set the sample position.
     *
     * @param samplePosition The sample position to set
     */
    public void setSamplePosition(Point2D samplePosition) {
        this.samplePosition = samplePosition;
    }

    /**
     * Set the sample height.
     *
     * @param sampleHeight The sample height to set
     */
    public void setSampleHeight(int sampleHeight) {
        this.sampleHeight = sampleHeight;
    }

    /**
     * Get the sample text.
     *
     * @return The sample text content
     */
    public String getSampleText() {
        return sampleText;
    }

    /**
     * Set the sample text.
     *
     * @param sampleText The sample text to set
     */
    public void setSampleText(String sampleText) {
        this.sampleText = sampleText;
    }

    /**
     * Set sample information for positioning the font difference on the page.
     *
     * @param position Position of a text element using this font
     * @param width Width of the sample text
     * @param height Height of the sample text
     * @param text Sample text content
     */
    public void setSampleInfo(Point2D position, int width, int height, String text) {
        this.samplePosition = position;
        this.sampleWidth = width;
        this.sampleHeight = height;
        this.sampleText = text;
    }
}