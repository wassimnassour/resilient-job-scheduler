package com.example.jobscheduler.job.handler;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class JobHandlerRegistry {
    private final Map<String, JobHandler> handlers = new HashMap<>();


    public JobHandlerRegistry(List<JobHandler> listJobHandlers) {


        for (JobHandler handler : listJobHandlers) {
            String jobType = handler.getType();
            if (handlers.containsKey(jobType)) {
                throw new IllegalStateException(
                        "Multiple handlers registered for job type: " + jobType
                );

            }
            handlers.put(jobType, handler);
        }

    }

    public JobHandler getHandler(String jobType) throws IllegalArgumentException {
        if (!handlers.containsKey(jobType)) {
            throw new IllegalArgumentException("This job Type is not Supported");
        }
        return handlers.get(jobType);
    }
}
