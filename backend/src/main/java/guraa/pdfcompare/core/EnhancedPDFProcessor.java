// File: backend/src/main/java/guraa/pdfcompare/core/EnhancedPDFProcessor.java

package guraa.pdfcompare.core;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Enhanced PDF processor with improved text extraction
 */
public class EnhancedPDFProcessor {
    private static final Logger logger = LoggerFactory.getLogger(EnhancedPDFProcessor.class);

    /**
     * Process a PDF file and extract its content model with enhanced text extraction
     * @param pdfFile The PDF file to process
     * @return A document model containing all extracted content
     * @throws IOException If there's an error reading the file
     */
    public PDFDocumentModel processDocument(File pdfFile) throws IOException {
        PDFDocumentModel model = new PDFDocumentModel();
        model.setFileName(pdfFile.getName());

        try (PDDocument document = PDDocument.load(pdfFile)) {
            // Extract basic document information
            model.setPageCount(document.getNumberOfPages());
            model.setMetadata(extractMetadata(document));

            // Process each page
            List<PDFPageModel> pages = new ArrayList<>();
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                PDPage page = document.getPage(i);
                PDFPageModel pageModel = processPage(document, page, i);
                pages.add(pageModel);
            }
            model.setPages(pages);

            logger.info("Successfully processed document {} with {} pages",
                    pdfFile.getName(), pages.size());
        }

