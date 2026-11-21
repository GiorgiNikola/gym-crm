package com.giorgi.workload.persistence;

import com.giorgi.workload.config.MongoConfig;
import com.giorgi.workload.model.TrainerWorkload;
import com.giorgi.workload.repository.TrainerWorkloadRepository;
import com.giorgi.workload.repository.WorkloadUpdate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Repository
public class MongoTrainerWorkloadRepository implements TrainerWorkloadRepository {

    private final TrainerWorkloadMongoRepository documents;
    private final TrainerWorkloadDocumentMapper mapper;
    private final RetryTemplate versionConflictRetry;
    private final int recentEventIdLimit;

    public MongoTrainerWorkloadRepository(TrainerWorkloadMongoRepository documents,
                                          TrainerWorkloadDocumentMapper mapper,
                                          RetryTemplate versionConflictRetry,
                                          PersistenceProperties properties) {
        this.documents = documents;
        this.mapper = mapper;
        this.versionConflictRetry = versionConflictRetry;
        this.recentEventIdLimit = properties.recentEventIds();
    }

    @Override
    public Optional<TrainerWorkload> findByUsername(String username) {
        return find(username).map(mapper::toDomain);
    }

    @Override
    public WorkloadUpdate update(String username, UnaryOperator<Optional<TrainerWorkload>> change) {
        return updateWithRetry(username, Optional.empty(), change);
    }

    @Override
    public WorkloadUpdate update(String username, UUID eventId, UnaryOperator<Optional<TrainerWorkload>> change) {
        return updateWithRetry(username, Optional.of(eventId.toString()), change);
    }

    private WorkloadUpdate updateWithRetry(String username, Optional<String> eventId,
                                           UnaryOperator<Optional<TrainerWorkload>> change) {
        AtomicInteger attempts = new AtomicInteger();
        return versionConflictRetry.invoke(() -> {
            int attempt = attempts.incrementAndGet();
            if (attempt > 1) {
                log.warn("Version conflict on the workload of trainer {}, retry {} of {}",
                        username, attempt - 1, MongoConfig.VERSION_CONFLICT_RETRIES);
            }
            return attemptUpdate(username, eventId, change);
        });
    }

    private WorkloadUpdate attemptUpdate(String username, Optional<String> eventId,
                                         UnaryOperator<Optional<TrainerWorkload>> change) {
        Optional<TrainerWorkloadDocument> stored = find(username);
        Optional<TrainerWorkload> before = stored.map(mapper::toDomain);
        List<String> recentEventIds = stored.map(MongoTrainerWorkloadRepository::recentEventIds).orElse(List.of());
        if (eventId.isPresent() && recentEventIds.contains(eventId.get())) {
            log.info("Event {} is already in the workload of trainer {} at version {}, nothing saved",
                    eventId.get(), username, stored.get().getVersion());
            return WorkloadUpdate.duplicateOf(before);
        }

        Optional<TrainerWorkload> after = change.apply(before);
        WorkloadUpdate update = new WorkloadUpdate(before, after);
        if (after.isEmpty()) {
            return update;
        }

        TrainerWorkloadDocument document = mapper.toDocument(after.get());
        document.setRecentEventIds(eventId.map(id -> append(recentEventIds, id)).orElse(recentEventIds));
        stored.ifPresent(found -> {
            document.setId(found.getId());
            document.setVersion(found.getVersion());
        });
        TrainerWorkloadDocument saved = save(username, document);

        if (stored.isEmpty()) {
            log.info("Created the workload of trainer {} (active {}) at version {}: {}",
                    username, saved.getStatus(), saved.getVersion(), describe(update));
        } else {
            log.info("Updated the workload of trainer {} (active {}) from version {} to {}: {}",
                    username, saved.getStatus(), stored.get().getVersion(), saved.getVersion(), describe(update));
        }
        return update;
    }

    private static List<String> recentEventIds(TrainerWorkloadDocument document) {
        return document.getRecentEventIds() == null ? List.of() : document.getRecentEventIds();
    }

    private List<String> append(List<String> recentEventIds, String eventId) {
        int overflow = Math.max(0, recentEventIds.size() + 1 - recentEventIdLimit);
        return Stream.concat(recentEventIds.stream().skip(overflow), Stream.of(eventId)).toList();
    }

    private Optional<TrainerWorkloadDocument> find(String username) {
        Optional<TrainerWorkloadDocument> found = documents.findByUsername(username);
        if (found.isPresent()) {
            log.debug("Found the workload of trainer {} at version {}", username, found.get().getVersion());
        } else {
            log.debug("No workload stored for trainer {}", username);
        }
        return found;
    }

    private TrainerWorkloadDocument save(String username, TrainerWorkloadDocument document) {
        try {
            return documents.save(document);
        } catch (DuplicateKeyException duplicate) {
            throw new OptimisticLockingFailureException(
                    "The workload of trainer " + username + " was created concurrently", duplicate);
        }
    }

    private static String describe(WorkloadUpdate update) {
        if (update.changedPeriods().isEmpty()) {
            return "no month changed";
        }
        return update.changedPeriods().stream()
                .map(period -> describe(update, period))
                .collect(Collectors.joining(", "));
    }

    private static String describe(WorkloadUpdate update, YearMonth period) {
        return "year %d month %d from %d to %d min".formatted(period.getYear(), period.getMonthValue(),
                update.minutesBefore(period), update.minutesAfter(period));
    }
}
