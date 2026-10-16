package com.giorgi.gymcrm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record StatusRequest(

        @NotNull
        @Schema(description = "The state the user should end up in", example = "false")
        Boolean isActive) {
}
