package guraa.pdfcompare.util;

import lombok.Data;

import java.awt.*;
import java.util.UUID;

/**
 * Class representing a text element with positioning information.
 */
@Data
public class TextElement {
    private String id;
    private String text;
    private float x;
    private float y;
    private float width;
    private float height;
    private String fontName;
    private float fontSize;
    private float fontSizeInPt;
    private Color color;

    /**
     * Default constructor.
     */
    public TextElement() {
        // Default color is black
        this.color = Color.BLACK;
    }

    /**
     * Constructor with positioning information.
     *
     * @param id Unique identifier for the element
     * @param text Text content
     * @param x X-coordinate
     * @param y Y-coordinate
     * @param width Width of the element
     * @param height Height of the element
     * @param fontName Font name
     * @param fontSize Font size
     * @param fontSizeInPt Font size in points
     */
    public TextElement(String id, String text, float x, float y, float width, float height,
                       String fontName, float fontSize, float fontSizeInPt) {
        this.id = id;
        this.text = text;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.fontName = fontName;
        this.fontSize = fontSize;
        this.fontSizeInPt = fontSizeInPt;
        this.color = Color.BLACK; // Default color
    }

    /**
     * Constructor with positioning information and color.
     *
     * @param id Unique identifier for the element
     * @param text Text content
     * @param x X-coordinate
     * @param y Y-coordinate
     * @param width Width of the element
     * @param height Height of the element
     * @param fontName Font name
     * @param fontSize Font size
     * @param fontSizeInPt Font size in points
     * @param color Text color
     */
    public TextElement(String id, String text, float x, float y, float width, float height,
                       String fontName, float fontSize, float fontSizeInPt, Color color) {
        this.id = id;
        this.text = text;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.fontName = fontName;
        this.fontSize = fontSize;
        this.fontSizeInPt = fontSizeInPt;
        this.color = color != null ? color : Color.BLACK;
    }

    /**
     * Get the unique ID.
     *
     * @return The ID
     */
    public String getId() {
        return id;
    }

    /**
     * Set the unique ID.
     *
     * @param id The ID to set
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Get the text content.
     *
     * @return The text content
     */
    public String getText() {
        return text;
    }

    /**
     * Set the text content.
     *
     * @param text The text to set
     */
    public void setText(String text) {
        this.text = text;
    }

    /**
     * Get the X-coordinate.
     *
     * @return The X-coordinate
     */
    public float getX() {
        return x;
    }

    /**
     * Set the X-coordinate.
     *
     * @param x The X-coordinate to set
     */
    public void setX(float x) {
        this.x = x;
    }

    /**
     * Get the Y-coordinate.
     *
     * @return The Y-coordinate
     */
    public float getY() {
        return y;
    }

    /**
     * Set the Y-coordinate.
     *
     * @param y The Y-coordinate to set
     */
    public void setY(float y) {
        this.y = y;
    }

    /**
     * Get the width.
     *
     * @return The width
     */
    public float getWidth() {
        return width;
    }

    /**
     * Set the width.
     *
     * @param width The width to set
     */
    public void setWidth(float width) {
        this.width = width;
    }

    /**
     * Get the height.
     *
     * @return The height
     */
    public float getHeight() {
        return height;
    }

    /**
     * Set the height.
     *
     * @param height The height to set
     */
    public void setHeight(float height) {
        this.height = height;
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
     * Get the font size.
     *
     * @return The font size
     */
    public float getFontSize() {
        return fontSize;
    }

    /**
     * Set the font size.
     *
     * @param fontSize The font size to set
     */
    public void setFontSize(float fontSize) {
        this.fontSize = fontSize;
    }

    /**
     * Get the font size in points.
     *
     * @return The font size in points
     */
    public float getFontSizeInPt() {
        return fontSizeInPt;
    }

    /**
     * Set the font size in points.
     *
     * @param fontSizeInPt The font size in points to set
     */
    public void setFontSizeInPt(float fontSizeInPt) {
        this.fontSizeInPt = fontSizeInPt;
    }

    /**
     * Get the text color.
     *
     * @return The text color
     */
    public Color getColor() {
        return color != null ? color : Color.BLACK;
    }

    /**
     * Set the text color.
     *
     * @param color The text color to set
     */
    public void setColor(Color color) {
        this.color = color;
    }

    /**
     * Get the bounds of the text element as a Rectangle2D.
     *
     * @return The bounds as a Rectangle2D
     */
    public java.awt.geom.Rectangle2D getBounds() {
        return new java.awt.geom.Rectangle2D.Float(x, y, width, height);
    }

    /**
     * Check if this text element contains the given text.
     *
     * @param searchText The text to search for
     * @return true if this element contains the text, false otherwise
     */
    public boolean containsText(String searchText) {
        return text != null && searchText != null &&
                text.toLowerCase().contains(searchText.toLowerCase());
    }

    /**
     * Calculate the distance to another text element.
     *
     * @param other The other text element
     * @return The Euclidean distance between the centers of the two elements
     */
    public double distanceTo(TextElement other) {
        if (other == null) {
            return Double.MAX_VALUE;
        }

        float thisX = this.x + this.width / 2;
        float thisY = this.y + this.height / 2;
        float otherX = other.x + other.width / 2;
        float otherY = other.y + other.height / 2;

        return Math.sqrt(Math.pow(thisX - otherX, 2) + Math.pow(thisY - otherY, 2));
    }

    /**
     * Determine if this text element has the same style as another text element.
     * Style includes font name, font size, and color.
     *
     * @param other The other text element to compare styles with
     * @return true if the styles match, false otherwise
     */
    public boolean hasSameStyle(TextElement other) {
        if (other == null) {
            return false;
        }

        // Compare font names (case-insensitive)
        boolean sameFont = (fontName == null && other.fontName == null) ||
                (fontName != null && other.fontName != null &&
                        fontName.equalsIgnoreCase(other.fontName));

        // Compare font sizes (with small tolerance)
        boolean sameFontSize = Math.abs(fontSize - other.fontSize) < 0.1f;

        // Compare colors
        boolean sameColor = (color == null && other.color == null) ||
                (color != null && other.color != null &&
                        color.equals(other.color));

        return sameFont && sameFontSize && sameColor;
    }

    /**
     * Create a copy of this text element with the same properties.
     *
     * @return A new text element with the same properties
     */
    public TextElement copy() {
        TextElement copy = new TextElement();
        copy.setId(UUID.randomUUID().toString()); // Generate a new ID
        copy.setText(this.text);
        copy.setX(this.x);
        copy.setY(this.y);
        copy.setWidth(this.width);
        copy.setHeight(this.height);
        copy.setFontName(this.fontName);
        copy.setFontSize(this.fontSize);
        copy.setFontSizeInPt(this.fontSizeInPt);
        copy.setColor(this.color);
        return copy;
    }
}