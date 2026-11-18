package com.giorgi.workload.repository;

import com.giorgi.workload.model.MonthSummary;
import com.giorgi.workload.model.TrainerStatus;
import com.giorgi.workload.model.TrainerWorkload;
import com.giorgi.workload.model.YearSummary;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

class WorkloadUpdateTest {

    private static final YearMonth AUGUST = YearMonth.of(2026, 8);

    private Optional<TrainerWorkload> workload(long minutes) {
        return Optional.of(new TrainerWorkload("Sarah.Miller", "Sarah", "Miller", TrainerStatus.ACTIVE,
                List.of(new YearSummary(2026, List.of(new MonthSummary(8, minutes))))));
    }

    @Test
    @DisplayName("an update with a workload after it counts as stored")
    void isStoredWhenWorkloadRemains() {
        Assertions.assertTrue(new WorkloadUpdate(Optional.empty(), workload(60)).stored());
    }

    @Test
    @DisplayName("an update with no workload after it does not count as stored")
    void isNotStoredWhenNothingRemains() {
        Assertions.assertFalse(new WorkloadUpdate(Optional.empty(), Optional.empty()).stored());
    }

    @Test
    @DisplayName("the minutes before and after come from the two workloads")
    void readsMinutesOnBothSides() {
        WorkloadUpdate update = new WorkloadUpdate(workload(90), workload(150));

        Assertions.assertEquals(90, update.minutesBefore(AUGUST));
        Assertions.assertEquals(150, update.minutesAfter(AUGUST));
    }

    @Test
    @DisplayName("a missing workload counts as zero minutes")
    void readsZeroFromMissingWorkload() {
        WorkloadUpdate update = new WorkloadUpdate(Optional.empty(), Optional.empty());

        Assertions.assertEquals(0, update.minutesBefore(AUGUST));
        Assertions.assertEquals(0, update.minutesAfter(AUGUST));
    }
}
