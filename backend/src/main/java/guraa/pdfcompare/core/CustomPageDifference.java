package guraa.pdfcompare.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents differences between corresponding pages in two PDF documents.
 * This class is used to store information about what changed between pages
 * and provides methods for categorizing and describing those changes.
 */
public class CustomPageDifference {
    private int pageNumber;
    private boolean basePageExists;
    private boolean comparePageExists;
    private int totalDifferences;
    private int textDifferences;
    private int imageDifferences;
    private int styleDifferences;
    private int fontDifferences;
    private String description;
    private List<DifferenceDetail> differenceDetails;

    public CustomPageDifference() {
        this.differenceDetails = new ArrayList<>();
    }

    /**
     * Get the page number where the difference was found
     * @return Page number
     */
    public int getPageNumber() {
        return pageNumber;
    }

    /**
     * Set the page number where the difference was found
     * @param pageNumber Page number
     */
    public void setPageNumber(int pageNumber) {
        this.pageNumber = pageNumber;
    }

    /**
     * Check if the page exists in the base document
     * @return true if the page exists in the base document, false otherwise
     */
    public boolean isBasePageExists() {
        return basePageExists;
    }

    /**
     * Set whether the page exists in the base document
     * @param basePageExists true if the page exists in the base document, false otherwise
     */
    public void setBasePageExists(boolean basePageExists) {
        this.basePageExists = basePageExists;
    }

    /**
     * Check if the page exists in the compare document
     * @return true if the page exists in the compare document, false otherwise
     */
    public boolean isComparePageExists() {
        return comparePageExists;
    }

    /**
     * Set whether the page exists in the compare document
     * @param comparePageExists true if the page exists in the compare document, false otherwise
     */
    public void setComparePageExists(boolean comparePageExists) {
        this.comparePageExists = comparePageExists;
    }

    /**
     * Get the total number of differences found on this page
     * @return Total number of differences
     */
    public int getTotalDifferences() {
        return totalDifferences;
    }

    /**
     * Set the total number of differences found on this page
     * @param totalDifferences Total number of differences
     */
    public void setTotalDifferences(int totalDifferences) {
        this.totalDifferences = totalDifferences;
    }

    /**
     * Get the number of text differences found on this page
     * @return Number of text differences
     */
    public int getTextDifferences() {
        return textDifferences;
    }

    /**
     * Set the number of text differences found on this page
     * @param textDifferences Number of text differences
     */
    public void setTextDifferences(int textDifferences) {
        this.textDifferences = textDifferences;
    }

    /**
     * Get the number of image differences found on this page
     * @return Number of image differences
     */
    public int getImageDifferences() {
        return imageDifferences;
    }

    /**
     * Set the number of image differences found on this page
     * @param imageDifferences Number of image differences
     */
    public void setImageDifferences(int imageDifferences) {
        this.imageDifferences = imageDifferences;
    }

    /**
     * Get the number of style differences found on this page
     * @return Number of style differences
     */
    public int getStyleDifferences() {
        return styleDifferences;
    }

    /**
     * Set the number of style differences found on this page
     * @param styleDifferences Number of style differences
     */
    public void setStyleDifferences(int styleDifferences) {
        this.styleDifferences = styleDifferences;
    }

    /**
     * Get the number of font differences found on this page
     * @return Number of font differences
     */
    public int getFontDifferences() {
        return fontDifferences;
    }

    /**
     * Set the number of font differences found on this page
     * @param fontDifferences Number of font differences
     */
    public void setFontDifferences(int fontDifferences) {
        this.fontDifferences = fontDifferences;
    }

    /**
     * Get the description of the differences found on this page
     * @return Description of differences
     */
    public String getDescription() {
        return description;
    }

    /**
     * Set the description of the differences found on this page
     * @param description Description of differences
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Get the detailed list of differences found on this page
     * @return List of difference details
     */
    public List<DifferenceDetail> getDifferenceDetails() {
        return differenceDetails;
    }

