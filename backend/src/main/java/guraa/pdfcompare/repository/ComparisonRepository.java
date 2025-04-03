package guraa.pdfcompare.repository;


import guraa.pdfcompare.model.Comparison;
import guraa.pdfcompare.model.PdfDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ComparisonRepository extends JpaRepository<Comparison, Long> {

    Optional<Comparison> findByComparisonId(String comparisonId);

    List<Comparison> findByBaseDocumentOrCompareDocument(PdfDocument baseDocument, PdfDocument compareDocument);

    List<Comparison> findByBaseDocumentAndCompareDocument(PdfDocument baseDocument, PdfDocument compareDocument);

    List<Comparison> findByStatus(Comparison.ComparisonStatus status);

    // Methods for cleanup service
    List<Comparison> findByCompletionTimeBefore(LocalDateTime cutoffDate);

    List<Comparison> findByCompletionTimeAfter(LocalDateTime cutoffDate);
}