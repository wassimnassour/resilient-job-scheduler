package com.example.jobscheduler.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class JobExecutorConfig {

    @Bean(name = "jobExecutor", destroyMethod = "shutdown")
    public ThreadPoolExecutor jobExecutor(
            @Value("${scheduler.worker-count:5}") int workerCount,
            @Value("${scheduler.queue-capacity:10}") int queueCapacity
    ) {
        return new ThreadPoolExecutor(
                workerCount,
                workerCount,
                0,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }
}
