package com.example.jobscheduler.config;

import com.example.jobscheduler.eventbus.EventBus;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EventBusSubscriptions {

    private final EventBus eventBus;

    public EventBusSubscriptions(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    @PostConstruct
    public void registerSubscriptions() {
        // TODO: Register your event bus listeners here
    }
}
