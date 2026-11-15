package com.giorgi.gymcrm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(

        @NotBlank
        @Schema(description = "The username, as handed out at registration", example = "John.Smith")
        String username,

        @NotBlank
        @Schema(description = "The current password", example = "aX7kQ2mN9p")
        String password) {
}
