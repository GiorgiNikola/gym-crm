package com.giorgi.workload.persistence;

import com.giorgi.workload.config.MongoConfig;
import com.giorgi.workload.model.ActionType;
import com.giorgi.workload.model.TrainerWorkload;
import com.giorgi.workload.model.WorkloadChange;
import jakarta.validation.ConstraintViolationException;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexField;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

@DataMongoTest
@ActiveProfiles("test")
@ImportAutoConfiguration(ValidationAutoConfiguration.class)
@EnableConfigurationProperties(PersistenceProperties.class)
@TestPropertySource(properties = "workload.persistence.recent-event-ids=3")
@Import({MongoTestContainerConfig.class, MongoConfig.class, MongoIndexInitializer.class,
        TrainerWorkloadDocumentMapper.class, MongoTrainerWorkloadRepository.class})
class MongoPersistenceIntegrationTest {

    @Autowired
    private TrainerWorkloadMongoRepository documents;

    @Autowired
    private MongoTrainerWorkloadRepository workloads;

    @Autowired
    private MongoIndexInitializer indexInitializer;

    @Autowired
    private MongoTemplate mongoTemplate;

    private static String newUsername() {
        return "trainer." + UUID.randomUUID();
    }

    private static TrainerWorkloadDocument document(String username, List<YearDocument> years) {
        return new TrainerWorkloadDocument(null, username, "Sarah", "Miller", false, years, List.of(), null);
    }

    private Map<String, IndexInfo> indexesOf(Class<?> type) {
        return mongoTemplate.indexOps(type).getIndexInfo().stream()
                .collect(Collectors.toMap(IndexInfo::getName, Function.identity()));
    }

    private static List<String> keys(IndexInfo index) {
        Assertions.assertTrue(index.getIndexFields().stream()
                .allMatch(field -> field.getDirection() == Sort.Direction.ASC), index.toString());
        return index.getIndexFields().stream().map(IndexField::getKey).toList();
    }

    @Test
    @DisplayName("a document with nested years and months and a false status is stored and read back as it was")
    void roundTripsNestedDocument() {
        String username = newUsername();
        List<YearDocument> years = List.of(
                new YearDocument(2026, List.of(new MonthDocument(8, 60), new MonthDocument(9, 45))),
                new YearDocument(2027, List.of(new MonthDocument(1, 30))));

        TrainerWorkloadDocument saved = documents.save(document(username, years));
        TrainerWorkloadDocument found = documents.findByUsername(username).orElseThrow();

        Assertions.assertNotNull(saved.getId());
        Assertions.assertEquals(0L, found.getVersion());
        Assertions.assertEquals(saved.getId(), found.getId());
        Assertions.assertEquals(Boolean.FALSE, found.getStatus());
        Assertions.assertEquals(years, found.getYears());

        Document raw = mongoTemplate.getCollection(TrainerWorkloadDocument.COLLECTION)
                .find(new Document("username", username)).first();
        Assertions.assertNotNull(raw);
        Assertions.assertInstanceOf(ObjectId.class, raw.get("_id"));
        Assertions.assertEquals(Boolean.FALSE, raw.get("status"));
        Document august = raw.getList("years", Document.class).getFirst().getList("months", Document.class).getFirst();
        Assertions.assertEquals(8, august.get("month"));
        Assertions.assertEquals(60L, august.get("trainingSummaryDuration"));
    }

    @Test
    @DisplayName("a second document with the same username is rejected by the unique index")
    void rejectsDuplicateUsername() {
        String username = newUsername();
        documents.save(document(username, List.of()));

        Assertions.assertThrows(DuplicateKeyException.class, () -> documents.save(document(username, List.of())));
        Assertions.assertEquals(1, mongoTemplate.count(query(where("username").is(username)),
                TrainerWorkloadDocument.class));
    }

    @Test
    @DisplayName("the workload collection has a unique username index and a first and last name index")
    void createsWorkloadIndexes() {
        Map<String, IndexInfo> indexes = indexesOf(TrainerWorkloadDocument.class);

        IndexInfo username = indexes.get(MongoIndexInitializer.USERNAME_INDEX);
        Assertions.assertEquals(List.of("username"), keys(username));
        Assertions.assertTrue(username.isUnique());
        IndexInfo names = indexes.get(MongoIndexInitializer.NAME_INDEX);
        Assertions.assertEquals(List.of("firstName", "lastName"), keys(names));
        Assertions.assertFalse(names.isUnique());
    }

    @Test
    @DisplayName("creating the indexes again on a restart changes nothing")
    void createsIndexesIdempotently() {
        Map<String, IndexInfo> workloadIndexes = indexesOf(TrainerWorkloadDocument.class);

        Assertions.assertDoesNotThrow(indexInitializer::afterSingletonsInstantiated);

        Assertions.assertEquals(workloadIndexes, indexesOf(TrainerWorkloadDocument.class));
    }

    @Test
    @DisplayName("saving a copy read before another save fails with an optimistic locking failure")
    void rejectsStaleVersion() {
        String username = newUsername();
        documents.save(document(username, List.of()));
        TrainerWorkloadDocument first = documents.findByUsername(username).orElseThrow();
        TrainerWorkloadDocument stale = documents.findByUsername(username).orElseThrow();

        first.setStatus(true);
        documents.save(first);
        stale.setLastName("Miller-Jones");

        Assertions.assertThrows(OptimisticLockingFailureException.class, () -> documents.save(stale));
        TrainerWorkloadDocument stored = documents.findByUsername(username).orElseThrow();
        Assertions.assertEquals(1L, stored.getVersion());
        Assertions.assertEquals("Miller", stored.getLastName());
    }

