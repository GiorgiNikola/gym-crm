package com.giorgi.workload.model;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

class WorkloadChangeTest {

    private static final String USERNAME = "Sarah.Miller";
    private static final LocalDate DATE = LocalDate.of(2026, 8, 20);

    private WorkloadChange change(ActionType actionType, int minutes) {
        return new WorkloadChange(USERNAME, "Sarah", "Miller", true, DATE, minutes, actionType);
    }

    private Optional<TrainerWorkload> existing(long minutes) {
        return Optional.of(new TrainerWorkload(USERNAME, "Sarah", "Miller", true,
                List.of(new YearSummary(2026, List.of(new MonthSummary(8, minutes))))));
    }

    @Test
    @DisplayName("the period is the year and month of the training date")
    void derivesPeriodFromDate() {
        Assertions.assertEquals(YearMonth.of(2026, 8), change(ActionType.ADD, 60).period());
    }

    @Test
    @DisplayName("an add for an unknown trainer creates the workload from the change")
    void addCreatesWorkload() {
        TrainerWorkload created = change(ActionType.ADD, 60).applyTo(Optional.empty()).orElseThrow();

        Assertions.assertEquals(USERNAME, created.trainerUsername());
        Assertions.assertEquals("Sarah", created.trainerFirstName());
        Assertions.assertEquals("Miller", created.trainerLastName());
        Assertions.assertTrue(created.trainerStatus());
        Assertions.assertEquals(60, created.durationIn(2026, 8));
    }

    @Test
    @DisplayName("an add for a known trainer adds to what is there")
    void addExtendsWorkload() {
        TrainerWorkload updated = change(ActionType.ADD, 60).applyTo(existing(90)).orElseThrow();

        Assertions.assertEquals(150, updated.durationIn(2026, 8));
    }

    @Test
    @DisplayName("a delete for an unknown trainer creates nothing")
    void deleteLeavesUnknownTrainerUnknown() {
        Assertions.assertTrue(change(ActionType.DELETE, 60).applyTo(Optional.empty()).isEmpty());
    }

    @Test
    @DisplayName("a delete for a known trainer subtracts from what is there")
    void deleteReducesWorkload() {
        TrainerWorkload updated = change(ActionType.DELETE, 60).applyTo(existing(90)).orElseThrow();

        Assertions.assertEquals(30, updated.durationIn(2026, 8));
    }
}
