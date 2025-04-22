package guraa.pdfcompare.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Configuration for data persistence and JPA settings.
 */
@Configuration
@EnableTransactionManagement
@EntityScan("guraa.pdfcompare.model")
@EnableJpaRepositories("guraa.pdfcompare.repository")
public class PersistenceConfig {

    /**
     * Creates a PersistenceExceptionTranslationPostProcessor bean for better
     * exception handling.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public PersistenceExceptionTranslationPostProcessor persistenceExceptionTranslationPostProcessor() {
        return new PersistenceExceptionTranslationPostProcessor();
    }
}