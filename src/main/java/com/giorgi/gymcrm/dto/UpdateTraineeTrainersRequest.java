package com.giorgi.gymcrm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateTraineeTrainersRequest(

        @NotNull
        @Schema(description = "The usernames of every trainer the trainee should end up with",
                example = "[\"Sarah.Miller\", \"Adam.Brown\"]")
        List<@NotBlank String> trainerUsernames) {
}
