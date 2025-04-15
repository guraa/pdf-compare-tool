package guraa.pdfcompare;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.annotation.PreDestroy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Main application class for PDF Compare with performance optimizations.
 * Optimized for Java 17.
 */
@Slf4j
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class PDFCompareApplication {

    private static final List<String> REQUIRED_DIRECTORIES = List.of(
            "uploads",
            "uploads/documents",
            "uploads/comparisons",
            "uploads/reports",
            "uploads/temp",
            "uploads/debug",
            "logs"
    );

    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(30);

    public static void main(String[] args) {
        Instant startTime = Instant.now();

        // Set JVM options for better performance
        configureJVMOptions();

        // Pre-flight checks
        boolean preflightPassed = performPreflightChecks();

        if (!preflightPassed) {
            log.error("Pre-flight checks failed. Application may not function correctly.");
            // Continue anyway, but warn the user
        }

        // Create necessary directories
        createDirectories();

        // Start Spring Boot application
        ApplicationContext context = SpringApplication.run(PDFCompareApplication.class, args);

        // Log successful startup
        Instant endTime = Instant.now();
        Duration startupTime = Duration.between(startTime, endTime);
        logStartupInfo(startupTime);
    }

    /**
     * Configure JVM options for better performance.
     */
    private static void configureJVMOptions() {
        // Set system properties that might improve performance
        System.getProperties().putIfAbsent("java.awt.headless", "true");
        System.getProperties().putIfAbsent("com.sun.management.jmxremote", "false");
        System.getProperties().putIfAbsent("networkaddress.cache.ttl", "300");

        // Enable preview features if running on Java 17+
        String javaVersion = System.getProperty("java.version");
        if (javaVersion != null && javaVersion.startsWith("17")) {
            log.info("Running on Java 17, enabling advanced optimizations");
        }

        // Set the Xmx value (informational only)
        long maxMemory = Runtime.getRuntime().maxMemory();
        if (maxMemory != Long.MAX_VALUE) {
            long suggestedXmx = Math.min((long)(maxMemory * 0.8), 4L * 1024L * 1024L * 1024L);
            log.info("Current heap size: {} MB, suggested heap size: {} MB",
                    maxMemory / (1024 * 1024), suggestedXmx / (1024 * 1024));
        }
    }

    /**
     * Perform pre-flight checks to ensure the environment is properly set up.
     *
     * @return true if all checks pass, false otherwise
     */
    private static boolean performPreflightChecks() {
        boolean allPassed = true;

        // Check Java version
        String javaVersion = System.getProperty("java.version");
        if (javaVersion != null) {
            log.info("Java version: {}", javaVersion);

            // Verify we're running on Java 17 or higher
            if (!javaVersion.startsWith("17") && !javaVersion.startsWith("18") &&
                    !javaVersion.startsWith("19") && !javaVersion.startsWith("20") &&
                    !javaVersion.startsWith("21")) {
                log.warn("This application is optimized for Java 17+. Current version: {}", javaVersion);
                // Don't fail for this, just warn
            }
        }

        // Check available memory
        long maxMemory = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        log.info("Max memory (JVM): {} MB", maxMemory);

        if (maxMemory < 512) {
            log.warn("Low memory available. At least 512MB is recommended.");
            allPassed = false;
        }

        // Check available disk space
        try {
            var path = Path.of(".");
            long availableSpace = Files.getFileStore(path).getUsableSpace() / (1024 * 1024);
            log.info("Available disk space: {} MB", availableSpace);

            if (availableSpace < 500) {
                log.warn("Low disk space available. At least 500MB is recommended.");
                allPassed = false;
            }
        } catch (Exception e) {
            log.warn("Failed to check disk space: {}", e.getMessage());
            allPassed = false;
        }

        // Check processor cores
        int processors = Runtime.getRuntime().availableProcessors();
        log.info("Available processors: {}", processors);

        if (processors < 2) {
            log.warn("Application performance may be limited with only 1 processor core.");
            allPassed = false;
        }

        return allPassed;
    }

    /**
     * Create necessary directories for the application.
     */
    private static void createDirectories() {
        int successCount = 0;

        for (String dirPath : REQUIRED_DIRECTORIES) {
            try {
                var dir = Path.of(dirPath);
                if (Files.notExists(dir)) {
                    Files.createDirectories(dir);
                    log.debug("Created directory: {}", dirPath);
                } else {
                    log.debug("Directory already exists: {}", dirPath);
                }
                successCount++;
            } catch (Exception e) {
                log.error("Failed to create directory {}: {}", dirPath, e.getMessage(), e);
            }
        }

        log.info("Created {}/{} application directories", successCount, REQUIRED_DIRECTORIES.size());
    }

    /**
     * Log information about the application startup.
     *
     * @param startupTime The time taken to start up
     */
    private static void logStartupInfo(Duration startupTime) {
        log.info("==========================================================");
        log.info("PDF Compare application started in {}", formatDuration(startupTime));
        log.info("System information:");
        log.info("  Java: {}", System.getProperty("java.version"));
        log.info("  OS: {} {}", System.getProperty("os.name"), System.getProperty("os.version"));
        log.info("  Available processors: {}", Runtime.getRuntime().availableProcessors());
        log.info("  JVM Max memory: {} MB", Runtime.getRuntime().maxMemory() / (1024 * 1024));
        log.info("==========================================================");
    }

    /**
     * Format a duration to a readable string.
     *
     * @param duration The duration
     * @return A formatted string (e.g., "2m 30s")
     */
    private static String formatDuration(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        long millis = duration.toMillisPart();

        if (hours > 0) {
            return String.format("%dh %dm %d.%03ds", hours, minutes, seconds, millis);
        } else if (minutes > 0) {
            return String.format("%dm %d.%03ds", minutes, seconds, millis);
        } else {
            return String.format("%d.%03ds", seconds, millis);
        }
    }

    /**
     * Event listener for application shutdown.
     * This ensures clean shutdown of all resources.
     *
     * @param event The context closed event
     */
    @EventListener
    public void onApplicationEvent(ContextClosedEvent event) {
        log.info("Application is shutting down, cleaning up resources...");

        // Additional cleanup can be done here
    }

    /**
     * Cleanup method that runs before application shutdown.
     */
    @PreDestroy
    public void cleanup() {
        log.info("Cleaning up temporary files...");

        try {
            // Clean up temporary files
            var tempDir = Path.of("uploads/temp");
            if (Files.exists(tempDir)) {
                try (Stream<Path> tempFiles = Files.list(tempDir)) {
                    tempFiles
                            .filter(path -> path.toString().endsWith(".tmp"))
                            .forEach(path -> {
                                try {
                                    Files.delete(path);
                                } catch (Exception e) {
                                    log.warn("Failed to delete temporary file: {}", path);
                                }
                            });
                }
            }
        } catch (Exception e) {
            log.error("Error cleaning up temporary files: {}", e.getMessage(), e);
        }
    }

    /**
     * Clean shutdown hook for executor services.
     *
     * @return A bean that gracefully shuts down executor services
     */
    @Bean
    public ExecutorServiceShutdownHook executorServiceShutdownHook() {
        return new ExecutorServiceShutdownHook();
    }

    /**
     * Utility class to handle clean shutdown of executor services.
     */
    public static class ExecutorServiceShutdownHook {

        /**
         * Shut down an executor service gracefully.
         *
         * @param executorService The executor service to shut down
         * @param serviceName The name of the service (for logging)
         */
        public void shutdownGracefully(ExecutorService executorService, String serviceName) {
            if (executorService != null && !executorService.isShutdown()) {
                try {
                    log.info("Shutting down {} executor service...", serviceName);
                    executorService.shutdown();

                    if (!executorService.awaitTermination(SHUTDOWN_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                        log.warn("{} executor did not terminate in time, forcing shutdown", serviceName);
                        var remainingTasks = executorService.shutdownNow();
                        log.info("{} task(s) were canceled during forced shutdown", remainingTasks.size());
                    }

                    log.info("{} executor service shut down successfully", serviceName);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("{} executor shutdown interrupted", serviceName);
                    executorService.shutdownNow();
                } catch (Exception e) {
                    log.error("Error shutting down {} executor: {}", serviceName, e.getMessage());
                    executorService.shutdownNow();
                }
            }
        }
    }
}