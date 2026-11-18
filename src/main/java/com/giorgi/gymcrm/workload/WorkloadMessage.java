package com.giorgi.gymcrm.workload;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record WorkloadMessage(
        UUID eventId,
        Instant occurredAt,
        String trainerUsername,
        String trainerFirstName,
        String trainerLastName,
        boolean isActive,
        LocalDate trainingDate,
        int trainingDuration,
        ActionType actionType) {

    public static WorkloadMessage of(WorkloadChangedEvent event) {
        WorkloadChange change = event.change();
        return new WorkloadMessage(event.eventId(), event.occurredAt(), change.trainerUsername(),
                change.trainerFirstName(), change.trainerLastName(), change.isActive(), change.trainingDate(),
                change.trainingDuration(), change.actionType());
    }
}
