package com.giorgi.gymcrm.dao;

import com.giorgi.gymcrm.model.Trainee;
import com.giorgi.gymcrm.model.Trainer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class TrainerDaoImpl implements TrainerDao {
    private Map<Long, Trainer> trainers;

    @Autowired
    public void setTrainers(Map<Long, Trainer> trainers) {
        this.trainers = trainers;
    }

    @Override
    public Trainer save(Trainer trainer) {
        if (trainers.get(trainer.getUserID()) != null) {
            log.error("Trainer with id: {} already exists", trainer.getUserID());
            throw new IllegalArgumentException("Trainer with id: " + trainer.getUserID() +" already exists");
        }
        trainers.put(trainer.getUserID(), trainer);
        return trainer;
    }

    @Override
    public Trainer update(Trainer trainer) {
        Trainer existingTrainer = trainers.get(trainer.getUserID());
        if (existingTrainer != null) {
            trainers.put(trainer.getUserID(), trainer);
            return trainer;
        } else {
            log.error("Trainer with id: {} does not exist", trainer.getUserID());
            throw new IllegalArgumentException("Trainer with id: " + trainer.getUserID() +" does not exist");
        }
    }

    @Override
    public Trainer findById(long id) {
        return trainers.get(id);
    }

    @Override
    public List<Trainer> findAll() {
        return trainers.values().stream().toList();
    }
}
