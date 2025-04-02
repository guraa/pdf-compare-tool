package guraa.pdfcompare.service;

import guraa.pdfcompare.comparison.PageComparisonResult;

/**
 * Adapter class to convert between different PageComparisonResult classes
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

        // Copy basic properties
        target.setPageNumber(source.getPageNumber());
        target.setOnlyInBase(source.isOnlyInBase());
        target.setOnlyInCompare(source.isOnlyInCompare());
        target.setDimensionsDifferent(source.isDimensionsDifferent());
        target.setBaseDimensions(source.getBaseDimensions());
        target.setCompareDimensions(source.getCompareDimensions());

        // Copy text differences
        target.setTextDifferences(source.getTextDifferences());

        // Copy element differences
        target.setTextElementDifferences(source.getTextElementDifferences());

        // Copy image differences
        target.setImageDifferences(source.getImageDifferences());

        // Copy font differences
        target.setFontDifferences(source.getFontDifferences());

        // Set additional service-specific properties
        if (source.isOnlyInBase()) {
            target.setChangeType("DELETION");
        } else if (source.isOnlyInCompare()) {
            target.setChangeType("ADDITION");
        } else if (hasDifferences(source)) {
            target.setChangeType("MODIFIED");
            target.setHasDifferences(true);
            target.setTotalDifferences(countDifferences(source));
        } else {
            target.setChangeType("IDENTICAL");
            target.setHasDifferences(false);
            target.setTotalDifferences(0);
        }

        return target;
    }

    /**
     * Convert from service.PageComparisonResult to comparison.PageComparisonResult
     *
     * @param source The source service comparison result
     * @return The converted comparison page comparison result
     */
    public static guraa.pdfcompare.comparison.PageComparisonResult toComparisonResult(PageComparisonResult source) {
        if (source == null) {
            return null;
        }

        guraa.pdfcompare.comparison.PageComparisonResult target =
                new guraa.pdfcompare.comparison.PageComparisonResult();

        // Copy basic properties
        target.setPageNumber(source.getPageNumber());
        target.setOnlyInBase(source.isOnlyInBase());
        target.setOnlyInCompare(source.isOnlyInCompare());
        target.setDimensionsDifferent(source.isDimensionsDifferent());
        target.setBaseDimensions(source.getBaseDimensions());
        target.setCompareDimensions(source.getCompareDimensions());

        // Copy text differences
        target.setTextDifferences(source.getTextDifferences());

        // Copy element differences
        target.setTextElementDifferences(source.getTextElementDifferences());

        // Copy image differences
        target.setImageDifferences(source.getImageDifferences());

        // Copy font differences
        target.setFontDifferences(source.getFontDifferences());

        // Set original page numbers if available from PagePair
        if (source.getPagePair() != null) {
            PagePair pagePair = source.getPagePair();

            if (pagePair.getBaseFingerprint() != null) {
                target.setOriginalBasePageNumber(pagePair.getBaseFingerprint().getPageIndex() + 1);
            }

            if (pagePair.getCompareFingerprint() != null) {
                target.setOriginalComparePageNumber(pagePair.getCompareFingerprint().getPageIndex() + 1);
            }
        }

        return target;
    }

    /**
     * Check if a comparison result has any differences
     */
    private static boolean hasDifferences(guraa.pdfcompare.comparison.PageComparisonResult result) {
        return result.isOnlyInBase() ||
                result.isOnlyInCompare() ||
                result.isDimensionsDifferent() ||
                (result.getTextDifferences() != null &&
                        result.getTextDifferences().getDifferenceCount() > 0) ||
                (result.getTextElementDifferences() != null &&
                        !result.getTextElementDifferences().isEmpty()) ||
                (result.getImageDifferences() != null &&
                        !result.getImageDifferences().isEmpty()) ||
                (result.getFontDifferences() != null &&
                        !result.getFontDifferences().isEmpty());
    }

    /**
     * Count the total number of differences in a comparison result
     */
    private static int countDifferences(guraa.pdfcompare.comparison.PageComparisonResult result) {
        int count = 0;

        // Count structure differences
        if (result.isOnlyInBase() || result.isOnlyInCompare()) {
            count += 1;
        }

        if (result.isDimensionsDifferent()) {
            count += 1;
        }

        // Count text differences
        if (result.getTextDifferences() != null && result.getTextDifferences().getDifferences() != null) {
            count += result.getTextDifferences().getDifferences().size();
        }

        // Count element differences
        if (result.getTextElementDifferences() != null) {
            count += result.getTextElementDifferences().size();
        }

        // Count image differences
        if (result.getImageDifferences() != null) {
            count += result.getImageDifferences().size();
        }

        // Count font differences
        if (result.getFontDifferences() != null) {
            count += result.getFontDifferences().size();
        }

        return count;
    }
}