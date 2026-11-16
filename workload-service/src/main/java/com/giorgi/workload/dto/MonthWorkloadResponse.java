package com.giorgi.workload.dto;

import com.giorgi.workload.model.MonthSummary;
import io.swagger.v3.oas.annotations.media.Schema;

public record MonthWorkloadResponse(

        @Schema(description = "The month, from 1 to 12", example = "8")
        int month,

        @Schema(description = "The total training time in that month, in minutes", example = "150")
        long trainingSummaryDuration) {

    public static MonthWorkloadResponse from(MonthSummary summary) {
        return new MonthWorkloadResponse(summary.month(), summary.trainingSummaryDuration());
    }
}
