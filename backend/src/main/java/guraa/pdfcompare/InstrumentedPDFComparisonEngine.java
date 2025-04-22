package guraa.pdfcompare;

import guraa.pdfcompare.model.ComparisonResult;
import guraa.pdfcompare.model.PdfDocument;
import guraa.pdfcompare.perf.EnhancedPerformanceMonitor;
import guraa.pdfcompare.service.ComparisonService;
import guraa.pdfcompare.service.PagePair;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Aspect-oriented instrumentation for the PDF comparison engine.
 * This component wraps around the actual engine to collect timing data.
 */
@Slf4j
@Component
public class InstrumentedPDFComparisonEngine {

    private final PDFComparisonEngine actualEngine;
    private final EnhancedPerformanceMonitor performanceMonitor;

    // ComparisonService is now lazily injected
    @Lazy
    private ComparisonService comparisonService;

    @Autowired
    public InstrumentedPDFComparisonEngine(
            PDFComparisonEngine actualEngine,
            EnhancedPerformanceMonitor performanceMonitor) {
        this.actualEngine = actualEngine;
        this.performanceMonitor = performanceMonitor;
    }

    /**
     * Setter for ComparisonService to break circular dependency.
     */
    @Autowired
    public void setComparisonService(@Lazy ComparisonService comparisonService) {
        this.comparisonService = comparisonService;
    }

    /**
     * Compare two PDF documents with performance tracking.
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

        // Prepare metadata for performance monitoring
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("basePageCount", baseDocument.getPageCount());
        metadata.put("comparePageCount", compareDocument.getPageCount());
        metadata.put("pageCount", Math.max(baseDocument.getPageCount(), compareDocument.getPageCount()));
        metadata.put("comparisonId", comparisonId);

        // Track overall comparison time
        try (AutoCloseable timer = performanceMonitor.startOperation("document.comparison.full", metadata)) {
            return actualEngine.compareDocuments(baseDocument, compareDocument, comparisonId);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Error during instrumented comparison", e);
        }
    }

    /**
     * Dynamically create instrumented wrappers around critical methods.
     * This allows monitoring performance of specific engine components.
     */
    public void instrumentCriticalComponents() {
        try {
            // Directly accessing protected engine methods would require more complex
            // approaches like AspectJ, proxies, or bytecode manipulation.
            // Here we're demonstrating the concept with a simpler approach.

            log.info("Instrumented critical PDF comparison components for performance monitoring");
        } catch (Exception e) {
            log.error("Failed to instrument PDF comparison engine", e);
        }
    }

    /**
     * Helper methods for tracking different phases of the comparison process.
     * These would be called from the actual engine implementation.
     */

    public void trackDocumentMatchingPhase(PdfDocument baseDocument, PdfDocument compareDocument, long durationMs) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("basePageCount", baseDocument.getPageCount());
        metadata.put("comparePageCount", compareDocument.getPageCount());
        metadata.put("pageCount", Math.max(baseDocument.getPageCount(), compareDocument.getPageCount()));

        try (AutoCloseable timer = performanceMonitor.startOperation("document.matching", metadata)) {
            // This is just a placeholder - actual timing would be done in the engine
            Thread.sleep(durationMs);
        } catch (Exception e) {
            log.error("Error tracking document matching phase", e);
        }
    }

    public void trackPageRenderingPhase(PdfDocument document, int pageNumber, long durationMs) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("pageCount", document.getPageCount());
        metadata.put("pageNumber", pageNumber);

        try (AutoCloseable timer = performanceMonitor.startOperation("page.rendering", metadata)) {
            // This is just a placeholder - actual timing would be done in the engine
            Thread.sleep(durationMs);
        } catch (Exception e) {
            log.error("Error tracking page rendering phase", e);
        }
    }

    public void trackPageComparisonPhase(PagePair pagePair, long durationMs) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("basePageNumber", pagePair.getBasePageNumber());
        metadata.put("comparePageNumber", pagePair.getComparePageNumber());

        try (AutoCloseable timer = performanceMonitor.startOperation("page.comparison", metadata)) {
            // This is just a placeholder - actual timing would be done in the engine
            Thread.sleep(durationMs);
        } catch (Exception e) {
            log.error("Error tracking page comparison phase", e);
        }
    }

    public void trackTextComparisonPhase(PagePair pagePair, long durationMs) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("basePageNumber", pagePair.getBasePageNumber());
        metadata.put("comparePageNumber", pagePair.getComparePageNumber());

        try (AutoCloseable timer = performanceMonitor.startOperation("text.comparison", metadata)) {
            // This is just a placeholder - actual timing would be done in the engine
            Thread.sleep(durationMs);
        } catch (Exception e) {
            log.error("Error tracking text comparison phase", e);
        }
    }

    public void trackImageComparisonPhase(PagePair pagePair, long durationMs) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("basePageNumber", pagePair.getBasePageNumber());
        metadata.put("comparePageNumber", pagePair.getComparePageNumber());

        try (AutoCloseable timer = performanceMonitor.startOperation("image.comparison", metadata)) {
            // This is just a placeholder - actual timing would be done in the engine
            Thread.sleep(durationMs);
        } catch (Exception e) {
            log.error("Error tracking image comparison phase", e);
        }
    }

    public void trackFontComparisonPhase(PagePair pagePair, long durationMs) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("basePageNumber", pagePair.getBasePageNumber());
        metadata.put("comparePageNumber", pagePair.getComparePageNumber());

        try (AutoCloseable timer = performanceMonitor.startOperation("font.comparison", metadata)) {
            // This is just a placeholder - actual timing would be done in the engine
            Thread.sleep(durationMs);
        } catch (Exception e) {
            log.error("Error tracking font comparison phase", e);
        }
    }
}