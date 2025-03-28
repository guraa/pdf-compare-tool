package guraa.pdfcompare.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for the application
 */
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Storage storage = new Storage();
    private final Comparison comparison = new Comparison();
    private final Cors cors = new Cors();

    public Storage getStorage() {
        return storage;
    }

    public Comparison getComparison() {
        return comparison;
    }

    public Cors getCors() {
        return cors;
    }

    /**
     * Storage configuration properties
     */
    public static class Storage {
        private String location;

        public String getLocation() {
            return location;
        }

        public void setLocation(String location) {
            this.location = location;
        }
    }

    /**
     * Comparison configuration properties
     */
    public static class Comparison {
        private String resultExpiration = "24h";

        public String getResultExpiration() {
            return resultExpiration;
        }

        public void setResultExpiration(String resultExpiration) {
            this.resultExpiration = resultExpiration;
        }
    }

    /**
     * CORS configuration properties
     */
    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>();
        private List<String> allowedMethods = new ArrayList<>();
        private Long maxAge = 3600L;

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }

        public List<String> getAllowedMethods() {
            return allowedMethods;
        }

        public void setAllowedMethods(List<String> allowedMethods) {
            this.allowedMethods = allowedMethods;
        }

        public Long getMaxAge() {
            return maxAge;
        }

        public void setMaxAge(Long maxAge) {
            this.maxAge = maxAge;
        }
    }
}