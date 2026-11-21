package com.giorgi.workload.dto;

import com.giorgi.workload.model.ActionType;
import com.giorgi.workload.model.WorkloadChange;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record WorkloadRequest(

        @NotBlank
        @Schema(description = "The username of the trainer", example = "Sarah.Miller")
        String trainerUsername,

        @NotBlank
        @Schema(description = "The first name of the trainer", example = "Sarah")
        String trainerFirstName,

        @NotBlank
        @Schema(description = "The last name of the trainer", example = "Miller")
        String trainerLastName,

        @NotNull
        @Schema(description = "Whether the trainer is active", example = "true")
        Boolean isActive,

        @NotNull
        @Schema(description = "The day of the training, as yyyy-MM-dd", example = "2026-08-01")
        LocalDate trainingDate,

        @NotNull
        @Positive
        @Schema(description = "How long the training lasts, in minutes", example = "60")
        Integer trainingDuration,

        @NotNull
        @Schema(description = "ADD when a training is planned, DELETE when it is cancelled", example = "ADD")
        ActionType actionType) {

    public WorkloadChange toChange() {
        return new WorkloadChange(trainerUsername, trainerFirstName, trainerLastName,
                isActive, trainingDate, trainingDuration, actionType);
    }
}
