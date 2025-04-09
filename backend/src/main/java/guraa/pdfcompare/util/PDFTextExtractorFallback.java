package guraa.pdfcompare.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.PDFTextStripperByArea;

import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Fallback text extractor for PDFs that have issues with the standard extractor.
 * This class provides alternative methods for extracting text from problematic PDFs.
 */
@Slf4j
public class PDFTextExtractorFallback {

    /**
     * Extract text from a page using fallback methods.
     *
     * @param document The PDF document
     * @param pageIndex The zero-based page index
     * @return The extracted text
     * @throws IOException If there's an error extracting text
     */
    public String extractTextFromPage(PDDocument document, int pageIndex) throws IOException {
        // Try different approaches in sequence
        
        // Approach 1: Use PDFTextStripper with different settings
        try {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setStartPage(pageIndex + 1);
            stripper.setEndPage(pageIndex + 1);
            stripper.setAddMoreFormatting(false);
            stripper.setSpacingTolerance(0.5f);
            String text = stripper.getText(document);
            
            if (text != null && !text.trim().isEmpty()) {
                return text;
            }
        } catch (Exception e) {
            log.warn("First fallback approach failed: {}", e.getMessage());
        }
        
        // Approach 2: Extract text by dividing the page into regions
        try {
            return extractTextByRegions(document, pageIndex);
        } catch (Exception e) {
            log.warn("Region-based extraction failed: {}", e.getMessage());
        }
        
        // Approach 3: Use a very basic character extraction approach
        try {
            CharacterExtractor extractor = new CharacterExtractor();
            PDPage page = document.getPage(pageIndex);
            return extractor.extractBasicText(page);
        } catch (Exception e) {
            log.warn("Basic character extraction failed: {}", e.getMessage());
        }
        
        // If all approaches fail, return empty string
        return "";
    }
    
    /**
     * Extract text by dividing the page into grid regions.
     * This can help with PDFs that have complex layouts or formatting issues.
     *
     * @param document The PDF document
     * @param pageIndex The zero-based page index
     * @return The extracted text
     * @throws IOException If there's an error extracting text
     */
    private String extractTextByRegions(PDDocument document, int pageIndex) throws IOException {
        PDPage page = document.getPage(pageIndex);
        PDFTextStripperByArea stripper = new PDFTextStripperByArea();
        stripper.setSortByPosition(true);
        
        // Get page dimensions
        float width = page.getMediaBox().getWidth();
        float height = page.getMediaBox().getHeight();
        
        // Create a grid of regions
        int gridSize = 3; // 3x3 grid
        float regionWidth = width / gridSize;
        float regionHeight = height / gridSize;
        
        List<String> textParts = new ArrayList<>();
        
        // Process each region
        for (int x = 0; x < gridSize; x++) {
            for (int y = 0; y < gridSize; y++) {
                String regionName = "region_" + x + "_" + y;
                Rectangle rect = new Rectangle(
                        (int)(x * regionWidth),
                        (int)(y * regionHeight),
                        (int)regionWidth,
                        (int)regionHeight
                );
                
                stripper.addRegion(regionName, rect);
                stripper.extractRegions(page);
                
                String regionText = stripper.getTextForRegion(regionName);
                if (regionText != null && !regionText.trim().isEmpty()) {
                    textParts.add(regionText);
                }
                
                // Clear regions for next iteration
                stripper.removeRegion(regionName);
            }
        }
        
        // Combine all text parts
        return String.join("\n", textParts);
    }
}
