package guraa.pdfcompare.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Entity representing a PDF comparison.
 */
@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Comparison {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String comparisonId;

    @ManyToOne
    @JoinColumn(name = "base_document_id")
    private PdfDocument baseDocument;

    @ManyToOne
    @JoinColumn(name = "compare_document_id")
    private PdfDocument compareDocument;

    private LocalDateTime startTime;
    private LocalDateTime completionTime;

    @Enumerated(EnumType.STRING)
    private ComparisonStatus status;

    private String resultFilePath;

    @ElementCollection
    @MapKeyColumn(name = "option_key")
    @Column(name = "option_value")
    @CollectionTable(name = "comparison_options", joinColumns = @JoinColumn(name = "comparison_id"))
    private Map<String, String> options = new HashMap<>();

    private boolean smartMatching;
    private String textComparisonMethod;
    private String differenceThreshold;

    // Errors or processing messages
    private String statusMessage;

    // JSON result paths
    private String fullResultPath;
    private String summaryResultPath;

    /**
     * Enumeration of comparison statuses.
     */
    public enum ComparisonStatus {
        PENDING,
        PROCESSING,
        PROCESSING_DOCUMENTS,
        DOCUMENT_MATCHING,
        COMPARING,
        COMPLETED,
        FAILED
    }

    /**
     * Get the base document.
     *
     * @return The base document
     */
    public PdfDocument getBaseDocument() {
        return baseDocument;
    }

    /**
     * Set the base document.
     *
     * @param baseDocument The base document to set
     */
    public void setBaseDocument(PdfDocument baseDocument) {
        this.baseDocument = baseDocument;
    }

    /**
     * Get the comparison document.
     *
     * @return The comparison document
     */
    public PdfDocument getCompareDocument() {
        return compareDocument;
    }

    /**
     * Set the comparison document.
     *
     * @param compareDocument The comparison document to set
     */
    public void setCompareDocument(PdfDocument compareDocument) {
        this.compareDocument = compareDocument;
    }
}