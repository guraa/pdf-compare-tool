package guraa.pdfcompare.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Utility class for handling password-protected PDF files.
 */
@Slf4j
@Component
public class PdfPasswordHandler {

    /**
     * Check if a PDF file is password-protected.
     *
     * @param pdfFile The PDF file
     * @return True if the PDF file is password-protected
     */
    public boolean isPasswordProtected(File pdfFile) {
        try (PDDocument document = PDDocument.load(pdfFile)) {
            // If we can load the document without exception, it's not encrypted
            return false;
        } catch (IOException e) {
            // Check if the exception is due to encryption
            return e.getMessage() != null &&
                    (e.getMessage().contains("encrypted") ||
                            e.getMessage().contains("password"));
        }
    }

    /**
     * Unlock a password-protected PDF file.
     *
     * @param pdfFile The password-protected PDF file
     * @param password The password
     * @return The unlocked PDF file, or null if unlocking failed
     */
    public File unlockPdf(File pdfFile, String password) {
        try {
            // Try to load the document with the provided password
            PDDocument document = PDDocument.load(pdfFile, password);

            // If loaded successfully, create an unencrypted copy
            File unlockFile = createTempFile(pdfFile.getName());
            document.setAllSecurityToBeRemoved(true);
            document.save(unlockFile);
            document.close();

            log.info("Successfully unlocked PDF: {}", pdfFile.getName());
            return unlockFile;
        } catch (IOException e) {
            log.error("Failed to unlock PDF: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Create a temporary file for the unlocked PDF.
     *
     * @param originalName Original file name
     * @return Temporary file
     * @throws IOException If there's an error creating the temporary file
     */
    private File createTempFile(String originalName) throws IOException {
        // Create temp directory if it doesn't exist
        Path tempDir = Path.of("uploads", "temp");
        if (!Files.exists(tempDir)) {
            Files.createDirectories(tempDir);
        }

        // Generate a new file name with "unlocked_" prefix
        String fileName = "unlocked_" + originalName;
        Path tempFile = tempDir.resolve(fileName);

        // Create the file
        return Files.createFile(tempFile).toFile();
    }

    /**
     * Create a copy of a PDF file.
     *
     * @param sourceFile The source file
     * @param targetFile The target file
     * @return True if the copy was successful
     */
    public boolean copyPdfFile(File sourceFile, File targetFile) {
        try {
            Files.copy(sourceFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            log.error("Failed to copy PDF file: {}", e.getMessage());
            return false;
        }
    }
}