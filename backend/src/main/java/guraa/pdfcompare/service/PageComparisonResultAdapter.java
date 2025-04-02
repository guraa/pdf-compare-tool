package guraa.pdfcompare.service;

/**
 * Adapter class for converting between comparison and service result types.
 */
public class PageComparisonResultAdapter {
    
    /**
     * Convert a comparison result to a service result
     * @param comparisonResult Comparison result
     * @return Service result
     */
    public static PageComparisonResult toServiceResult(guraa.pdfcompare.comparison.PageComparisonResult comparisonResult) {
        if (comparisonResult == null) {
            return null;
        }
        
        PageComparisonResult serviceResult = new PageComparisonResult();
        
        // Copy basic properties
        serviceResult.setChangeType(comparisonResult.getChangeType());
        serviceResult.setHasDifferences(comparisonResult.isHasDifferences());
        serviceResult.setTotalDifferences(comparisonResult.getTotalDifferences());
        serviceResult.setError(comparisonResult.getError());
        
        // Copy page status
        serviceResult.setOnlyInBase(comparisonResult.isOnlyInBase());
        serviceResult.setOnlyInCompare(comparisonResult.isOnlyInCompare());
        serviceResult.setPageNumber(comparisonResult.getPageNumber());
        
        // Copy dimensions
        serviceResult.setBaseDimensions(comparisonResult.getBaseDimensions());
        serviceResult.setCompareDimensions(comparisonResult.getCompareDimensions());
        serviceResult.setDimensionsDifferent(comparisonResult.isDimensionsDifferent());
        
        // Copy difference details
        if (comparisonResult.getTextDifferences() != null) {
            serviceResult.setTextDifferences(comparisonResult.getTextDifferences());
            
            // Copy difference type if available
            if (comparisonResult.getTextDifferences().getDifferenceType() != null) {
                serviceResult.getTextDifferences().setDifferenceType(
                    comparisonResult.getTextDifferences().getDifferenceType());
            }
        }
        if (comparisonResult.getTextElementDifferences() != null) {
            serviceResult.setTextElementDifferences(comparisonResult.getTextElementDifferences());
        }
        if (comparisonResult.getImageDifferences() != null) {
            serviceResult.setImageDifferences(comparisonResult.getImageDifferences());
        }
        if (comparisonResult.getFontDifferences() != null) {
            serviceResult.setFontDifferences(comparisonResult.getFontDifferences());
        }
        
        // Copy page numbers
        serviceResult.setOriginalBasePageNumber(comparisonResult.getOriginalBasePageNumber());
        serviceResult.setOriginalComparePageNumber(comparisonResult.getOriginalComparePageNumber());
        
        return serviceResult;
    }
    
    /**
     * Convert a service result to a comparison result
     * @param serviceResult Service result
     * @return Comparison result
     */
    public static guraa.pdfcompare.comparison.PageComparisonResult toComparisonResult(PageComparisonResult serviceResult) {
        if (serviceResult == null) {
            return null;
        }
        
        guraa.pdfcompare.comparison.PageComparisonResult comparisonResult = new guraa.pdfcompare.comparison.PageComparisonResult();
        
        // Copy basic properties
        comparisonResult.setChangeType(serviceResult.getChangeType());
        comparisonResult.setHasDifferences(serviceResult.isHasDifferences());
        comparisonResult.setTotalDifferences(serviceResult.getTotalDifferences());
        comparisonResult.setError(serviceResult.getError());
        
        // Copy page status
        comparisonResult.setOnlyInBase(serviceResult.isOnlyInBase());
        comparisonResult.setOnlyInCompare(serviceResult.isOnlyInCompare());
        comparisonResult.setPageNumber(serviceResult.getPageNumber());
        
        // Copy dimensions
        comparisonResult.setBaseDimensions(serviceResult.getBaseDimensions());
        comparisonResult.setCompareDimensions(serviceResult.getCompareDimensions());
        comparisonResult.setDimensionsDifferent(serviceResult.isDimensionsDifferent());
        
        // Copy difference details
        if (serviceResult.getTextDifferences() != null) {
            comparisonResult.setTextDifferences(serviceResult.getTextDifferences());
            
            // Copy difference type if available
            if (serviceResult.getTextDifferences().getDifferenceType() != null) {
                comparisonResult.getTextDifferences().setDifferenceType(
                    serviceResult.getTextDifferences().getDifferenceType());
            }
        }
        if (serviceResult.getTextElementDifferences() != null) {
            comparisonResult.setTextElementDifferences(serviceResult.getTextElementDifferences());
        }
        if (serviceResult.getImageDifferences() != null) {
            comparisonResult.setImageDifferences(serviceResult.getImageDifferences());
        }
        if (serviceResult.getFontDifferences() != null) {
            comparisonResult.setFontDifferences(serviceResult.getFontDifferences());
        }
        
        // Copy page numbers
        comparisonResult.setOriginalBasePageNumber(serviceResult.getOriginalBasePageNumber());
        comparisonResult.setOriginalComparePageNumber(serviceResult.getOriginalComparePageNumber());
        
        return comparisonResult;
    }
}
