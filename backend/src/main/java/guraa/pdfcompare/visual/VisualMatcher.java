package guraa.pdfcompare.visual;

import guraa.pdfcompare.model.PdfDocument;
import guraa.pdfcompare.service.PagePair;

import java.io.IOException;
import java.util.List;

/**
 * Interface for visual matching of PDF pages.
 * Implementations of this interface provide algorithms for matching
 * pages between two PDF documents based on visual similarity.
 */
public interface VisualMatcher {

    /**
     * Match pages between two PDF documents.
     *
     * @param baseDocument The base document
     * @param compareDocument The document to compare against the base
     * @return A list of page pairs
     * @throws IOException If there is an error matching the pages
     */
    List<PagePair> matchPages(PdfDocument baseDocument, PdfDocument compareDocument) throws IOException;
}