package com.giorgi.workload.persistence;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;

public record MonthDocument(
        @Min(1) @Max(12) int month,
        @PositiveOrZero long trainingSummaryDuration) {
}
