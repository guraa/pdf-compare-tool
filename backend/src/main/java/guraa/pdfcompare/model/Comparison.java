package guraa.pdfcompare.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;
import javax.persistence.Transient;
import java.time.LocalDateTime;

/**
 * Represents a comparison between two PDF documents.
 * This class stores information about the comparison,
 * such as the documents being compared, the status, and the result.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "comparisons")
public class Comparison {

    /**
     * Unique identifier for this comparison.
     */
    @Id
    private String id;

    /**
     * The ID of the base document.
     */
    @Column(name = "base_document_id", nullable = false)
    private String baseDocumentId;

    /**
     * The ID of the document being compared against the base.
     */
    @Column(name = "compare_document_id", nullable = false)
    private String compareDocumentId;

    /**
     * The status of the comparison.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    public ComparisonStatus status;

    /**
     * The error message, if any.
     */
    @Column(name = "error_message")
    private String errorMessage;

    /**
     * The time the comparison was created.
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * The time the comparison was last updated.
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * The result of the comparison.
     * This is not stored in the database, but is populated when needed.
     */
    @Transient
    private ComparisonResult result;

    /**
     * The status of a comparison.
     */
    public enum ComparisonStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    /**
     * Set the status of the comparison.
     *
     * @param status The status
     */
    public void setStatus(String status) {
        this.status = ComparisonStatus.valueOf(status.toUpperCase());
    }

    /**
     * Get the status of the comparison as a string.
     *
     * @return The status as a string
     */
    public String getStatusAsString() {
        return status != null ? status.name().toLowerCase() : null;
    }

    /**
     * Pre-persist hook to set the created and updated times.
     */
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    /**
     * Pre-update hook to set the updated time.
     */
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
