package com.giorgi.workload.model;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

public record WorkloadChange(String trainerUsername,
                             String trainerFirstName,
                             String trainerLastName,
                             boolean trainerStatus,
                             LocalDate trainingDate,
                             int trainingDuration,
                             ActionType actionType) {

    public YearMonth period() {
        return YearMonth.from(trainingDate);
    }

    public Optional<TrainerWorkload> applyTo(Optional<TrainerWorkload> existing) {
        if (actionType == ActionType.ADD) {
            return Optional.of(existing.orElseGet(this::emptyWorkload).add(this));
        }
        return existing.map(workload -> workload.subtract(this));
    }

    private TrainerWorkload emptyWorkload() {
        return new TrainerWorkload(trainerUsername, trainerFirstName, trainerLastName, trainerStatus, List.of());
    }
}