    /**
     * Set the detailed list of differences found on this page
     * @param differenceDetails List of difference details
     */
    public void setDifferenceDetails(List<DifferenceDetail> differenceDetails) {
        this.differenceDetails = differenceDetails;
    }

    /**
     * Add a new difference detail to the list
     * @param detail Difference detail to add
     */
    public void addDifferenceDetail(DifferenceDetail detail) {
        if (this.differenceDetails == null) {
            this.differenceDetails = new ArrayList<>();
        }
        this.differenceDetails.add(detail);

        // Update counters based on difference type
        totalDifferences++;

        switch (detail.getType()) {
            case TEXT:
                textDifferences++;
                break;
            case IMAGE:
                imageDifferences++;
                break;
            case STYLE:
                styleDifferences++;
                break;
            case FONT:
                fontDifferences++;
                break;
        }
    }

    /**
     * Get the type of page change (ADDITION, DELETION, MODIFICATION)
     * @return Change type as a string
     */
    public String getChangeType() {
        if (!basePageExists && comparePageExists) {
            return "ADDITION";
        } else if (basePageExists && !comparePageExists) {
            return "DELETION";
        } else if (totalDifferences > 0) {
            return "MODIFICATION";
        } else {
            return "IDENTICAL";
        }
    }

    /**
     * Generate a human-readable summary of the page differences
     * @return Summary text
     */
    public String generateSummary() {
        if (!basePageExists && comparePageExists) {
            return "Page " + pageNumber + " was added in the compare document";
        } else if (basePageExists && !comparePageExists) {
            return "Page " + pageNumber + " was deleted from the base document";
        } else if (totalDifferences == 0) {
            return "Page " + pageNumber + " is identical in both documents";
        } else {
            StringBuilder summary = new StringBuilder("Page " + pageNumber + " has " + totalDifferences + " differences: ");

            if (textDifferences > 0) {
                summary.append(textDifferences).append(" text, ");
            }

            if (imageDifferences > 0) {
                summary.append(imageDifferences).append(" image, ");
            }

            if (fontDifferences > 0) {
                summary.append(fontDifferences).append(" font, ");
            }

            if (styleDifferences > 0) {
                summary.append(styleDifferences).append(" style, ");
            }

            // Remove trailing comma and space
            if (summary.charAt(summary.length() - 2) == ',') {
                summary.delete(summary.length() - 2, summary.length());
            }

            return summary.toString();
        }
    }

    @Override
    public String toString() {
        return generateSummary();
    }

    /**
     * Enumeration of different types of differences that can be found
     */
    public enum DifferenceType {
        TEXT,
        IMAGE,
        FONT,
        STYLE,
        LAYOUT,
        OTHER
    }

    /**
     * Detailed information about a specific difference
     */
    public static class DifferenceDetail {
        private DifferenceType type;
        private String baseContent;
        private String compareContent;
        private double x;
        private double y;
        private double width;
        private double height;
        private String description;
        private double severity;

        public DifferenceDetail() {
        }

        public DifferenceDetail(DifferenceType type, String baseContent, String compareContent) {
            this.type = type;
            this.baseContent = baseContent;
            this.compareContent = compareContent;
        }

        // Getter and setter methods

        public DifferenceType getType() {
            return type;
        }

        public void setType(DifferenceType type) {
            this.type = type;
        }

        public String getBaseContent() {
            return baseContent;
        }

        public void setBaseContent(String baseContent) {
            this.baseContent = baseContent;
        }

        public String getCompareContent() {
            return compareContent;
        }

        public void setCompareContent(String compareContent) {
            this.compareContent = compareContent;
        }

        public double getX() {
            return x;
        }

        public void setX(double x) {
            this.x = x;
        }

        public double getY() {
            return y;
        }

        public void setY(double y) {
            this.y = y;
        }

        public double getWidth() {
            return width;
        }

        public void setWidth(double width) {
            this.width = width;
        }

        public double getHeight() {
            return height;
        }

        public void setHeight(double height) {
            this.height = height;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public double getSeverity() {
            return severity;
        }

        public void setSeverity(double severity) {
            this.severity = severity;
        }
    }
}