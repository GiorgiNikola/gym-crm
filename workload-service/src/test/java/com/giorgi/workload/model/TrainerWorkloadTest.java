package com.giorgi.workload.model;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

class TrainerWorkloadTest {

    private static final String USERNAME = "Sarah.Miller";
    private static final LocalDate AUGUST_1 = LocalDate.of(2026, 8, 1);
    private static final LocalDate AUGUST_20 = LocalDate.of(2026, 8, 20);
    private static final LocalDate SEPTEMBER_5 = LocalDate.of(2026, 9, 5);
    private static final LocalDate NEXT_JANUARY_10 = LocalDate.of(2027, 1, 10);

    private TrainerWorkload empty() {
        return new TrainerWorkload(USERNAME, "Sarah", "Miller", TrainerStatus.ACTIVE, List.of());
    }

    private WorkloadChange add(LocalDate date, int minutes) {
        return new WorkloadChange(USERNAME, "Sarah", "Miller", TrainerStatus.ACTIVE, date, minutes, ActionType.ADD);
    }

    private WorkloadChange delete(LocalDate date, int minutes) {
        return new WorkloadChange(USERNAME, "Sarah", "Miller", TrainerStatus.ACTIVE, date, minutes, ActionType.DELETE);
    }

    private YearSummary year(int year, MonthSummary... months) {
        return new YearSummary(year, List.of(months));
    }

    private MonthSummary month(int month, long minutes) {
        return new MonthSummary(month, minutes);
    }

    @Test
    @DisplayName("the first add creates the year and the month")
    void addCreatesYearAndMonth() {
        TrainerWorkload workload = empty().add(add(AUGUST_1, 60));

        Assertions.assertEquals(List.of(year(2026, month(8, 60))), workload.years());
    }

    @Test
    @DisplayName("adds in the same month are summed")
    void addSumsWithinMonth() {
        TrainerWorkload workload = empty().add(add(AUGUST_1, 60)).add(add(AUGUST_20, 90));

        Assertions.assertEquals(List.of(year(2026, month(8, 150))), workload.years());
    }

    @Test
    @DisplayName("adds in different months and years stay apart")
    void addKeepsPeriodsApart() {
        TrainerWorkload workload = empty()
                .add(add(AUGUST_1, 60))
                .add(add(SEPTEMBER_5, 45))
                .add(add(NEXT_JANUARY_10, 30));

        Assertions.assertEquals(
                List.of(year(2026, month(8, 60), month(9, 45)), year(2027, month(1, 30))),
                workload.years());
    }

    @Test
    @DisplayName("years and months come out ascending whatever the order of the adds")
    void sortsYearsAndMonths() {
        TrainerWorkload workload = empty()
                .add(add(NEXT_JANUARY_10, 30))
                .add(add(SEPTEMBER_5, 45))
                .add(add(AUGUST_1, 60))
                .add(add(LocalDate.of(2025, 12, 31), 15));

        Assertions.assertEquals(
                List.of(year(2025, month(12, 15)),
                        year(2026, month(8, 60), month(9, 45)),
                        year(2027, month(1, 30))),
                workload.years());
    }

    @Test
    @DisplayName("an add leaves the original workload untouched")
    void addReturnsNewWorkload() {
        TrainerWorkload original = empty().add(add(AUGUST_1, 60));

        original.add(add(AUGUST_20, 90));

        Assertions.assertEquals(60, original.durationIn(2026, 8));
    }

    @Test
    @DisplayName("a delete subtracts from the month")
    void subtractReducesMonth() {
        TrainerWorkload workload = empty().add(add(AUGUST_1, 150)).subtract(delete(AUGUST_20, 60));

        Assertions.assertEquals(List.of(year(2026, month(8, 90))), workload.years());
    }

    @Test
    @DisplayName("a delete larger than the month total stops at zero")
    void subtractClampsAtZero() {
        TrainerWorkload workload = empty().add(add(AUGUST_1, 60)).subtract(delete(AUGUST_1, 200));

        Assertions.assertEquals(0, workload.durationIn(2026, 8));
    }

    @Test
    @DisplayName("a month that reaches zero is removed and its neighbours stay")
    void subtractRemovesEmptiedMonth() {
        TrainerWorkload workload = empty()
                .add(add(AUGUST_1, 60))
                .add(add(SEPTEMBER_5, 45))
                .subtract(delete(AUGUST_1, 60));

        Assertions.assertEquals(List.of(year(2026, month(9, 45))), workload.years());
    }

