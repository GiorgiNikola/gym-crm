package com.giorgi.gymcrm.service;

import com.giorgi.gymcrm.dao.TraineeDao;
import com.giorgi.gymcrm.dao.TrainerDao;
import com.giorgi.gymcrm.dao.TrainingDao;
import com.giorgi.gymcrm.model.Trainer;
import com.giorgi.gymcrm.model.Training;
import com.giorgi.gymcrm.model.TrainingType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Slf4j
@Service
public class TrainingService {
    private TrainingDao trainingDao;
    private TraineeDao traineeDao;
    private TrainerDao trainerDao;

    @Autowired
    public void setTrainingDao(TrainingDao trainingDao) {
        this.trainingDao = trainingDao;
    }

    @Autowired
    public void setTraineeDao(TraineeDao traineeDao) {
        this.traineeDao = traineeDao;
    }

    @Autowired
    public void setTrainerDao(TrainerDao trainerDao) {
        this.trainerDao = trainerDao;
    }

    public Training createTrainingProfile(long traineeID,
                                         long trainerID,
                                         String name,
                                         TrainingType type,
                                         LocalDate date,
                                         long duration) {
        if (!traineeDao.existsByID(traineeID)) {
            log.error("Trainee with id: {} does not exist", traineeID);
            throw new IllegalArgumentException("Trainee with id: " + traineeID +" does not exist");
        }

        Trainer trainer = trainerDao.findById(trainerID);

        if (trainer == null) {
            log.error("Trainer with id: {} does not exist", trainerID);
            throw new IllegalArgumentException("Trainer with id: " + trainerID +" does not exist");
        }

        if (name == null) {
            log.error("Training name is null");
            throw new IllegalArgumentException("Training name should not be null");
        }

        if (type == null) {
            log.error("Training type is null");
            throw new IllegalArgumentException("Training type should not be null");
        }

        if (date == null) {
            log.error("Training date is null");
            throw new IllegalArgumentException("Training date should not be null");
        }

        if (duration <= 0) {
            log.error("Training duration is not positive number");
            throw new IllegalArgumentException("Training duration should not be zero or negative");
        }

        TrainingType trainersSpecialization = trainer.getSpecialization();

        if (!type.equals(trainersSpecialization)) {
            log.error("Training type: {} does not match trainers specialization: {}", type, trainersSpecialization);
            throw new IllegalArgumentException("Training type: " + type + " does not match trainers specialization: " + trainersSpecialization);
        }

        long id = trainingDao.generateId();

        Training training = Training.builder()
                .ID(id)
                .traineeID(traineeID)
                .trainerID(trainerID)
                .name(name)
                .type(type)
                .date(date)
                .duration(duration)
                .build();

        log.info("Creating training with id: {}", id);
        return trainingDao.save(training);
    }

    public Training selectTrainingProfile(long id) {
        return trainingDao.findById(id);
    }
}
