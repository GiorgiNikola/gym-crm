package com.giorgi.workload.persistence;

import com.giorgi.workload.model.MonthSummary;
import com.giorgi.workload.model.TrainerWorkload;
import com.giorgi.workload.model.YearSummary;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TrainerWorkloadDocumentMapper {

    public TrainerWorkload toDomain(TrainerWorkloadDocument document) {
        return new TrainerWorkload(document.getUsername(), document.getFirstName(), document.getLastName(),
                document.getStatus(), document.getYears().stream().map(this::toDomain).toList());
    }

    public TrainerWorkloadDocument toDocument(TrainerWorkload workload) {
        TrainerWorkloadDocument document = new TrainerWorkloadDocument();
        document.setUsername(workload.trainerUsername());
        document.setFirstName(workload.trainerFirstName());
        document.setLastName(workload.trainerLastName());
        document.setStatus(workload.trainerStatus());
        document.setYears(workload.years().stream().map(this::toDocument).toList());
        document.setRecentEventIds(List.of());
        return document;
    }

    private YearSummary toDomain(YearDocument year) {
        return new YearSummary(year.year(), year.months().stream()
                .map(month -> new MonthSummary(month.month(), month.trainingSummaryDuration()))
                .toList());
    }

    private YearDocument toDocument(YearSummary year) {
        return new YearDocument(year.year(), year.months().stream()
                .map(month -> new MonthDocument(month.month(), month.trainingSummaryDuration()))
                .toList());
    }
}
