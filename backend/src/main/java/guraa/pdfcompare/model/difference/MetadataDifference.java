package guraa.pdfcompare.model.difference;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Represents a metadata difference between documents.
 * This class extends the base Difference class with metadata-specific properties.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MetadataDifference extends Difference {

    private String key;
    private String baseValue;
    private String compareValue;
    private boolean onlyInBase;
    private boolean onlyInCompare;
    private boolean valueDifferent;

    /**
     * Get the metadata key.
     *
     * @return The metadata key
     */
    public String getKey() {
        return key;
    }

    /**
     * Set the metadata key.
     *
     * @param key The metadata key
     */
    public void setKey(String key) {
        this.key = key;
    }

    /**
     * Get the value in the base document.
     *
     * @return The base value
     */
    public String getBaseValue() {
        return baseValue;
    }

    /**
     * Set the value in the base document.
     *
     * @param baseValue The base value
     */
    public void setBaseValue(String baseValue) {
        this.baseValue = baseValue;
    }

    /**
     * Get the value in the compare document.
     *
     * @return The compare value
     */
    public String getCompareValue() {
        return compareValue;
    }

    /**
     * Set the value in the compare document.
     *
     * @param compareValue The compare value
     */
    public void setCompareValue(String compareValue) {
        this.compareValue = compareValue;
    }

    /**
     * Check if the metadata key exists only in the base document.
     *
     * @return true if only in base, false otherwise
     */
    public boolean isOnlyInBase() {
        return onlyInBase;
    }

    /**
     * Set whether the metadata key exists only in the base document.
     *
     * @param onlyInBase Whether only in base
     */
    public void setOnlyInBase(boolean onlyInBase) {
        this.onlyInBase = onlyInBase;
    }

    /**
     * Check if the metadata key exists only in the compare document.
     *
     * @return true if only in compare, false otherwise
     */
    public boolean isOnlyInCompare() {
        return onlyInCompare;
    }

    /**
     * Set whether the metadata key exists only in the compare document.
     *
     * @param onlyInCompare Whether only in compare
     */
    public void setOnlyInCompare(boolean onlyInCompare) {
        this.onlyInCompare = onlyInCompare;
    }

    /**
     * Check if the metadata value is different between documents.
     *
     * @return true if value different, false otherwise
     */
    public boolean isValueDifferent() {
        return valueDifferent;
    }

    /**
     * Set whether the metadata value is different between documents.
     *
     * @param valueDifferent Whether value different
     */
    public void setValueDifferent(boolean valueDifferent) {
        this.valueDifferent = valueDifferent;
    }
}