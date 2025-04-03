package guraa.pdfcompare.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Configuration for thread pools and executor services.
 */
@Configuration
@EnableAsync
public class ExecutorConfig {

    @Value("${app.async.core-pool-size:4}")
    private int corePoolSize;

    @Value("${app.async.max-pool-size:8}")
    private int maxPoolSize;

    @Value("${app.async.queue-capacity:100}")
    private int queueCapacity;

    @Value("${app.comparison.executor.threads:2}")
    private int comparisonThreads;

    /**
     * Task executor for processing PDFs asynchronously.
     *
     * @return The thread pool task executor
     */
    @Bean(name = "pdfProcessingExecutor")
    public ThreadPoolTaskExecutor pdfProcessingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize / 2);
        executor.setMaxPoolSize(maxPoolSize / 2);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("pdf-proc-");

        // Use CallerRunsPolicy to avoid rejection when queue is full
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        executor.initialize();
        return executor;
    }

    /**
     * Task executor for comparing PDFs asynchronously.
     *
     * @return The thread pool task executor
     */
    @Bean(name = "comparisonExecutor")
    public ExecutorService comparisonExecutor() {
        // Using a fixed thread pool with the configured number of threads
        return Executors.newFixedThreadPool(comparisonThreads);
    }

    /**
     * Task executor for general background tasks.
     *
     * @return The thread pool task executor
     */
    @Bean(name = "taskExecutor")
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("task-exec-");

        // Use CallerRunsPolicy to avoid rejection when queue is full
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        executor.initialize();
        return executor;
    }
}