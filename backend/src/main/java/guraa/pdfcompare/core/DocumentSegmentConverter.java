package guraa.pdfcompare.core;

/**
 * Utility class for converting between new and legacy document segments
 */
public class DocumentSegmentConverter {
    /**
     * Inner class representing a legacy document segment
     * to maintain compatibility with existing code
     */
    public static class LegacyDocumentSegment {
        private int startPage;
        private int endPage;
        private String title;
        private java.util.Map<String, Object> features;

        public LegacyDocumentSegment(int startPage, int endPage, String title) {
            this.startPage = startPage;
            this.endPage = endPage;
            this.title = title;
            this.features = new java.util.HashMap<>();
        }

        public int getStartPage() {
            return startPage;
        }

        public int getEndPage() {
            return endPage;
        }

        public String getTitle() {
            return title;
        }

        public java.util.Map<String, Object> getFeatures() {
            return features;
        }

        public void setFeatures(java.util.Map<String, Object> features) {
            this.features = features;
        }
    }

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