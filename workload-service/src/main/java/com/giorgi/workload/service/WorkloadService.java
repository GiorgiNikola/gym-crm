package com.giorgi.workload.service;

import com.giorgi.workload.exception.TrainerNotFoundException;
import com.giorgi.workload.model.ActionType;
import com.giorgi.workload.model.TrainerWorkload;
import com.giorgi.workload.model.WorkloadChange;
import com.giorgi.workload.repository.TrainerWorkloadRepository;
import com.giorgi.workload.repository.WorkloadUpdate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.UUID;

@Slf4j
@Service
public class WorkloadService {

    private final TrainerWorkloadRepository trainerWorkloadRepository;

    public WorkloadService(TrainerWorkloadRepository trainerWorkloadRepository) {
        this.trainerWorkloadRepository = trainerWorkloadRepository;
    }

    public ApplyOutcome apply(WorkloadChange change) {
        return report(change, trainerWorkloadRepository.update(change.trainerUsername(), change::applyTo));
    }

    public ApplyOutcome apply(WorkloadChange change, UUID eventId) {
        WorkloadUpdate update = trainerWorkloadRepository.update(change.trainerUsername(), eventId, change::applyTo);
        if (update.duplicate()) {
            log.info("Skipped {} of {} min in {} for trainer {}: event {} was already applied",
                    change.actionType(), change.trainingDuration(), change.period(), change.trainerUsername(),
                    eventId);
            return ApplyOutcome.DUPLICATE;
        }
        return report(change, update);
    }

    private ApplyOutcome report(WorkloadChange change, WorkloadUpdate update) {
        String username = change.trainerUsername();
        YearMonth period = change.period();
        int minutes = change.trainingDuration();

        if (!update.stored()) {
            log.warn("Ignored {} of {} min in {} for unknown trainer {}",
                    change.actionType(), minutes, period, username);
            return ApplyOutcome.APPLIED;
        }

        long available = update.minutesBefore(period);
        if (change.actionType() == ActionType.DELETE && minutes > available) {
            log.warn("DELETE of {} min in {} for trainer {} exceeds the {} min recorded, clamped to 0",
                    minutes, period, username, available);
        }

        log.info("Applied {} of {} min in {} for trainer {}, month total is now {} min",
                change.actionType(), minutes, period, username, update.minutesAfter(period));
        return ApplyOutcome.APPLIED;
    }

    public TrainerWorkload getWorkload(String username) {
        log.debug("Reading the workload summary of trainer {}", username);
        return trainerWorkloadRepository.findByUsername(username)
                .orElseThrow(() -> new TrainerNotFoundException(username));
    }

    public long getMonthlyDuration(String username, int year, int month) {
        log.debug("Reading the workload of trainer {} for year {} month {}", username, year, month);
        return trainerWorkloadRepository.findByUsername(username)
                .orElseThrow(() -> new TrainerNotFoundException(username))
                .durationIn(year, month);
    }
}