    @Test
    @DisplayName("month 13 is rejected before it reaches the database")
    void rejectsMonthOutOfRange() {
        String username = newUsername();
        TrainerWorkloadDocument invalid = document(username,
                List.of(new YearDocument(2026, List.of(new MonthDocument(13, 60)))));

        ConstraintViolationException thrown = Assertions.assertThrows(ConstraintViolationException.class,
                () -> documents.save(invalid));

        Assertions.assertTrue(thrown.getMessage().contains("years[0].months[0].month"), thrown.getMessage());
        Assertions.assertTrue(documents.findByUsername(username).isEmpty());
    }

    @Test
    @DisplayName("a negative duration and a missing status are rejected before they reach the database")
    void rejectsNegativeDurationAndMissingStatus() {
        String username = newUsername();
        TrainerWorkloadDocument invalid = document(username,
                List.of(new YearDocument(2026, List.of(new MonthDocument(8, -1)))));
        invalid.setStatus(null);

        ConstraintViolationException thrown = Assertions.assertThrows(ConstraintViolationException.class,
                () -> documents.save(invalid));

        Assertions.assertTrue(thrown.getMessage().contains("years[0].months[0].trainingSummaryDuration"),
                thrown.getMessage());
        Assertions.assertTrue(thrown.getMessage().contains("status"), thrown.getMessage());
        Assertions.assertTrue(documents.findByUsername(username).isEmpty());
    }

    @Test
    @DisplayName("an event id stored in the document is detected as a duplicate and nothing is saved again")
    void detectsDuplicateFromStoredDocument() {
        String username = newUsername();
        UUID eventId = UUID.randomUUID();
        WorkloadChange add = new WorkloadChange(username, "Sarah", "Miller", true, LocalDate.of(2026, 8, 1), 60,
                ActionType.ADD);

        Assertions.assertFalse(workloads.update(username, eventId, add::applyTo).duplicate());
        Assertions.assertTrue(workloads.update(username, eventId, add::applyTo).duplicate());

        TrainerWorkloadDocument stored = documents.findByUsername(username).orElseThrow();
        Assertions.assertEquals(0L, stored.getVersion());
        Assertions.assertEquals(List.of(eventId.toString()), stored.getRecentEventIds());
        Assertions.assertEquals(60, workloads.findByUsername(username).orElseThrow().durationIn(2026, 8));
        Document raw = mongoTemplate.getCollection(TrainerWorkloadDocument.COLLECTION)
                .find(new Document("username", username)).first();
        Assertions.assertEquals(List.of(eventId.toString()), raw.getList("recentEventIds", String.class));
    }

    @Test
    @DisplayName("the stored event ids stay within the configured limit, oldest dropped first")
    void boundsStoredEventIds() {
        String username = newUsername();
        WorkloadChange add = new WorkloadChange(username, "Sarah", "Miller", true, LocalDate.of(2026, 8, 1), 10,
                ActionType.ADD);
        List<UUID> eventIds = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID());

        eventIds.forEach(eventId -> workloads.update(username, eventId, add::applyTo));

        TrainerWorkloadDocument stored = documents.findByUsername(username).orElseThrow();
        Assertions.assertEquals(eventIds.subList(2, 5).stream().map(UUID::toString).toList(),
                stored.getRecentEventIds());
        Assertions.assertEquals(50, workloads.findByUsername(username).orElseThrow().durationIn(2026, 8));
        Assertions.assertFalse(workloads.update(username, eventIds.getFirst(), add::applyTo).duplicate());
    }

    @Test
    @DisplayName("the adapter creates and then updates one document per trainer")
    void appliesChangesThroughAdapter() {
        String username = newUsername();
        LocalDate august = LocalDate.of(2026, 8, 1);
        workloads.update(username, new WorkloadChange(username, "Sarah", "Miller", true, august, 60,
                ActionType.ADD)::applyTo);
        workloads.update(username, new WorkloadChange(username, "Sarah", "Miller", false, august, 15,
                ActionType.DELETE)::applyTo);

        TrainerWorkloadDocument stored = documents.findByUsername(username).orElseThrow();
        Assertions.assertEquals(1L, stored.getVersion());
        Assertions.assertEquals(Boolean.FALSE, stored.getStatus());
        Assertions.assertEquals(45, workloads.findByUsername(username).orElseThrow().durationIn(2026, 8));
    }

    @Test
    @DisplayName("a trainer created by another writer meanwhile is re-read and the change lands on top of it")
    void retriesConcurrentCreateAgainstRealIndex() {
        String username = newUsername();
        WorkloadChange add = new WorkloadChange(username, "Sarah", "Miller", true, LocalDate.of(2026, 8, 1), 60,
                ActionType.ADD);
        AtomicBoolean competitorWrote = new AtomicBoolean();

        workloads.update(username, before -> {
            if (competitorWrote.compareAndSet(false, true)) {
                documents.save(document(username,
                        List.of(new YearDocument(2026, List.of(new MonthDocument(8, 30))))));
            }
            return add.applyTo(before);
        });

        TrainerWorkload stored = workloads.findByUsername(username).orElseThrow();
        Assertions.assertEquals(90, stored.durationIn(YearMonth.of(2026, 8)));
        Assertions.assertEquals(1L, documents.findByUsername(username).orElseThrow().getVersion());
    }
}
