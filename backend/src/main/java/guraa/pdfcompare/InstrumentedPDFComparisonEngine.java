package guraa.pdfcompare;

import guraa.pdfcompare.model.ComparisonResult;
import guraa.pdfcompare.model.PdfDocument;
import guraa.pdfcompare.service.ComparisonService;
import guraa.pdfcompare.service.PagePair;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Wrapper for the PDF comparison engine.
 * This component wraps around the actual engine.
 */
@Slf4j
@Component
public class InstrumentedPDFComparisonEngine {

    private final PDFComparisonEngine actualEngine;

    // ComparisonService is now lazily injected
    @Lazy
    private ComparisonService comparisonService;

    @Autowired
    public InstrumentedPDFComparisonEngine(PDFComparisonEngine actualEngine) {
        this.actualEngine = actualEngine;
    }

    /**
     * Setter for ComparisonService to break circular dependency.
     */
    @Autowired
    public void setComparisonService(@Lazy ComparisonService comparisonService) {
        this.comparisonService = comparisonService;
    }

    /**
     * Compare two PDF documents.
     *
     * @param baseDocument The base document
     * @param compareDocument The document to compare against the base
     * @param comparisonId The comparison ID (optional)
     * @return The comparison result
     * @throws IOException If there is an error comparing the documents
     */
    public ComparisonResult compareDocuments(
            PdfDocument baseDocument,
            PdfDocument compareDocument,
            String comparisonId) throws IOException {

        try {
            return actualEngine.compareDocuments(baseDocument, compareDocument, comparisonId);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Error during instrumented comparison", e);
        }
    }

    /**
     * Placeholder for future instrumentation.
     */
    public void instrumentCriticalComponents() {
        try {
            log.info("Instrumentation of PDF comparison components is disabled");
        } catch (Exception e) {
            log.error("Failed to instrument PDF comparison engine", e);
        }
    }
}
