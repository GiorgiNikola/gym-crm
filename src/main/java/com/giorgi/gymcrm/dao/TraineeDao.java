package com.giorgi.gymcrm.dao;

import com.giorgi.gymcrm.model.Trainee;
import com.giorgi.gymcrm.model.User;

import java.util.Optional;

public interface TraineeDao {
    Trainee save(Trainee trainee);
    Trainee update(Trainee trainee);
    void delete(Trainee trainee);
    Optional<Trainee> findByUsername(String username);
    Optional<Trainee> findProfileByUsername(String username);
}