package com.giorgi.gymcrm.workload;

public record MonthlyWorkloadResponse(
        String trainerUsername,
        int year,
        int month,
        long trainingSummaryDuration) {
}
