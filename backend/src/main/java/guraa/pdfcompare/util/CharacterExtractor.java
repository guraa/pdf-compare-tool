package guraa.pdfcompare.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.contentstream.PDFStreamEngine;
import org.apache.pdfbox.contentstream.operator.DrawObject;
import org.apache.pdfbox.contentstream.operator.state.Concatenate;
import org.apache.pdfbox.contentstream.operator.state.Restore;
import org.apache.pdfbox.contentstream.operator.state.Save;
import org.apache.pdfbox.contentstream.operator.state.SetGraphicsStateParameters;
import org.apache.pdfbox.contentstream.operator.state.SetMatrix;
import org.apache.pdfbox.contentstream.operator.text.*;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.state.PDTextState;
import org.apache.pdfbox.util.Matrix;
import org.apache.pdfbox.util.Vector;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * A very basic character extractor for PDFs that have issues with standard text extraction.
 * This class extracts individual characters and their positions from a PDF page.
 */
@Slf4j
public class CharacterExtractor extends PDFStreamEngine {
    
    private final List<CharacterInfo> characters = new ArrayList<>();
    
    /**
     * Constructor.
     */
    public CharacterExtractor() {
        // Add the supported operators
        addOperator(new BeginText());
        addOperator(new Concatenate());
        addOperator(new DrawObject());
        addOperator(new EndText());
        addOperator(new MoveText());
        addOperator(new MoveTextSetLeading());
        addOperator(new NextLine());
        addOperator(new Restore());
        addOperator(new Save());
        addOperator(new SetFontAndSize());
        addOperator(new SetGraphicsStateParameters());
        addOperator(new SetMatrix());
        addOperator(new SetTextLeading());
        addOperator(new SetTextRenderingMode());
        addOperator(new SetTextRise());
        addOperator(new SetWordSpacing());
        addOperator(new SetCharSpacing());
        addOperator(new SetTextHorizontalScaling());
        addOperator(new ShowText());
        addOperator(new ShowTextAdjusted());
    }
    
    /**
     * Extract basic text from a PDF page.
     *
     * @param page The PDF page
     * @return The extracted text
     * @throws IOException If there's an error processing the page
     */
    public String extractBasicText(PDPage page) throws IOException {
        characters.clear();
        
        try {
            // Process the page content
            processPage(page);
            
            // Sort characters by position
            sortCharacters();
            
            // Combine characters into text
            return combineCharacters();
        } catch (Exception e) {
            log.error("Error extracting basic text", e);
            return "";
        }
    }
    
