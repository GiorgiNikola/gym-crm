package com.giorgi.workload.dto;

import com.giorgi.workload.model.TrainerWorkload;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record TrainerWorkloadResponse(

        @Schema(description = "The username of the trainer", example = "Sarah.Miller")
        String trainerUsername,

        @Schema(description = "The first name of the trainer", example = "Sarah")
        String trainerFirstName,

        @Schema(description = "The last name of the trainer", example = "Miller")
        String trainerLastName,

        @Schema(description = "Whether the trainer is active, as the latest change reported it", example = "true")
        boolean trainerStatus,

        @Schema(description = "The years with training time, in ascending order")
        List<YearWorkloadResponse> years) {

    public static TrainerWorkloadResponse from(TrainerWorkload workload) {
        return new TrainerWorkloadResponse(
                workload.trainerUsername(),
                workload.trainerFirstName(),
                workload.trainerLastName(),
                workload.trainerStatus(),
                workload.years().stream().map(YearWorkloadResponse::from).toList());
    }
}
