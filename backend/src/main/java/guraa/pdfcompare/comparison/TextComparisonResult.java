package guraa.pdfcompare.comparison;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the result of comparing text between two pages.
 */
public class TextComparisonResult {
    private boolean hasDifferences;
    private List<TextDifference> differences = new ArrayList<>();
    private List<TextDifferenceItem> differenceItems = new ArrayList<>();
    private String baseText;
    private String compareText;
    private double similarityScore;
    private String differenceType;

    /**
     * Default constructor
     */
    public TextComparisonResult() {
    }

    /**
     * Check if there are differences
     * @return true if there are differences
     */
    public boolean isHasDifferences() {
        return hasDifferences;
    }

    /**
     * Set whether there are differences
     * @param hasDifferences Whether there are differences
     */
    public void setHasDifferences(boolean hasDifferences) {
        this.hasDifferences = hasDifferences;
    }

    /**
     * Get the differences
     * @return List of differences
     */
    public List<TextDifference> getDifferences() {
        return differences;
    }

    /**
     * Set the differences
     * @param differences List of differences
     */
    public void setDifferences(List<TextDifference> differences) {
        this.differences = differences;
    }
    
    /**
     * Get the difference items
     * @return List of difference items
     */
    public List<TextDifferenceItem> getDifferenceItems() {
        return differenceItems;
    }
    
    /**
     * Set the difference items
     * @param differenceItems List of difference items
     */
    public void setDifferenceItems(List<TextDifferenceItem> differenceItems) {
        this.differenceItems = differenceItems;
    }

    /**
     * Add a difference
     * @param difference Difference to add
     */
    public void addDifference(TextDifference difference) {
        if (this.differences == null) {
            this.differences = new ArrayList<>();
        }
        this.differences.add(difference);
    }
    
    /**
     * Add a difference item
     * @param differenceItem Difference item to add
     */
    public void addDifferenceItem(TextDifferenceItem differenceItem) {
        if (this.differenceItems == null) {
            this.differenceItems = new ArrayList<>();
        }
        this.differenceItems.add(differenceItem);
    }

    /**
     * Get the base text
     * @return Base text
     */
    public String getBaseText() {
        return baseText;
    }

    /**
     * Set the base text
     * @param baseText Base text
     */
    public void setBaseText(String baseText) {
        this.baseText = baseText;
    }

    /**
     * Get the compare text
     * @return Compare text
     */
    public String getCompareText() {
        return compareText;
    }

    /**
     * Set the compare text
     * @param compareText Compare text
     */
    public void setCompareText(String compareText) {
        this.compareText = compareText;
    }

    /**
     * Get the similarity score
     * @return Similarity score
     */
    public double getSimilarityScore() {
        return similarityScore;
    }

    /**
     * Set the similarity score
     * @param similarityScore Similarity score
     */
    public void setSimilarityScore(double similarityScore) {
        this.similarityScore = similarityScore;
    }

    /**
     * Get the number of differences
     * @return Number of differences
     */
    public int getDifferenceCount() {
        return differences != null ? differences.size() : 0;
    }
    
    /**
     * Get the number of difference items
     * @return Number of difference items
     */
    public int getDifferenceItemCount() {
        return differenceItems != null ? differenceItems.size() : 0;
    }
    
    /**
     * Set the number of differences
     * @param count Number of differences
     */
    public void setDifferenceCount(int count) {
        // This is a convenience method for compatibility
        // It doesn't actually set the count directly, but ensures
        // the differences list has the right size
        if (differences == null) {
            differences = new ArrayList<>();
        }
        
        // If we need to add differences to match the count
        while (differences.size() < count) {
            differences.add(new TextDifference());
        }
        
        // If we need to remove differences to match the count
        while (differences.size() > count) {
            differences.remove(differences.size() - 1);
        }
    }
    
    /**
     * Get the difference type
     * @return Difference type
     */
    public String getDifferenceType() {
        return differenceType;
    }
    
    /**
     * Set the difference type
     * @param differenceType Difference type
     */
    public void setDifferenceType(String differenceType) {
        this.differenceType = differenceType;
    }
}
