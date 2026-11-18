package com.giorgi.workload.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record MonthlyWorkloadResponse(

        @Schema(description = "The username of the trainer", example = "Sarah.Miller")
        String trainerUsername,

        @Schema(description = "The calendar year", example = "2026")
        int year,

        @Schema(description = "The month, from 1 to 12", example = "8")
        int month,

        @Schema(description = "The total training time in that month, in minutes", example = "150")
        long trainingSummaryDuration) {
}
