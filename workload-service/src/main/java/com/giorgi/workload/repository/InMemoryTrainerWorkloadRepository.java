package com.giorgi.workload.repository;

import com.giorgi.workload.model.TrainerWorkload;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

@Repository
public class InMemoryTrainerWorkloadRepository implements TrainerWorkloadRepository {

    private final ConcurrentMap<String, TrainerWorkload> workloads = new ConcurrentHashMap<>();

    @Override
    public Optional<TrainerWorkload> findByUsername(String username) {
        return Optional.ofNullable(workloads.get(username));
    }

    @Override
    public WorkloadUpdate update(String username, UnaryOperator<Optional<TrainerWorkload>> change) {
        AtomicReference<TrainerWorkload> before = new AtomicReference<>();
        TrainerWorkload after = workloads.compute(username, (key, existing) -> {
            before.set(existing);
            return change.apply(Optional.ofNullable(existing)).orElse(null);
        });
        return new WorkloadUpdate(Optional.ofNullable(before.get()), Optional.ofNullable(after));
    }
}
