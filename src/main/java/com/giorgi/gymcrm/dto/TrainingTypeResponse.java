package com.giorgi.gymcrm.dto;

import com.giorgi.gymcrm.model.TrainingType;
import io.swagger.v3.oas.annotations.media.Schema;

public record TrainingTypeResponse(

        @Schema(description = "The id of the training type", example = "1")
        Long id,

        @Schema(description = "The name of the training type", example = "FITNESS")
        String name) {

    public static TrainingTypeResponse from(TrainingType type) {
        return new TrainingTypeResponse(type.getId(), type.getName());
    }
}
