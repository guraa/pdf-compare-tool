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
    @MapKeyColumn(name = "key")
    @Column(name = "value")
    @CollectionTable(name = "pdf_metadata", joinColumns = @JoinColumn(name = "pdf_id"))
    private Map<String, String> metadata = new HashMap<>();

    private LocalDateTime uploadDate;
    private LocalDateTime processedDate;
    private boolean processed;

    // Storing MD5 hash for quick duplicate detection
    private String contentHash;

    // Flag to indicate if the PDF is password-protected
    private boolean passwordProtected;
}