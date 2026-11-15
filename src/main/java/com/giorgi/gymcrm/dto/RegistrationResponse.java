package com.giorgi.gymcrm.dto;

import com.giorgi.gymcrm.facade.RegisteredProfile;
import io.swagger.v3.oas.annotations.media.Schema;

public record RegistrationResponse(

        @Schema(description = "The generated username", example = "John.Smith")
        String username,

        @Schema(description = "The generated password, shown this once and never again",
                example = "aX7kQ2mN9p")
        String password,

        @Schema(description = "A signed JWT, so the caller does not have to log in right after registering",
                example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJKb2huLlNtaXRoIn0.3rGhPx1mJ4K8")
        String token) {

    public static RegistrationResponse from(RegisteredProfile registered) {
        return new RegistrationResponse(registered.username(), registered.password(), registered.token());
    }
}
