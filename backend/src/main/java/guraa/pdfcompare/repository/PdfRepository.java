package guraa.pdfcompare.repository;

import guraa.pdfcompare.model.PdfDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for PDF documents.
 */
@Repository
public interface PdfRepository extends JpaRepository<PdfDocument, String> {

    /**
     * Find documents by content hash.
     *
     * @param contentHash The content hash to search for
     * @return List of documents with the matching content hash
     */
    List<PdfDocument> findByContentHash(String contentHash);
}