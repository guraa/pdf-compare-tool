package guraa.pdfcompare.service;

import guraa.pdfcompare.model.DocumentPair;
import guraa.pdfcompare.model.PdfDocument;
import guraa.pdfcompare.util.TextExtractor;
import guraa.pdfcompare.util.DifferenceCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentMatchingService {

    private final TextExtractor textExtractor;
    private final DifferenceCalculator differenceCalculator;

    // Constants for document matching
    private static final double TEXT_SIMILARITY_THRESHOLD = 0.5;
    private static final double VISUAL_SIMILARITY_THRESHOLD = 0.6;
    private static final int MIN_PAGES_FOR_DOCUMENT = 1;
    private static final int MAX_SAMPLE_PAGES = 3; // For performance when comparing large documents

    /**
     * Match documents between base and comparison PDFs.
     *
     * @param baseDocument The base document
     * @param compareDocument The comparison document
     * @return List of document pairs
     * @throws IOException If there's an error processing the documents
     */
    public List<DocumentPair> matchDocuments(PdfDocument baseDocument, PdfDocument compareDocument)
            throws IOException {
        // Load PDF documents
        PDDocument basePdf = PDDocument.load(new File(baseDocument.getFilePath()));
        PDDocument comparePdf = PDDocument.load(new File(compareDocument.getFilePath()));

        try {
            List<DocumentPair> documentPairs = new ArrayList<>();

            // Extract text contents for each page and render sample pages for visual comparison
            List<String> baseTexts = extractPageTexts(basePdf);
            List<String> compareTexts = extractPageTexts(comparePdf);

            // Create page renderers
            PDFRenderer baseRenderer = new PDFRenderer(basePdf);
            PDFRenderer compareRenderer = new PDFRenderer(comparePdf);

            // Identify document boundaries in each PDF
            List<DocumentBoundary> baseDocuments = identifyDocumentBoundaries(baseTexts);
            List<DocumentBoundary> compareDocuments = identifyDocumentBoundaries(compareTexts);

            log.info("Identified {} documents in base PDF and {} in comparison PDF",
                    baseDocuments.size(), compareDocuments.size());

            // Match documents between base and compare PDFs using combined text and visual features
            List<DocumentMatch> matches = matchDocumentBoundaries(baseDocuments, compareDocuments,
                    baseTexts, compareTexts, basePdf, comparePdf, baseRenderer, compareRenderer);

            // Create document pairs from matches
            int pairIndex = 0;

            // First, handle matched documents
            for (DocumentMatch match : matches) {
                DocumentBoundary baseBoundary = baseDocuments.get(match.getBaseDocumentIndex());
                DocumentBoundary compareBoundary = compareDocuments.get(match.getCompareDocumentIndex());

                // Create document pair for matched documents
                DocumentPair pair = DocumentPair.builder()
                        .pairIndex(pairIndex++)
                        .matched(true)
                        .baseStartPage(baseBoundary.getStartPage() + 1) // Convert to 1-based
                        .baseEndPage(baseBoundary.getEndPage() + 1)     // Convert to 1-based
                        .basePageCount(baseBoundary.getEndPage() - baseBoundary.getStartPage() + 1)
                        .compareStartPage(compareBoundary.getStartPage() + 1) // Convert to 1-based
                        .compareEndPage(compareBoundary.getEndPage() + 1)     // Convert to 1-based
                        .comparePageCount(compareBoundary.getEndPage() - compareBoundary.getStartPage() + 1)
                        .similarityScore(match.getSimilarityScore())
                        .hasBaseDocument(true)
                        .hasCompareDocument(true)
                        .build();

                // Create page mappings
                createPageMappings(pair, baseBoundary, compareBoundary, baseTexts, compareTexts,
                        basePdf, comparePdf, baseRenderer, compareRenderer);

                documentPairs.add(pair);

                // Mark these documents as processed
                baseBoundary.setMatched(true);
                compareBoundary.setMatched(true);
            }

            // Handle unmatched base documents
            for (int i = 0; i < baseDocuments.size(); i++) {
                DocumentBoundary boundary = baseDocuments.get(i);
                if (!boundary.isMatched()) {
                    DocumentPair pair = DocumentPair.builder()
                            .pairIndex(pairIndex++)
                            .matched(false)
                            .baseStartPage(boundary.getStartPage() + 1) // Convert to 1-based
                            .baseEndPage(boundary.getEndPage() + 1)     // Convert to 1-based
                            .basePageCount(boundary.getEndPage() - boundary.getStartPage() + 1)
                            .hasBaseDocument(true)
                            .hasCompareDocument(false)
                            .similarityScore(0.0)
                            .build();

                    documentPairs.add(pair);
                }
            }

            // Handle unmatched compare documents
            for (int i = 0; i < compareDocuments.size(); i++) {
                DocumentBoundary boundary = compareDocuments.get(i);
                if (!boundary.isMatched()) {
                    DocumentPair pair = DocumentPair.builder()
                            .pairIndex(pairIndex++)
                            .matched(false)
                            .compareStartPage(boundary.getStartPage() + 1) // Convert to 1-based
                            .compareEndPage(boundary.getEndPage() + 1)     // Convert to 1-based
                            .comparePageCount(boundary.getEndPage() - boundary.getStartPage() + 1)
                            .hasBaseDocument(false)
                            .hasCompareDocument(true)
                            .similarityScore(0.0)
                            .build();

                    documentPairs.add(pair);
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
     * Identify document boundaries within a PDF.
     *
     * @param pageTexts List of text contents for each page
     * @return List of document boundaries
     */
    /**
     * Identify document boundaries within a PDF.
     *
     * @param pageTexts List of text contents for each page
     * @return List of document boundaries
     */
    private List<DocumentBoundary> identifyDocumentBoundaries(List<String> pageTexts) {
        List<DocumentBoundary> boundaries = new ArrayList<>();

        // If the PDF is empty, return an empty list
        if (pageTexts.isEmpty()) {
            return boundaries;
        }

        // If there's only one page, return it as a single document
        if (pageTexts.size() == 1) {
            boundaries.add(new DocumentBoundary(0, 0));
            return boundaries;
        }

        // Algorithm to identify document boundaries
        int currentStart = 0;
        Map<String, Double> documentFingerprints = new HashMap<>();

        // Calculate fingerprint for first page
        documentFingerprints.put(getPageFingerprint(pageTexts.get(0)), 1.0);

        for (int i = 1; i < pageTexts.size(); i++) {
            String currentText = pageTexts.get(i);
            String prevText = pageTexts.get(i - 1);

            // Check if this page likely starts a new document
            if (isNewDocumentStart(currentText, prevText, pageTexts, i)) {
                // Create boundary for previous document
                boundaries.add(new DocumentBoundary(currentStart, i - 1));
                currentStart = i;

                // Add fingerprint for the new document's first page
                documentFingerprints.put(getPageFingerprint(currentText), 1.0);
            }
        }

        // Add the last document
        boundaries.add(new DocumentBoundary(currentStart, pageTexts.size() - 1));

        return boundaries;