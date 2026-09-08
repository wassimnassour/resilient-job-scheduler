package com.example.jobscheduler.job.event;

import java.time.Instant;
import java.util.Objects;


public record JobExhaustedEvent(Long jobId, Instant occurredAt) implements JobEvent {
    public JobExhaustedEvent {
        Objects.requireNonNull(jobId, "Job Id is Required");
    }

    public static JobExhaustedEvent now(Long jobId) {
        return new JobExhaustedEvent(jobId, Instant.now());
    }
}