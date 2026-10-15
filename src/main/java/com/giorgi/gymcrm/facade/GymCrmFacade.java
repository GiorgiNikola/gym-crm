package com.giorgi.gymcrm.facade;

import com.giorgi.gymcrm.model.Trainee;
import com.giorgi.gymcrm.model.Trainer;
import com.giorgi.gymcrm.model.Training;
import com.giorgi.gymcrm.service.TraineeService;
import com.giorgi.gymcrm.service.TrainerService;
import com.giorgi.gymcrm.service.TrainingService;
import com.giorgi.gymcrm.service.UserService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Component
public class GymCrmFacade {

    private final TraineeService traineeService;
    private final TrainerService trainerService;
    private final TrainingService trainingService;
    private final UserService userService;

    public GymCrmFacade(TraineeService traineeService,
                        TrainerService trainerService,
                        TrainingService trainingService,
                        UserService userService) {
        this.traineeService = traineeService;
        this.trainerService = trainerService;
        this.trainingService = trainingService;
        this.userService = userService;
    }

    public Trainee createTraineeProfile(String firstName, String lastName,
                                        LocalDate dateOfBirth, String address) {
        return traineeService.createProfile(firstName, lastName, dateOfBirth, address);
    }

    public Trainee selectTraineeProfile(String username, String password) {
        return traineeService.selectByUsername(username, password);
    }

    public void changePassword(String username, String password, String newPassword) {
        userService.changePassword(username, password, newPassword);
    }

    public Trainee updateTraineeProfile(String username, String password,
                                        String firstName, String lastName,
                                        LocalDate dateOfBirth, String address) {
        return traineeService.updateProfile(username, password, firstName, lastName, dateOfBirth, address);
    }

    public void changeStatus(String username, String password, boolean active) {
        userService.changeStatus(username, password, active);
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

    public Trainer updateTrainerProfile(String username, String password,
                                        String firstName, String lastName,
                                        String specializationName) {
        return trainerService.updateProfile(username, password, firstName, lastName, specializationName);
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