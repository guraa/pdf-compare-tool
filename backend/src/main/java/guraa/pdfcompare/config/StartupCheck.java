package guraa.pdfcompare.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Verifies that the application has the necessary resources and configuration to run properly
 */
@Configuration
public class StartupCheck {
    private static final Logger logger = LoggerFactory.getLogger(StartupCheck.class);

    @Bean
    public CommandLineRunner checkEnvironment() {
        return args -> {
            logger.info("Performing startup environment check...");

            // Check available memory
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            long maxHeapSize = memoryBean.getHeapMemoryUsage().getMax() / (1024 * 1024);
            logger.info("Maximum heap size: {} MB", maxHeapSize);

            if (maxHeapSize < 512) {
                logger.warn("Available heap memory may be too low for comparing large PDF files. " +
                        "Consider increasing the JVM heap size with -Xmx1024m or higher.");
            }

            // Check temporary directory
            String tempDir = System.getProperty("java.io.tmpdir");
            Path tempPath = Paths.get(tempDir);

            if (!Files.isWritable(tempPath)) {
                logger.error("Temporary directory is not writable: {}", tempDir);
            } else {
                logger.info("Temporary directory available at: {}", tempDir);

                // Check free space
                long freeSpace = new File(tempDir).getFreeSpace() / (1024 * 1024);
                logger.info("Free space in temp directory: {} MB", freeSpace);

                if (freeSpace < 1024) {
                    logger.warn("Low disk space detected. PDF comparison requires adequate disk space.");
                }
            }

            // Verify PDF libraries availability
            try {
                Class.forName("org.apache.pdfbox.pdmodel.PDDocument");
                logger.info("PDFBox library is available");
            } catch (ClassNotFoundException e) {
                logger.error("PDFBox library not found. PDF comparison will not work properly.", e);
            }

            logger.info("Startup environment check completed.");
        };
    }
}