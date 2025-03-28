package guraa.pdfcompare.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web configuration class
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private AppProperties appProperties;

    /**
     * Configure CORS
     * @param registry The CORS registry
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(appProperties.getCors().getAllowedOrigins().toArray(new String[0]))
                .allowedMethods(appProperties.getCors().getAllowedMethods().toArray(new String[0]))
                .allowCredentials(true)
                .maxAge(appProperties.getCors().getMaxAge());
    }

    /**
     * Configure resource handlers
     * @param registry The resource handler registry
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Add resource handlers for static resources
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/");
    }
}