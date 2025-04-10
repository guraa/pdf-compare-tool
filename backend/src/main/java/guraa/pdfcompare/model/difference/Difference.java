package guraa.pdfcompare.model.difference;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Abstract base class for all types of differences between documents.
 * Using JsonTypeInfo and JsonSubTypes to handle polymorphic deserialization.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = TextDifference.class, name = "text"),
        @JsonSubTypes.Type(value = ImageDifference.class, name = "image"),
        @JsonSubTypes.Type(value = FontDifference.class, name = "font"),
        @JsonSubTypes.Type(value = StyleDifference.class, name = "style"),
        @JsonSubTypes.Type(value = MetadataDifference.class, name = "metadata")
})
public abstract class Difference {

    private String id;
    private String type; // text, image, font, style, metadata
    private String changeType; // added, deleted, modified
    private String severity; // major, minor, cosmetic
    private String description;

    // For visualizing the difference on the page
    private double x;
    private double y;
    private double width;
    private double height;

    // Alternative representation of position as bounds
    private double left;
    private double top;
    private double right;
    private double bottom;

    /**
     * Get the ID of the difference.
     *
     * @return The difference ID
     */
    public String getId() {
        return id;
    }

    /**
     * Set the ID of the difference.
     *
     * @param id The difference ID
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Get the type of difference.
     *
     * @return The difference type
     */
    public String getType() {
        return type;
    }

    /**
     * Set the type of difference.
     *
     * @param type The difference type
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Get the change type.
     *
     * @return The change type
     */
    public String getChangeType() {
        return changeType;
    }

    /**
     * Set the change type.
     *
     * @param changeType The change type
     */
    public void setChangeType(String changeType) {
        this.changeType = changeType;
    }

    /**
     * Get the severity.
     *
     * @return The severity
     */
    public String getSeverity() {
        return severity;
    }

    /**
     * Set the severity.
     *
     * @param severity The severity
     */
    public void setSeverity(String severity) {
        this.severity = severity;
    }

    /**
     * Get the description.
     *
     * @return The description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Set the description.
     *
     * @param description The description
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Get the X-coordinate.
     *
     * @return The X-coordinate
     */
    public double getX() {
        return x;
    }

    /**
     * Set the X-coordinate.
     *
     * @param x The X-coordinate
     */
    public void setX(double x) {
        this.x = x;
    }

    /**
     * Get the Y-coordinate.
     *
     * @return The Y-coordinate
     */
    public double getY() {
        return y;
    }

    /**
     * Set the Y-coordinate.
     *
     * @param y The Y-coordinate
     */
    public void setY(double y) {
        this.y = y;
    }

    /**
     * Get the width.
     *
     * @return The width
     */
    public double getWidth() {
        return width;
    }

    /**
     * Set the width.
     *
     * @param width The width
     */
    public void setWidth(double width) {
        this.width = width;
    }

    /**
     * Get the height.
     *
     * @return The height
     */
    public double getHeight() {
        return height;
    }

    /**
     * Set the height.
     *
     * @param height The height
     */
    public void setHeight(double height) {
        this.height = height;
    }

    /**
     * Get the left coordinate.
     *
     * @return The left coordinate
     */
    public double getLeft() {
        return left;
    }

    /**
     * Set the left coordinate.
     *
     * @param left The left coordinate
     */
    public void setLeft(double left) {
        this.left = left;
    }

    /**
     * Get the top coordinate.
     *
     * @return The top coordinate
     */
    public double getTop() {
        return top;
    }

    /**
     * Set the top coordinate.
     *
     * @param top The top coordinate
     */
    public void setTop(double top) {
        this.top = top;
    }

    /**
     * Get the right coordinate.
     *
     * @return The right coordinate
     */
    public double getRight() {
        return right;
    }

    /**
     * Set the right coordinate.
     *
     * @param right The right coordinate
     */
    public void setRight(double right) {
        this.right = right;
    }

    /**
     * Get the bottom coordinate.
     *
     * @return The bottom coordinate
     */
    public double getBottom() {
        return bottom;
    }

    /**
     * Set the bottom coordinate.
     *
     * @param bottom The bottom coordinate
     */
    public void setBottom(double bottom) {
        this.bottom = bottom;
    }
}