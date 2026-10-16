package com.giorgi.gymcrm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(

        @NotBlank
        @Schema(description = "The password the user has now", example = "aX7kQ2mN9p")
        String oldPassword,

        @NotBlank
        @Schema(description = "The password the user wants instead", example = "bY8lR3nO0q")
        String newPassword) {
}
