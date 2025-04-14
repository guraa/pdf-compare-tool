package guraa.pdfcompare.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.io.RandomAccessBufferedFileInputStream;
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
 * Provides multiple fallback strategies to handle corrupted or problematic PDF files.
 */
@Slf4j
public class PdfLoader {

    private static final int MAX_RETRIES = 3;

    /**
     * Load a PDF document with maximum error resilience.
     * Tries multiple fallback strategies when standard loading fails.
     *
     * @param file The PDF file to load
     * @return The loaded PDF document
     * @throws IOException If the document cannot be loaded with any method
     */
    public static PDDocument loadDocumentWithFallbackOptions(File file) throws IOException {
        // Increase the push back size for better handling of problematic PDFs
        System.setProperty("org.apache.pdfbox.baseParser.pushBackSize", "8192");

        // First attempt: Standard loading
        try {
            return PDDocument.load(file);
        } catch (InvalidPasswordException e) {
            throw e; // Password protected PDFs need special handling
        } catch (Exception e) {
            log.warn("Standard PDF loading failed for {}: {}. Trying with fallbacks...",
                    file.getName(), e.getMessage());
        }

        // Second attempt: Try with RandomAccessBufferedFileInputStream
        try {
            return PDDocument.load(new RandomAccessBufferedFileInputStream(file));
        } catch (Exception e) {
            log.warn("RandomAccessBufferedFileInputStream loading failed: {}", e.getMessage());
        }

        // Third attempt: Try with direct FileInputStream
        try (FileInputStream fis = new FileInputStream(file)) {
            return PDDocument.load(fis);
        } catch (Exception e) {
            log.warn("Direct FileInputStream loading failed: {}", e.getMessage());
        }

        // Fourth attempt: Create a sanitized copy that may fix stream issues
        try {
            File sanitizedFile = createSanitizedCopy(file);
            try {
                PDDocument doc = PDDocument.load(sanitizedFile);
                // If we get here, loading succeeded
                Files.deleteIfExists(sanitizedFile.toPath()); // Clean up temp file
                return doc;
            } catch (Exception e) {
                log.warn("Loading sanitized copy failed: {}", e.getMessage());
                Files.deleteIfExists(sanitizedFile.toPath()); // Clean up temp file
            }
        } catch (Exception e) {
            log.warn("Failed to create sanitized copy: {}", e.getMessage());
        }

        // Fifth attempt: Try with memory usage optimizations
        try {
            System.gc(); // Suggest garbage collection before trying again
            // Set memory-saving options
            System.setProperty("org.apache.pdfbox.rendering.UsePureJava", "true");
            return PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly());
        } catch (Exception e) {
            log.warn("Memory-optimized loading failed: {}", e.getMessage());
        }

        // Last resort: Throw a meaningful error message
        throw new IOException("Failed to load PDF document after trying all available methods. " +
                "The file may be corrupted or use unsupported features.");
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

    /**
     * Helper method for determining if PDF loading error indicates a corrupted file.
     *
     * @param e The exception to check
     * @return true if the exception indicates corruption
     */
    public static boolean isCorruptionError(Exception e) {
        if (e == null) return false;

        String message = e.getMessage();
        if (message == null) return false;

        return message.contains("cross reference") ||
                message.contains("xref") ||
                message.contains("corrupt") ||
                message.contains("trailer") ||
                message.contains("startxref") ||
                message.contains("EOF") ||
                message.contains("expected") ||
                message.contains("missing");
    }

    /**
     * Helper method to check if a PDF document is valid.
     *
     * @param file The PDF file to check
     * @return true if valid, false otherwise
     */
    public static boolean isValidPdf(File file) {
        try (PDDocument ignored = loadDocumentWithFallbackOptions(file)) {
            return true;
        } catch (Exception e) {
            log.debug("PDF validation failed: {}", e.getMessage());
            return false;
        }
    }
}