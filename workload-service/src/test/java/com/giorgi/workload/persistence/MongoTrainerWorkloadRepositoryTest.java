package com.giorgi.workload.persistence;

import ch.qos.logback.classic.Level;
import com.giorgi.workload.LogCapture;
import com.giorgi.workload.config.MongoConfig;
import com.giorgi.workload.model.ActionType;
import com.giorgi.workload.model.TrainerWorkload;
import com.giorgi.workload.model.WorkloadChange;
import com.giorgi.workload.repository.WorkloadUpdate;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MongoTrainerWorkloadRepositoryTest {

    private static final String USERNAME = "Sarah.Miller";
    private static final ObjectId ID = new ObjectId();

    @Mock
    private TrainerWorkloadMongoRepository documents;

    private MongoTrainerWorkloadRepository repository;
    private LogCapture logs;

    @BeforeEach
    void setUp() {
        repository = new MongoTrainerWorkloadRepository(documents, new TrainerWorkloadDocumentMapper(),
                new RetryTemplate(MongoConfig.versionConflictPolicy(Duration.ZERO)), new PersistenceProperties(3));
        logs = LogCapture.of(MongoTrainerWorkloadRepository.class);
    }

    @AfterEach
    void releaseLogs() {
        logs.close();
    }

    private static WorkloadChange change(ActionType actionType, String date, int minutes) {
        return new WorkloadChange(USERNAME, "Sarah", "Miller", true, LocalDate.parse(date), minutes, actionType);
    }

    private static final UUID EVENT = UUID.fromString("3f1c9a52-7d0e-4b8a-9c61-2e5f8d4a7b10");

    private static TrainerWorkloadDocument stored(long version, YearDocument... years) {
        return storedWithEvents(version, List.of(), years);
    }

    private static TrainerWorkloadDocument storedWithEvents(long version, List<String> eventIds,
                                                            YearDocument... years) {
        return new TrainerWorkloadDocument(ID, USERNAME, "Sarah", "Miller", true, List.of(years), eventIds, version);
    }

    private static YearDocument year(int year, MonthDocument... months) {
        return new YearDocument(year, List.of(months));
    }

    private void savesWithNextVersion() {
        when(documents.save(any())).thenAnswer(invocation -> {
            TrainerWorkloadDocument document = invocation.getArgument(0);
            document.setVersion(document.getVersion() == null ? 0 : document.getVersion() + 1);
            return document;
        });
    }

    private TrainerWorkloadDocument savedDocument() {
        ArgumentCaptor<TrainerWorkloadDocument> saved = ArgumentCaptor.forClass(TrainerWorkloadDocument.class);
        verify(documents).save(saved.capture());
        return saved.getValue();
    }

    @Test
    @DisplayName("find maps the stored document and logs that it was found")
    void findsStoredWorkload() {
        when(documents.findByUsername(USERNAME)).thenReturn(Optional.of(stored(2, year(2026, new MonthDocument(8, 60)))));

        TrainerWorkload workload = repository.findByUsername(USERNAME).orElseThrow();

        Assertions.assertEquals(60, workload.durationIn(2026, 8));
        Assertions.assertTrue(workload.trainerStatus());
        Assertions.assertEquals(List.of("Found the workload of trainer Sarah.Miller at version 2"),
                logs.messages(Level.DEBUG));
    }

    @Test
    @DisplayName("find of an unknown trainer is empty and logs that nothing is stored")
    void findsNothingForUnknownTrainer() {
        when(documents.findByUsername(USERNAME)).thenReturn(Optional.empty());

        Assertions.assertTrue(repository.findByUsername(USERNAME).isEmpty());
        Assertions.assertEquals(List.of("No workload stored for trainer Sarah.Miller"), logs.messages(Level.DEBUG));
    }

    @Test
    @DisplayName("the first change of a trainer creates a document with the year and month of the training")
    void createsDocumentForNewTrainer() {
        when(documents.findByUsername(USERNAME)).thenReturn(Optional.empty());
        savesWithNextVersion();

        WorkloadUpdate update = repository.update(USERNAME, change(ActionType.ADD, "2026-08-14", 60)::applyTo);

        TrainerWorkloadDocument saved = savedDocument();
        Assertions.assertNull(saved.getId());
        Assertions.assertEquals(USERNAME, saved.getUsername());
        Assertions.assertEquals(Boolean.TRUE, saved.getStatus());
        Assertions.assertEquals(List.of(year(2026, new MonthDocument(8, 60))), saved.getYears());
        Assertions.assertTrue(update.before().isEmpty());
        Assertions.assertEquals(60, update.minutesAfter(YearMonth.of(2026, 8)));
        Assertions.assertEquals(List.of("Created the workload of trainer Sarah.Miller (active true) at version 0: "
                + "year 2026 month 8 from 0 to 60 min"), logs.messages(Level.INFO));
    }

    @Test
    @DisplayName("a training in a new year adds that year and keeps the id and version of the document")
    void addsNewYear() {
        when(documents.findByUsername(USERNAME)).thenReturn(Optional.of(stored(4, year(2026, new MonthDocument(8, 60)))));
        savesWithNextVersion();

        repository.update(USERNAME, change(ActionType.ADD, "2027-01-05", 30)::applyTo);

        TrainerWorkloadDocument saved = savedDocument();
        Assertions.assertEquals(ID, saved.getId());
        Assertions.assertEquals(List.of(year(2026, new MonthDocument(8, 60)), year(2027, new MonthDocument(1, 30))),
                saved.getYears());
        Assertions.assertEquals(List.of("Updated the workload of trainer Sarah.Miller (active true) from version 4 "
                + "to 5: year 2027 month 1 from 0 to 30 min"), logs.messages(Level.INFO));
    }

    @Test
    @DisplayName("a training in a new month of a known year adds the month in order")
    void addsNewMonthToExistingYear() {
        when(documents.findByUsername(USERNAME)).thenReturn(Optional.of(stored(1, year(2026, new MonthDocument(9, 45)))));
        savesWithNextVersion();

        repository.update(USERNAME, change(ActionType.ADD, "2026-08-20", 60)::applyTo);

        Assertions.assertEquals(List.of(year(2026, new MonthDocument(8, 60), new MonthDocument(9, 45))),
                savedDocument().getYears());
    }

    @Test
    @DisplayName("a training in a known month adds its minutes to that month")
    void addsToExistingMonth() {
        when(documents.findByUsername(USERNAME)).thenReturn(Optional.of(stored(1, year(2026, new MonthDocument(8, 60)))));
        savesWithNextVersion();

        WorkloadUpdate update = repository.update(USERNAME, change(ActionType.ADD, "2026-08-01", 90)::applyTo);

        Assertions.assertEquals(List.of(year(2026, new MonthDocument(8, 150))), savedDocument().getYears());
        Assertions.assertEquals(60, update.minutesBefore(YearMonth.of(2026, 8)));
        Assertions.assertTrue(logs.messages(Level.INFO).getFirst().endsWith("year 2026 month 8 from 60 to 150 min"));
    }

    @Test
    @DisplayName("a delete larger than the month clamps it at zero and removes the empty month")
    void clampsDeleteAtZero() {
        when(documents.findByUsername(USERNAME)).thenReturn(Optional.of(stored(1,
                year(2026, new MonthDocument(8, 30), new MonthDocument(9, 45)))));
        savesWithNextVersion();

        WorkloadUpdate update = repository.update(USERNAME, change(ActionType.DELETE, "2026-08-01", 60)::applyTo);

        Assertions.assertEquals(List.of(year(2026, new MonthDocument(9, 45))), savedDocument().getYears());
        Assertions.assertEquals(0, update.minutesAfter(YearMonth.of(2026, 8)));
        Assertions.assertTrue(logs.messages(Level.INFO).getFirst().endsWith("year 2026 month 8 from 30 to 0 min"));
    }

    @Test
    @DisplayName("a delete in an empty month still saves the latest names and logs that no month changed")
    void savesWhenNoMonthChanged() {
        when(documents.findByUsername(USERNAME)).thenReturn(Optional.of(stored(1, year(2026, new MonthDocument(9, 45)))));
        savesWithNextVersion();

        repository.update(USERNAME, change(ActionType.DELETE, "2026-08-01", 60)::applyTo);

        Assertions.assertEquals(List.of(year(2026, new MonthDocument(9, 45))), savedDocument().getYears());
        Assertions.assertTrue(logs.messages(Level.INFO).getFirst().endsWith("from version 1 to 2: no month changed"));
    }

    @Test
    @DisplayName("a delete for an unknown trainer saves nothing")
    void savesNothingForUnknownTrainer() {
        when(documents.findByUsername(USERNAME)).thenReturn(Optional.empty());

        WorkloadUpdate update = repository.update(USERNAME, change(ActionType.DELETE, "2026-08-01", 60)::applyTo);

        Assertions.assertFalse(update.stored());
        verify(documents, never()).save(any());
        Assertions.assertTrue(logs.messages(Level.INFO).isEmpty());
    }

    @Test
    @DisplayName("a version conflict re-reads the document, applies the change again and warns once")
    void retriesVersionConflict() {
        when(documents.findByUsername(USERNAME))
                .thenReturn(Optional.of(stored(1, year(2026, new MonthDocument(8, 60)))))
                .thenReturn(Optional.of(stored(2, year(2026, new MonthDocument(8, 100)))));
        List<Long> savedVersions = new ArrayList<>();
        when(documents.save(any()))
                .thenAnswer(invocation -> {
                    savedVersions.add(invocation.<TrainerWorkloadDocument>getArgument(0).getVersion());
                    throw new OptimisticLockingFailureException("stale version 1");
                })
                .thenAnswer(invocation -> {
                    TrainerWorkloadDocument document = invocation.getArgument(0);
                    savedVersions.add(document.getVersion());
                    document.setVersion(3L);
                    return document;
                });

        WorkloadUpdate update = repository.update(USERNAME, change(ActionType.ADD, "2026-08-01", 30)::applyTo);

        Assertions.assertEquals(List.of(1L, 2L), savedVersions);
        Assertions.assertEquals(130, update.minutesAfter(YearMonth.of(2026, 8)));
        Assertions.assertEquals(List.of("Version conflict on the workload of trainer Sarah.Miller, retry 1 of 3"),
                logs.messages(Level.WARN));
    }

    @Test
    @DisplayName("a trainer created by someone else at the same moment counts as a version conflict and is retried")
    void retriesConcurrentCreate() {
        when(documents.findByUsername(USERNAME))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(stored(0, year(2026, new MonthDocument(8, 45)))));
        when(documents.save(any()))
                .thenThrow(new DuplicateKeyException("E11000 duplicate key username_unique"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WorkloadUpdate update = repository.update(USERNAME, change(ActionType.ADD, "2026-08-01", 60)::applyTo);

        Assertions.assertEquals(105, update.minutesAfter(YearMonth.of(2026, 8)));
        Assertions.assertEquals(1, logs.messages(Level.WARN).size());
    }

    @Test
    @DisplayName("when every retry conflicts the version conflict reaches the caller after three retries")
    void rethrowsWhenRetriesRunOut() {
        when(documents.findByUsername(USERNAME)).thenReturn(Optional.of(stored(1, year(2026, new MonthDocument(8, 60)))));
        when(documents.save(any())).thenThrow(new OptimisticLockingFailureException("stale version 1"));

        OptimisticLockingFailureException thrown = Assertions.assertThrows(OptimisticLockingFailureException.class,
                () -> repository.update(USERNAME, change(ActionType.ADD, "2026-08-01", 30)::applyTo));

        Assertions.assertEquals("stale version 1", thrown.getMessage());
        verify(documents, times(1 + MongoConfig.VERSION_CONFLICT_RETRIES)).save(any());
        Assertions.assertEquals(List.of(
                "Version conflict on the workload of trainer Sarah.Miller, retry 1 of 3",
                "Version conflict on the workload of trainer Sarah.Miller, retry 2 of 3",
                "Version conflict on the workload of trainer Sarah.Miller, retry 3 of 3"), logs.messages(Level.WARN));
    }

    @Test
    @DisplayName("any other failure is not retried")
    void doesNotRetryOtherFailures() {
        when(documents.findByUsername(USERNAME)).thenThrow(new IllegalStateException("connection refused"));

        Assertions.assertThrows(IllegalStateException.class,
                () -> repository.update(USERNAME, change(ActionType.ADD, "2026-08-01", 30)::applyTo));

        verify(documents, times(1)).findByUsername(USERNAME);
        Assertions.assertTrue(logs.messages(Level.WARN).isEmpty());
    }

    @Test
    @DisplayName("no first or last name ever reaches the logs")
    void keepsNamesOutOfLogs() {
        when(documents.findByUsername(USERNAME)).thenReturn(Optional.empty());
        savesWithNextVersion();

        repository.update(USERNAME, change(ActionType.ADD, "2026-08-01", 30)::applyTo);

        Assertions.assertFalse(logs.messages().isEmpty());
        for (String message : logs.messages()) {
            Assertions.assertFalse(message.replace(USERNAME, "").contains("Sarah"), message);
            Assertions.assertFalse(message.replace(USERNAME, "").contains("Miller"), message);
        }
    }

    @Test
    @DisplayName("the first event of a trainer creates a document that already holds its id")
    void startsNewDocumentWithEventId() {
        when(documents.findByUsername(USERNAME)).thenReturn(Optional.empty());
        savesWithNextVersion();

        WorkloadUpdate update = repository.update(USERNAME, EVENT, change(ActionType.ADD, "2026-08-01", 60)::applyTo);

        Assertions.assertFalse(update.duplicate());
        Assertions.assertEquals(List.of(EVENT.toString()), savedDocument().getRecentEventIds());
    }

    @Test
    @DisplayName("an event id the document already holds is a duplicate: the change is not applied and nothing saved")
    void detectsDuplicateFromDocument() {
        when(documents.findByUsername(USERNAME)).thenReturn(Optional.of(
                storedWithEvents(5, List.of("other-event", EVENT.toString()), year(2026, new MonthDocument(8, 60)))));

        WorkloadUpdate update = repository.update(USERNAME, EVENT, before -> {
            throw new AssertionError("a duplicate must not be applied");
        });

        Assertions.assertTrue(update.duplicate());
        Assertions.assertEquals(60, update.minutesAfter(YearMonth.of(2026, 8)));
        verify(documents, never()).save(any());
        Assertions.assertEquals(List.of("Event 3f1c9a52-7d0e-4b8a-9c61-2e5f8d4a7b10 is already in the workload of "
                + "trainer Sarah.Miller at version 5, nothing saved"), logs.messages(Level.INFO));
    }

    @Test
    @DisplayName("a new event id is appended and the oldest ids are dropped past the limit")
    void keepsEventIdsBounded() {
        when(documents.findByUsername(USERNAME)).thenReturn(Optional.of(
                storedWithEvents(2, List.of("first", "second", "third"), year(2026, new MonthDocument(8, 60)))));
        savesWithNextVersion();

        repository.update(USERNAME, EVENT, change(ActionType.ADD, "2026-08-01", 30)::applyTo);

        TrainerWorkloadDocument saved = savedDocument();
        Assertions.assertEquals(List.of("second", "third", EVENT.toString()), saved.getRecentEventIds());
        Assertions.assertEquals(List.of(year(2026, new MonthDocument(8, 90))), saved.getYears());
    }

    @Test
    @DisplayName("a REST change keeps the stored event ids as they are")
    void keepsEventIdsOnRestChange() {
        when(documents.findByUsername(USERNAME)).thenReturn(Optional.of(
                storedWithEvents(2, List.of("first"), year(2026, new MonthDocument(8, 60)))));
        savesWithNextVersion();

        repository.update(USERNAME, change(ActionType.ADD, "2026-08-01", 30)::applyTo);

        Assertions.assertEquals(List.of("first"), savedDocument().getRecentEventIds());
    }

    @Test
    @DisplayName("a document written before event ids were kept counts as holding none")
    void readsMissingEventIdsAsEmpty() {
        when(documents.findByUsername(USERNAME)).thenReturn(Optional.of(
                storedWithEvents(2, null, year(2026, new MonthDocument(8, 60)))));
        savesWithNextVersion();

        WorkloadUpdate update = repository.update(USERNAME, EVENT, change(ActionType.ADD, "2026-08-01", 30)::applyTo);

        Assertions.assertFalse(update.duplicate());
        Assertions.assertEquals(List.of(EVENT.toString()), savedDocument().getRecentEventIds());
    }

    @Test
    @DisplayName("after a version conflict the id is checked again on the fresh document, which already holds it")
    void rechecksEventIdAfterVersionConflict() {
        when(documents.findByUsername(USERNAME))
                .thenReturn(Optional.of(stored(1, year(2026, new MonthDocument(8, 60)))))
                .thenReturn(Optional.of(storedWithEvents(2, List.of(EVENT.toString()),
                        year(2026, new MonthDocument(8, 90)))));
        when(documents.save(any())).thenThrow(new OptimisticLockingFailureException("stale version 1"));

        WorkloadUpdate update = repository.update(USERNAME, EVENT, change(ActionType.ADD, "2026-08-01", 30)::applyTo);

        Assertions.assertTrue(update.duplicate());
        Assertions.assertEquals(90, update.minutesAfter(YearMonth.of(2026, 8)));
        verify(documents, times(1)).save(any());
        Assertions.assertEquals(1, logs.messages(Level.WARN).size());
    }

    @Test
    @DisplayName("when a save fails the id is not stored, so the redelivered event is applied exactly once")
    void appliesRedeliveryAfterFailedSaveOnce() {
        TrainerWorkloadDocument[] database = {stored(1, year(2026, new MonthDocument(8, 60)))};
        when(documents.findByUsername(USERNAME)).thenAnswer(invocation -> Optional.of(copyOf(database[0])));
        when(documents.save(any()))
                .thenThrow(new IllegalStateException("write concern not satisfied"))
                .thenAnswer(invocation -> {
                    TrainerWorkloadDocument document = invocation.getArgument(0);
                    document.setVersion(document.getVersion() + 1);
                    database[0] = copyOf(document);
                    return document;
                });
        UnaryOperator<Optional<TrainerWorkload>> add = change(ActionType.ADD, "2026-08-01", 30)::applyTo;

        Assertions.assertThrows(IllegalStateException.class, () -> repository.update(USERNAME, EVENT, add));
        WorkloadUpdate redelivered = repository.update(USERNAME, EVENT, add);
        WorkloadUpdate repeated = repository.update(USERNAME, EVENT, add);

        Assertions.assertFalse(redelivered.duplicate());
        Assertions.assertTrue(repeated.duplicate());
        Assertions.assertEquals(List.of(year(2026, new MonthDocument(8, 90))), database[0].getYears());
        Assertions.assertEquals(List.of(EVENT.toString()), database[0].getRecentEventIds());
        Assertions.assertEquals(2L, database[0].getVersion());
    }

    private static TrainerWorkloadDocument copyOf(TrainerWorkloadDocument document) {
        return new TrainerWorkloadDocument(document.getId(), document.getUsername(), document.getFirstName(),
                document.getLastName(), document.getStatus(), document.getYears(), document.getRecentEventIds(),
                document.getVersion());
    }
}
