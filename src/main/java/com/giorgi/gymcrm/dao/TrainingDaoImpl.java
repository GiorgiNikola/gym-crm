package com.giorgi.gymcrm.dao;

import com.giorgi.gymcrm.model.Training;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class TrainingDaoImpl implements TrainingDao {
    private Map<Long, Training> trainings;

    @Autowired
    public void setTrainings(Map<Long, Training> trainings) {
        this.trainings = trainings;
    }
    @Override
    public Training save(Training training) {
        if (trainings.get(training.getID()) != null) {
            log.error("Training with id: {} already exists", training.getID());
            throw new IllegalArgumentException("Training with id: " + training.getID() +" already exists");
        }
        trainings.put(training.getID(), training);
        return training;
    }

    @Override
    public Training findById(long id) {
        return trainings.get(id);
    }
}
