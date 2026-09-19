package com.giorgi.gymcrm.dao;

import com.giorgi.gymcrm.model.Trainer;

import java.util.List;

public interface TrainerDao {
    Trainer save(Trainer trainer);
    Trainer update(Trainer trainer);
    Trainer findById(long id);
    List<Trainer> findAll();
}
