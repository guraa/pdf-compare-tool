package guraa.pdfcompare;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Main application class for PDF Compare.
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class PDFCompareApplication {

    public static void main(String[] args) {
        // Create necessary directories
        createDirectories();

        // Start Spring Boot application
        SpringApplication.run(PDFCompareApplication.class, args);
    }

    /**
     * Create necessary directories for the application.
     */
    private static void createDirectories() {
        try {
            // Create uploads directory
            Path uploadsDir = Paths.get("uploads");
            if (!Files.exists(uploadsDir)) {
                Files.createDirectories(uploadsDir);
            }

            // Create documents directory
            Path documentsDir = Paths.get("uploads", "documents");
            if (!Files.exists(documentsDir)) {
                Files.createDirectories(documentsDir);
            }

            // Create comparisons directory
            Path comparisonsDir = Paths.get("uploads", "comparisons");
            if (!Files.exists(comparisonsDir)) {
                Files.createDirectories(comparisonsDir);
            }

            // Create reports directory
            Path reportsDir = Paths.get("uploads", "reports");
            if (!Files.exists(reportsDir)) {
                Files.createDirectories(reportsDir);
            }

            // Create temp directory
            Path tempDir = Paths.get("uploads", "temp");
            if (!Files.exists(tempDir)) {
                Files.createDirectories(tempDir);
            }

            // Create debug directory for development
            Path debugDir = Paths.get("uploads", "debug");
            if (!Files.exists(debugDir)) {
                Files.createDirectories(debugDir);
            }

            System.out.println("Application directories created successfully.");
        } catch (Exception e) {
            System.err.println("Failed to create application directories: " + e.getMessage());
            e.printStackTrace();
        }
    }
}