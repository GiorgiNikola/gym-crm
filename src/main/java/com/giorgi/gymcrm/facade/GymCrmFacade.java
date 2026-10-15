package com.giorgi.gymcrm.facade;

import com.giorgi.gymcrm.model.Trainee;
import com.giorgi.gymcrm.model.Trainer;
import com.giorgi.gymcrm.model.Training;
import com.giorgi.gymcrm.service.TraineeService;
import com.giorgi.gymcrm.service.TrainerService;
import com.giorgi.gymcrm.service.TrainingService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Component
public class GymCrmFacade {

    private final TraineeService traineeService;
    private final TrainerService trainerService;
    private final TrainingService trainingService;

    public GymCrmFacade(TraineeService traineeService,
                        TrainerService trainerService,
                        TrainingService trainingService) {
        this.traineeService = traineeService;
        this.trainerService = trainerService;
        this.trainingService = trainingService;
    }

    public Trainee createTraineeProfile(String firstName, String lastName,
                                        LocalDate dateOfBirth, String address) {
        return traineeService.createProfile(firstName, lastName, dateOfBirth, address);
    }

    public Trainee selectTraineeProfile(String username, String password) {
        return traineeService.selectByUsername(username, password);
    }

    public void changeTraineePassword(String username, String password, String newPassword) {
        traineeService.changePassword(username, password, newPassword);
    }

    public Trainee updateTraineeProfile(String username, String password,
                                        String firstName, String lastName,
                                        LocalDate dateOfBirth, String address) {
        return traineeService.updateProfile(username, password, firstName, lastName, dateOfBirth, address);
    }

    public void activateTrainee(String username, String password) {
        traineeService.activate(username, password);
    }

    public void deactivateTrainee(String username, String password) {
        traineeService.deactivate(username, password);
    }

    public void deleteTraineeProfile(String username, String password) {
        traineeService.deleteByUsername(username, password);
    }

    public Trainee updateTraineeTrainers(String username, String password, Set<String> trainerUsernames) {
        return traineeService.updateTrainers(username, password, trainerUsernames);
    }

    public List<Trainer> getUnassignedTrainers(String username, String password) {
        return traineeService.findUnassignedTrainers(username, password);
    }

    public Trainer createTrainerProfile(String firstName, String lastName, String specializationName) {
        return trainerService.createProfile(firstName, lastName, specializationName);
    }

    public Trainer selectTrainerProfile(String username, String password) {
        return trainerService.selectByUsername(username, password);
    }

    public void changeTrainerPassword(String username, String password, String newPassword) {
        trainerService.changePassword(username, password, newPassword);
    }

    public Trainer updateTrainerProfile(String username, String password,
                                        String firstName, String lastName,
                                        String specializationName) {
        return trainerService.updateProfile(username, password, firstName, lastName, specializationName);
    }

    public void activateTrainer(String username, String password) {
        trainerService.activate(username, password);
    }

    public void deactivateTrainer(String username, String password) {
        trainerService.deactivate(username, password);
    }

    public Training addTraining(String username, String password,
                                String traineeUsername, String trainerUsername,
                                String name, String typeName,
                                LocalDate date, Long duration) {
        return trainingService.addTraining(username, password, traineeUsername, trainerUsername,
                name, typeName, date, duration);
    }

    public List<Training> getTraineeTrainings(String username, String password,
                                              LocalDate fromDate, LocalDate toDate,
                                              String trainerName, String typeName) {
        return trainingService.getTraineeTrainings(username, password, fromDate, toDate, trainerName, typeName);
    }

    public List<Training> getTrainerTrainings(String username, String password,
                                              LocalDate fromDate, LocalDate toDate,
                                              String traineeName) {
        return trainingService.getTrainerTrainings(username, password, fromDate, toDate, traineeName);
    }
}