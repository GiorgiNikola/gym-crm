package com.giorgi.gymcrm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateTrainerRequest(

        @NotBlank
        @Size(max = 50)
        @Schema(description = "The first name of the trainer", example = "Sarah")
        String firstName,

        @NotBlank
        @Size(max = 50)
        @Schema(description = "The last name of the trainer", example = "Miller")
        String lastName,

        @NotNull
        @Schema(description = "The state the trainer should be in. Sending the current state again is "
                + "accepted, unlike PATCH /users/{username}/status", example = "true")
        Boolean isActive) {
}
