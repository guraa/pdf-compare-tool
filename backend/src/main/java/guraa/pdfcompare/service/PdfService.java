package guraa.pdfcompare.service;

import guraa.pdfcompare.PDFComparisonEngine;
import guraa.pdfcompare.model.ComparisonResult;
import guraa.pdfcompare.model.PdfDocument;
import guraa.pdfcompare.repository.PdfRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * Service for managing PDF documents.
 * This service provides methods for storing, retrieving, and comparing
 * PDF documents.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfService {

    private final PdfRepository pdfRepository;
    private final ExecutorService executorService;
    private final PDFComparisonEngine comparisonEngine;

    @Value("${app.documents.storage-path:uploads/documents}")
    private String documentsStoragePath;

    // Cache of comparison tasks
    private final ConcurrentHashMap<String, CompletableFuture<String>> comparisonTasks = new ConcurrentHashMap<>();

    /**
     * Store a PDF file.
     *
     * @param file The file to store
     * @return The stored PDF document
     * @throws IOException If there is an error storing the file
     */
    public PdfDocument storePdf(MultipartFile file) throws IOException {
        // Generate a unique file ID
        String fileId = UUID.randomUUID().toString();

        // Create the directory for the document
        Path documentDir = Paths.get(documentsStoragePath, fileId);
        Files.createDirectories(documentDir);

        // Store the file
        Path filePath = documentDir.resolve(file.getOriginalFilename());
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        // Check if we already have a document with the same content hash
        String contentHash = calculateContentHash(filePath.toFile());
        Optional<PdfDocument> existingDocument = findDocumentByContentHash(contentHash);

        if (existingDocument.isPresent()) {
            // Reuse the existing document
            return existingDocument.get();
        }

        // Create and save the document
        PdfDocument document = PdfDocument.builder()
                .fileId(fileId)
                .fileName(file.getOriginalFilename())
                .filePath(filePath.toString())
                .uploadDate(LocalDateTime.now())
                .contentHash(contentHash)
                .build();

        // Extract metadata and update the document
        document = extractMetadata(document);

        // Save the document
        saveDocument(document);

        return document;
    }

    /**
     * Process a PDF file.
     *
     * @param file The file to process
     * @param fileName The file name
     * @return The processed PDF document
     * @throws IOException If there is an error processing the file
     */
    public PdfDocument processPdfFile(File file, String fileName) throws IOException {
        // Generate a unique file ID
        String fileId = UUID.randomUUID().toString();

        // Create the directory for the document
        Path documentDir = Paths.get(documentsStoragePath, fileId);
        Files.createDirectories(documentDir);

        // Store the file
        Path filePath = documentDir.resolve(fileName);
        Files.copy(file.toPath(), filePath, StandardCopyOption.REPLACE_EXISTING);

        // Check if we already have a document with the same content hash
        String contentHash = calculateContentHash(filePath.toFile());
        Optional<PdfDocument> existingDocument = findDocumentByContentHash(contentHash);

        if (existingDocument.isPresent()) {
            // Reuse the existing document
            return existingDocument.get();
        }

        // Create and save the document
        PdfDocument document = PdfDocument.builder()
                .fileId(fileId)
                .fileName(fileName)
                .filePath(filePath.toString())
                .uploadDate(LocalDateTime.now())
                .contentHash(contentHash)
                .build();

        // Extract metadata and update the document
        document = extractMetadata(document);

        // Save the document
        saveDocument(document);

        return document;
    }

    /**
     * Get a document by ID.
     *
     * @param fileId The file ID
     * @return The document
     */
    public PdfDocument getDocumentById(String fileId) {
        return pdfRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + fileId));
    }

    /**
     * Compare two PDF documents.
     *
     * @param baseFileId The ID of the base document
     * @param compareFileId The ID of the document to compare against the base
     * @param options Additional options for the comparison
     * @return The comparison ID
     * @throws IOException If there is an error comparing the documents
     */
    public String comparePdfs(String baseFileId, String compareFileId, Map<String, Object> options) throws IOException {
        // Get the documents
        PdfDocument baseDocument = getDocumentById(baseFileId);
        PdfDocument compareDocument = getDocumentById(compareFileId);

        // Generate a unique comparison ID
        String comparisonId = UUID.randomUUID().toString();

        // Start the comparison in the background
        comparisonTasks.computeIfAbsent(comparisonId, key -> {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    // Perform the comparison
                    ComparisonResult result = comparisonEngine.compareDocuments(baseDocument, compareDocument);

                    // Store the result
                    // In a real implementation, this would store the result in a database
                    // For now, we'll just return the comparison ID
                    return comparisonId;
                } catch (IOException e) {
                    log.error("Error comparing documents: {}", e.getMessage(), e);
                    throw new RuntimeException("Error comparing documents", e);
                }
            }, executorService);
        });

        return comparisonId;
    }

    /**
     * Calculate the content hash of a file.
     *
     * @param file The file
     * @return The content hash
     */
    private String calculateContentHash(File file) {
        try {
            // Create a MessageDigest instance for MD5
            MessageDigest md = MessageDigest.getInstance("MD5");

            // Read the file in chunks to avoid loading large files into memory
            byte[] buffer = new byte[8192];
            int bytesRead;

            // Open the file and read it
            try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                while ((bytesRead = fis.read(buffer)) != -1) {
                    md.update(buffer, 0, bytesRead);
                }
            }

            // Get the hash as a byte array
            byte[] digest = md.digest();

            // Convert the byte array to a hex string
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }

            return sb.toString();
        } catch (NoSuchAlgorithmException | IOException e) {
            log.error("Error calculating content hash: {}", e.getMessage(), e);
            // Return a random UUID as a fallback
            return UUID.randomUUID().toString();
        }
    }

    /**
     * Find a document by content hash.
     *
     * @param contentHash The content hash
     * @return The document, if found
     */
    private Optional<PdfDocument> findDocumentByContentHash(String contentHash) {
        // In a real implementation, this would query the database
        // For now, we'll just return an empty result
        return Optional.empty();
    }

    /**
     * Extract metadata from a document.
     *
     * @param document The document
     * @return The updated document
     */
    private PdfDocument extractMetadata(PdfDocument document) {
        try (PDDocument pdDocument = PDDocument.load(new File(document.getFilePath()))) {
            // Set the page count
            document.setPageCount(pdDocument.getNumberOfPages());

            // Extract metadata
            if (pdDocument.getDocumentInformation() != null) {
                document.setTitle(pdDocument.getDocumentInformation().getTitle());
                document.setAuthor(pdDocument.getDocumentInformation().getAuthor());
                document.setSubject(pdDocument.getDocumentInformation().getSubject());
                document.setKeywords(pdDocument.getDocumentInformation().getKeywords());
                document.setCreator(pdDocument.getDocumentInformation().getCreator());
                document.setProducer(pdDocument.getDocumentInformation().getProducer());
                document.setCreationDate(pdDocument.getDocumentInformation().getCreationDate() != null ?
                        pdDocument.getDocumentInformation().getCreationDate().toString() : null);
                document.setModificationDate(pdDocument.getDocumentInformation().getModificationDate() != null ?
                        pdDocument.getDocumentInformation().getModificationDate().toString() : null);
            }

            document.setEncrypted(pdDocument.isEncrypted());
        } catch (IOException e) {
            log.error("Error extracting metadata: {}", e.getMessage(), e);
        }

        return document;
    }

    /**
     * Save a document.
     *
     * @param document The document to save
     */
    private void saveDocument(PdfDocument document) {
        // In a real implementation, this would save the document to a database
        // For now, we'll just log that we're saving the document
        log.info("Saving document: {}", document.getFileId());
    }
}