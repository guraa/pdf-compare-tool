package guraa.pdfcompare.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;

/**
 * Configuration for server timeouts and connection settings.
 */
@Configuration
public class ServerTimeoutConfig {

    /**
     * Customize the Tomcat server factory to set appropriate timeouts for
     * long-running PDF comparison operations.
     *
     * @return WebServerFactoryCustomizer
     */
    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatCustomizer() {
        return tomcat -> {
            // Set connection timeout to 5 minutes (300000 milliseconds)
            tomcat.addConnectorCustomizers(connector -> {
                connector.setProperty("connectionTimeout", "300000");
            });
        };
    }
}