    @Test
    @DisplayName("a year without months is removed and the other years stay")
    void subtractRemovesEmptiedYear() {
        TrainerWorkload workload = empty()
                .add(add(AUGUST_1, 60))
                .add(add(NEXT_JANUARY_10, 30))
                .subtract(delete(AUGUST_1, 60));

        Assertions.assertEquals(List.of(year(2027, month(1, 30))), workload.years());
    }

    @Test
    @DisplayName("the trainer is kept when the last month is removed")
    void subtractKeepsTrainerWithoutYears() {
        TrainerWorkload workload = empty().add(add(AUGUST_1, 60)).subtract(delete(AUGUST_1, 60));

        Assertions.assertEquals(empty(), workload);
    }

    @Test
    @DisplayName("a delete in a month with nothing recorded changes no totals")
    void subtractIgnoresMissingMonth() {
        TrainerWorkload workload = empty().add(add(AUGUST_1, 60)).subtract(delete(SEPTEMBER_5, 45));

        Assertions.assertEquals(List.of(year(2026, month(8, 60))), workload.years());
    }

    @Test
    @DisplayName("an add replaces the names and the status with the latest ones")
    void addTakesLatestTrainerDetails() {
        WorkloadChange renamed = new WorkloadChange(USERNAME, "Sara", "Miller-Jones", TrainerStatus.INACTIVE,
                AUGUST_20, 30, ActionType.ADD);

        TrainerWorkload workload = empty().add(add(AUGUST_1, 60)).add(renamed);

        Assertions.assertEquals(USERNAME, workload.trainerUsername());
        Assertions.assertEquals("Sara", workload.trainerFirstName());
        Assertions.assertEquals("Miller-Jones", workload.trainerLastName());
        Assertions.assertEquals(TrainerStatus.INACTIVE, workload.trainerStatus());
    }

    @Test
    @DisplayName("a delete replaces the names and the status with the latest ones")
    void subtractTakesLatestTrainerDetails() {
        WorkloadChange renamed = new WorkloadChange(USERNAME, "Sara", "Miller-Jones", TrainerStatus.INACTIVE,
                AUGUST_20, 30, ActionType.DELETE);

        TrainerWorkload workload = empty().add(add(AUGUST_1, 60)).subtract(renamed);

        Assertions.assertEquals("Sara", workload.trainerFirstName());
        Assertions.assertEquals("Miller-Jones", workload.trainerLastName());
        Assertions.assertEquals(TrainerStatus.INACTIVE, workload.trainerStatus());
        Assertions.assertEquals(30, workload.durationIn(2026, 8));
    }

    @Test
    @DisplayName("a month or a year with nothing recorded has a duration of zero")
    void durationIsZeroForUnknownPeriod() {
        TrainerWorkload workload = empty().add(add(AUGUST_1, 60));

        Assertions.assertEquals(0, workload.durationIn(2026, 7));
        Assertions.assertEquals(0, workload.durationIn(2025, 8));
    }

    @Test
    @DisplayName("the duration can be asked for by year and month or by period")
    void durationAcceptsPeriod() {
        TrainerWorkload workload = empty().add(add(AUGUST_1, 60));

        Assertions.assertEquals(60, workload.durationIn(2026, 8));
        Assertions.assertEquals(60, workload.durationIn(YearMonth.of(2026, 8)));
    }

    @Test
    @DisplayName("the years and the months cannot be changed from outside")
    void exposesUnmodifiableLists() {
        List<MonthSummary> months = new ArrayList<>(List.of(month(8, 60)));
        List<YearSummary> years = new ArrayList<>(List.of(new YearSummary(2026, months)));
        TrainerWorkload workload = new TrainerWorkload(USERNAME, "Sarah", "Miller", TrainerStatus.ACTIVE, years);

        months.clear();
        years.clear();

        Assertions.assertEquals(60, workload.durationIn(2026, 8));
        Assertions.assertThrows(UnsupportedOperationException.class,
                () -> workload.years().add(year(2027, month(1, 30))));
        Assertions.assertThrows(UnsupportedOperationException.class,
                () -> workload.years().getFirst().months().add(month(9, 45)));
    }
}
