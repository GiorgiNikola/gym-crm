package com.giorgi.workload.repository;

import com.giorgi.workload.model.ActionType;
import com.giorgi.workload.model.TrainerStatus;
import com.giorgi.workload.model.TrainerWorkload;
import com.giorgi.workload.model.WorkloadChange;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

class InMemoryTrainerWorkloadRepositoryTest {

    private static final String USERNAME = "Sarah.Miller";
    private static final LocalDate DATE = LocalDate.of(2026, 8, 1);

    private final InMemoryTrainerWorkloadRepository repository = new InMemoryTrainerWorkloadRepository();

    private WorkloadChange change(String username, ActionType actionType, int minutes) {
        return new WorkloadChange(username, "Sarah", "Miller", TrainerStatus.ACTIVE, DATE, minutes, actionType);
    }

    @Test
    @DisplayName("an unknown trainer is not found")
    void findsNothingForUnknownTrainer() {
        Assertions.assertTrue(repository.findByUsername(USERNAME).isEmpty());
    }

    @Test
    @DisplayName("the result of the change is stored and returned")
    void storesResultOfChange() {
        WorkloadUpdate update = repository.update(USERNAME, change(USERNAME, ActionType.ADD, 60)::applyTo);

        TrainerWorkload stored = repository.findByUsername(USERNAME).orElseThrow();
        Assertions.assertTrue(update.before().isEmpty());
        Assertions.assertSame(stored, update.after().orElseThrow());
        Assertions.assertEquals(60, stored.durationIn(2026, 8));
    }

    @Test
    @DisplayName("the change receives the workload stored before it")
    void handsStoredWorkloadToChange() {
        WorkloadUpdate first = repository.update(USERNAME, change(USERNAME, ActionType.ADD, 60)::applyTo);

        WorkloadUpdate second = repository.update(USERNAME, change(USERNAME, ActionType.ADD, 90)::applyTo);

        Assertions.assertSame(first.after().orElseThrow(), second.before().orElseThrow());
        Assertions.assertEquals(150, second.after().orElseThrow().durationIn(2026, 8));
    }

    @Test
    @DisplayName("a change that returns nothing stores nothing")
    void storesNothingForEmptyResult() {
        WorkloadUpdate update = repository.update(USERNAME, change(USERNAME, ActionType.DELETE, 60)::applyTo);

        Assertions.assertFalse(update.stored());
        Assertions.assertTrue(repository.findByUsername(USERNAME).isEmpty());
    }

    @Test
    @DisplayName("trainers are stored apart")
    void keepsTrainersApart() {
        repository.update(USERNAME, change(USERNAME, ActionType.ADD, 60)::applyTo);
        repository.update("Mike.Brown", change("Mike.Brown", ActionType.ADD, 45)::applyTo);

        Assertions.assertEquals(60, repository.findByUsername(USERNAME).orElseThrow().durationIn(2026, 8));
        Assertions.assertEquals(45, repository.findByUsername("Mike.Brown").orElseThrow().durationIn(2026, 8));
    }

    @Test
    @DisplayName("parallel adds for one trainer add up to the exact sum")
    void losesNoParallelUpdate() throws Exception {
        int updates = 2_000;
        int minutes = 3;
        WorkloadChange add = change(USERNAME, ActionType.ADD, minutes);
        Callable<WorkloadUpdate> task = () -> repository.update(USERNAME, add::applyTo);

        try (ExecutorService executor = Executors.newFixedThreadPool(16)) {
            List<Future<WorkloadUpdate>> results = executor.invokeAll(Collections.nCopies(updates, task));
            for (Future<WorkloadUpdate> result : results) {
                Assertions.assertTrue(result.get().stored());
            }
        }

        Optional<TrainerWorkload> stored = repository.findByUsername(USERNAME);
        Assertions.assertEquals((long) updates * minutes, stored.orElseThrow().durationIn(2026, 8));
    }
}
