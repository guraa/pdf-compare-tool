package guraa.pdfcompare.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.util.List;

/**
 * A fallback text extractor that handles problematic PDFs.
 * This class is simplified to be compatible with older PDFBox versions.
 */
@Slf4j
public class PDFTextExtractorFallback {

    /**
     * Extract text from a specific page with error handling for problematic PDFs.
     *
     * @param document The PDF document
     * @param pageIndex The 0-based page index
     * @return The extracted text or an error message
     */
    public String extractTextFromPage(PDDocument document, int pageIndex) {
        try {
            // Try with more robust settings
            RobustTextStripper stripper = new RobustTextStripper();
            stripper.setSortByPosition(true);
            stripper.setStartPage(pageIndex + 1);
            stripper.setEndPage(pageIndex + 1);

            // Set stripper to suppress most errors
            stripper.setSuppressDuplicateOverlappingText(true);
            stripper.setAddMoreFormatting(false);
            stripper.setLineSeparator("\n");

            return stripper.getText(document);
        } catch (Exception e) {
            log.warn("Error in fallback text extraction: {}", e.getMessage());

            try {
                // Try with most basic approach - just get any text without worrying about format
                SimpleTextStripper simpleStripper = new SimpleTextStripper();
                simpleStripper.setStartPage(pageIndex + 1);
                simpleStripper.setEndPage(pageIndex + 1);

                String basicText = simpleStripper.getText(document);
                if (basicText != null && !basicText.trim().isEmpty()) {
                    return "[Partial text extraction - document has problematic content]\n\n" + basicText;
                }

                return "[Text extraction failed - no text content could be extracted]";
            } catch (Exception e2) {
                log.error("Complete failure in text extraction: {}", e2.getMessage());
                return "[Text extraction failed]";
            }
        }
    }

    /**
     * A more robust text stripper that handles problematic PDFs.
     */
    private static class RobustTextStripper extends PDFTextStripper {

        public RobustTextStripper() throws IOException {
            super();
        }

        @Override
        protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
            try {
                super.writeString(text, textPositions);
            } catch (Exception e) {
                // Log but continue
                log.debug("Error writing text: {}", e.getMessage());

                // Try to write directly to the output
                try {
                    getOutput().write(text);
                } catch (Exception ignored) {
                    // Can't even write directly - ignore
                }
            }
        }

        @Override
        protected void writeLineSeparator() throws IOException {
            try {
                super.writeLineSeparator();
            } catch (Exception e) {
                // Try to write directly
                try {
                    getOutput().write('\n');
                } catch (Exception ignored) {
                    // Ignore
                }
            }
        }

        @Override
        public void processTextPosition(TextPosition text) {
            try {
                super.processTextPosition(text);
            } catch (Exception e) {
                // Log but continue
                log.debug("Error processing text position: {}", e.getMessage());
            }
        }
    }

    /**
     * A very simple text stripper that focuses only on getting text content.
     */
    private static class SimpleTextStripper extends PDFTextStripper {

        public SimpleTextStripper() throws IOException {
            super();
            // Configure for maximum robustness
            this.setSuppressDuplicateOverlappingText(true);
            this.setSortByPosition(false);
            this.setAddMoreFormatting(false);
            this.setLineSeparator("\n");
        }

        @Override
        protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
            try {
                // Remove control characters that might cause problems
                text = text.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "");
                super.writeString(text, textPositions);
            } catch (Exception e) {
                // Ignore errors and just try to write the text directly
                try {
                    getOutput().write(text);
                } catch (Exception ignored) {
                    // Ignore nested exceptions
                }
            }
        }

        @Override
        public void processTextPosition(TextPosition text) {
            try {
                super.processTextPosition(text);
            } catch (Exception e) {
                // Silently ignore errors
            }
        }
    }
}