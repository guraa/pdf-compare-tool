package guraa.pdfcompare.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for extracting features from PDF documents with fallback mechanisms
 */
public class DocumentFeaturesExtractor {
    private static final Logger logger = LoggerFactory.getLogger(DocumentFeaturesExtractor.class);

    /**
     * Extract basic features from a document segment with fallbacks for failures
     * @param document The source PDF document
     * @param startPage Start page index
     * @param endPage End page index
     * @return Map of document features
     */
    public static Map<String, Object> extractBasicFeatures(PDFDocumentModel document, int startPage, int endPage) {
        Map<String, Object> features = new HashMap<>();

        try {
            // Add basic info
            features.put("pageCount", endPage - startPage + 1);

            // Extract title if possible
            String title = extractDocumentTitle(document, startPage, endPage);
            features.put("title", title);

            // Extract full text with fallback
            String fullText = extractFullText(document, startPage, endPage);
            features.put("fullText", fullText);

            // Add layout information if available
            try {
                features.put("dimensions", extractPageDimensions(document, startPage, endPage));
            } catch (Exception e) {
                logger.warn("Failed to extract page dimensions: {}", e.getMessage());
                features.put("dimensions", new float[][]{{0,0}});
            }

            // Add image count if available
            try {
                features.put("imageCount", countImages(document, startPage, endPage));
            } catch (Exception e) {
                logger.warn("Failed to count images: {}", e.getMessage());
                features.put("imageCount", 0);
            }

            logger.debug("Successfully extracted features for pages {}-{}", startPage, endPage);
        } catch (Exception e) {
            logger.error("Error extracting document features: {}", e.getMessage(), e);
            // Ensure minimum features are always available
            features.put("pageCount", endPage - startPage + 1);
            features.put("title", "Untitled Document");
            features.put("fullText", "");
            features.put("error", e.getMessage());
        }

        return features;
    }

    /**
     * Extract document title with fallback for errors
     */
    private static String extractDocumentTitle(PDFDocumentModel document, int startPage, int endPage) {
        try {
            // Look for title-like elements in the first page
            if (document != null && document.getPages() != null &&
                    startPage < document.getPages().size() && document.getPages().get(startPage) != null) {

                PDFPageModel firstPage = document.getPages().get(startPage);

                if (firstPage.getTextElements() != null && !firstPage.getTextElements().isEmpty()) {
                    // Find the largest text near top
                    return firstPage.getTextElements().stream()
                            .filter(el -> el != null &&
                                    el.getY() < firstPage.getHeight() * 0.3 &&
                                    el.getText() != null &&
                                    el.getText().length() > 3)
                            .sorted((a, b) -> Float.compare(b.getFontSize(), a.getFontSize()))
                            .findFirst()
                            .map(TextElement::getText)
                            .orElse("Untitled Document");
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to extract document title: {}", e.getMessage());
        }

        return "Untitled Document";
    }

    /**
     * Extract full text with fallback for errors
     */
    private static String extractFullText(PDFDocumentModel document, int startPage, int endPage) {
        StringBuilder text = new StringBuilder();

        try {
            if (document != null && document.getPages() != null) {
                for (int i = startPage; i <= endPage && i < document.getPages().size(); i++) {
                    PDFPageModel page = document.getPages().get(i);
                    if (page != null && page.getText() != null) {
                        text.append(page.getText()).append("\n");
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to extract full text: {}", e.getMessage());
        }

        return text.toString();
    }

    /**
     * Extract page dimensions safely
     */
    private static float[][] extractPageDimensions(PDFDocumentModel document, int startPage, int endPage) {
        if (document == null || document.getPages() == null) {
            return new float[0][0];
        }

        int pageCount = Math.min(endPage - startPage + 1, document.getPages().size() - startPage);
        float[][] dimensions = new float[pageCount][2];

        for (int i = 0; i < pageCount; i++) {
            int pageIndex = startPage + i;
            if (pageIndex < document.getPages().size()) {
                PDFPageModel page = document.getPages().get(pageIndex);
                if (page != null) {
                    dimensions[i][0] = page.getWidth();
                    dimensions[i][1] = page.getHeight();
                }
            }
        }

        return dimensions;
    }

    /**
     * Count images safely
     */
    private static int countImages(PDFDocumentModel document, int startPage, int endPage) {
        if (document == null || document.getPages() == null) {
            return 0;
        }

        int imageCount = 0;
        for (int i = startPage; i <= endPage && i < document.getPages().size(); i++) {
            PDFPageModel page = document.getPages().get(i);
            if (page != null && page.getImages() != null) {
                imageCount += page.getImages().size();
            }
        }

        return imageCount;
    }
}