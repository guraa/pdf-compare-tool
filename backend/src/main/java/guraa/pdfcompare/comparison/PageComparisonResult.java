package guraa.pdfcompare.comparison;

import guraa.pdfcompare.core.CustomPageDifference;
import guraa.pdfcompare.core.ImageElement;
import guraa.pdfcompare.core.TextElement;
import guraa.pdfcompare.service.PagePair;

import java.util.*;

/**
 * Class representing comparison results for a single page
 */
public class PageComparisonResult {
    private int pageNumber;
    private boolean onlyInBase;
    private boolean onlyInCompare;
    private boolean dimensionsDifferent;
    private float[] baseDimensions;
    private PageDifference pageDifference;
    private float[] compareDimensions;
    private TextComparisonResult textDifferences;
    private List<TextElementDifference> textElementDifferences;
    private List<ImageDifference> imageDifferences;
    private List<FontDifference> fontDifferences;

    // Fields from the service version
    private Integer originalBasePageNumber;
    private Integer originalComparePageNumber;
    private PDFComparisonResult comparisonResult;
    private PagePair pagePair;
    private boolean hasDifferences;
    private int totalDifferences;
    private String changeType;
    private String error;
    private CustomPageDifference customPageDifference;



    public void setComparisonResult(PDFComparisonResult comparisonResult) {
        this.comparisonResult = comparisonResult;

        // If we have a comparison result with page differences, extract the first one
        if (comparisonResult != null && comparisonResult.getPageDifferences() != null
                && !comparisonResult.getPageDifferences().isEmpty()) {
            guraa.pdfcompare.comparison.PageComparisonResult pageResult =
                    comparisonResult.getPageDifferences().get(0);

            // Copy properties from the comparison result to this object for compatibility
            this.pageNumber = pageResult.getPageNumber();
            this.onlyInBase = pageResult.isOnlyInBase();
            this.onlyInCompare = pageResult.isOnlyInCompare();
            this.dimensionsDifferent = pageResult.isDimensionsDifferent();
            this.baseDimensions = pageResult.getBaseDimensions();
            this.compareDimensions = pageResult.getCompareDimensions();
            this.textDifferences = pageResult.getTextDifferences();
            this.textElementDifferences = pageResult.getTextElementDifferences();
            this.imageDifferences = pageResult.getImageDifferences();
            this.fontDifferences = pageResult.getFontDifferences();
        }
    }

