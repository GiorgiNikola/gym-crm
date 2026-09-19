package com.giorgi.gymcrm.dao;

import com.giorgi.gymcrm.model.Training;

public interface TrainingDao {
    Training save(Training training);
    Training findById(long id);
}
