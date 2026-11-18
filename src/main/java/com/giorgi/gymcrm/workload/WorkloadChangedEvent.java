package com.giorgi.gymcrm.workload;

import java.time.Instant;
import java.util.UUID;

public record WorkloadChangedEvent(UUID eventId, Instant occurredAt, WorkloadChange change) {

    public static WorkloadChangedEvent of(WorkloadChange change) {
        return new WorkloadChangedEvent(UUID.randomUUID(), Instant.now(), change);
    }
}
