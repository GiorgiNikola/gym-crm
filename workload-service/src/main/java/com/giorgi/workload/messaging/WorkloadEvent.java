package com.giorgi.workload.messaging;

import com.giorgi.workload.model.ActionType;
import com.giorgi.workload.model.TrainerStatus;
import com.giorgi.workload.model.WorkloadChange;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record WorkloadEvent(
        @NotNull UUID eventId,
        @NotNull Instant occurredAt,
        @NotBlank String trainerUsername,
        @NotBlank String trainerFirstName,
        @NotBlank String trainerLastName,
        @NotNull Boolean isActive,
        @NotNull LocalDate trainingDate,
        @NotNull @Positive Integer trainingDuration,
        @NotNull ActionType actionType) {

    public WorkloadChange toChange() {
        return new WorkloadChange(trainerUsername, trainerFirstName, trainerLastName,
                TrainerStatus.of(isActive), trainingDate, trainingDuration, actionType);
    }
}
