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
@Table(name = "comparison")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Comparison {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
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

    @Column(length = 2000)
    private String resultFilePath;

    @ElementCollection
    @MapKeyColumn(name = "option_key")
    @Column(name = "option_value", length = 2000) // Increased column length
    @CollectionTable(name = "comparison_options", joinColumns = @JoinColumn(name = "comparison_id"))
    private Map<String, String> options = new HashMap<>();

    private boolean smartMatching;

    @Column(length = 50)
    private String textComparisonMethod;

    @Column(length = 50)
    private String differenceThreshold;

    // Errors or processing messages
    @Column(length = 2000)
    private String statusMessage;

    // JSON result paths
    @Column(length = 2000)
    private String fullResultPath;

    @Column(length = 2000)
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
}