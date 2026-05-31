package com.example.aipr.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 任务级异步执行器：每个 PR Review 任务独占一个线程。
     */
    @Bean("reviewAsyncExecutor")
    public Executor reviewAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(6);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("review-task-");
        executor.initialize();
        return executor;
    }

    /**
     * 文件级并发执行器：单个任务内多个文件并行调用 AI。
     * 核心线程数由 review.ai.file-review-concurrency 控制，默认 3。
     */
    @Bean("fileReviewExecutor")
    public Executor fileReviewExecutor(ReviewProperties reviewProperties) {
        int concurrency = reviewProperties.getAi().getFileReviewConcurrency();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(concurrency);
        executor.setMaxPoolSize(concurrency);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("file-review-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
