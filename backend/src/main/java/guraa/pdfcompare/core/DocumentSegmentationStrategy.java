package guraa.pdfcompare.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Strategy for segmenting PDF documents into meaningful chunks
 */
public class DocumentSegmentationStrategy {
    private static final Logger logger = LoggerFactory.getLogger(DocumentSegmentationStrategy.class);

    // Configuration parameters
    private int minDocumentPages = 3;
    private double titleDetectionConfidence = 0.7;

    /**
     * Segment a PDF document into document chunks
     * @param document The PDF document to segment
     * @return List of document segments
     */
    public List<DocumentSegment> segment(PDFDocumentModel document) {
        if (document == null || document.getPages() == null || document.getPages().isEmpty()) {
            logger.warn("Cannot segment null or empty document");
            return new ArrayList<>();
        }

        List<DocumentSegment> segments = new ArrayList<>();
        int currentStart = 0;

        for (int i = 0; i < document.getPageCount(); i++) {
            PDFPageModel page = document.getPages().get(i);

            // Detect potential document start
            if (isDocumentStart(page, document, i)) {
                // Create segment if it meets minimum page requirements
                if (i - currentStart >= minDocumentPages) {
                    DocumentSegment segment = createSegment(document, currentStart, i - 1);
                    segments.add(segment);
                    logger.debug("Created document segment: {}", segment);
                }

                // Reset start for new segment
                currentStart = i;
            }
        }

        // Add final segment if it meets minimum page requirements
        if (document.getPageCount() - currentStart >= minDocumentPages) {
            DocumentSegment finalSegment = createSegment(
                    document,
                    currentStart,
                    document.getPageCount() - 1
            );
            segments.add(finalSegment);
            logger.debug("Created final document segment: {}", finalSegment);
        }

        // If no segments found, treat entire document as one segment
        if (segments.isEmpty()) {
            DocumentSegment wholeDocument = createSegment(document, 0, document.getPageCount() - 1);
            segments.add(wholeDocument);
            logger.debug("Created single segment for entire document: {}", wholeDocument);
        }

        return segments;
    }

    /**
     * Determine if a page is likely the start of a new document
     * @param page Current page
     * @param document Full document model
     * @param pageIndex Current page index
     * @return true if page appears to be a document start
     */
    private boolean isDocumentStart(PDFPageModel page, PDFDocumentModel document, int pageIndex) {
        // First page is always a potential start
        if (pageIndex == 0) return true;

        // Null check for page
        if (page == null) return false;

        // Check for title-like elements
        if (page.getTextElements() != null && !page.getTextElements().isEmpty()) {
            List<TextElement> topElements = page.getTextElements().stream()
                    .filter(el -> el != null && el.getY() < page.getHeight() * 0.3)  // Top 30% of page
                    .sorted(Comparator.comparingDouble(TextElement::getFontSize).reversed())
                    .limit(3)
                    .collect(Collectors.toList());

            // Assess potential title elements
            return topElements.stream()
                    .anyMatch(el ->
                            el != null &&
                                    el.getFontSize() > 14 &&  // Large font
                                    el.getText() != null &&  // Not null text
                                    el.getText().length() > 5 &&  // Not too short
                                    el.getText().length() < 100  // Not too long
                    );
        }

        return false;
    }

    /**
     * Create a document segment with extracted title
     * @param document Source document
     * @param startPage Start page of the segment
     * @param endPage End page of the segment
     * @return Created document segment
     */
    private DocumentSegment createSegment(PDFDocumentModel document, int startPage, int endPage) {
        String title = extractSegmentTitle(document, startPage, endPage);

        DocumentSegment segment = new DocumentSegment(startPage, endPage, title);

        // Initialize features map with some basic info
        Map<String, Object> features = new HashMap<>();
        features.put("pageCount", segment.getPageCount());
        features.put("title", title);

        segment.setFeatures(features);

        return segment;
    }

    /**
     * Extract a title for a document segment
     * @param document Source document
     * @param startPage Start page of the segment
     * @param endPage End page of the segment
     * @return Extracted title
     */
    private String extractSegmentTitle(PDFDocumentModel document, int startPage, int endPage) {
        // Handle possible null document or pages
        if (document == null || document.getPages() == null) {
            return "Untitled Document";
        }

        // Look for title-like elements in the first pages of the segment
        for (int i = startPage; i <= Math.min(endPage, startPage + 1); i++) {
            if (i >= document.getPages().size()) {
                continue;
            }

            PDFPageModel page = document.getPages().get(i);
            if (page == null || page.getTextElements() == null || page.getTextElements().isEmpty()) {
                continue;
            }

            // Find potential title elements with null checks
            List<TextElement> titleCandidates = page.getTextElements().stream()
                    .filter(el ->
                            el != null &&
                                    el.getY() < page.getHeight() * 0.3 &&  // Top 30% of page
                                    el.getFontSize() > 14 &&  // Large font
                                    el.getText() != null &&
                                    el.getText().length() > 5 &&  // Not too short
                                    el.getText().length() < 100  // Not too long
                    )
                    .sorted(Comparator.comparingDouble(TextElement::getFontSize).reversed())
                    .collect(Collectors.toList());

            // Return first suitable title
            if (!titleCandidates.isEmpty() && titleCandidates.get(0).getText() != null) {
                return titleCandidates.get(0).getText();
            }
        }

        // Fallback title
        return "Untitled Document";
    }

    // Configuration methods
    public void setMinDocumentPages(int minPages) {
        this.minDocumentPages = minPages;
    }

    public void setTitleDetectionConfidence(double confidence) {
        this.titleDetectionConfidence = confidence;
    }
}