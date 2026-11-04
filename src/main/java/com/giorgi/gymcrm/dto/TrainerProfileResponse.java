package com.giorgi.gymcrm.dto;

import com.giorgi.gymcrm.model.Trainer;
import com.giorgi.gymcrm.model.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Comparator;
import java.util.List;

public record TrainerProfileResponse(

        @Schema(description = "The username of the trainer", example = "Sarah.Miller")
        String username,

        @Schema(description = "The first name of the trainer", example = "Sarah")
        String firstName,

        @Schema(description = "The last name of the trainer", example = "Miller")
        String lastName,

        @Schema(description = "The training type the trainer teaches", example = "YOGA")
        String specialization,

        @Schema(description = "Whether the trainer is active", example = "true")
        Boolean isActive,

        @Schema(description = "The assigned trainees, sorted by username")
        List<TraineeSummary> trainees) {

    public static TrainerProfileResponse from(Trainer trainer) {
        User user = trainer.getUser();
        return new TrainerProfileResponse(
                user.getUsername(),
                user.getFirstName(),
                user.getLastName(),
                trainer.getSpecialization().getName(),
                user.isActive(),
                trainer.getTrainees().stream()
                        .map(TraineeSummary::from)
                        .sorted(Comparator.comparing(TraineeSummary::username))
                        .toList());
    }
}
