package com.giorgi.gymcrm.service;

import com.giorgi.gymcrm.dao.TraineeDao;
import com.giorgi.gymcrm.dao.TrainerDao;
import com.giorgi.gymcrm.dao.TrainingDao;
import com.giorgi.gymcrm.dao.TrainingTypeDao;
import com.giorgi.gymcrm.exception.ProfileNotFoundException;
import com.giorgi.gymcrm.model.Trainee;
import com.giorgi.gymcrm.model.Trainer;
import com.giorgi.gymcrm.model.Training;
import com.giorgi.gymcrm.model.TrainingType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static com.giorgi.gymcrm.util.Validations.requireText;

@Slf4j
@Service
public class TrainingService {

    private TrainingDao trainingDao;
    private TraineeDao traineeDao;
    private TrainerDao trainerDao;
    private TrainingTypeDao trainingTypeDao;
    private AuthenticationService authenticationService;

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

    @Autowired
    public void setTrainingTypeDao(TrainingTypeDao trainingTypeDao) {
        this.trainingTypeDao = trainingTypeDao;
    }

    @Autowired
    public void setAuthenticationService(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @Transactional
    public Training addTraining(String username, String password,
                                String traineeUsername, String trainerUsername,
                                String name, String typeName,
                                LocalDate date, Long duration) {
        authenticationService.authenticate(username, password);
        requireText(traineeUsername, "Trainee username");
        requireText(trainerUsername, "Trainer username");
        requireText(name, "Training name");
        requireText(typeName, "Training type");

        if (date == null) {
            throw new IllegalArgumentException("Training date must not be null");
        }
        if (duration == null || duration <= 0) {
            throw new IllegalArgumentException("Training duration must be a positive number");
        }

        Trainee trainee = traineeDao.findByUsername(traineeUsername)
                .orElseThrow(() -> new ProfileNotFoundException("Trainee not found: " + traineeUsername));
        Trainer trainer = trainerDao.findByUsername(trainerUsername)
                .orElseThrow(() -> new ProfileNotFoundException("Trainer not found: " + trainerUsername));
        TrainingType type = trainingTypeDao.findByName(typeName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown training type: " + typeName));

        if (!trainer.getSpecialization().getId().equals(type.getId())) {
            throw new IllegalArgumentException("Training type " + typeName
                    + " does not match the specialization of trainer " + trainerUsername);
        }

        Training training = Training.builder()
                .trainee(trainee)
                .trainer(trainer)
                .name(name)
                .type(type)
                .date(date)
                .duration(duration)
                .build();

        trainingDao.save(training);
        log.info("Added training '{}' for trainee {} with trainer {}", name, traineeUsername, trainerUsername);
        return training;
    }

    @Transactional(readOnly = true)
    public List<Training> getTraineeTrainings(String username, String password,
                                              LocalDate fromDate, LocalDate toDate,
                                              String trainerName, String typeName) {
        authenticationService.authenticate(username, password);
        return trainingDao.findTraineeTrainings(username, fromDate, toDate, trainerName, typeName);
    }

    @Transactional(readOnly = true)
    public List<Training> getTrainerTrainings(String username, String password,
                                              LocalDate fromDate, LocalDate toDate,
                                              String traineeName) {
        authenticationService.authenticate(username, password);
        return trainingDao.findTrainerTrainings(username, fromDate, toDate, traineeName);
    }
}