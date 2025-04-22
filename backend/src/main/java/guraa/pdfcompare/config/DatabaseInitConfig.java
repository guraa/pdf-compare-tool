package guraa.pdfcompare.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;

/**
 * Configuration for database schema initialization.
 * This ensures that database tables are properly created before the application starts.
 */
@Configuration
public class DatabaseInitConfig {

    @Value("${spring.jpa.hibernate.ddl-auto:none}")
    private String ddlAuto;

    /**
     * Initialize the database with schema if needed.
     * This will only run if spring.jpa.hibernate.ddl-auto is not set to "create" or "create-drop".
     */
    @Bean
    @ConditionalOnProperty(name = "spring.jpa.hibernate.ddl-auto", havingValue = "none")
    public DataSourceInitializer dataSourceInitializer(DataSource dataSource) {
        ResourceDatabasePopulator resourceDatabasePopulator = new ResourceDatabasePopulator();
        resourceDatabasePopulator.addScript(new ClassPathResource("schema.sql"));

        DataSourceInitializer dataSourceInitializer = new DataSourceInitializer();
        dataSourceInitializer.setDataSource(dataSource);
        dataSourceInitializer.setDatabasePopulator(resourceDatabasePopulator);
        return dataSourceInitializer;
    }
}