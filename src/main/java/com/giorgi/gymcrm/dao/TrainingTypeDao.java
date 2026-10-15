package com.giorgi.gymcrm.dao;

import com.giorgi.gymcrm.model.TrainingType;

import java.util.List;
import java.util.Optional;

public interface TrainingTypeDao {
    Optional<TrainingType> findByName(String name);
    List<TrainingType> findAll();
}
