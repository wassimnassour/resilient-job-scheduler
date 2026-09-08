package com.example.jobscheduler.job.event;

import com.example.jobscheduler.eventbus.Event;

import java.time.Instant;

public sealed interface JobEvent extends Event permits JobSucceededEvent, JobExhaustedEvent, JobRetryScheduledEvent {
    Instant occurredAt();

    Long jobId();
}
