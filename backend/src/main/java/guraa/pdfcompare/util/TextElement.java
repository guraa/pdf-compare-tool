package guraa.pdfcompare.util;

import lombok.Data;

import java.awt.geom.Rectangle2D;

/**
 * Text element with position and style information.
 * Represents a piece of text from a PDF with its formatting and positioning.
 */
@Data
public class TextElement {
    private String id;
    private String text;
    private float x;
    private float y;
    private float width;
    private float height;
    private float fontSize;
    private String fontName;
    private String color;
    private int pageIndex;
    private int rotation;
    private boolean isBold;
    private boolean isItalic;
    private boolean isEmbedded;
    private String lineId;
    private String wordId;
    private String characterSequence;

    /**
     * Get the bounding box as Rectangle2D.
     *
     * @return The bounding box
     */
    public Rectangle2D.Float getBoundingBox() {
        return new Rectangle2D.Float(x, y, width, height);
    }

    /**
     * Set the bold property.
     *
     * @param bold The bold property value
     */
    public void setIsBold(boolean bold) {
        this.isBold = bold;
    }

    /**
     * Set the italic property.
     *
     * @param italic The italic property value
     */
    public void setIsItalic(boolean italic) {
        this.isItalic = italic;
    }

    /**
     * Set the embedded property.
     *
     * @param embedded The embedded property value
     */
    public void setIsEmbedded(boolean embedded) {
        this.isEmbedded = embedded;
    }
}