package guraa.pdfcompare.config;

import guraa.pdfcompare.PDFComparisonEngine;
import guraa.pdfcompare.service.EnhancedMatchingService;
import guraa.pdfcompare.service.EnhancedPageMatcher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuration for enhanced PDF comparison components
 */
@Configuration
@EnableAsync
public class EnhancedMatchingConfig {

    /**
     * Configure the enhanced page matcher
     * @return EnhancedPageMatcher
     */
    @Bean
    public EnhancedPageMatcher enhancedPageMatcher() {
        return new EnhancedPageMatcher();
    }

    /**
     * Configure the enhanced matching service
     * @param comparisonEngine The PDF comparison engine
     * @return EnhancedMatchingService
     */
    @Bean
    public EnhancedMatchingService enhancedMatchingService(PDFComparisonEngine comparisonEngine) {
        return new EnhancedMatchingService(comparisonEngine);
    }

    /**
     * Configure an async task executor for comparison tasks
     * @return Executor
     */
    @Bean(name = "comparisonTaskExecutor")
    public Executor comparisonTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("PDF-Comparison-");
        executor.initialize();
        return executor;
    }
}