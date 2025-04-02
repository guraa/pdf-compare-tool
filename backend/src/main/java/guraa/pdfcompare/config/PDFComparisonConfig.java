package guraa.pdfcompare.config;

import guraa.pdfcompare.PDFComparisonEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for PDF comparison beans
 */
@Configuration
public class PDFComparisonConfig {

    /**
     * Creates a PDFComparisonEngine bean for dependency injection
     * @return The PDFComparisonEngine instance
     */
    @Bean
    public PDFComparisonEngine pdfComparisonEngine() {
        return new PDFComparisonEngine();
    }
}