package com.giorgi.gymcrm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateTraineeRequest(

        @NotBlank
        @Size(max = 50)
        @Schema(description = "The first name of the trainee", example = "John")
        String firstName,

        @NotBlank
        @Size(max = 50)
        @Schema(description = "The last name of the trainee", example = "Smith")
        String lastName,

        @Past
        @Schema(description = "The date of birth. Leaving it out clears the stored one",
                example = "1998-05-14")
        LocalDate dateOfBirth,

        @Size(max = 255)
        @Schema(description = "The home address. Leaving it out clears the stored one",
                example = "123 Main St New York")
        String address,

        @NotNull
        @Schema(description = "The state the trainee should be in. Sending the current state again is "
                + "accepted, unlike PATCH /users/{username}/status", example = "true")
        Boolean isActive) {
}
