package guraa.pdfcompare.model.difference;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Represents a text difference between documents.
 * This class extends the base Difference class with text-specific properties.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TextDifference extends Difference {

    private String baseText;
    private String compareText;
    private String text;
    private int startIndex;
    private int endIndex;
    private int length;
    private String context;

    /**
     * Get the text from the base document.
     *
     * @return The base text
     */
    public String getBaseText() {
        return baseText;
    }

    /**
     * Set the text from the base document.
     *
     * @param baseText The base text
     */
    public void setBaseText(String baseText) {
        this.baseText = baseText;
    }

    /**
     * Get the text from the compare document.
     *
     * @return The compare text
     */
    public String getCompareText() {
        return compareText;
    }

    /**
     * Set the text from the compare document.
     *
     * @param compareText The compare text
     */
    public void setCompareText(String compareText) {
        this.compareText = compareText;
    }

    /**
     * Get the display text.
     * This is a convenience method that returns the appropriate text
     * based on the change type.
     *
     * @return The display text
     */
    public String getText() {
        // If text is already set, return it
        if (text != null) {
            return text;
        }

        // Otherwise derive it from base or compare text
        if ("deleted".equals(getChangeType())) {
            return baseText;
        } else if ("added".equals(getChangeType())) {
            return compareText;
        } else {
            // For modified, prefer base text if available
            return baseText != null ? baseText : compareText;
        }
    }

    /**
     * Set the display text.
     *
     * @param text The display text
     */
    public void setText(String text) {
        this.text = text;
    }

    /**
     * Get the start index.
     *
     * @return The start index
     */
    public int getStartIndex() {
        return startIndex;
    }

    /**
     * Set the start index.
     *
     * @param startIndex The start index
     */
    public void setStartIndex(int startIndex) {
        this.startIndex = startIndex;
    }

    /**
     * Get the end index.
     *
     * @return The end index
     */
    public int getEndIndex() {
        return endIndex;
    }

    /**
     * Set the end index.
     *
     * @param endIndex The end index
     */
    public void setEndIndex(int endIndex) {
        this.endIndex = endIndex;
    }

    /**
     * Get the length.
     *
     * @return The length
     */
    public int getLength() {
        return length;
    }

    /**
     * Set the length.
     *
     * @param length The length
     */
    public void setLength(int length) {
        this.length = length;
    }

    /**
     * Get the context.
     *
     * @return The context
     */
    public String getContext() {
        return context;
    }

    /**
     * Set the context.
     *
     * @param context The context
     */
    public void setContext(String context) {
        this.context = context;
    }
}