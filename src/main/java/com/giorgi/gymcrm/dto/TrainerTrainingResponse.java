package com.giorgi.gymcrm.dto;

import com.giorgi.gymcrm.model.Training;
import com.giorgi.gymcrm.model.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

public record TrainerTrainingResponse(

        @Schema(description = "The name of the training", example = "Morning cardio")
        String trainingName,

        @Schema(description = "The day the training happens", example = "2026-08-01")
        LocalDate trainingDate,

        @Schema(description = "The training type, taken from the trainer", example = "YOGA")
        String trainingType,

        @Schema(description = "How long the training lasts, in minutes", example = "60")
        Long trainingDuration,

        @Schema(description = "The username of the trainee", example = "John.Smith")
        String traineeUsername,

        @Schema(description = "The first and last name of the trainee", example = "John Smith")
        String traineeName) {

    public static TrainerTrainingResponse from(Training training) {
        User trainee = training.getTrainee().getUser();
        return new TrainerTrainingResponse(
                training.getName(),
                training.getDate(),
                training.getType().getName(),
                training.getDuration(),
                trainee.getUsername(),
                trainee.getFirstName() + " " + trainee.getLastName());
    }
}
