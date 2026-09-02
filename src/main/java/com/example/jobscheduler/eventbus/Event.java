package com.example.jobscheduler.eventbus;

import java.time.Instant;

public interface Event {
    Instant occurredAt();
}
