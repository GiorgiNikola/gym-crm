package com.giorgi.workload.model;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

public record TrainerWorkload(String trainerUsername,
                              String trainerFirstName,
                              String trainerLastName,
                              boolean trainerStatus,
                              List<YearSummary> years) {

    public TrainerWorkload {
        years = List.copyOf(years);
    }

    public TrainerWorkload add(WorkloadChange change) {
        return withTotal(change, durationIn(change.period()) + change.trainingDuration());
    }

    public TrainerWorkload subtract(WorkloadChange change) {
        return withTotal(change, Math.max(0, durationIn(change.period()) - change.trainingDuration()));
    }

    public long durationIn(YearMonth period) {
        return durationIn(period.getYear(), period.getMonthValue());
    }

    public long durationIn(int year, int month) {
        return years.stream()
                .filter(summary -> summary.year() == year)
                .flatMap(summary -> summary.months().stream())
                .filter(summary -> summary.month() == month)
                .mapToLong(MonthSummary::trainingSummaryDuration)
                .sum();
    }

    private TrainerWorkload withTotal(WorkloadChange change, long total) {
        SortedMap<YearMonth, Long> totals = totalsByPeriod();
        if (total > 0) {
            totals.put(change.period(), total);
        } else {
            totals.remove(change.period());
        }
        return new TrainerWorkload(trainerUsername, change.trainerFirstName(), change.trainerLastName(),
                change.trainerStatus(), toYears(totals));
    }

    private SortedMap<YearMonth, Long> totalsByPeriod() {
        SortedMap<YearMonth, Long> totals = new TreeMap<>();
        for (YearSummary year : years) {
            for (MonthSummary month : year.months()) {
                totals.put(YearMonth.of(year.year(), month.month()), month.trainingSummaryDuration());
            }
        }
        return totals;
    }

    private static List<YearSummary> toYears(SortedMap<YearMonth, Long> totals) {
        SortedMap<Integer, List<MonthSummary>> monthsByYear = new TreeMap<>();
        totals.forEach((period, total) -> monthsByYear
                .computeIfAbsent(period.getYear(), year -> new ArrayList<>())
                .add(new MonthSummary(period.getMonthValue(), total)));
        return monthsByYear.entrySet().stream()
                .map(entry -> new YearSummary(entry.getKey(), entry.getValue()))
                .toList();
    }
}
