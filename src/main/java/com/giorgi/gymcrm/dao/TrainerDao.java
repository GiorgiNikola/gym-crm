package com.giorgi.gymcrm.dao;

import com.giorgi.gymcrm.model.Trainer;

import java.util.List;
import java.util.Optional;

public interface TrainerDao {
    Trainer save(Trainer trainer);
    Trainer update(Trainer trainer);
    Optional<Trainer> findByUsername(String username);
    List<Trainer> findNotAssignedToTrainee(String traineeUsername);
}