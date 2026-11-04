package com.giorgi.gymcrm.dto;

import com.giorgi.gymcrm.model.Trainer;
import com.giorgi.gymcrm.model.User;
import io.swagger.v3.oas.annotations.media.Schema;

public record TrainerSummary(

        @Schema(description = "The username of the trainer", example = "Sarah.Miller")
        String username,

        @Schema(description = "The first name of the trainer", example = "Sarah")
        String firstName,

        @Schema(description = "The last name of the trainer", example = "Miller")
        String lastName,

        @Schema(description = "The training type the trainer teaches", example = "YOGA")
        String specialization) {

    public static TrainerSummary from(Trainer trainer) {
        User user = trainer.getUser();
        return new TrainerSummary(user.getUsername(), user.getFirstName(), user.getLastName(),
                trainer.getSpecialization().getName());
    }
}
