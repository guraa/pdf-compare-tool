package guraa.pdfcompare.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuration for PDF rendering settings and thread pools.
 */
@Configuration
@EnableAsync
public class PdfRenderingConfig {

    /**
     * Configure the thread pool for PDF rendering tasks.
     * This pool uses a limited number of threads to avoid excessive memory usage.
     *
     * @return The executor for PDF rendering tasks
     */
    @Bean(name = "pdfRenderingExecutor")
    public Executor pdfRenderingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Limit to a reasonable number of concurrent rendering tasks
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("pdf-render-");

        // Allow threads to time out if idle
        executor.setKeepAliveSeconds(120);
        executor.setAllowCoreThreadTimeOut(true);

        return executor;
    }

    // This bean has been moved to ConcurrencyConfig to centralize thread pool management
    // and avoid bean definition conflicts

    /**
     * Configure a general-purpose task executor for asynchronous operations.
     *
     * @return The executor for general tasks
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("task-");
        return executor;
    }
}
