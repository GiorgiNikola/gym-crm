package com.giorgi.gymcrm.dto;

import com.giorgi.gymcrm.model.Trainee;
import com.giorgi.gymcrm.model.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

public record TraineeProfileResponse(

        @Schema(description = "The username of the trainee", example = "John.Smith")
        String username,

        @Schema(description = "The first name of the trainee", example = "John")
        String firstName,

        @Schema(description = "The last name of the trainee", example = "Smith")
        String lastName,

        @Schema(description = "The date of birth, null when it is not stored", example = "1998-05-14")
        LocalDate dateOfBirth,

        @Schema(description = "The home address, null when it is not stored",
                example = "123 Main St New York")
        String address,

        @Schema(description = "Whether the trainee is active", example = "true")
        Boolean isActive,

        @Schema(description = "The assigned trainers, sorted by username")
        List<TrainerSummary> trainers) {

    public static TraineeProfileResponse from(Trainee trainee) {
        User user = trainee.getUser();
        return new TraineeProfileResponse(
                user.getUsername(),
                user.getFirstName(),
                user.getLastName(),
                trainee.getDateOfBirth(),
                trainee.getAddress(),
                user.isActive(),
                trainee.getTrainers().stream()
                        .map(TrainerSummary::from)
                        .sorted(Comparator.comparing(TrainerSummary::username))
                        .toList());
    }
}
