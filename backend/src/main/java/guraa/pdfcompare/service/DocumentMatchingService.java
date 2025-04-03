package guraa.pdfcompare.service;

import guraa.pdfcompare.model.DocumentPair;
import guraa.pdfcompare.model.PdfDocument;
import guraa.pdfcompare.util.TextExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for matching documents between PDFs.
 * This is a high-level service that coordinates document detection, matching, and page mapping.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentMatchingService {

    private final TextExtractor textExtractor;
    private final DocumentDetector documentDetector;
    private final DocumentMatcher documentMatcher;
    private final PageMatcher pageMatcher;

    @Value("${app.comparison.min-pages-per-document:1}")
    private int minPagesPerDocument;

    /**
     * Match documents between base and comparison PDFs.
     *
     * @param baseDocument    The base document
     * @param compareDocument The comparison document
     * @return List of document pairs
     * @throws IOException If there's an error processing the documents
     */
    public List<DocumentPair> matchDocuments(PdfDocument baseDocument, PdfDocument compareDocument)
            throws IOException {
        log.info("Starting document matching between {} and {}",
                baseDocument.getFileName(), compareDocument.getFileName());

        // Load PDF documents
        PDDocument basePdf = PDDocument.load(new File(baseDocument.getFilePath()));
        PDDocument comparePdf = PDDocument.load(new File(compareDocument.getFilePath()));

        try {
            List<DocumentPair> documentPairs = new ArrayList<>();

            // Extract text contents for each page to use in matching
            List<String> baseTexts = extractPageTexts(basePdf);
            List<String> compareTexts = extractPageTexts(comparePdf);

            log.debug("Extracted text from {} base pages and {} compare pages",
                    baseTexts.size(), compareTexts.size());

            // Create page renderers for visual matching
            PDFRenderer baseRenderer = new PDFRenderer(basePdf);
            PDFRenderer compareRenderer = new PDFRenderer(comparePdf);

            // Identify document boundaries in each PDF
            List<DocumentBoundary> baseDocuments = documentDetector.identifyDocumentBoundaries(baseTexts);
            List<DocumentBoundary> compareDocuments = documentDetector.identifyDocumentBoundaries(compareTexts);

            log.info("Identified {} documents in base PDF and {} in comparison PDF",
                    baseDocuments.size(), compareDocuments.size());

            // Log details of identified documents
            logDocumentBoundaries("Base", baseDocuments, baseTexts);
            logDocumentBoundaries("Compare", compareDocuments, compareTexts);

            // Match documents between base and compare PDFs
            List<DocumentMatch> matches = documentMatcher.matchDocuments(
                    baseDocuments, compareDocuments,
                    baseTexts, compareTexts,
                    basePdf, comparePdf,
                    baseRenderer, compareRenderer);

            log.info("Found {} document matches", matches.size());

            // Create directory for visualizations if debugging is enabled
            Path visualDir = Paths.get("uploads", "debug", "visual_matching");
            if (log.isDebugEnabled()) {
                visualDir.toFile().mkdirs();
            }

            // Create document pairs from matches
            int pairIndex = 0;

            // First, handle matched documents
            for (DocumentMatch match : matches) {
                DocumentBoundary baseBoundary = baseDocuments.get(match.getBaseDocumentIndex());
                DocumentBoundary compareBoundary = compareDocuments.get(match.getCompareDocumentIndex());

                // Create document pair for matched documents
                DocumentPair pair = createDocumentPair(
                        pairIndex++, true, baseBoundary, compareBoundary, match.getSimilarityScore());

                // Create page mappings between the two documents
                pageMatcher.createPageMappings(pair, baseBoundary, compareBoundary, baseTexts, compareTexts,
                        basePdf, comparePdf, baseRenderer, compareRenderer);

                documentPairs.add(pair);

                // Mark these documents as matched
                baseBoundary.setMatched(true);
                compareBoundary.setMatched(true);

                log.debug("Created document pair {}: Base pages {}-{}, Compare pages {}-{}, Similarity {}",
                        pairIndex - 1, pair.getBaseStartPage(), pair.getBaseEndPage(),
                        pair.getCompareStartPage(), pair.getCompareEndPage(),
                        String.format("%.2f", pair.getSimilarityScore()));
            }

            // Handle unmatched base documents
            for (int i = 0; i < baseDocuments.size(); i++) {
                DocumentBoundary boundary = baseDocuments.get(i);
                if (!boundary.isMatched()) {
                    DocumentPair pair = createBaseOnlyDocumentPair(pairIndex++, boundary);
                    documentPairs.add(pair);

                    log.debug("Created unmatched base document pair {}: Base pages {}-{}",
                            pairIndex - 1, pair.getBaseStartPage(), pair.getBaseEndPage());
                }
            }

            // Handle unmatched compare documents
            for (int i = 0; i < compareDocuments.size(); i++) {
                DocumentBoundary boundary = compareDocuments.get(i);
                if (!boundary.isMatched()) {
                    DocumentPair pair = createCompareOnlyDocumentPair(pairIndex++, boundary);
                    documentPairs.add(pair);

                    log.debug("Created unmatched compare document pair {}: Compare pages {}-{}",
                            pairIndex - 1, pair.getCompareStartPage(), pair.getCompareEndPage());
                }
            }

            return documentPairs;
        } finally {
            // Close PDFs
            basePdf.close();
            comparePdf.close();
        }
    }

    /**
     * Create a document pair for matched documents.
     */
    private DocumentPair createDocumentPair(
            int pairIndex, boolean matched,
            DocumentBoundary baseBoundary, DocumentBoundary compareBoundary,
            double similarityScore) {

        return DocumentPair.builder()
                .pairIndex(pairIndex)
                .matched(matched)
                .baseStartPage(baseBoundary.getStartPage() + 1) // Convert to 1-based
                .baseEndPage(baseBoundary.getEndPage() + 1)     // Convert to 1-based
                .basePageCount(baseBoundary.getEndPage() - baseBoundary.getStartPage() + 1)
                .compareStartPage(compareBoundary.getStartPage() + 1) // Convert to 1-based
                .compareEndPage(compareBoundary.getEndPage() + 1)     // Convert to 1-based
                .comparePageCount(compareBoundary.getEndPage() - compareBoundary.getStartPage() + 1)
                .similarityScore(similarityScore)
                .hasBaseDocument(true)
                .hasCompareDocument(true)
                .build();
    }

    /**
     * Create a document pair for a document that only exists in the base PDF.
     */
    private DocumentPair createBaseOnlyDocumentPair(int pairIndex, DocumentBoundary boundary) {
        return DocumentPair.builder()
                .pairIndex(pairIndex)
                .matched(false)
                .baseStartPage(boundary.getStartPage() + 1) // Convert to 1-based
                .baseEndPage(boundary.getEndPage() + 1)     // Convert to 1-based
                .basePageCount(boundary.getEndPage() - boundary.getStartPage() + 1)
                .hasBaseDocument(true)
                .hasCompareDocument(false)
                .similarityScore(0.0)
                .build();
    }

    /**
     * Create a document pair for a document that only exists in the comparison PDF.
     */
    private DocumentPair createCompareOnlyDocumentPair(int pairIndex, DocumentBoundary boundary) {
        return DocumentPair.builder()
                .pairIndex(pairIndex)
                .matched(false)
                .compareStartPage(boundary.getStartPage() + 1) // Convert to 1-based
                .compareEndPage(boundary.getEndPage() + 1)     // Convert to 1-based
                .comparePageCount(boundary.getEndPage() - boundary.getStartPage() + 1)
                .hasBaseDocument(false)
                .hasCompareDocument(true)
                .similarityScore(0.0)
                .build();
    }

    /**
     * Extract text from each page of a PDF document.
     *
     * @param document The PDF document
     * @return List of text contents for each page
     * @throws IOException If there's an error extracting text
     */
    private List<String> extractPageTexts(PDDocument document) throws IOException {
        List<String> pageTexts = new ArrayList<>();

        for (int i = 0; i < document.getNumberOfPages(); i++) {
            String text = textExtractor.extractTextFromPage(document, i);
            pageTexts.add(text);
        }

        return pageTexts;
    }

    /**
     * Log information about identified document boundaries.
     *
     * @param prefix Label for the document type (Base or Compare)
     * @param boundaries List of document boundaries
     * @param pageTexts List of page texts
     */
    private void logDocumentBoundaries(String prefix, List<DocumentBoundary> boundaries, List<String> pageTexts) {
        for (int i = 0; i < boundaries.size(); i++) {
            DocumentBoundary boundary = boundaries.get(i);
            int startPage = boundary.getStartPage() + 1; // Convert to 1-based
            int endPage = boundary.getEndPage() + 1;     // Convert to 1-based
            int pageCount = boundary.getEndPage() - boundary.getStartPage() + 1;

            // Extract first line of text from first page as a title hint
            String firstPage = pageTexts.get(boundary.getStartPage());
            String title = firstPage.trim().split("\\r?\\n")[0];
            if (title.length() > 50) {
                title = title.substring(0, 47) + "...";
            }

            log.debug("{} Document #{}: Pages {}-{} ({} pages), Title hint: \"{}\"",
                    prefix, i + 1, startPage, endPage, pageCount, title);
        }
    }
}