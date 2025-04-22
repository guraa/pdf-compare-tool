package guraa.pdfcompare.service;

import com.itextpdf.kernel.pdf.PdfDate;
import com.itextpdf.kernel.pdf.PdfDocumentInfo;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.ReaderProperties;
import guraa.pdfcompare.PDFComparisonEngine;
import guraa.pdfcompare.model.ComparisonResult;
import guraa.pdfcompare.model.PdfDocument;
import guraa.pdfcompare.repository.PdfRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
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
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.Calendar;
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
public class PdfService {

    private final PdfRepository pdfRepository;
    private final ExecutorService executorService;
    private final PDFComparisonEngine comparisonEngine;

    /**
     * Constructor with qualifier to specify which executor service to use.
     *
     * @param pdfRepository The PDF repository
     * @param executorService The executor service for comparison operations
     * @param comparisonEngine The PDF comparison engine
     */
    public PdfService(
            PdfRepository pdfRepository,
            @Qualifier("comparisonExecutor") ExecutorService executorService,
            PDFComparisonEngine comparisonEngine) {
        this.pdfRepository = pdfRepository;
        this.executorService = executorService;
        this.comparisonEngine = comparisonEngine;
    }

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
        // Query the repository for documents with the given content hash
        return pdfRepository.findByContentHash(contentHash).stream().findFirst();
    }

    /**
     * Format a Calendar object as a string.
     *
     * @param calendar The Calendar object to format
     * @return The formatted date string
     */
    private String formatCalendarDate(Calendar calendar) {
        if (calendar == null) {
            return null;
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(calendar.getTime());
    }

    /**
     * Extract metadata from a document.
     *
     * @param document The document
     * @return The updated document
     */
    private PdfDocument extractMetadata(PdfDocument document) {
        try {
            // Use ReaderProperties to handle potential encryption without a password
            ReaderProperties readerProperties = new ReaderProperties();
            // If you need to handle password-protected files, you'd set the password here:
            // readerProperties.setPassword("password".getBytes());

            PdfReader reader = new PdfReader(document.getFilePath(), readerProperties);
            // Use try-with-resources for the iText PdfDocument
            try (com.itextpdf.kernel.pdf.PdfDocument iTextPdfDocument = new com.itextpdf.kernel.pdf.PdfDocument(reader)) {
                // Set the page count
                document.setPageCount(iTextPdfDocument.getNumberOfPages());

                // Extract metadata
                PdfDocumentInfo info = iTextPdfDocument.getDocumentInfo();
                if (info != null) {
                    document.setTitle(info.getTitle());
                    document.setAuthor(info.getAuthor());
                    document.setSubject(info.getSubject());
                    document.setKeywords(info.getKeywords());
                    document.setCreator(info.getCreator());
                    document.setProducer(info.getProducer());

                    // Format dates properly using iText's PdfDate utility and standard keys
                    document.setCreationDate(
                            formatCalendarDate(PdfDate.decode(info.getMoreInfo("CreationDate")))
                    );
                    document.setModificationDate(
                            formatCalendarDate(PdfDate.decode(info.getMoreInfo("ModDate"))) // ModDate is the standard key
                    );
                    // No need for fallback, getMoreInfo handles missing keys returning null
                }

                // Check encryption status via the reader
                document.setEncrypted(reader.isEncrypted());
            }
        } catch (IOException e) {
            // Log specific iText exceptions if needed, e.g., BadPasswordException
            log.error("Error extracting metadata using iText: {}", e.getMessage(), e);
        } catch (Exception e) {
            // Catch broader exceptions during iText processing
            log.error("Unexpected error during iText metadata extraction: {}", e.getMessage(), e);
        }

        return document;
    }

    /**
     * Save a document.
     *
     * @param document The document to save
     */
    private void saveDocument(PdfDocument document) {
        try {
            log.info("Saving document: {}", document.getFileId());
            // Actually save the document to the repository
            pdfRepository.save(document);
        } catch (Exception e) {
            log.error("Error saving document to repository: {}", e.getMessage(), e);
            throw e; // Re-throw to allow calling code to handle the error
        }
    }
}