package com.giorgi.gymcrm.service;

import com.giorgi.gymcrm.dao.TraineeDao;
import com.giorgi.gymcrm.dao.TrainerDao;
import com.giorgi.gymcrm.dao.TrainingDao;
import com.giorgi.gymcrm.exception.ProfileNotFoundException;
import com.giorgi.gymcrm.metrics.GymMetrics;
import com.giorgi.gymcrm.model.Trainee;
import com.giorgi.gymcrm.model.Trainer;
import com.giorgi.gymcrm.model.Training;
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
    private TrainingTypeService trainingTypeService;
    private AuthenticationService authenticationService;
    private GymMetrics gymMetrics;

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
    public void setTrainingTypeService(TrainingTypeService trainingTypeService) {
        this.trainingTypeService = trainingTypeService;
    }

    @Autowired
    public void setAuthenticationService(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @Autowired
    public void setGymMetrics(GymMetrics gymMetrics) {
        this.gymMetrics = gymMetrics;
    }

    @Transactional
    public Training addTraining(String username, String password,
                                String traineeUsername, String trainerUsername,
                                String name, LocalDate date, Long duration) {
        authenticationService.authenticate(username, password);
        requireText(traineeUsername, "Trainee username");
        requireText(trainerUsername, "Trainer username");
        requireText(name, "Training name");

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

        Training training = Training.builder()
                .trainee(trainee)
                .trainer(trainer)
                .name(name)
                .type(trainer.getSpecialization())
                .date(date)
                .duration(duration)
                .build();

        trainingDao.save(training);
        gymMetrics.trainingAdded(duration);
        log.info("Added training '{}' for trainee {} with trainer {}", name, traineeUsername, trainerUsername);
        return training;
    }

    @Transactional(readOnly = true)
    public List<Training> getTraineeTrainings(String username, String password,
                                              LocalDate fromDate, LocalDate toDate,
                                              String trainerName, String typeName) {
        authenticationService.authenticate(username, password);
        requireValidRange(fromDate, toDate);
        if (typeName != null && !typeName.isBlank()) {
            trainingTypeService.requireByName(typeName);
        }

        return trainingDao.findTraineeTrainings(username, fromDate, toDate, trainerName, typeName);
    }

    @Transactional(readOnly = true)
    public List<Training> getTrainerTrainings(String username, String password,
                                              LocalDate fromDate, LocalDate toDate,
                                              String traineeName) {
        authenticationService.authenticate(username, password);
        requireValidRange(fromDate, toDate);

        return trainingDao.findTrainerTrainings(username, fromDate, toDate, traineeName);
    }

    private void requireValidRange(LocalDate fromDate, LocalDate toDate) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("The from date " + fromDate + " is after the to date " + toDate);
        }
    }
}