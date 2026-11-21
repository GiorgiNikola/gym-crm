package com.giorgi.workload.repository;

import com.giorgi.workload.model.TrainerWorkload;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public record WorkloadUpdate(Optional<TrainerWorkload> before, Optional<TrainerWorkload> after, boolean duplicate) {

    public WorkloadUpdate(Optional<TrainerWorkload> before, Optional<TrainerWorkload> after) {
        this(before, after, false);
    }

    public static WorkloadUpdate duplicateOf(Optional<TrainerWorkload> current) {
        return new WorkloadUpdate(current, current, true);
    }

    public boolean stored() {
        return after.isPresent();
    }

    public long minutesBefore(YearMonth period) {
        return minutesIn(before, period);
    }

    public long minutesAfter(YearMonth period) {
        return minutesIn(after, period);
    }

    public List<YearMonth> changedPeriods() {
        return Stream.concat(periodsOf(before), periodsOf(after))
                .distinct()
                .sorted()
                .filter(period -> minutesBefore(period) != minutesAfter(period))
                .toList();
    }

    private static long minutesIn(Optional<TrainerWorkload> workload, YearMonth period) {
        return workload.map(found -> found.durationIn(period)).orElse(0L);
    }

    private static Stream<YearMonth> periodsOf(Optional<TrainerWorkload> workload) {
        return workload.stream().flatMap(found -> found.years().stream()
                .flatMap(year -> year.months().stream()
                        .map(month -> YearMonth.of(year.year(), month.month()))));
    }
}