        return model;
    }

    /**
     * Extract document metadata
     * @param document The PDF document
     * @return Map of metadata key-value pairs
     */
    private Map<String, String> extractMetadata(PDDocument document) {
        Map<String, String> metadata = new HashMap<>();

        // Extract document information
        if (document.getDocumentInformation() != null) {
            metadata.put("author", document.getDocumentInformation().getAuthor());
            metadata.put("creator", document.getDocumentInformation().getCreator());
            metadata.put("producer", document.getDocumentInformation().getProducer());
            metadata.put("title", document.getDocumentInformation().getTitle());
            metadata.put("subject", document.getDocumentInformation().getSubject());
            metadata.put("keywords", document.getDocumentInformation().getKeywords());
            metadata.put("creationDate", document.getDocumentInformation().getCreationDate() != null ?
                    document.getDocumentInformation().getCreationDate().getTime().toString() : "");
            metadata.put("modificationDate", document.getDocumentInformation().getModificationDate() != null ?
                    document.getDocumentInformation().getModificationDate().getTime().toString() : "");
        }

        return metadata;
    }

    /**
     * Process a single page of the PDF with enhanced text extraction
     * @param document The PDF document
     * @param page The page to process
     * @param pageIndex The index of the page
     * @return A page model containing all extracted content
     * @throws IOException If there's an error processing the page
     */
    protected PDFPageModel processPage(PDDocument document, PDPage page, int pageIndex) throws IOException {
        PDFPageModel pageModel = new PDFPageModel();
        pageModel.setPageNumber(pageIndex + 1);

        // Log page processing
        logger.debug("Processing page {} of document {}", pageIndex + 1, document.getDocumentId());

        // Extract page properties
        PDRectangle mediaBox = page.getMediaBox();
        pageModel.setWidth(mediaBox.getWidth());
        pageModel.setHeight(mediaBox.getHeight());

        // Extract text content with enhanced stripper that preserves more formatting
        EnhancedTextStripper textStripper = new EnhancedTextStripper();
        textStripper.setStartPage(pageIndex + 1);
        textStripper.setEndPage(pageIndex + 1);
        textStripper.setSortByPosition(true);
        textStripper.setAddMoreFormatting(true);
        String text = textStripper.getText(document);
        pageModel.setText(text != null ? text : "");

        logger.debug("Extracted {} characters of text from page {}",
                text != null ? text.length() : 0, pageIndex + 1);

        // If text is empty but page exists, try backup extraction method
        if ((text == null || text.trim().isEmpty()) && page.hasContents()) {
            logger.warn("Primary text extraction yielded no content on page {}. Trying backup method.", pageIndex + 1);
            BackupTextStripper backupStripper = new BackupTextStripper();
            backupStripper.setStartPage(pageIndex + 1);
            backupStripper.setEndPage(pageIndex + 1);
            text = backupStripper.getText(document);
            pageModel.setText(text != null ? text : "");

            logger.debug("Backup extraction method yielded {} characters",
                    text != null ? text.length() : 0);
        }

        // Extract text elements with style using enhanced extractor - safely handle nulls
        try {
            EnhancedTextStyleExtractor styleExtractor = new EnhancedTextStyleExtractor();
            List<TextElement> textElements = styleExtractor.extractTextElements(document, pageIndex + 1);
            if (textElements != null) {
                pageModel.setTextElements(textElements);
                logger.debug("Extracted {} text elements with style information from page {}",
                        textElements.size(), pageIndex + 1);
            } else {
                pageModel.setTextElements(new ArrayList<>());
                logger.debug("No text elements extracted from page {}", pageIndex + 1);
            }
        } catch (Exception e) {
            logger.error("Error extracting text elements from page {}: {}", pageIndex + 1, e.getMessage());
            pageModel.setTextElements(new ArrayList<>());
        }

        // Extract images - safely handle nulls
        try {
            List<ImageElement> images = extractImages(document, page);
            if (images != null) {
                pageModel.setImages(images);
                logger.debug("Extracted {} images from page {}", images.size(), pageIndex + 1);
            } else {
                pageModel.setImages(new ArrayList<>());
                logger.debug("No images extracted from page {}", pageIndex + 1);
            }
        } catch (Exception e) {
            logger.error("Error extracting images from page {}: {}", pageIndex + 1, e.getMessage());
            pageModel.setImages(new ArrayList<>());
        }

        // Extract fonts - safely handle nulls
        try {
            Set<FontInfo> fonts = extractFonts(page);
            if (fonts != null) {
                pageModel.setFonts(new ArrayList<>(fonts));
                logger.debug("Extracted {} fonts from page {}", fonts.size(), pageIndex + 1);
            } else {
                pageModel.setFonts(new ArrayList<>());
                logger.debug("No fonts extracted from page {}", pageIndex + 1);
            }
        } catch (Exception e) {
            logger.error("Error extracting fonts from page {}: {}", pageIndex + 1, e.getMessage());
            pageModel.setFonts(new ArrayList<>());
        }

        return pageModel;
    }
    /**
     * Extract images from a page
     * @param document The PDF document
     * @param page The page to extract images from
     * @return List of image elements
     */
    private List<ImageElement> extractImages(PDDocument document, PDPage page) {
        List<ImageElement> images = new ArrayList<>();

        // This is a simplified implementation that would be replaced with actual
        // image extraction code in a real implementation

        // For now, return an empty list
        logger.debug("Image extraction not fully implemented yet");

        return images;
    }

    /**
     * Extract fonts used in a page
     * @param page The page to extract fonts from
     * @return Set of font information
     */
    private Set<FontInfo> extractFonts(PDPage page) {
        Set<FontInfo> fonts = new HashSet<>();

        // This is a simplified implementation that would be replaced with actual
        // font extraction code in a real implementation

        // For now, return an empty set
        logger.debug("Font extraction not fully implemented yet");

        return fonts;
    }

    /**
     * Enhanced text stripper that preserves more formatting
     */
    private class EnhancedTextStripper extends PDFTextStripper {
        private boolean addMoreFormatting = false;

        public EnhancedTextStripper() throws IOException {
            super();
            this.setLineSeparator("\n");
            this.setWordSeparator(" ");
            this.setParagraphStart("");
            this.setParagraphEnd("\n");
            this.setAddMoreFormatting(false);
        }

        public void setAddMoreFormatting(boolean addMoreFormatting) {
            this.addMoreFormatting = addMoreFormatting;
        }

        @Override
        protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
            // Enhanced text extraction with better positioning awareness
            if (addMoreFormatting) {
                // Analyze text positions for better position-based formatting
                if (!textPositions.isEmpty()) {
                    TextPosition firstPos = textPositions.get(0);
                    float fontSize = firstPos.getFontSize();

                    // Add extra newlines for larger font sizes (potential headers)
                    if (fontSize > 14.0f) {
                        writeString("\n");
                    }

                    // Detect if this text is likely to be a bullet point
                    if (text.startsWith("•") || text.startsWith("-") || text.startsWith("*")) {
                        writeString("  ");  // Add indentation
                    }
                }
            }

            super.writeString(text, textPositions);
        }
    }

    /**
     * Backup text stripper for cases where the primary extractor fails
     */
    private class BackupTextStripper extends PDFTextStripper {
        public BackupTextStripper() throws IOException {
            super();
            this.setSuppressDuplicateOverlappingText(false);
            this.setSortByPosition(true);
            this.setSpacingTolerance(0.5f);
            this.setAverageCharTolerance(0.3f);
        }
    }

    /**
     * Enhanced text style extractor with better font detection
     */
    private class EnhancedTextStyleExtractor {
        /**
         * Extract text elements with style information
         * @param document The PDF document
         * @param pageNumber The page number to extract from
         * @return List of text elements with style information
         * @throws IOException If there's an error processing the page
         */
        public List<TextElement> extractTextElements(PDDocument document, int pageNumber) throws IOException {
            List<TextElement> elements = new ArrayList<>();

            try {
                // Simple implementation that extracts basic text elements
                PDFTextStripper stripper = new PDFTextStripper() {
                    @Override
                    protected void processTextPosition(TextPosition text) {
                        TextElement element = new TextElement();
                        element.setText(text.getUnicode());
                        element.setX(text.getX());
                        element.setY(text.getY());
                        element.setWidth(text.getWidth());
                        element.setHeight(text.getHeight());
                        element.setFontName(text.getFont().getName());
                        element.setFontSize(text.getFontSize());

                        // Try to determine font style from name
                        String fontName = text.getFont().getName().toLowerCase();
                        if (fontName.contains("bold")) {
                            element.setFontStyle("bold");
                        } else if (fontName.contains("italic") || fontName.contains("oblique")) {
                            element.setFontStyle("italic");
                        } else {
                            element.setFontStyle("normal");
                        }

                        // Get color (this is simplified - actual implementation would be more complex)
                        element.setColor(new int[] {0, 0, 0}); // Default to black

                        elements.add(element);
                    }
                };

                stripper.setStartPage(pageNumber);
                stripper.setEndPage(pageNumber);
                stripper.getText(document);

                logger.debug("Extracted {} text elements from page {}", elements.size(), pageNumber);
            } catch (Exception e) {
                logger.error("Error extracting text elements: {}", e.getMessage(), e);
            }

            return elements;
        }
    }
}