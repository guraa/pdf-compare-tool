package guraa.pdfcompare.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Configuration for thread pools and concurrency settings.
 * This class provides centralized management of thread pools with improved
 * error handling and monitoring.
 */
@Slf4j
@Configuration
@EnableAsync
@ConfigurationProperties(prefix = "app.concurrency")
public class ConcurrencyConfig {

    /**
     * Core pool size for the thread pools.
     */
    private int corePoolSize = Runtime.getRuntime().availableProcessors();

    /**
     * Maximum pool size for the thread pools.
     */
    private int maxPoolSize = Runtime.getRuntime().availableProcessors() * 2;

    /**
     * Queue capacity for the thread pools.
     */
    private int queueCapacity = 100;

    /**
     * Number of threads for PDF comparison operations.
     */
    private int comparisonThreads = 4;

    /**
     * Number of threads for PDF page processing operations.
     */
    private int pageProcessingThreads = 8;

    /**
     * Number of threads for PDF rendering operations.
     */
    private int renderingThreads = 2;

    /**
     * Timeout in seconds for thread termination during shutdown.
     */
    private int shutdownTimeoutSeconds = 10;

    /**
     * Maximum concurrent image comparisons.
     */
    private int maxConcurrentImageComparisons = 4;

    /**
     * Maximum concurrent PDF renderings.
     */
    private int maxConcurrentRenderings = 2;

    /**
     * Retry count for failed operations.
     */
    private int retryCount = 3;

    /**
     * Delay between retry attempts in milliseconds.
     */
    private int retryDelayMs = 100;

    /**
     * Timeout for acquiring locks in seconds.
     */
    private int lockTimeoutSeconds = 10;

    /**
     * Task executor for processing PDFs asynchronously.
     *
     * @return The thread pool task executor
     */
    @Bean(name = "pdfProcessingExecutor")
    public ThreadPoolTaskExecutor pdfProcessingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("pdf-proc-");
        executor.setAwaitTerminationSeconds(shutdownTimeoutSeconds);
        executor.setWaitForTasksToCompleteOnShutdown(true);

        // Use CallerRunsPolicy to avoid rejection when queue is full
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // Monitor rejected tasks
        executor.setRejectedExecutionHandler((r, e) -> {
            log.warn("Task rejected by PDF processing executor. Current queue size: {}", e.getQueue().size());
            // Use caller runs policy
            if (!e.isShutdown()) {
                r.run();
            }
        });

        executor.initialize();
        return executor;
    }

    /**
     * Task executor specifically for processing PDF pages in parallel.
     * This executor is optimized for CPU-intensive tasks like rendering and text extraction.
     *
     * @return The thread pool executor
     */
    @Bean(name = "pdfPageProcessingExecutor")
    @Primary
    public ExecutorService pdfPageProcessingExecutor() {
        // Use the number of available processors, but limit to a reasonable number
        // to avoid excessive resource usage
        int numThreads = Math.min(Runtime.getRuntime().availableProcessors(), pageProcessingThreads);
        log.info("Creating PDF page processing executor with {} threads", numThreads);
        return Executors.newFixedThreadPool(numThreads, r -> {
            Thread thread = new Thread(r);
            thread.setName("pdf-page-" + thread.getId());
            thread.setUncaughtExceptionHandler((t, e) -> {
                log.error("Uncaught exception in PDF page processing thread {}: {}", t.getName(), e.getMessage(), e);
            });
            return thread;
        });
    }

    /**
     * Task executor for comparing PDFs asynchronously.
     *
     * @return The thread pool task executor
     */
    @Bean(name = "comparisonExecutor")
    public ExecutorService comparisonExecutor() {
        // Using a fixed thread pool with the configured number of threads
        log.info("Creating comparison executor with {} threads", comparisonThreads);
        return Executors.newFixedThreadPool(comparisonThreads, r -> {
            Thread thread = new Thread(r);
            thread.setName("compare-" + thread.getId());
            thread.setUncaughtExceptionHandler((t, e) -> {
                log.error("Uncaught exception in comparison thread {}: {}", t.getName(), e.getMessage(), e);
            });
            return thread;
        });
    }

    /**
     * Task executor for PDF rendering operations.
     *
     * @return The thread pool executor
     */
    @Bean(name = "renderingExecutor")
    public ExecutorService renderingExecutor() {
        log.info("Creating rendering executor with {} threads", renderingThreads);
        return Executors.newFixedThreadPool(renderingThreads, r -> {
            Thread thread = new Thread(r);
            thread.setName("render-" + thread.getId());
            thread.setUncaughtExceptionHandler((t, e) -> {
                log.error("Uncaught exception in rendering thread {}: {}", t.getName(), e.getMessage(), e);
            });
            return thread;
        });
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
        executor.setAwaitTerminationSeconds(shutdownTimeoutSeconds);
        executor.setWaitForTasksToCompleteOnShutdown(true);

        // Monitor rejected tasks
        executor.setRejectedExecutionHandler((r, e) -> {
            log.warn("Task rejected by general task executor. Current queue size: {}", e.getQueue().size());
            // Use caller runs policy
            if (!e.isShutdown()) {
                r.run();
            }
        });

        executor.initialize();
        return executor;
    }

    // Getters and setters

    public int getCorePoolSize() {
        return corePoolSize;
    }

    public void setCorePoolSize(int corePoolSize) {
        this.corePoolSize = corePoolSize;
    }

    public int getMaxPoolSize() {
        return maxPoolSize;
    }
}