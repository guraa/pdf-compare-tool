package guraa.pdfcompare.service;

import guraa.pdfcompare.core.PageFingerprint;

/**
 * Adapter class for working with PageFingerprint objects.
 * Provides utility methods for extracting information from fingerprints.
 */
public class FingerprintAdapter {
    
    /**
     * Get the page index from a fingerprint
     * @param fingerprint The page fingerprint
     * @return The page index or -1 if fingerprint is null
     */
    public static int getPageIndex(PageFingerprint fingerprint) {
        if (fingerprint == null) {
            return -1;
        }
        return fingerprint.getPageIndex();
    }
    
    /**
     * Create a new fingerprint with the specified source type and page index
     * @param sourceType Source type (base or compare)
     * @param pageIndex Page index
     * @return New fingerprint
     */
    public static PageFingerprint createFingerprint(String sourceType, int pageIndex) {
        return new PageFingerprint(sourceType, pageIndex);
    }
    
    /**
     * Check if the fingerprint is from the base document
     * @param fingerprint The page fingerprint
     * @return true if the fingerprint is from the base document
     */
    public static boolean isBaseFingerprint(PageFingerprint fingerprint) {
        if (fingerprint == null) {
            return false;
        }
        return "base".equals(fingerprint.getSourceType());
    }
    
    /**
     * Check if the fingerprint is from the compare document
     * @param fingerprint The page fingerprint
     * @return true if the fingerprint is from the compare document
     */
    public static boolean isCompareFingerprint(PageFingerprint fingerprint) {
        if (fingerprint == null) {
            return false;
        }
        return "compare".equals(fingerprint.getSourceType());
    }
}
