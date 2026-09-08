package com.example.jobscheduler.job.event;

import java.time.Instant;
import java.util.Objects;

public record JobSucceededEvent(Long jobId, Instant occurredAt) implements JobEvent {

    public JobSucceededEvent {
        Objects.requireNonNull(jobId, "Job Id is Required");
    }

    public static JobSucceededEvent now(Long jobId) {
        return new JobSucceededEvent(jobId, Instant.now());
    }
}
