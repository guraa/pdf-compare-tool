package guraa.pdfcompare.comparison;

/**
 * Enum representing the type of text difference.
 */
public enum TextDifferenceType {
    /**
     * Text was added in the compare document
     */
    ADDITION,
    
    /**
     * Text was deleted from the base document
     */
    DELETION,
    
    /**
     * Text was modified between the base and compare documents
     */
    MODIFIED;
    
    /**
     * Text was added in the compare document (alias for ADDITION)
     */
    public static final TextDifferenceType ADDED = ADDITION;
    
    /**
     * Text was deleted from the base document (alias for DELETION)
     */
    public static final TextDifferenceType DELETED = DELETION;
}
