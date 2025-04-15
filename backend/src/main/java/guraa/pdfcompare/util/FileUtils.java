package guraa.pdfcompare.util;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Utility class for file operations with improved thread safety.
 */
@Slf4j
public class FileUtils {

    private static final ConcurrentHashMap<String, ReentrantLock> fileLocks = new ConcurrentHashMap<>();
    private static final int DEFAULT_LOCK_TIMEOUT_SECONDS = 10;
    private static final int DEFAULT_RETRY_COUNT = 3;
    private static final int DEFAULT_RETRY_DELAY_MS = 100;

    /**
     * Private constructor to prevent instantiation.
     */
    private FileUtils() {
        // Utility class, no instances allowed
    }

    /**
     * Copy a file with proper locking and error handling.
     *
     * @param source The source file
     * @param target The target file
     * @throws IOException If there is an error copying the file
     */
    public static void copyFile(File source, File target) throws IOException {
        copyFile(source, target, DEFAULT_RETRY_COUNT, DEFAULT_RETRY_DELAY_MS, DEFAULT_LOCK_TIMEOUT_SECONDS);
    }

    /**
     * Copy a file with proper locking and error handling.
     *
     * @param source The source file
     * @param target The target file
     * @param retryCount Number of retry attempts
     * @param retryDelayMs Delay between retry attempts in milliseconds
     * @param lockTimeoutSeconds Timeout for acquiring locks in seconds
     * @throws IOException If there is an error copying the file
     */
    public static void copyFile(File source, File target, int retryCount, int retryDelayMs, int lockTimeoutSeconds) throws IOException {
        if (!source.exists()) {
            throw new IOException("Source file does not exist: " + source.getPath());
        }

        if (!source.canRead()) {
            throw new IOException("Source file is not readable: " + source.getPath());
        }

        // Create parent directories if they don't exist
        if (target.getParentFile() != null && !target.getParentFile().exists()) {
            if (!target.getParentFile().mkdirs()) {
                throw new IOException("Failed to create parent directories for target file: " + target.getPath());
            }
        }

        // Get locks for both files to ensure thread safety
        ReentrantLock sourceLock = fileLocks.computeIfAbsent(source.getAbsolutePath(), k -> new ReentrantLock());
        ReentrantLock targetLock = fileLocks.computeIfAbsent(target.getAbsolutePath(), k -> new ReentrantLock());

        IOException lastException = null;

        for (int attempt = 0; attempt < retryCount; attempt++) {
            try {
                // Acquire locks for both files
                if (!sourceLock.tryLock(lockTimeoutSeconds, TimeUnit.SECONDS)) {
                    throw new IOException("Timed out waiting for source file access: " + source.getPath());
                }

                try {
                    if (!targetLock.tryLock(lockTimeoutSeconds, TimeUnit.SECONDS)) {
                        throw new IOException("Timed out waiting for target file access: " + target.getPath());
                    }

                    try {
                        // Use a temporary file for the copy to ensure atomicity
                        Path tempFile = Files.createTempFile(target.getParentFile().toPath(), "copy_", ".tmp");

                        try {
                            // Copy source to temp file
                            Files.copy(source.toPath(), tempFile, StandardCopyOption.REPLACE_EXISTING);

                            // Ensure the copy was successful
                            if (Files.size(tempFile) != Files.size(source.toPath())) {
                                throw new IOException("Copied file size differs from source file size");
                            }

                            // Atomically move temp file to target
                            Files.move(tempFile, target.toPath(), StandardCopyOption.REPLACE_EXISTING,
                                    StandardCopyOption.ATOMIC_MOVE);

                            return; // Success!
                        } finally {
                            // Clean up temp file if it still exists
                            try {
                                Files.deleteIfExists(tempFile);
                            } catch (Exception e) {
                                log.warn("Failed to delete temporary file: {}", e.getMessage());
                            }
                        }
                    } finally {
                        targetLock.unlock();
                    }
                } finally {
                    sourceLock.unlock();
                }
            } catch (IOException e) {
                lastException = e;
                log.warn("Attempt {} failed to copy file from {} to {}: {}",
                        attempt + 1, source.getPath(), target.getPath(), e.getMessage());

                if (attempt < retryCount - 1) {
                    try {
                        Thread.sleep(retryDelayMs * (attempt + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Thread interrupted during retry delay", ie);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Thread interrupted while waiting for file access", e);
            }
        }

        // All retries failed
        throw new IOException("Failed to copy file after " + retryCount + " attempts", lastException);
    }

    /**
     * Delete a file with proper locking and error handling.
     *
     * @param file The file to delete
     * @return true if the file was deleted, false otherwise
     */
    public static boolean deleteFile(File file) {
        return deleteFile(file, DEFAULT_RETRY_COUNT, DEFAULT_RETRY_DELAY_MS, DEFAULT_LOCK_TIMEOUT_SECONDS);
    }

    /**
     * Delete a file with proper locking and error handling.
     *
     * @param file The file to delete
     * @param retryCount Number of retry attempts
     * @param retryDelayMs Delay between retry attempts in milliseconds
     * @param lockTimeoutSeconds Timeout for acquiring locks in seconds
     * @return true if the file was deleted, false otherwise
     */
    public static boolean deleteFile(File file, int retryCount, int retryDelayMs, int lockTimeoutSeconds) {
        if (!file.exists()) {
            return true; // File doesn't exist, consider it deleted
        }

        // Get a lock for the file
        ReentrantLock fileLock = fileLocks.computeIfAbsent(file.getAbsolutePath(), k -> new ReentrantLock());

        for (int attempt = 0; attempt < retryCount; attempt++) {
            try {
                // Acquire lock for the file
                if (!fileLock.tryLock(lockTimeoutSeconds, TimeUnit.SECONDS)) {
                    log.warn("Timed out waiting for file access to delete: {}", file.getPath());
                    continue;
                }

                try {
                    // Delete the file
                    boolean deleted = file.delete();
                    if (deleted) {
                        // Remove the lock from the map
                        fileLocks.remove(file.getAbsolutePath());
                        return true;
                    }
                } finally {
                    fileLock.unlock();
                }

                // If deletion failed, wait before retrying
                if (attempt < retryCount - 1) {
                    Thread.sleep(retryDelayMs * (attempt + 1));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Thread interrupted while waiting for file access to delete: {}", file.getPath());
                return false;
            }
        }

        log.warn("Failed to delete file after {} attempts: {}", retryCount, file.getPath());
        return false;
    }

    /**
     * Create directories with proper error handling.
     *
     * @param directory The directory to create
     * @throws IOException If there is an error creating the directory
     */
    public static void createDirectories(File directory) throws IOException {
        createDirectories(directory, DEFAULT_RETRY_COUNT, DEFAULT_RETRY_DELAY_MS);
    }

    /**
     * Create directories with proper error handling.
     *
     * @param directory The directory to create
     * @param retryCount Number of retry attempts
     * @param retryDelayMs Delay between retry attempts in milliseconds
     * @throws IOException If there is an error creating the directory
     */
    public static void createDirectories(File directory, int retryCount, int retryDelayMs) throws IOException {
        if (directory.exists()) {
            if (!directory.isDirectory()) {
                throw new IOException("Path exists but is not a directory: " + directory.getPath());
            }
            return; // Directory already exists
        }

        IOException lastException = null;

        for (int attempt = 0; attempt < retryCount; attempt++) {
            try {
                if (directory.mkdirs() || directory.exists()) {
                    return; // Success!
                }
            } catch (SecurityException e) {
                lastException = new IOException("Security exception creating directories: " + e.getMessage(), e);
                log.warn("Attempt {} failed to create directories {}: {}",
                        attempt + 1, directory.getPath(), e.getMessage());
            }

            if (attempt < retryCount - 1) {
                try {
                    Thread.sleep(retryDelayMs * (attempt + 1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Thread interrupted during retry delay", ie);
                }
            }
        }

        // All retries failed
        if (lastException != null) {
            throw lastException;
        } else {
            throw new IOException("Failed to create directories after " + retryCount +
                    " attempts: " + directory.getPath());
        }
    }

    /**
     * Check if a file exists and is readable.
     *
     * @param file The file to check
     * @return true if the file exists and is readable, false otherwise
     */
    public static boolean isFileReadable(File file) {
        return file != null && file.exists() && file.isFile() && file.canRead();
    }

    /**
     * Check if a file exists, is readable, and is not empty.
     *
     * @param file The file to check
     * @return true if the file exists, is readable, and is not empty, false otherwise
     */
    public static boolean isFileValid(File file) {
        return isFileReadable(file) && file.length() > 0;
    }

    /**
     * Create a safe temporary file.
     *
     * @param prefix The prefix for the temp file name
     * @param suffix The suffix for the temp file name
     * @return The created temporary file
     * @throws IOException If there is an error creating the temporary file
     */
    public static File createTempFile(String prefix, String suffix) throws IOException {
        File tempFile = File.createTempFile(prefix, suffix);
        tempFile.deleteOnExit();
        return tempFile;
    }

    /**
     * Create a safe temporary file in a specific directory.
     *
     * @param directory The directory to create the temp file in
     * @param prefix The prefix for the temp file name
     * @param suffix The suffix for the temp file name
     * @return The created temporary file
     * @throws IOException If there is an error creating the temporary file
     */
    public static File createTempFile(File directory, String prefix, String suffix) throws IOException {
        // Ensure the directory exists
        createDirectories(directory);

        File tempFile = File.createTempFile(prefix, suffix, directory);
        tempFile.deleteOnExit();
        return tempFile;
    }
}