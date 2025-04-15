package guraa.pdfcompare.core;

import guraa.pdfcompare.model.PdfDocument;
import guraa.pdfcompare.service.PagePair;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Interface for document matching strategies.
 * Implementations of this interface provide algorithms for matching
 * pages between two PDF documents.
 */
public interface DocumentMatchingStrategy {

    /**
     * Match pages between two PDF documents.
     *
     * @param baseDocument The base document
     * @param compareDocument The document to compare against the base
     * @param options Additional options for the matching algorithm
     * @return A list of page pairs
     * @throws IOException If there is an error matching the documents
     */
    List<PagePair> matchDocuments(PdfDocument baseDocument, PdfDocument compareDocument, Map<String, Object> options) throws IOException;

    /**
     * Get the name of this matching strategy.
     *
     * @return The strategy name
     */
    String getStrategyName();

    /**
     * Get the confidence level of the matching.
     * This is a value between 0.0 and 1.0 that indicates how confident
     * the strategy is in the matching it produced.
     *
     * @return The confidence level
     */
    double getConfidenceLevel();
}
