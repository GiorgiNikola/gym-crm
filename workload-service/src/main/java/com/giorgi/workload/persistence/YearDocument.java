package com.giorgi.workload.persistence;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record YearDocument(
        int year,
        @NotNull List<@Valid @NotNull MonthDocument> months) {
}