    /**
     * Extract page differences as a list of items for API response
     * @param isBase Whether to extract differences from base or comparison document
     * @return List of difference items
     */
    public List<Map<String, Object>> extractPageDifferences(boolean isBase) {
        List<Map<String, Object>> differences = new ArrayList<>();

        // If page exists only in one document
        if (onlyInBase && !isBase) {
            Map<String, Object> diff = new HashMap<>();
            diff.put("id", "page-" + pageNumber + "-missing");
            diff.put("type", "structure");
            diff.put("description", "Page only exists in base document");
            diff.put("severity", "critical");
            diff.put("changeType", "deleted");
            differences.add(diff);
            return differences;
        }

        if (onlyInCompare && isBase) {
            Map<String, Object> diff = new HashMap<>();
            diff.put("id", "page-" + pageNumber + "-missing");
            diff.put("type", "structure");
            diff.put("description", "Page only exists in comparison document");
            diff.put("severity", "critical");
            diff.put("changeType", "added");
            differences.add(diff);
            return differences;
        }

        // Add text differences
        if (textDifferences != null && textDifferences.getDifferences() != null) {
            for (int i = 0; i < textDifferences.getDifferences().size(); i++) {
                TextDifferenceItem textDiff = textDifferences.getDifferences().get(i);

                Map<String, Object> diff = new HashMap<>();
                diff.put("id", "text-" + pageNumber + "-" + i);
                diff.put("type", "text");

                TextDifferenceType diffType = textDiff.getDifferenceType();
                if (diffType == TextDifferenceType.ADDED) {
                    if (isBase) continue; // Skip added text for base document
                    diff.put("changeType", "added");
                    diff.put("compareText", textDiff.getCompareText());
                    diff.put("description", "Text added: " + textDiff.getCompareText());
                } else if (diffType == TextDifferenceType.DELETED) {
                    if (!isBase) continue; // Skip deleted text for compare document
                    diff.put("changeType", "deleted");
                    diff.put("baseText", textDiff.getBaseText());
                    diff.put("description", "Text deleted: " + textDiff.getBaseText());
                } else if (diffType == TextDifferenceType.MODIFIED) {
                    diff.put("changeType", "modified");
                    diff.put("baseText", textDiff.getBaseText());
                    diff.put("compareText", textDiff.getCompareText());
                    diff.put("description", "Text modified from \"" +
                            (isBase ? textDiff.getBaseText() : textDiff.getCompareText()) +
                            "\" to \"" +
                            (isBase ? textDiff.getCompareText() : textDiff.getBaseText()) + "\"");
                }

                diff.put("lineNumber", textDiff.getLineNumber());
                diff.put("severity", "minor");

                differences.add(diff);
            }
        }

        // Add text element differences
        if (textElementDifferences != null) {
            for (int i = 0; i < textElementDifferences.size(); i++) {
                TextElementDifference elementDiff = textElementDifferences.get(i);

                // Skip differences not relevant to current document
                if (isBase && elementDiff.isOnlyInCompare()) continue;
                if (!isBase && elementDiff.isOnlyInBase()) continue;

                Map<String, Object> diff = new HashMap<>();
                diff.put("id", "element-" + pageNumber + "-" + i);

                if (elementDiff.isStyleDifferent()) {
                    diff.put("type", "style");
                    diff.put("changeType", "modified");

                    TextElement element = isBase ? elementDiff.getBaseElement() : elementDiff.getCompareElement();
                    if (element != null) {
                        diff.put("text", element.getText());

                        Map<String, Object> style = new HashMap<>();
                        style.put("fontName", element.getFontName());
                        style.put("fontSize", element.getFontSize());
                        style.put("fontStyle", element.getFontStyle());
                        style.put("color", element.getColor());

                        if (isBase) {
                            diff.put("baseStyle", style);
                        } else {
                            diff.put("compareStyle", style);
                        }
                    }

                    diff.put("severity", "minor");
                    diff.put("description", "Text style differs");
                } else {
                    diff.put("type", "text");

                    if (elementDiff.isOnlyInBase()) {
                        diff.put("changeType", "deleted");
                        diff.put("baseText", elementDiff.getBaseElement().getText());
                        diff.put("description", "Text deleted: " + elementDiff.getBaseElement().getText());
                    } else if (elementDiff.isOnlyInCompare()) {
                        diff.put("changeType", "added");
                        diff.put("compareText", elementDiff.getCompareElement().getText());
                        diff.put("description", "Text added: " + elementDiff.getCompareElement().getText());
                    }

                    diff.put("severity", "minor");
                }

                // Set position bounds for highlighting
                TextElement element = isBase ? elementDiff.getBaseElement() : elementDiff.getCompareElement();
                if (element != null) {
                    Map<String, Float> bounds = new HashMap<>();
                    bounds.put("x", element.getX());
                    bounds.put("y", element.getY());
                    bounds.put("width", element.getWidth());
                    bounds.put("height", element.getHeight());
                    diff.put("bounds", bounds);

                    Map<String, Float> position = new HashMap<>();
                    position.put("x", element.getX());
                    position.put("y", element.getY());
                    diff.put("position", position);
                }

                differences.add(diff);
            }
        }

        // Add image differences
        if (imageDifferences != null) {
            for (int i = 0; i < imageDifferences.size(); i++) {
                ImageDifference imageDiff = imageDifferences.get(i);

                // Skip differences not relevant to current document
                if (isBase && imageDiff.isOnlyInCompare()) continue;
                if (!isBase && imageDiff.isOnlyInBase()) continue;

                Map<String, Object> diff = new HashMap<>();
                diff.put("id", "image-" + pageNumber + "-" + i);
                diff.put("type", "image");

                ImageElement image = isBase ? imageDiff.getBaseImage() : imageDiff.getCompareImage();

                if (imageDiff.isOnlyInBase()) {
                    diff.put("changeType", "deleted");
                    diff.put("description", "Image deleted");
                } else if (imageDiff.isOnlyInCompare()) {
                    diff.put("changeType", "added");
                    diff.put("description", "Image added");
                } else {
                    diff.put("changeType", "modified");

                    List<String> changes = new ArrayList<>();
                    if (imageDiff.isDimensionsDifferent()) {
                        changes.add("Dimensions differ");
                    }
                    if (imageDiff.isPositionDifferent()) {
                        changes.add("Position differs");
                    }
                    if (imageDiff.isFormatDifferent()) {
                        changes.add("Format differs");
                    }

                    diff.put("changes", changes);
                    diff.put("description", String.join(", ", changes));
                }

                if (image != null) {
                    diff.put("imageName", image.getName());
                    diff.put("format", image.getFormat());

                    Map<String, Float> dimensions = new HashMap<>();
                    dimensions.put("width", image.getWidth());
                    dimensions.put("height", image.getHeight());
                    diff.put("dimensions", dimensions);

                    // Set position bounds for highlighting
                    Map<String, Float> bounds = new HashMap<>();
                    bounds.put("x", image.getX());
                    bounds.put("y", image.getY());
                    bounds.put("width", image.getWidth());
                    bounds.put("height", image.getHeight());
                    diff.put("bounds", bounds);

                    Map<String, Float> position = new HashMap<>();
                    position.put("x", image.getX());
                    position.put("y", image.getY());
                    diff.put("position", position);
                }

                diff.put("severity", "major");

                differences.add(diff);
            }
        }

        // Add font differences
        if (fontDifferences != null) {
            for (int i = 0; i < fontDifferences.size(); i++) {
                // Implementation for font differences would go here
                // Similar to the other difference types
            }
        }

        return differences;
    }

