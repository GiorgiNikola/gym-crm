package com.giorgi.gymcrm.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record RegistrationResponse(

        @Schema(description = "The generated username", example = "John.Smith")
        String username,

        @Schema(description = "The generated password", example = "aX7kQ2mN9p")
        String password) {
}
