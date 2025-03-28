package guraa.pdfcompare.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application configuration
 */
@Configuration
@EnableScheduling
public class AppConfig {

    /**
     * Configure Jackson ObjectMapper
     * @return The configured ObjectMapper
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // Configure as needed (e.g., date formats, etc.)
        return mapper;
    }
}