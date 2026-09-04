package com.example.jobscheduler.job.scheduler;

import com.example.jobscheduler.job.handler.JobHandler;
import com.example.jobscheduler.job.handler.JobHandlerRegistry;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class JobScheduler {

    private final JobHandlerRegistry jobHandlerRegistry;

    public JobScheduler(JobHandlerRegistry jobHandlerRegistry) {
        this.jobHandlerRegistry = jobHandlerRegistry;
    }

    public void executeJob(String jobType, JsonNode payload) throws Exception {
        JobHandler handler = jobHandlerRegistry.getHandler(jobType);
        handler.execute(payload);
    }
}