    @Override
    protected void showText(byte[] string) throws IOException {
        try {
            PDTextState textState = getGraphicsState().getTextState();
            PDFont font = textState.getFont();
            
            if (font == null) {
                return;
            }
            
            // Get current transformation matrix
            Matrix ctm = getGraphicsState().getCurrentTransformationMatrix();
            Matrix textMatrix = getTextMatrix();
            
            // Process each character
            float fontSize = textState.getFontSize();
            float horizontalScaling = textState.getHorizontalScaling() / 100f;
            float charSpacing = textState.getCharacterSpacing();
            
            float spaceWidth = 0;
            try {
                spaceWidth = font.getSpaceWidth() * fontSize * horizontalScaling;
            } catch (Exception e) {
                spaceWidth = fontSize / 3;
            }
            
            int codeLength = 1;
            for (int i = 0; i < string.length; i += codeLength) {
                try {
                    // Get the character code
                    codeLength = 1;
                    int code = string[i] & 0xff;
                    String unicode;
                    
                    try {
                        // Try to convert to Unicode
                        unicode = font.toUnicode(code);
                        
                        // If we couldn't map to Unicode, try to use the character code as-is
                        if (unicode == null) {
                            unicode = String.format("[%02X]", code);
                        }
                    } catch (Exception e) {
                        unicode = String.format("[%02X]", code);
                    }
                    
                    // Get character width
                    float charWidth;
                    try {
                        charWidth = font.getWidth(code) / 1000 * fontSize * horizontalScaling;
                    } catch (Exception e) {
                        charWidth = fontSize / 2;
                    }
                    
                    // Calculate position - simplified for compatibility
                    float tx = charWidth;
                    float ty = 0;
                    
                    // Create character info
                    Matrix textRenderingMatrix = textMatrix.multiply(ctm);
                    float x = textRenderingMatrix.getTranslateX();
                    float y = textRenderingMatrix.getTranslateY();
                    
                    CharacterInfo charInfo = new CharacterInfo();
                    charInfo.setCharacter(unicode);
                    charInfo.setX(x);
                    charInfo.setY(y);
                    charInfo.setWidth(charWidth);
                    charInfo.setHeight(fontSize);
                    
                    characters.add(charInfo);
                    
                    // Move text position
                    textMatrix.translate(tx, ty);
                } catch (Exception e) {
                    // Skip this character if there's an error
                    log.debug("Error processing character at index {}: {}", i, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Error in showText method", e);
        }
    }
    
    /**
     * Sort characters by position.
     */
    private void sortCharacters() {
        // Group characters by line
        float lineHeight = getAverageLineHeight();
        List<List<CharacterInfo>> lines = new ArrayList<>();
        List<CharacterInfo> currentLine = new ArrayList<>();
        
        // Sort by Y position first
        Collections.sort(characters, Comparator.comparing(CharacterInfo::getY));
        
        if (!characters.isEmpty()) {
            float lastY = characters.get(0).getY();
            currentLine.add(characters.get(0));
            
            for (int i = 1; i < characters.size(); i++) {
                CharacterInfo charInfo = characters.get(i);
                
                // Check if this is a new line
                if (Math.abs(charInfo.getY() - lastY) > lineHeight * 0.5) {
                    // Sort current line by X position
                    Collections.sort(currentLine, Comparator.comparing(CharacterInfo::getX));
                    lines.add(currentLine);
                    
                    // Start a new line
                    currentLine = new ArrayList<>();
                    lastY = charInfo.getY();
                }
                
                currentLine.add(charInfo);
            }
            
            // Add the last line
            if (!currentLine.isEmpty()) {
                Collections.sort(currentLine, Comparator.comparing(CharacterInfo::getX));
                lines.add(currentLine);
            }
        }
        
        // Clear and rebuild the characters list
        characters.clear();
        for (List<CharacterInfo> line : lines) {
            characters.addAll(line);
        }
    }
    
    /**
     * Calculate the average line height.
     *
     * @return The average line height
     */
    private float getAverageLineHeight() {
        if (characters.isEmpty()) {
            return 12; // Default
        }
        
        float totalHeight = 0;
        for (CharacterInfo charInfo : characters) {
            totalHeight += charInfo.getHeight();
        }
        
        return totalHeight / characters.size();
    }
    
    /**
     * Combine characters into text.
     *
     * @return The combined text
     */
    private String combineCharacters() {
        if (characters.isEmpty()) {
            return "";
        }
        
        StringBuilder text = new StringBuilder();
        float lineHeight = getAverageLineHeight();
        float lastY = characters.get(0).getY();
        float lastX = characters.get(0).getX() + characters.get(0).getWidth();
        
        text.append(characters.get(0).getCharacter());
        
        for (int i = 1; i < characters.size(); i++) {
            CharacterInfo charInfo = characters.get(i);
            
            // Check if this is a new line
            if (Math.abs(charInfo.getY() - lastY) > lineHeight * 0.5) {
                text.append("\n");
                lastX = 0;
            }
            // Check if this is a new word
            else if (charInfo.getX() - lastX > charInfo.getWidth() * 0.5) {
                text.append(" ");
            }
            
            text.append(charInfo.getCharacter());
            lastY = charInfo.getY();
            lastX = charInfo.getX() + charInfo.getWidth();
        }
        
        return text.toString();
    }
    
    /**
     * Class to store character information.
     */
    private static class CharacterInfo {
        private String character;
        private float x;
        private float y;
        private float width;
        private float height;
        
        public String getCharacter() {
            return character;
        }
        
        public void setCharacter(String character) {
            this.character = character;
        }
        
        public float getX() {
            return x;
        }
        
        public void setX(float x) {
            this.x = x;
        }
        
        public float getY() {
            return y;
        }
        
        public void setY(float y) {
            this.y = y;
        }
        
        public float getWidth() {
            return width;
        }
        
        public void setWidth(float width) {
            this.width = width;
        }
        
        public float getHeight() {
            return height;
        }
        
        public void setHeight(float height) {
            this.height = height;
        }
    }
}
