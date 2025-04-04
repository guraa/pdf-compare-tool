package guraa.pdfcompare.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "pdf_document")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PdfDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fileId;
    private String fileName;
    private String filePath;
    private Long fileSize;
    private Integer pageCount;

    @ElementCollection(fetch = FetchType.EAGER)
    @MapKeyColumn(name = "metadata_key")
    @Column(name = "metadata_value", length = 4000) // Increased column length to accommodate longer values
    @CollectionTable(name = "document_metadata", joinColumns = @JoinColumn(name = "document_id"))
    private Map<String, String> metadata = new HashMap<>();

    private LocalDateTime uploadDate;
    private LocalDateTime processedDate;
    private boolean processed;

    // Storing MD5 hash for quick duplicate detection
    private String contentHash;

    // Flag to indicate if the PDF is password-protected
    private boolean passwordProtected;
}