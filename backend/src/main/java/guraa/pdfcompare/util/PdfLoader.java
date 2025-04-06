package guraa.pdfcompare.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility for loading PDF documents with enhanced error recovery.
 * Compatible with PDFBox 2.0.x
 */
@Slf4j
public class PdfLoader {

    /**
     * Load a PDF document with enhanced error recovery.
     *
     * @param file The PDF file to load
     * @return The loaded PDF document
     * @throws IOException If the document cannot be loaded
     */
    public static PDDocument loadDocument(File file) throws IOException {
        try {
            // First try standard loading with memory usage settings
            return PDDocument.load(file, createMemorySettings());
        } catch (InvalidPasswordException e) {
            throw e; // Password protected PDFs need special handling
        } catch (Exception e) {
            log.warn("Error loading PDF with standard method: {}. Trying with fallback options...", e.getMessage());

            // Try with more lenient parsing
            return loadWithFallbackOptions(file);
        }
    }

    /**
     * Create memory usage settings optimized for reliability.
     *
     * @return Memory usage settings
     */
    private static MemoryUsageSetting createMemorySettings() {
        // Use memory with temp file fallback, up to 50MB in memory
        return MemoryUsageSetting.setupMixed(50 * 1024 * 1024);
    }

    /**
     * Load a PDF document with enhanced error recovery and provide metadata.
     *
     * @param file The PDF file to load
     * @return Map containing the document and any extracted metadata
     * @throws IOException If the document cannot be loaded
     */
    public static Map<String, Object> loadDocumentWithInfo(File file) throws IOException {
        PDDocument document = loadDocument(file);
        Map<String, Object> result = new HashMap<>();
        result.put("document", document);

        try {
            // Extract basic metadata
            PDDocumentInformation info = document.getDocumentInformation();
            Map<String, String> metadata = new HashMap<>();

            if (info != null) {
                if (info.getTitle() != null) metadata.put("title", info.getTitle());
                if (info.getAuthor() != null) metadata.put("author", info.getAuthor());
                if (info.getSubject() != null) metadata.put("subject", info.getSubject());
                if (info.getKeywords() != null) metadata.put("keywords", info.getKeywords());
                if (info.getCreator() != null) metadata.put("creator", info.getCreator());
                if (info.getProducer() != null) metadata.put("producer", info.getProducer());
            }

            result.put("metadata", metadata);
            result.put("pageCount", document.getNumberOfPages());
        } catch (Exception e) {
            log.warn("Error extracting metadata: {}", e.getMessage());
            result.put("metadata", new HashMap<>());
            result.put("pageCount", document.getNumberOfPages());
        }

        return result;
    }

    /**
     * Load a PDF document with fallback options for problematic PDFs.
     *
     * @param file The PDF file to load
     * @return The loaded PDF document
     * @throws IOException If the document cannot be loaded
     */
    private static PDDocument loadWithFallbackOptions(File file) throws IOException {
        // Try with memory-only settings (avoids file system issues)
        try {
            MemoryUsageSetting memSettings = MemoryUsageSetting.setupMainMemoryOnly();
            PDDocument doc = PDDocument.load(file, memSettings);
            log.info("Successfully loaded PDF with memory-only settings");
            return doc;
        } catch (Exception e) {
            log.warn("Error loading PDF with memory-only settings: {}", e.getMessage());

            // Try with strict memory usage settings (temp files allowed)
            try {
                MemoryUsageSetting memSettings = MemoryUsageSetting.setupTempFileOnly();
                PDDocument doc = PDDocument.load(file, memSettings);
                log.info("Successfully loaded PDF with temp file settings");
                return doc;
            } catch (Exception e2) {
                log.warn("Error loading PDF with temp file settings: {}", e2.getMessage());

                // Last-ditch attempt - try with buffered stream approach
                try (FileInputStream fis = new FileInputStream(file)) {
                    PDDocument doc = PDDocument.load(fis);
                    log.info("Successfully loaded PDF from stream");
                    return doc;
                } catch (Exception e3) {
                    log.error("Failed to load PDF with all fallback options: {}", e3.getMessage());
                    throw new IOException("Could not load PDF document after multiple attempts", e3);
                }
            }
        }
    }
}