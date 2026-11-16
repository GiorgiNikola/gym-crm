package com.giorgi.workload.repository;

import com.giorgi.workload.model.TrainerWorkload;

import java.time.YearMonth;
import java.util.Optional;

public record WorkloadUpdate(Optional<TrainerWorkload> before, Optional<TrainerWorkload> after) {

    public boolean stored() {
        return after.isPresent();
    }

    public long minutesBefore(YearMonth period) {
        return minutesIn(before, period);
    }

    public long minutesAfter(YearMonth period) {
        return minutesIn(after, period);
    }

    private static long minutesIn(Optional<TrainerWorkload> workload, YearMonth period) {
        return workload.map(found -> found.durationIn(period)).orElse(0L);
    }
}
