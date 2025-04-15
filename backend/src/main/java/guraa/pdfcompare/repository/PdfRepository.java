package guraa.pdfcompare.repository;

import guraa.pdfcompare.model.PdfDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for PDF documents.
 */
@Repository
public interface PdfRepository extends JpaRepository<PdfDocument, String> {
}
