package com.giorgi.gymcrm.facade;

import com.giorgi.gymcrm.model.Trainee;
import com.giorgi.gymcrm.model.Trainer;
import com.giorgi.gymcrm.model.Training;
import com.giorgi.gymcrm.model.TrainingType;
import com.giorgi.gymcrm.service.TraineeService;
import com.giorgi.gymcrm.service.TrainerService;
import com.giorgi.gymcrm.service.TrainingService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class GymCrmFacade {
    private final TraineeService traineeService;
    private final TrainerService trainerService;
    private final TrainingService trainingService;

    public GymCrmFacade(TraineeService traineeService, TrainerService trainerService, TrainingService trainingService) {
        this.traineeService = traineeService;
        this.trainerService = trainerService;
        this.trainingService = trainingService;
    }

    public Trainee createTraineeProfile(String firstname,
                                        String lastname,
                                        boolean isActive,
                                        LocalDate dateOfBirth,
                                        String address) {
        return traineeService.createTraineeProfile(firstname,
                                            lastname,
                                            isActive,
                                            dateOfBirth,
                                            address);
    }

    public Trainee updateTraineeProfile(Trainee trainee) {
        return traineeService.updateTraineeProfile(trainee);
    }

    public void deleteTraineeProfile(long id) {
        traineeService.deleteTraineeProfile(id);
    }

    public Trainee selectTraineeProfile(long id) {
        return traineeService.selectTraineeProfile(id);
    }

    public Trainer createTrainerProfile(String firstname,
                                        String lastname,
                                        boolean isActive,
                                        TrainingType specialization) {
        return trainerService.createTrainerProfile(firstname,
                                                   lastname,
                                                   isActive,
                                                   specialization);
    }

    public Trainer updateTrainerProfile(Trainer trainer) {
        return trainerService.updateTrainerProfile(trainer);
    }

    public Trainer selectTrainerProfile(long id) {
        return trainerService.selectTrainerProfile(id);
    }

    public Training createTrainingProfile(long traineeID,
                                          long trainerID,
                                          String name,
                                          TrainingType type,
                                          LocalDate date,
                                          long duration) {
        return trainingService.createTrainingProfile(traineeID,
                                                     trainerID,
                                                     name,
                                                     type,
                                                     date,
                                                     duration);
    }

    public Training selectTrainingProfile(long id) {
        return trainingService.selectTrainingProfile(id);
    }
}
