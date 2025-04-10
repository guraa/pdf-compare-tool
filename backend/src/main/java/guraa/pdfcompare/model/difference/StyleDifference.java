package guraa.pdfcompare.model.difference;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Represents a style difference between documents.
 * This class extends the base Difference class with style-specific properties.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StyleDifference extends Difference {

    private String text;
    private String baseStyle;
    private String compareStyle;

    /**
     * Get the text content associated with this style difference.
     *
     * @return The text content
     */
    public String getText() {
        return text;
    }

    /**
     * Set the text content associated with this style difference.
     *
     * @param text The text content
     */
    public void setText(String text) {
        this.text = text;
    }

    /**
     * Get the style from the base document.
     * This is a JSON string representation of style properties.
     *
     * @return The base style
     */
    public String getBaseStyle() {
        return baseStyle;
    }

    /**
     * Set the style from the base document.
     * This should be a JSON string representation of style properties.
     *
     * @param baseStyle The base style
     */
    public void setBaseStyle(String baseStyle) {
        this.baseStyle = baseStyle;
    }

    /**
     * Get the style from the compare document.
     * This is a JSON string representation of style properties.
     *
     * @return The compare style
     */
    public String getCompareStyle() {
        return compareStyle;
    }

    /**
     * Set the style from the compare document.
     * This should be a JSON string representation of style properties.
     *
     * @param compareStyle The compare style
     */
    public void setCompareStyle(String compareStyle) {
        this.compareStyle = compareStyle;
    }
}