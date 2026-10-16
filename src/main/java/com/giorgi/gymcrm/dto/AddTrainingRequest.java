package com.giorgi.gymcrm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record AddTrainingRequest(

        @NotBlank
        @Schema(description = "The username of the trainee", example = "John.Smith")
        String traineeUsername,

        @NotBlank
        @Schema(description = "The username of the trainer, whose specialization becomes the training type",
                example = "Sarah.Miller")
        String trainerUsername,

        @NotBlank
        @Size(max = 100)
        @Schema(description = "The name of the training", example = "Morning cardio")
        String trainingName,

        @NotNull
        @Schema(description = "The day the training happens", example = "2026-08-01")
        LocalDate trainingDate,

        @NotNull
        @Positive
        @Schema(description = "How long the training lasts, in minutes", example = "60")
        Long trainingDuration) {
}
