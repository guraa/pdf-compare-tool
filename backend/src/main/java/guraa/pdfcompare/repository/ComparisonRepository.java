package guraa.pdfcompare.repository;

import guraa.pdfcompare.model.Comparison;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ComparisonRepository extends JpaRepository<Comparison, String> {
}