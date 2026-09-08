package com.example.jobscheduler.config;

import com.example.jobscheduler.eventbus.EventBus;
import com.example.jobscheduler.eventbus.impl.AsyncEventBus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;

@Configuration
public class EventBusConfig {

    @Value("${eventbus.worker-threads:4}")
    private int workerThreads;


    @Bean()
    public EventBus eventBus() {
        return new AsyncEventBus(Executors.newFixedThreadPool(workerThreads), workerThreads);
    }

}
