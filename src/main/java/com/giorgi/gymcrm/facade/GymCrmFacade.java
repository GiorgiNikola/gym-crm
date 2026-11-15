package com.giorgi.gymcrm.facade;

import com.giorgi.gymcrm.model.Trainee;
import com.giorgi.gymcrm.model.Trainer;
import com.giorgi.gymcrm.model.Training;
import com.giorgi.gymcrm.model.TrainingType;
import com.giorgi.gymcrm.service.LoginService;
import com.giorgi.gymcrm.service.NewUser;
import com.giorgi.gymcrm.service.TraineeService;
import com.giorgi.gymcrm.service.TrainerService;
import com.giorgi.gymcrm.service.TrainingService;
import com.giorgi.gymcrm.service.TrainingTypeService;
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
    private final TrainingTypeService trainingTypeService;
    private final UserService userService;
    private final LoginService loginService;

    public GymCrmFacade(TraineeService traineeService,
                        TrainerService trainerService,
                        TrainingService trainingService,
                        TrainingTypeService trainingTypeService,
                        UserService userService,
                        LoginService loginService) {
        this.traineeService = traineeService;
        this.trainerService = trainerService;
        this.trainingService = trainingService;
        this.trainingTypeService = trainingTypeService;
        this.userService = userService;
        this.loginService = loginService;
    }

    public String login(String username, String password) {
        return loginService.login(username, password);
    }

    public RegisteredProfile createTraineeProfile(String firstName, String lastName,
                                                  LocalDate dateOfBirth, String address) {
        return registered(traineeService.createProfile(firstName, lastName, dateOfBirth, address));
    }

    public RegisteredProfile createTrainerProfile(String firstName, String lastName, String specializationName) {
        return registered(trainerService.createProfile(firstName, lastName, specializationName));
    }

    private RegisteredProfile registered(NewUser created) {
        String username = created.user().getUsername();
        return new RegisteredProfile(username, created.rawPassword(), loginService.issueFor(username));
    }

    public Trainee selectTraineeProfile(String username) {
        return traineeService.selectByUsername(username);
    }

    public Trainee updateTraineeProfile(String username,
                                        String firstName, String lastName,
                                        LocalDate dateOfBirth, String address, boolean isActive) {
        return traineeService.updateProfile(username, firstName, lastName, dateOfBirth, address, isActive);
    }

    public void deleteTraineeProfile(String username) {
        traineeService.deleteByUsername(username);
    }

    public Trainee updateTraineeTrainers(String username, Set<String> trainerUsernames) {
        return traineeService.updateTrainers(username, trainerUsernames);
    }

    public List<Trainer> getUnassignedTrainers(String username) {
        return traineeService.findUnassignedTrainers(username);
    }

    public Trainer selectTrainerProfile(String username) {
        return trainerService.selectByUsername(username);
    }

    public Trainer updateTrainerProfile(String username, String firstName, String lastName, boolean isActive) {
        return trainerService.updateProfile(username, firstName, lastName, isActive);
    }

    public void changePassword(String username, String oldPassword, String newPassword) {
        userService.changePassword(username, oldPassword, newPassword);
    }

    public void changeStatus(String username, boolean active) {
        userService.changeStatus(username, active);
    }

    public Training addTraining(String traineeUsername, String trainerUsername,
                                String name, LocalDate date, Long duration) {
        return trainingService.addTraining(traineeUsername, trainerUsername, name, date, duration);
    }

    public List<Training> getTraineeTrainings(String username,
                                              LocalDate fromDate, LocalDate toDate,
                                              String trainerName, String typeName) {
        return trainingService.getTraineeTrainings(username, fromDate, toDate, trainerName, typeName);
    }

    public List<Training> getTrainerTrainings(String username,
                                              LocalDate fromDate, LocalDate toDate,
                                              String traineeName) {
        return trainingService.getTrainerTrainings(username, fromDate, toDate, traineeName);
    }

    public List<TrainingType> getTrainingTypes() {
        return trainingTypeService.findAll();
    }
}
