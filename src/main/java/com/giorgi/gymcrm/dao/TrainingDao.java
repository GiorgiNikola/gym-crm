package com.giorgi.gymcrm.dao;

import com.giorgi.gymcrm.model.Training;

import java.util.List;

public interface TrainingDao {
    Training save(Training training);
    Training findById(long id);
    List<Training> findAll();
    long generateId();
}
