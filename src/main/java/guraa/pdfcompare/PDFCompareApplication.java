package guraa.pdfcompare;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


/**
 * Main application class for the PDF comparison tool
 */
@SpringBootApplication
public class PDFCompareApplication {

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

        return factory;
    }
}
