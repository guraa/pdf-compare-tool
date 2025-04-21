package guraa.pdfcompare.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
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
    private ComparisonStatus status;

    /**
     * The error message, if any.
     */
    @Column(name = "error_message", length = 2000)
    private String errorMessage;
    
    /**
     * The current progress of the comparison (0-100).
     */
    @Column(name = "progress", nullable = false, columnDefinition = "INTEGER DEFAULT 0")
    private int progress = 0;
    
    /**
     * The total number of operations to be performed.
     */
    @Column(name = "total_operations", nullable = false, columnDefinition = "INTEGER DEFAULT 0")
    private int totalOperations = 0;
    
    /**
     * The number of completed operations.
     */
    @Column(name = "completed_operations", nullable = false, columnDefinition = "INTEGER DEFAULT 0")
    private int completedOperations = 0;
    
    /**
     * The current phase of the comparison.
     */
    @Column(name = "current_phase", length = 100, columnDefinition = "VARCHAR(100) DEFAULT 'Initializing'")
    private String currentPhase = "Initializing";

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
     * @param status The status as a string
     */
    public void setStatus(String status) {
        if (status == null || status.isEmpty()) {
            throw new IllegalArgumentException("Status cannot be null or empty");
        }
        try {
            this.status = ComparisonStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid status: " + status);
        }
    }

    /**
     * Set the status of the comparison.
     *
     * @param status The status
     */
    public void setStatus(ComparisonStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("Status cannot be null");
        }
        this.status = status;
    }

    /**
     * Get the status of the comparison as a string.
     *
     * @return The status as a string
     */
    public String getStatusAsString() {
        return status != null ? status.name() : null;
    }

    /**
     * Pre-persist hook to set the created and updated times.
     */
    @PrePersist
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
    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
