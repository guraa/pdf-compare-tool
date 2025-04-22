package guraa.pdfcompare.config;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Configuration for thread pools and concurrency settings.
 * Optimized for lower resource usage to prevent system overload.
 */
@Slf4j
@Configuration
@EnableAsync
public class ConcurrencyConfig {

    // System information for adaptive scaling
    private final int availableProcessors = Runtime.getRuntime().availableProcessors();

    @Value("${app.concurrency.rendering-threads:2}")
    @Getter @Setter
    private int renderingThreads = Math.min(2, availableProcessors);

    @Value("${app.concurrency.comparison-threads:4}")
    @Getter @Setter
    private int comparisonThreads = Math.min(4, availableProcessors);

    @Value("${app.concurrency.page-processing-threads:3}")
    @Getter @Setter
    private int pageProcessingThreads = Math.min(3, availableProcessors);

    @Value("${app.concurrency.shutdown-timeout-seconds:30}")
    @Getter @Setter
    private int shutdownTimeoutSeconds = 30;

    /**
     * Task executor for rendering PDFs.
     * This executor is optimized for memory-intensive operations.
     */
    @Bean(name = "renderingExecutor")
    public ExecutorService renderingExecutor() {
        log.info("Creating rendering executor with {} threads", renderingThreads);
        return Executors.newFixedThreadPool(renderingThreads, createThreadFactory("render-", Thread.NORM_PRIORITY - 1));
    }

    /**
     * Task executor for comparing PDFs.
     */
    @Bean(name = "comparisonExecutor")
    public ExecutorService comparisonExecutor() {
        log.info("Creating comparison executor with {} threads", comparisonThreads);
        return Executors.newFixedThreadPool(comparisonThreads, createThreadFactory("compare-", Thread.NORM_PRIORITY));
    }

    /**
     * Task executor specifically for processing PDF pages in parallel.
     */
    @Bean(name = "pdfPageProcessingExecutor")
    @Primary
    public ExecutorService pdfPageProcessingExecutor() {
        log.info("Creating PDF page processing executor with {} threads", pageProcessingThreads);
        return Executors.newFixedThreadPool(pageProcessingThreads, createThreadFactory("pdf-page-", Thread.NORM_PRIORITY));
    }

    /**
     * Create a thread factory with proper naming, priority and error handling.
     *
     * @param prefix Thread name prefix
     * @param priority Thread priority
     * @return A ThreadFactory
     */
    private ThreadFactory createThreadFactory(String prefix, int priority) {
        return new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setName(prefix + threadNumber.getAndIncrement());
                thread.setPriority(priority);
                thread.setDaemon(false);

                // Handle uncaught exceptions
                thread.setUncaughtExceptionHandler((t, e) -> {
                    log.error("Uncaught exception in thread {}: {}", t.getName(), e.getMessage(), e);
                });

                return thread;
            }
        };
    }
}