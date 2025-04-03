package guraa.pdfcompare.repository;

import guraa.pdfcompare.model.PdfDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PdfRepository extends JpaRepository<PdfDocument, Long> {

    Optional<PdfDocument> findByFileId(String fileId);

    Optional<PdfDocument> findByContentHash(String contentHash);

    boolean existsByFileId(String fileId);

    // Method for cleanup service
    List<PdfDocument> findByUploadDateBefore(LocalDateTime cutoffDate);
}