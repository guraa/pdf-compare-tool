package guraa.pdfcompare.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.contentstream.PDFStreamEngine;
import org.apache.pdfbox.contentstream.operator.DrawObject;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.contentstream.operator.state.Restore;
import org.apache.pdfbox.contentstream.operator.state.Save;
import org.apache.pdfbox.contentstream.operator.state.SetGraphicsStateParameters;
import org.apache.pdfbox.contentstream.operator.state.SetMatrix;
import org.apache.pdfbox.contentstream.operator.text.BeginText;
import org.apache.pdfbox.contentstream.operator.text.EndText;
import org.apache.pdfbox.contentstream.operator.text.SetFontAndSize;
import org.apache.pdfbox.contentstream.operator.text.ShowText;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDFont;

import java.io.IOException;
import java.util.List;

/**
 * A minimal character extractor that can handle problematic PDF streams.
 * Uses direct character extraction with minimal stream processing to
 * avoid common PDF parsing issues.
 */
@Slf4j
public class CharacterExtractor extends PDFStreamEngine {

    private final StringBuilder textBuilder = new StringBuilder();
    private boolean hadErrors = false;

    public CharacterExtractor() {
        // Register only essential operators
        addOperator(new BeginText());
        addOperator(new EndText());
        addOperator(new SetGraphicsStateParameters());
        addOperator(new Save());
        addOperator(new Restore());
        addOperator(new SetFontAndSize());
        addOperator(new ShowText());
        addOperator(new DrawObject());
        addOperator(new SetMatrix());
    }

    /**
     * Extract basic text from a page with minimal processing.
     *
     * @param page The PDF page
     * @return Extracted text or error message
     */
    public String extractBasicText(PDPage page) {
        textBuilder.setLength(0);
        hadErrors = false;

        try {
            processPage(page);

            if (textBuilder.length() == 0) {
                return "[No text content found]";
            }

            if (hadErrors) {
                return "[Partial text extraction due to PDF errors]\n" + textBuilder;
            }

            return textBuilder.toString();
        } catch (Exception e) {
            log.error("Error in basic text extraction: {}", e.getMessage());
            if (textBuilder.length() > 0) {
                return "[Partial text extraction before error]\n" + textBuilder;
            }
            return "[Text extraction failed: " + e.getMessage() + "]";
        }
    }

    @Override
    protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
        try {
            String operation = operator.getName();

            // Handle text showing operators with direct extraction to avoid stream parsing issues
            if ("Tj".equals(operation) || "TJ".equals(operation) || "'".equals(operation) || "\"".equals(operation)) {
                extractTextFromOperands(operands);
            }

            // Continue normal processing
            super.processOperator(operator, operands);
        } catch (Exception e) {
            hadErrors = true;
            log.debug("Error processing PDF operator {}: {}", operator.getName(), e.getMessage());
            // Don't rethrow - continue processing to get as much text as possible
        }
    }

    /**
     * Extract text directly from operands.
     */
    private void extractTextFromOperands(List<COSBase> operands) {
        try {
            if (operands == null || operands.isEmpty()) {
                return;
            }

            // Get current font if possible
            PDFont font = getGraphicsState().getTextState().getFont();
            if (font == null) {
                return;
            }

            // Extract text from string operands
            for (COSBase operand : operands) {
                if (operand instanceof COSString) {
                    COSString string = (COSString) operand;
                    try {
                        // Get the Unicode string representation
                        String text = string.getString();
                        if (text != null && !text.isEmpty()) {
                            textBuilder.append(text);
                        }
                    } catch (Exception e) {
                        // Fallback to basic character extraction
                        byte[] bytes = string.getBytes();
                        for (byte b : bytes) {
                            // Only append printable ASCII characters
                            if (b >= 32 && b < 127) {
                                textBuilder.append((char) b);
                            }
                        }
                        hadErrors = true;
                    }
                }
            }

            // Add space after text elements
            textBuilder.append(" ");

        } catch (Exception e) {
            log.debug("Error extracting text from operands: {}", e.getMessage());
            hadErrors = true;
        }
    }
}