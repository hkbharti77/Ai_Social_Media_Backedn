package com.aiplatform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * AiTaskExecutorConfig - Implements the Bulkhead Pattern by isolating SYNC and BACKGROUND workloads.
 */
@Configuration
@EnableAsync
public class AiTaskExecutorConfig {

    /**
     * Specialized pool for User-Facing (Sync) requests.
     * Small queue to ensure "Fail Fast" behavior and prevent request stalling.
     */
    @Bean(name = "syncAiTaskExecutor")
    public Executor syncAiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(25);
        executor.setQueueCapacity(20); // Small queue for UX responsiveness
        executor.setThreadNamePrefix("AI-Sync-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.initialize();
        return executor;
    }

    /**
     * Specialized pool for Background/Batch requests.
     * Large queue to accommodate high-volume campaigns without affecting sync performance.
     */
    @Bean(name = "backgroundAiTaskExecutor")
    public Executor backgroundAiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(15);
        executor.setQueueCapacity(500); // Large queue for batch processing
        executor.setThreadNamePrefix("AI-Bg-");
        
        // Background tasks should wait or be handled by a persistent job store
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.initialize();
        return executor;
    }
}
