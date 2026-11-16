package com.giorgi.workload.service;

import ch.qos.logback.classic.Level;
import com.giorgi.workload.LogCapture;
import com.giorgi.workload.exception.TrainerNotFoundException;
import com.giorgi.workload.model.ActionType;
import com.giorgi.workload.model.MonthSummary;
import com.giorgi.workload.model.TrainerStatus;
import com.giorgi.workload.model.TrainerWorkload;
import com.giorgi.workload.model.WorkloadChange;
import com.giorgi.workload.model.YearSummary;
import com.giorgi.workload.repository.TrainerWorkloadRepository;
import com.giorgi.workload.repository.WorkloadUpdate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkloadServiceTest {

    private static final String USERNAME = "trainer.one";
    private static final LocalDate DATE = LocalDate.of(2026, 8, 1);

    @Mock
    private TrainerWorkloadRepository trainerWorkloadRepository;

    @InjectMocks
    private WorkloadService workloadService;

    @Captor
    private ArgumentCaptor<UnaryOperator<Optional<TrainerWorkload>>> changeCaptor;

    private LogCapture logs;

    @BeforeEach
    void captureLogs() {
        logs = LogCapture.of(WorkloadService.class);
    }

    @AfterEach
    void releaseLogs() {
        logs.close();
    }

    private WorkloadChange change(ActionType actionType, int minutes) {
        return new WorkloadChange(USERNAME, "Sarah", "Miller", TrainerStatus.ACTIVE, DATE, minutes, actionType);
    }

    private Optional<TrainerWorkload> workload(long minutes) {
        List<YearSummary> years = minutes == 0
                ? List.of()
                : List.of(new YearSummary(2026, List.of(new MonthSummary(8, minutes))));
        return Optional.of(new TrainerWorkload(USERNAME, "Sarah", "Miller", TrainerStatus.ACTIVE, years));
    }

    private void repositoryReports(Optional<TrainerWorkload> before, Optional<TrainerWorkload> after) {
        when(trainerWorkloadRepository.update(eq(USERNAME), any()))
                .thenReturn(new WorkloadUpdate(before, after));
    }

    @Test
    @DisplayName("apply hands the change to the repository under the trainer's username")
    void appliesChangeThroughRepository() {
        repositoryReports(Optional.empty(), workload(60));

        workloadService.apply(change(ActionType.ADD, 60));

        verify(trainerWorkloadRepository).update(eq(USERNAME), changeCaptor.capture());
        TrainerWorkload created = changeCaptor.getValue().apply(Optional.empty()).orElseThrow();
        Assertions.assertEquals(60, created.durationIn(2026, 8));
    }

    @Test
    @DisplayName("an applied add is logged with the trainer, the month, the minutes and the new total")
    void logsAppliedAdd() {
        repositoryReports(workload(90), workload(150));

        workloadService.apply(change(ActionType.ADD, 60));

        Assertions.assertEquals(
                List.of("Applied ADD of 60 min in 2026-08 for trainer trainer.one, month total is now 150 min"),
                logs.messages(Level.INFO));
        Assertions.assertTrue(logs.messages(Level.WARN).isEmpty());
    }

    @Test
    @DisplayName("the first add for a trainer is not mistaken for a clamp")
    void doesNotWarnOnFirstAdd() {
        repositoryReports(Optional.empty(), workload(60));

        workloadService.apply(change(ActionType.ADD, 60));

        Assertions.assertTrue(logs.messages(Level.WARN).isEmpty());
        Assertions.assertEquals(1, logs.messages(Level.INFO).size());
    }

    @Test
    @DisplayName("an applied delete is logged with the new total and no warning")
    void logsAppliedDelete() {
        repositoryReports(workload(150), workload(90));

        workloadService.apply(change(ActionType.DELETE, 60));

        Assertions.assertEquals(
                List.of("Applied DELETE of 60 min in 2026-08 for trainer trainer.one, month total is now 90 min"),
                logs.messages(Level.INFO));
        Assertions.assertTrue(logs.messages(Level.WARN).isEmpty());
    }

    @Test
    @DisplayName("a delete of exactly the recorded minutes is not a clamp")
    void doesNotWarnOnExactDelete() {
        repositoryReports(workload(60), workload(0));

        workloadService.apply(change(ActionType.DELETE, 60));

        Assertions.assertTrue(logs.messages(Level.WARN).isEmpty());
        Assertions.assertEquals(
                List.of("Applied DELETE of 60 min in 2026-08 for trainer trainer.one, month total is now 0 min"),
                logs.messages(Level.INFO));
    }

    @Test
    @DisplayName("a clamped delete warns with the requested and the available minutes")
    void warnsOnClampedDelete() {
        repositoryReports(workload(30), workload(0));

        workloadService.apply(change(ActionType.DELETE, 60));

        Assertions.assertEquals(
                List.of("DELETE of 60 min in 2026-08 for trainer trainer.one exceeds the 30 min recorded, "
                        + "clamped to 0"),
                logs.messages(Level.WARN));
        Assertions.assertEquals(1, logs.messages(Level.INFO).size());
    }

    @Test
    @DisplayName("a delete for an unknown trainer warns and reports nothing as applied")
    void warnsOnUnknownTrainer() {
        repositoryReports(Optional.empty(), Optional.empty());

        workloadService.apply(change(ActionType.DELETE, 60));

        Assertions.assertEquals(
                List.of("Ignored DELETE of 60 min in 2026-08 for unknown trainer trainer.one"),
                logs.messages(Level.WARN));
        Assertions.assertTrue(logs.messages(Level.INFO).isEmpty());
    }

    @Test
    @DisplayName("the names of the trainer never reach the logs")
    void keepsNamesOutOfLogs() {
        repositoryReports(workload(30), workload(0));

        workloadService.apply(change(ActionType.DELETE, 60));

        Assertions.assertFalse(logs.messages().isEmpty());
        for (String message : logs.messages()) {
            Assertions.assertFalse(message.contains("Sarah"), message);
            Assertions.assertFalse(message.contains("Miller"), message);
        }
    }

    @Test
    @DisplayName("getWorkload returns the stored workload and logs the read at debug")
    void returnsStoredWorkload() {
        Optional<TrainerWorkload> stored = workload(150);
        when(trainerWorkloadRepository.findByUsername(USERNAME)).thenReturn(stored);

        TrainerWorkload found = workloadService.getWorkload(USERNAME);

        Assertions.assertSame(stored.orElseThrow(), found);
        Assertions.assertEquals(List.of("Reading the workload summary of trainer trainer.one"),
                logs.messages(Level.DEBUG));
    }

    @Test
    @DisplayName("getWorkload throws for an unknown trainer")
    void throwsForUnknownTrainer() {
        when(trainerWorkloadRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());

        TrainerNotFoundException thrown = Assertions.assertThrows(TrainerNotFoundException.class,
                () -> workloadService.getWorkload(USERNAME));

        Assertions.assertEquals("Trainer not found: trainer.one", thrown.getMessage());
    }

    @Test
    @DisplayName("getMonthlyDuration returns the minutes of the month and logs the read at debug")
    void returnsMonthlyDuration() {
        when(trainerWorkloadRepository.findByUsername(USERNAME)).thenReturn(workload(150));

        Assertions.assertEquals(150, workloadService.getMonthlyDuration(USERNAME, 2026, 8));
        Assertions.assertEquals(List.of("Reading the workload of trainer trainer.one for year 2026 month 8"),
                logs.messages(Level.DEBUG));
    }

    @Test
    @DisplayName("getMonthlyDuration returns zero for a month with nothing recorded")
    void returnsZeroForEmptyMonth() {
        when(trainerWorkloadRepository.findByUsername(USERNAME)).thenReturn(workload(150));

        Assertions.assertEquals(0, workloadService.getMonthlyDuration(USERNAME, 2026, 9));
    }

    @Test
    @DisplayName("getMonthlyDuration throws for an unknown trainer")
    void throwsForUnknownTrainerMonth() {
        when(trainerWorkloadRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());

        Assertions.assertThrows(TrainerNotFoundException.class,
                () -> workloadService.getMonthlyDuration(USERNAME, 2026, 8));
    }
}
