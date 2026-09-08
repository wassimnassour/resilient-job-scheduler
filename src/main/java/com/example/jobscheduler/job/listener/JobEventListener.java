package com.example.jobscheduler.job.listener;

import com.example.jobscheduler.job.event.JobEvent;
import com.example.jobscheduler.job.event.JobExhaustedEvent;
import com.example.jobscheduler.job.event.JobRetryScheduledEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class JobEventListener {
    private static final Logger logger = LoggerFactory.getLogger(JobEventListener.class);

    public void log(JobEvent event) {
        logger.info("JobEventListener Job Success Type:{} | JobId:{} , Time:{}", event.getClass().getSimpleName(), event.jobId(), event.occurredAt());
    }

    public void logExhaustedJobs(JobExhaustedEvent event) {
        logger.info("JobEventListener Job Exhausted {}", event.jobId());
    }

    public void logRetryJobs(JobRetryScheduledEvent event) {
        logger.info("JobEventListener Job Retry Scheduled {}", event.jobId());
    }

}
