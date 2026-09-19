package com.giorgi.gymcrm.dao;

import com.giorgi.gymcrm.model.Trainee;

import java.util.List;

public interface TraineeDao {
    Trainee save(Trainee trainee);
    Trainee update(Trainee trainee);
    void delete(long id);
    Trainee findById(long id);
    List<Trainee> findAll();
}