    // Getters and setters from the original class

    /**
     * Get the page difference information
     * @return Page difference object
     */
    public PageDifference getPageDifference() {
        return pageDifference;
    }

    /**
     * Set the page difference information
     * @param pageDifference Page difference object
     */
    public void setPageDifference(PageDifference pageDifference) {
        this.pageDifference = pageDifference;
    }
    /**
     * Default constructor
     */
    public PageComparisonResult() {
    }

    /**
     * Create a result with a page pair
     * @param pagePair The page pair being compared
     */
    public PageComparisonResult(PagePair pagePair) {
        this.pagePair = pagePair;
    }

    public Integer getOriginalBasePageNumber() {
        return originalBasePageNumber;
    }

    public void setOriginalBasePageNumber(Integer originalBasePageNumber) {
        this.originalBasePageNumber = originalBasePageNumber;
    }

    public Integer getOriginalComparePageNumber() {
        return originalComparePageNumber;
    }

    public void setOriginalComparePageNumber(Integer originalComparePageNumber) {
        this.originalComparePageNumber = originalComparePageNumber;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(int pageNumber) {
        this.pageNumber = pageNumber;
    }

    public boolean isOnlyInBase() {
        return onlyInBase;
    }

    public void setOnlyInBase(boolean onlyInBase) {
        this.onlyInBase = onlyInBase;
    }

    public boolean isOnlyInCompare() {
        return onlyInCompare;
    }

    public void setOnlyInCompare(boolean onlyInCompare) {
        this.onlyInCompare = onlyInCompare;
    }

    public boolean isDimensionsDifferent() {
        return dimensionsDifferent;
    }

    public void setDimensionsDifferent(boolean dimensionsDifferent) {
        this.dimensionsDifferent = dimensionsDifferent;
    }

    public float[] getBaseDimensions() {
        return baseDimensions;
    }

    public void setBaseDimensions(float[] baseDimensions) {
        this.baseDimensions = baseDimensions;
    }

    public float[] getCompareDimensions() {
        return compareDimensions;
    }

    public void setCompareDimensions(float[] compareDimensions) {
        this.compareDimensions = compareDimensions;
    }

    public TextComparisonResult getTextDifferences() {
        return textDifferences;
    }

    public void setTextDifferences(TextComparisonResult textDifferences) {
        this.textDifferences = textDifferences;
    }

    public List<TextElementDifference> getTextElementDifferences() {
        return textElementDifferences;
    }

    public void setTextElementDifferences(List<TextElementDifference> textElementDifferences) {
        this.textElementDifferences = textElementDifferences;
    }

    public List<ImageDifference> getImageDifferences() {
        return imageDifferences;
    }

    public void setImageDifferences(List<ImageDifference> imageDifferences) {
        this.imageDifferences = imageDifferences;
    }

    public List<FontDifference> getFontDifferences() {
        return fontDifferences;
    }

    public void setFontDifferences(List<FontDifference> fontDifferences) {
        this.fontDifferences = fontDifferences;
    }

    // Additional methods from the service version
    public PagePair getPagePair() {
        return pagePair;
    }

    public void setPagePair(PagePair pagePair) {
        this.pagePair = pagePair;
    }

    public boolean isHasDifferences() {
        return hasDifferences;
    }

    public void setHasDifferences(boolean hasDifferences) {
        this.hasDifferences = hasDifferences;
    }

    public int getTotalDifferences() {
        return totalDifferences;
    }

    public void setTotalDifferences(int totalDifferences) {
        this.totalDifferences = totalDifferences;
    }

    public CustomPageDifference getCustomPageDifference() {
        return customPageDifference;
    }

    public void setCustomPageDifference(CustomPageDifference customPageDifference) {
        this.customPageDifference = customPageDifference;
    }

    public String getChangeType() {
        if (changeType != null) {
            return changeType;
        }

        if (!hasDifferences && pagePair != null && pagePair.isMatched()) {
            return "IDENTICAL";
        } else if (pagePair != null && pagePair.isMatched()) {
            return "MODIFIED";
        } else if (pagePair != null && pagePair.getBaseFingerprint() != null) {
            return "DELETION";
        } else {
            return "ADDITION";
        }
    }

    public void setChangeType(String changeType) {
        this.changeType = changeType;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public boolean hasError() {
        return error != null && !error.isEmpty();
    }

    public boolean isAddition() {
        return "ADDITION".equals(getChangeType());
    }

    public boolean isDeletion() {
        return "DELETION".equals(getChangeType());
    }

    public boolean isModification() {
        return "MODIFIED".equals(getChangeType());
    }

    public boolean isIdentical() {
        return "IDENTICAL".equals(getChangeType());
    }

    public String getSummary() {
        if (hasError()) {
            return "Error: " + error;
        }

        StringBuilder summary = new StringBuilder();
        String type = getChangeType();

        switch (type) {
            case "ADDITION":
                summary.append("Page added in compare document");
                break;
            case "DELETION":
                summary.append("Page deleted from base document");
                break;
            case "MODIFIED":
                summary.append("Page modified with ").append(totalDifferences).append(" differences");
                break;
            case "IDENTICAL":
                summary.append("Pages are identical");
                break;
            default:
                summary.append("Unknown change type");
        }

        return summary.toString();
    }

    @Override
    public String toString() {
        return "PageComparisonResult{" +
                "changeType='" + getChangeType() + '\'' +
                ", hasDifferences=" + hasDifferences +
                ", totalDifferences=" + totalDifferences +
                ", summary='" + getSummary() + '\'' +
                '}';
    }
}