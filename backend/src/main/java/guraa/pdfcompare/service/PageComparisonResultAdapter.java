package guraa.pdfcompare.service;

import guraa.pdfcompare.comparison.PageComparisonResult;

/**
 * Simplified adapter class to convert from core PageComparisonResult to service PageComparisonResult
 */
public class PageComparisonResultAdapter {

    /**
     * Convert from comparison.PageComparisonResult to service.PageComparisonResult
     *
     * @param source The source comparison result
     * @return The converted service page comparison result
     */
    public static PageComparisonResult toServiceResult(guraa.pdfcompare.comparison.PageComparisonResult source) {
        if (source == null) {
            return null;
        }

        PageComparisonResult target = new PageComparisonResult();

        // Copy dimensions info if available
        try {
            target.setDimensionsDifferent(source.isDimensionsDifferent());
            target.setBaseDimensions(source.getBaseDimensions());
            target.setCompareDimensions(source.getCompareDimensions());
        } catch (Exception e) {
            // Ignore if these methods don't exist
        }

        // Copy differences that should exist in all versions
        target.setTextDifferences(source.getTextDifferences());
        target.setTextElementDifferences(source.getTextElementDifferences());
        target.setImageDifferences(source.getImageDifferences());
        target.setFontDifferences(source.getFontDifferences());

        // Calculate total differences
        int totalDiffs = 0;

        // Count text differences
        if (source.getTextDifferences() != null && source.getTextDifferences().getDifferences() != null) {
            totalDiffs += source.getTextDifferences().getDifferences().size();
        }

        // Count text element differences
        if (source.getTextElementDifferences() != null) {
            totalDiffs += source.getTextElementDifferences().size();
        }

        // Count image differences
        if (source.getImageDifferences() != null) {
            totalDiffs += source.getImageDifferences().size();
        }

        // Count font differences
        if (source.getFontDifferences() != null) {
            totalDiffs += source.getFontDifferences().size();
        }

        target.setTotalDifferences(totalDiffs);
        target.setHasDifferences(totalDiffs > 0);

        // Set change type based on differences
        if (totalDiffs > 0) {
            target.setChangeType("MODIFIED");
        } else {
            target.setChangeType("IDENTICAL");
        }

        return target;
    }
}