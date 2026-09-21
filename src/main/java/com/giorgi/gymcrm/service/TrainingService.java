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
            log.warn("Training creation rejected, trainee with id: {} does not exist", traineeID);
            throw new IllegalArgumentException("Trainee with id: " + traineeID +" does not exist");
        }

        Trainer trainer = trainerDao.findById(trainerID);

        if (trainer == null) {
            log.warn("Training creation rejected, trainer with id: {} does not exist", trainerID);
            throw new IllegalArgumentException("Trainer with id: " + trainerID +" does not exist");
        }

        if (name == null || name.isBlank()) {
            log.warn("Training creation rejected, training name is missing");
            throw new IllegalArgumentException("Training name should not be null or blank");
        }

        if (type == null) {
            log.warn("Training creation rejected, training type is missing");
            throw new IllegalArgumentException("Training type should not be null");
        }

        if (date == null) {
            log.warn("Training creation rejected, training date is missing");
            throw new IllegalArgumentException("Training date should not be null");
        }

        if (duration <= 0) {
            log.warn("Training creation rejected, duration must be positive but was: {}", duration);
            throw new IllegalArgumentException("Training duration should not be zero or negative");
        }

        TrainingType trainersSpecialization = trainer.getSpecialization();

        if (!type.equals(trainersSpecialization)) {
            log.warn("Training creation rejected, type: {} does not match trainer specialization: {}", type, trainersSpecialization);
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

        Training savedTraining = trainingDao.save(training);
        log.info("Created training, id: {}, trainee id: {}, trainer id: {}", savedTraining.getID(), traineeID, trainerID);
        return savedTraining;
    }

    public Training selectTrainingProfile(long id) {
        log.debug("Selecting training profile, id: {}", id);
        return trainingDao.findById(id);
    }
}