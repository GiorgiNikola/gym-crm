package com.giorgi.gymcrm.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record TrainerWorkloadResponse(

        @Schema(description = "The username of the trainer", example = "Sarah.Miller")
        String trainerUsername,

        @Schema(description = "The calendar year", example = "2026")
        int year,

        @Schema(description = "The month, from 1 to 12", example = "8")
        int month,

        @Schema(description = "The total training time in that month, in minutes; 0 when there is none yet",
                example = "150")
        long trainingSummaryDuration) {
}
