package com.giorgi.gymcrm.dao;

import com.giorgi.gymcrm.model.Trainee;

import java.util.Optional;

public interface TraineeDao {
    Trainee save(Trainee trainee);
    Trainee update(Trainee trainee);
    void delete(Trainee trainee);
    Optional<Trainee> findByUsername(String username);
}