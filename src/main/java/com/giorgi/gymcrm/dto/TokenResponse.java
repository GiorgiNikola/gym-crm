package com.giorgi.gymcrm.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record TokenResponse(

        @Schema(description = "A signed JWT to send as Authorization: Bearer on every other call",
                example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJKb2huLlNtaXRoIn0.3rGhPx1mJ4K8")
        String token) {
}
