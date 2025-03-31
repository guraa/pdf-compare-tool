package guraa.pdfcompare.core;

import guraa.pdfcompare.core.SmartDocumentMatcher.DocumentSegment as LegacyDocumentSegment;

/**
 * Utility class for converting between new and legacy document segments
 */
public class DocumentSegmentConverter {
    /**
     * Convert new DocumentSegment to legacy DocumentSegment
     * @param newSegment New document segment
     * @return Converted legacy document segment
     */
    public static LegacyDocumentSegment toLegacySegment(DocumentSegment newSegment) {
        if (newSegment == null) return null;

        LegacyDocumentSegment legacySegment = new LegacyDocumentSegment(
                newSegment.getStartPage(),
                newSegment.getEndPage(),
                newSegment.getTitle()
        );
        legacySegment.setFeatures(newSegment.getFeatures());
        return legacySegment;
    }

    /**
     * Convert legacy DocumentSegment to new DocumentSegment
     * @param legacySegment Legacy document segment
     * @return Converted new document segment
     */
    public static DocumentSegment toNewSegment(LegacyDocumentSegment legacySegment) {
        if (legacySegment == null) return null;

        DocumentSegment newSegment = new DocumentSegment(
                legacySegment.getStartPage(),
                legacySegment.getEndPage(),
                legacySegment.getTitle()
        );
        newSegment.setFeatures(legacySegment.getFeatures());
        return newSegment;
    }
}