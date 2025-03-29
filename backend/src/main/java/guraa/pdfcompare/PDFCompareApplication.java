package guraa.pdfcompare;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;


/**
 * Main application class for the PDF comparison tool
 */
@SpringBootApplication
public class PDFCompareApplication {

    private static final Logger logger = LoggerFactory.getLogger(PDFCompareApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(PDFCompareApplication.class, args);
    }

    /**
     * Configure CORS for development
     * @return WebMvcConfigurer with CORS configuration
     */
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins("http://localhost:3000")
                        .allowedMethods("GET", "POST", "PUT", "DELETE")
                        .allowedHeaders("*")
                        .allowCredentials(true);
            }
        };
    }

    /**
     * Configure file upload settings
     * @return MultipartConfigElement with upload configuration
     */
    @Bean
    public org.springframework.boot.web.servlet.MultipartConfigFactory multipartConfigFactory() {
        org.springframework.boot.web.servlet.MultipartConfigFactory factory = new org.springframework.boot.web.servlet.MultipartConfigFactory();

        // Set max file size to 50MB
        factory.setMaxFileSize(org.springframework.util.unit.DataSize.ofMegabytes(50));
        factory.setMaxRequestSize(org.springframework.util.unit.DataSize.ofMegabytes(50));

        // Set file size threshold for when to write to disk
        factory.setFileSizeThreshold(org.springframework.util.unit.DataSize.ofKilobytes(512));

        // Location for temporary files
        try {
            Path tempDir = Files.createDirectories(Paths.get(System.getProperty("java.io.tmpdir"), "pdf-compare-uploads"));
            factory.setLocation(tempDir.toString());
        } catch (Exception e) {
            logger.warn("Could not create custom temp directory for uploads, using system default", e);
        }

        return factory;
    }
}
