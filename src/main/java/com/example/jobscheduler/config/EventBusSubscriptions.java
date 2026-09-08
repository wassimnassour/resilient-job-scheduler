package com.example.jobscheduler.config;

import com.example.jobscheduler.eventbus.EventBus;
import com.example.jobscheduler.job.event.JobEvent;
import com.example.jobscheduler.job.event.JobExhaustedEvent;
import com.example.jobscheduler.job.event.JobRetryScheduledEvent;
import com.example.jobscheduler.job.event.JobSucceededEvent;
import com.example.jobscheduler.job.listener.JobEventListener;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EventBusSubscriptions {

    private final EventBus eventBus;
    private final JobEventListener jobEventListener;


    public EventBusSubscriptions(EventBus eventBus, JobEventListener jobEventListener) {

        this.eventBus = eventBus;
        this.jobEventListener = jobEventListener;
    }

    @PostConstruct
    public void registerSubscriptions() {
        eventBus.subscribe(JobEvent.class, jobEventListener::log);
        eventBus.subscribe(JobExhaustedEvent.class, jobEventListener::logExhaustedJobs);
    }
}
