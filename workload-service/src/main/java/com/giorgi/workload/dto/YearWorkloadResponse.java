package com.giorgi.workload.dto;

import com.giorgi.workload.model.YearSummary;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record YearWorkloadResponse(

        @Schema(description = "The calendar year", example = "2026")
        int year,

        @Schema(description = "The months with training time, in ascending order")
        List<MonthWorkloadResponse> months) {

    public static YearWorkloadResponse from(YearSummary summary) {
        return new YearWorkloadResponse(summary.year(),
                summary.months().stream().map(MonthWorkloadResponse::from).toList());
    }
}
