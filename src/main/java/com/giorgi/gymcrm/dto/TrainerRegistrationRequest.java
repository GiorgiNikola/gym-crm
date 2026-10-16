package com.giorgi.gymcrm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TrainerRegistrationRequest(

        @NotBlank
        @Size(max = 50)
        @Schema(description = "The first name of the trainer", example = "Sarah")
        String firstName,

        @NotBlank
        @Size(max = 50)
        @Schema(description = "The last name of the trainer", example = "Miller")
        String lastName,

        @NotBlank
        @Schema(description = "The name of the training type the trainer teaches", example = "YOGA")
        String specialization) {
}
