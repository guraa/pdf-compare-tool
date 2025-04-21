package guraa.pdfcompare.config;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Optimized configuration for thread pools and concurrency settings.
 * This class provides centralized management of thread pools with improved
 * error handling, monitoring, and adaptive scaling based on system resources.
 */
@Slf4j
@Configuration
@EnableAsync
@ConfigurationProperties(prefix = "app.concurrency")
public class ConcurrencyConfig {

    // System information for adaptive scaling
    private final int availableProcessors = Runtime.getRuntime().availableProcessors();
    private final long totalMemory = Runtime.getRuntime().maxMemory() / (1024 * 1024); // in MB

    // Default values based on system resources
    @Getter @Setter
    private int corePoolSize = calculateOptimalCorePoolSize();

    @Getter @Setter
    private int maxPoolSize = calculateOptimalMaxPoolSize();

    @Getter @Setter
    private int queueCapacity = 100;

    @Getter @Setter
    private int comparisonThreads = calculateOptimalComparisonThreads();

    @Getter @Setter
    private int pageProcessingThreads = calculateOptimalPageProcessingThreads();

    @Getter @Setter
    private int renderingThreads = calculateOptimalRenderingThreads();

    @Getter @Setter
    private int shutdownTimeoutSeconds = 30;

    @Value("${app.concurrency.memory-per-thread:96}")
    private int memoryPerThreadMB = 96; // Reduced memory estimate per thread in MB

    /**
     * Calculate optimal core pool size based on system resources.
     *
     * @return The optimal core pool size
     */
    private int calculateOptimalCorePoolSize() {
        // Use available processors as a baseline, with a minimum of 2
        return Math.max(2, availableProcessors / 2);
    }

    /**
     * Calculate optimal maximum pool size based on system resources.
     *
     * @return The optimal maximum pool size
     */
    private int calculateOptimalMaxPoolSize() {
        // Use available processors and memory as constraints
        int byProcessor = availableProcessors;

        // Prevent division by zero
        int memoryLimit = (memoryPerThreadMB <= 0) ? 256 : memoryPerThreadMB;

        // Calculate based on available memory (protect against zero or very low values)
        int byMemory = (totalMemory <= 0) ? 4 : (int) Math.max(2, totalMemory / memoryLimit);

        // Return the minimum of the two constraints, with a minimum of 2 threads
        return Math.max(2, Math.min(byProcessor, byMemory));
    }

    /**
     * Calculate optimal comparison threads based on system resources.
     *
     * @return The optimal number of comparison threads
     */
    private int calculateOptimalComparisonThreads() {
        // PDF comparison is CPU and memory intensive, but we can use more processors
        // Increase the number of threads to handle more concurrent operations
        return Math.max(6, (int)(availableProcessors * 0.9));
    }

    /**
     * Calculate optimal page processing threads based on system resources.
     *
     * @return The optimal number of page processing threads
     */
    private int calculateOptimalPageProcessingThreads() {
        // Page processing can be parallelized more
        // Increase the number of threads to handle more concurrent operations
        return Math.max(6, (int)(availableProcessors * 1.25));
    }

    /**
     * Calculate optimal rendering threads based on system resources.
     *
     * @return The optimal number of rendering threads
     */
    private int calculateOptimalRenderingThreads() {
        // Rendering is memory-intensive but we can optimize it
        // Increase the number of threads to handle more concurrent operations
        return Math.max(4, (int)(availableProcessors * 0.75));
    }

    /**
     * Task executor for processing PDFs asynchronously.
     *
     * @return The thread pool task executor
     */
    @Bean(name = "pdfProcessingExecutor")
    public ThreadPoolTaskExecutor pdfProcessingExecutor() {
        ThreadPoolTaskExecutor executor = createBaseExecutor("pdf-proc-");
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
        int numThreads = pageProcessingThreads;
        log.info("Creating PDF page processing executor with {} threads", numThreads);

        return Executors.newFixedThreadPool(numThreads, createThreadFactory("pdf-page-"));
    }

    /**
     * Task executor for comparing PDFs asynchronously.
     *
     * @return The thread pool executor
     */
    @Bean(name = "comparisonExecutor")
    public ExecutorService comparisonExecutor() {
        int numThreads = comparisonThreads;
        log.info("Creating comparison executor with {} threads", numThreads);

        return Executors.newFixedThreadPool(numThreads, createThreadFactory("compare-"));
    }

    /**
     * Rendering executor specifically for PDF rendering operations.
     *
     * @return The executor service for rendering
     */
    @Bean(name = "renderingExecutor")
    public ExecutorService renderingExecutor() {
        int numThreads = renderingThreads;
        log.info("Creating rendering executor with {} threads", numThreads);

        return Executors.newFixedThreadPool(numThreads, createThreadFactory("render-"));
    }

    /**
     * Task executor for general background tasks.
     *
     * @return The thread pool task executor
     */
    @Bean(name = "taskExecutor")
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = createBaseExecutor("task-exec-");
        return executor;
    }

    /**
     * Create a base ThreadPoolTaskExecutor with common settings.
     *
     * @param threadNamePrefix The prefix for thread names
     * @return The configured executor
     */
    private ThreadPoolTaskExecutor createBaseExecutor(String threadNamePrefix) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setAwaitTerminationSeconds(shutdownTimeoutSeconds);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAllowCoreThreadTimeOut(true); // Allow idle threads to time out

        // Monitor rejected tasks
        executor.setRejectedExecutionHandler((r, e) -> {
            log.warn("Task rejected by executor {}. Current queue size: {} of {}",
                    threadNamePrefix, e.getQueue().size(), queueCapacity);

            // Use caller runs policy if not shutdown
            if (!e.isShutdown()) {
                log.info("Executing rejected task in caller thread");
                r.run();
            }
        });

        executor.initialize();
        return executor;
    }

    /**
     * Create a thread factory with proper naming and error handling.
     *
     * @param prefix The prefix for thread names
     * @return The thread factory
     */
    private ThreadFactory createThreadFactory(String prefix) {
        return new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setName(prefix + threadNumber.getAndIncrement());
                thread.setDaemon(false); // Use non-daemon threads

                // Handle uncaught exceptions
                thread.setUncaughtExceptionHandler((t, e) -> {
                    log.error("Uncaught exception in thread {}: {}", t.getName(), e.getMessage(), e);
                });

                return thread;
            }
        };
    }
}
