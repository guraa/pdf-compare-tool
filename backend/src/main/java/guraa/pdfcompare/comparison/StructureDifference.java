package guraa.pdfcompare.comparison;

/**
 * Class representing a difference in document structure
 */
public class StructureDifference {
    private String differenceType; // e.g., "Heading", "List", "Table"
    private String description;
    private String location; // e.g., "Page 3, Paragraph 2"

    // Getters and setters
    public String getDifferenceType() {
        return differenceType;
    }

    public void setDifferenceType(String differenceType) {
        this.differenceType = differenceType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }
}