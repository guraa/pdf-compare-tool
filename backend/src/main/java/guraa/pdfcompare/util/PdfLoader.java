package guraa.pdfcompare.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Enhanced utility for loading problematic PDF documents with advanced error recovery.
 * Handles common issues like Ascii85 stream errors, DataFormatExceptions, and bounds issues.
 */
@Slf4j
public class PdfLoader {


    /**
     * Load a PDF document with maximum error resilience.
     * Uses multiple fallback strategies for handling problematic files.
     *
     * @param file The PDF file to load
     * @return The loaded PDF document
     * @throws IOException If the document cannot be loaded with any method
     */
    public static PDDocument loadDocumentWithFallbackOptions(File file) throws IOException {
        System.setProperty("org.apache.pdfbox.baseParser.pushBackSize", "8192");
        try {
            // First try standard loading with memory usage settings
            return PDDocument.load(file, createMemorySettings());
        } catch (InvalidPasswordException e) {
            throw e; // Password protected PDFs need special handling
        } catch (Exception e) {
            log.warn("Error loading PDF with standard method: {}. Trying with fallback options...", e.getMessage());

            // Check if the error is related to stream issues
            if (e.getMessage() != null &&
                    (e.getMessage().contains("Ascii85") ||
                            e.getMessage().contains("FlateFilter") ||
                            e.getMessage().contains("DataFormatException") ||
                            e.getMessage().contains("bounds"))) {

                // Try with the sanitized copy approach
                return loadWithSanitizedCopy(file);
            }

            // Try parent class fallback options if sanitizing doesn't work
            try {
                MemoryUsageSetting memSettings = MemoryUsageSetting.setupMainMemoryOnly();
                PDDocument doc = PDDocument.load(file, memSettings);
                log.info("Successfully loaded PDF with memory-only settings");
                return doc;
            } catch (Exception e2) {
                log.warn("Error loading PDF with memory-only settings: {}", e2.getMessage());

                // Try with strict memory usage settings (temp files allowed)
                try {
                    MemoryUsageSetting memSettings = MemoryUsageSetting.setupTempFileOnly();
                    PDDocument doc = PDDocument.load(file, memSettings);
                    log.info("Successfully loaded PDF with temp file settings");
                    return doc;
                } catch (Exception e3) {
                    log.warn("Error loading PDF with temp file settings: {}", e3.getMessage());

                    // Last-ditch attempt - try with buffered stream approach
                    try (FileInputStream fis = new FileInputStream(file)) {
                        PDDocument doc = PDDocument.load(fis);
                        log.info("Successfully loaded PDF from stream");
                        return doc;
                    } catch (Exception e4) {
                        log.error("Failed to load PDF with all fallback options: {}", e4.getMessage());
                        throw new IOException("Could not load PDF document after multiple attempts", e4);
                    }
                }
            }
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
     * Load a PDF by first creating a sanitized copy that may fix some stream issues.
     * This approach can help with Ascii85 and FlateFilter errors.
     *
     * @param file The original PDF file
     * @return The loaded PDF document
     * @throws IOException If sanitizing and loading fails
     */
    private static PDDocument loadWithSanitizedCopy(File file) throws IOException {
        File sanitizedFile = null;
        try {
            // Create a sanitized copy in a temporary location
            sanitizedFile = createSanitizedCopy(file);

            // Try loading with memory-only settings first
            try {
                MemoryUsageSetting memSettings = MemoryUsageSetting.setupMainMemoryOnly();
                PDDocument doc = PDDocument.load(sanitizedFile, memSettings);
                log.info("Successfully loaded sanitized PDF with memory-only settings");
                return doc;
            } catch (Exception e) {
                log.warn("Error loading sanitized PDF with memory-only settings: {}", e.getMessage());

                // Try with temp-file settings
                try {
                    MemoryUsageSetting memSettings = MemoryUsageSetting.setupTempFileOnly();
                    PDDocument doc = PDDocument.load(sanitizedFile, memSettings);
                    log.info("Successfully loaded sanitized PDF with temp file settings");
                    return doc;
                } catch (Exception e2) {
                    log.warn("Error loading sanitized PDF with temp file settings: {}", e2.getMessage());

                    // Last attempt with FileInputStream
                    try (FileInputStream fis = new FileInputStream(sanitizedFile)) {
                        PDDocument doc = PDDocument.load(fis);
                        log.info("Successfully loaded sanitized PDF from stream");
                        return doc;
                    } catch (Exception e3) {
                        log.error("Failed to load sanitized PDF with all fallback options: {}", e3.getMessage());
                        throw new IOException("Could not load sanitized PDF document after multiple attempts", e3);
                    }
                }
            }
        } finally {
            // Clean up temporary file
            if (sanitizedFile != null && sanitizedFile.exists()) {
                try {
                    Files.delete(sanitizedFile.toPath());
                } catch (Exception e) {
                    log.warn("Failed to delete temporary sanitized file: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Create a sanitized copy of a PDF file, attempting to fix common corruption issues.
     *
     * @param originalFile The original PDF file
     * @return A temporary file containing the sanitized PDF
     * @throws IOException If sanitizing fails
     */
    private static File createSanitizedCopy(File originalFile) throws IOException {
        // Create a temporary file for the sanitized copy
        File sanitizedFile = File.createTempFile("sanitized_", ".pdf");

        // First try a simple copy which might fix some filesystem-level corruption
        try {
            Files.copy(originalFile.toPath(), sanitizedFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return sanitizedFile;
        } catch (Exception e) {
            log.warn("Simple copy failed, trying direct byte-by-byte copy: {}", e.getMessage());
        }

        // If simple copy fails, try a direct byte-by-byte copy
        try (RandomAccessFile in = new RandomAccessFile(originalFile, "r");
             RandomAccessFile out = new RandomAccessFile(sanitizedFile, "rw")) {

            byte[] buffer = new byte[8192];
            long fileSize = in.length();
            long totalBytesRead = 0;

            while (totalBytesRead < fileSize) {
                try {
                    int bytesRead = in.read(buffer);
                    if (bytesRead == -1) break;

                    out.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;
                } catch (Exception e) {
                    // If we hit an error reading a block, write zeros and continue
                    log.warn("Error reading block at position {}, writing zeros: {}",
                            totalBytesRead, e.getMessage());

                    // Fill buffer with zeros
                    for (int i = 0; i < buffer.length; i++) {
                        buffer[i] = 0;
                    }

                    // Write a block of zeros and advance position
                    out.write(buffer, 0, buffer.length);
                    totalBytesRead += buffer.length;

                    // Seek to next block boundary in input file
                    in.seek(totalBytesRead);
                }
            }

            log.info("Created sanitized copy with {} bytes, original size: {} bytes",
                    sanitizedFile.length(), fileSize);

            return sanitizedFile;
        } catch (Exception e) {
            log.error("Failed to create sanitized copy: {}", e.getMessage());
            throw new IOException("Failed to create sanitized copy of PDF", e);
        }
    }
}