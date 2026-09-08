package com.example.jobscheduler.job.event;

import java.time.Instant;
import java.util.Objects;


public record JobRetryScheduledEvent(Long jobId, Instant occurredAt) implements JobEvent {
    public JobRetryScheduledEvent {
        Objects.requireNonNull(jobId, "Job Id is Required");
    }

    public static JobRetryScheduledEvent now(Long jobId) {
        return new JobRetryScheduledEvent(jobId, Instant.now());
    }
}