package com.giorgi.gymcrm.dao;

import com.giorgi.gymcrm.model.Trainee;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class TraineeDaoImpl implements TraineeDao {
    private Map<Long, Trainee> trainees;

    @Autowired
    public void setTrainees(Map<Long, Trainee> trainees) {
        this.trainees = trainees;
    }

    @Override
    public Trainee save(Trainee trainee) {
        if (trainees.get(trainee.getUserID()) != null) {
            log.error("Trainee with id: {} already exists", trainee.getUserID());
            throw new IllegalArgumentException("Trainee with id: " + trainee.getUserID() +" already exists");
        }
        trainees.put(trainee.getUserID(), trainee);
        return trainee;
    }

    @Override
    public Trainee update(Trainee trainee) {
        Trainee existingTrainee = trainees.get(trainee.getUserID());
        if (existingTrainee != null) {
            trainees.put(trainee.getUserID(), trainee);
            return trainee;
        } else {
            log.error("Trainee with id: {} does not exist", trainee.getUserID());
            throw new IllegalArgumentException("Trainee with id: " + trainee.getUserID() +" does not exist");
        }
    }

    @Override
    public void delete(long id) {
        trainees.remove(id);
    }

    @Override
    public Trainee findById(long id) {
        return trainees.get(id);
    }

    @Override
    public List<Trainee> findAll() {
        return trainees.values().stream().toList();
    }
}
