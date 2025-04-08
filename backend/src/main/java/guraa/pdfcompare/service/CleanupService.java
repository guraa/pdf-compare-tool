package guraa.pdfcompare.service;

import guraa.pdfcompare.model.Comparison;
import guraa.pdfcompare.model.PdfDocument;
import guraa.pdfcompare.repository.ComparisonRepository;
import guraa.pdfcompare.repository.PdfRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for cleaning up old files and database records.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CleanupService {

    private final PdfRepository pdfRepository;
    private final ComparisonRepository comparisonRepository;

    @Value("${app.cleanup.comparison-expiration:7}")
    private int comparisonExpirationDays;

    @Value("${app.cleanup.document-expiration:30}")
    private int documentExpirationDays;

    @Value("${app.cleanup.temp-expiration:1}")
    private int tempExpirationDays;

    /**
     * Clean up old comparisons and documents.
     * Runs daily at 2:00 AM.
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void cleanupOldData() {
        log.info("Starting scheduled cleanup of old data");

        try {
            // Clean up comparisons
            cleanupOldComparisons();

            // Clean up documents
            cleanupOldDocuments();

            // Clean up temporary files
            cleanupTemporaryFiles();

            log.info("Completed scheduled cleanup of old data");
        } catch (Exception e) {
            log.error("Error during data cleanup", e);
        }
    }

    /**
     * Clean up old comparisons.
     */
    private void cleanupOldComparisons() {
        // Calculate cutoff date for comparisons
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(comparisonExpirationDays);

        // Find old completed and failed comparisons
        List<Comparison> oldComparisons = comparisonRepository.findByCompletionTimeBefore(cutoffDate);

        if (oldComparisons.isEmpty()) {
            log.info("No old comparisons to clean up");
            return;
        }

        log.info("Found {} old comparisons to clean up", oldComparisons.size());

        // Delete comparison files
        for (Comparison comparison : oldComparisons) {
            try {
                // Delete comparison directory
                Path comparisonDir = Paths.get("uploads", "comparisons", comparison.getComparisonId());
                if (Files.exists(comparisonDir)) {
                    deleteDirectory(comparisonDir.toFile());
                    log.debug("Deleted comparison directory: {}", comparisonDir);
                }

                // Delete from database
                comparisonRepository.delete(comparison);
                log.debug("Deleted comparison from database: {}", comparison.getComparisonId());
            } catch (Exception e) {
                log.error("Error deleting comparison {}: {}", comparison.getComparisonId(), e.getMessage());
            }
        }

        log.info("Cleaned up {} old comparisons", oldComparisons.size());
    }

    /**
     * Clean up old documents.
     */
    private void cleanupOldDocuments() {
        // Calculate cutoff date for documents
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(documentExpirationDays);

        // Find old documents
        List<PdfDocument> oldDocuments = pdfRepository.findByUploadDateBefore(cutoffDate);

        if (oldDocuments.isEmpty()) {
            log.info("No old documents to clean up");
            return;
        }

        log.info("Found {} old documents to clean up", oldDocuments.size());

        // Filter documents that are not used in any active comparisons
        List<PdfDocument> documentsToDelete = oldDocuments.stream()
                .filter(doc -> !isDocumentInUse(doc))
                .collect(Collectors.toList());

        for (PdfDocument document : documentsToDelete) {
            try {
                // Delete document directory
                Path documentDir = Paths.get("uploads", "documents", document.getFileId());
                if (Files.exists(documentDir)) {
                    deleteDirectory(documentDir.toFile());
                    log.debug("Deleted document directory: {}", documentDir);
                }

                // Delete from database
                pdfRepository.delete(document);
                log.debug("Deleted document from database: {}", document.getFileId());
            } catch (Exception e) {
                log.error("Error deleting document {}: {}", document.getFileId(), e.getMessage());
            }
        }

        log.info("Cleaned up {} old documents", documentsToDelete.size());
    }

    /**
     * Clean up temporary files.
     */
    private void cleanupTemporaryFiles() {
        // Clean up temp directory
        Path tempDir = Paths.get("uploads", "temp");
        if (!Files.exists(tempDir)) {
            return;
        }

        File[] tempFiles = tempDir.toFile().listFiles();
        if (tempFiles == null || tempFiles.length == 0) {
            log.info("No temporary files to clean up");
            return;
        }

        // Calculate cutoff time for temp files
        long cutoffTime = System.currentTimeMillis() - ((long) tempExpirationDays * 24 * 60 * 60 * 1000);

        int deletedCount = 0;
        for (File file : tempFiles) {
            if (file.lastModified() < cutoffTime) {
                if (file.delete()) {
                    deletedCount++;
                    log.debug("Deleted temporary file: {}", file.getName());
                } else {
                    log.warn("Failed to delete temporary file: {}", file.getName());
                }
            }
        }

        log.info("Cleaned up {} temporary files", deletedCount);
    }

    /**
     * Check if a document is in use by any active comparison.
     *
     * @param document The document to check
     * @return True if the document is in use
     */
    private boolean isDocumentInUse(PdfDocument document) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(comparisonExpirationDays);

        // Find comparisons that use this document and were created after the cutoff date
        List<Comparison> activeComparisons = comparisonRepository.findByBaseDocumentOrCompareDocument(document, document)
                .stream()
                .filter(comp -> comp.getCompletionTime() == null || comp.getCompletionTime().isAfter(cutoffDate))
                .collect(Collectors.toList());

        return !activeComparisons.isEmpty();
    }

    /**
     * Recursively delete a directory.
     *
     * @param directory The directory to delete
     * @return True if deletion was successful
     */
    private boolean deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
        }
        return directory.delete();
    }
}