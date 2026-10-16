package com.giorgi.gymcrm.dto;

import com.giorgi.gymcrm.model.Training;
import com.giorgi.gymcrm.model.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

public record TraineeTrainingResponse(

        @Schema(description = "The name of the training", example = "Morning cardio")
        String trainingName,

        @Schema(description = "The day the training happens", example = "2026-08-01")
        LocalDate trainingDate,

        @Schema(description = "The training type, taken from the trainer", example = "YOGA")
        String trainingType,

        @Schema(description = "How long the training lasts, in minutes", example = "60")
        Long trainingDuration,

        @Schema(description = "The username of the trainer", example = "Sarah.Miller")
        String trainerUsername,

        @Schema(description = "The first and last name of the trainer", example = "Sarah Miller")
        String trainerName) {

    public static TraineeTrainingResponse from(Training training) {
        User trainer = training.getTrainer().getUser();
        return new TraineeTrainingResponse(
                training.getName(),
                training.getDate(),
                training.getType().getName(),
                training.getDuration(),
                trainer.getUsername(),
                trainer.getFirstName() + " " + trainer.getLastName());
    }
}
