package com.giorgi.workload.repository;

import com.giorgi.workload.model.MonthSummary;
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
        return Optional.of(new TrainerWorkload("Sarah.Miller", "Sarah", "Miller", true,
                List.of(new YearSummary(2026, List.of(new MonthSummary(8, minutes))))));
    }

    @Test
    @DisplayName("an update with a workload after it counts as stored")
    void isStoredWhenWorkloadRemains() {
        Assertions.assertTrue(new WorkloadUpdate(Optional.empty(), workload(60)).stored());
    }

    @Test
    @DisplayName("an applied update is not a duplicate, and a duplicate keeps the current workload on both sides")
    void marksDuplicates() {
        WorkloadUpdate duplicate = WorkloadUpdate.duplicateOf(workload(60));

        Assertions.assertFalse(new WorkloadUpdate(Optional.empty(), workload(60)).duplicate());
        Assertions.assertTrue(duplicate.duplicate());
        Assertions.assertEquals(duplicate.before(), duplicate.after());
        Assertions.assertEquals(List.of(), duplicate.changedPeriods());
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

    @Test
    @DisplayName("only the months whose minutes moved are listed as changed, in calendar order")
    void listsChangedPeriods() {
        TrainerWorkload before = new TrainerWorkload("Sarah.Miller", "Sarah", "Miller", true, List.of(
                new YearSummary(2026, List.of(new MonthSummary(8, 60), new MonthSummary(9, 30)))));
        TrainerWorkload after = new TrainerWorkload("Sarah.Miller", "Sarah", "Miller", true, List.of(
                new YearSummary(2026, List.of(new MonthSummary(9, 30))),
                new YearSummary(2027, List.of(new MonthSummary(1, 45)))));

        WorkloadUpdate update = new WorkloadUpdate(Optional.of(after), Optional.of(before));
        WorkloadUpdate reversed = new WorkloadUpdate(Optional.of(before), Optional.of(after));

        Assertions.assertEquals(List.of(AUGUST, YearMonth.of(2027, 1)), update.changedPeriods());
        Assertions.assertEquals(List.of(AUGUST, YearMonth.of(2027, 1)), reversed.changedPeriods());
    }

    @Test
    @DisplayName("a first workload changes its months and an unchanged one changes none")
    void listsPeriodsOfNewAndUnchangedWorkload() {
        Assertions.assertEquals(List.of(AUGUST), new WorkloadUpdate(Optional.empty(), workload(60)).changedPeriods());
        Assertions.assertEquals(List.of(), new WorkloadUpdate(workload(60), workload(60)).changedPeriods());
        Assertions.assertEquals(List.of(), new WorkloadUpdate(Optional.empty(), Optional.empty()).changedPeriods());
    }
}
