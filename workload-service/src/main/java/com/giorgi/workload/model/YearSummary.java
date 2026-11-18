package com.giorgi.workload.model;

import java.util.List;

public record YearSummary(int year, List<MonthSummary> months) {

    public YearSummary {
        months = List.copyOf(months);
    }
}
