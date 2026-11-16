package com.giorgi.workload.repository;

import com.giorgi.workload.model.TrainerWorkload;

import java.util.Optional;
import java.util.function.UnaryOperator;

public interface TrainerWorkloadRepository {

    Optional<TrainerWorkload> findByUsername(String username);

    WorkloadUpdate update(String username, UnaryOperator<Optional<TrainerWorkload>> change);
}
