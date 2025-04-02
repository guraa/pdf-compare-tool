package guraa.pdfcompare.service;

/**
 * Represents custom differences found between two pages in a PDF comparison.
 * This class is used for special cases like page additions or deletions.
 */
public class CustomPageDifference {
    private int pageNumber;
    private boolean basePageExists;
    private boolean comparePageExists;
    private String differenceType;
    private String description;

    /**
     * Default constructor
     */
    public CustomPageDifference() {
    }

    /**
     * Get the page number
     * @return Page number
     */
    public int getPageNumber() {
        return pageNumber;
    }

    /**
     * Set the page number
     * @param pageNumber Page number
     */
    public void setPageNumber(int pageNumber) {
        this.pageNumber = pageNumber;
    }

    /**
     * Check if the base page exists
     * @return true if the base page exists
     */
    public boolean isBasePageExists() {
        return basePageExists;
    }

    /**
     * Set whether the base page exists
     * @param basePageExists Whether the base page exists
     */
    public void setBasePageExists(boolean basePageExists) {
        this.basePageExists = basePageExists;
    }

    /**
     * Check if the compare page exists
     * @return true if the compare page exists
     */
    public boolean isComparePageExists() {
        return comparePageExists;
    }

    /**
     * Set whether the compare page exists
     * @param comparePageExists Whether the compare page exists
     */
    public void setComparePageExists(boolean comparePageExists) {
        this.comparePageExists = comparePageExists;
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

    /**
     * Get the description
     * @return Description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Set the description
     * @param description Description
     */
    public void setDescription(String description) {
        this.description = description;
    }
}
