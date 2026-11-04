package com.giorgi.gymcrm.facade;

import com.giorgi.gymcrm.model.Trainee;
import com.giorgi.gymcrm.model.Trainer;
import com.giorgi.gymcrm.model.Training;
import com.giorgi.gymcrm.model.TrainingType;
import com.giorgi.gymcrm.model.User;
import com.giorgi.gymcrm.service.LoginService;
import com.giorgi.gymcrm.service.NewUser;
import com.giorgi.gymcrm.service.TraineeService;
import com.giorgi.gymcrm.service.TrainerService;
import com.giorgi.gymcrm.service.TrainingService;
import com.giorgi.gymcrm.service.TrainingTypeService;
import com.giorgi.gymcrm.service.UserService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GymCrmFacadeTest {

    private static final LocalDate DATE_OF_BIRTH = LocalDate.of(1998, 5, 14);
    private static final LocalDate TRAINING_DATE = LocalDate.of(2026, 8, 1);
    private static final String ADDRESS = "123 Main St New York";
    private static final String RAW_PASSWORD = "aX7kQ2mN9p";
    private static final String TOKEN = "a.signed.token";

    @Mock
    private TraineeService traineeService;

    @Mock
    private TrainerService trainerService;

    @Mock
    private TrainingService trainingService;

    @Mock
    private TrainingTypeService trainingTypeService;

    @Mock
    private UserService userService;

    @Mock
    private LoginService loginService;

    @InjectMocks
    private GymCrmFacade gymCrmFacade;

    private final Trainee trainee = Trainee.builder().id(1L).build();
    private final Trainer trainer = Trainer.builder().id(2L).build();
    private final Training training = Training.builder().id(3L).build();

    private NewUser created() {
        return new NewUser(User.builder().username("John.Smith").password("$2a$10$hash").build(), RAW_PASSWORD);
    }

    @Test
    @DisplayName("createTraineeProfile registers and comes back with a token")
    void createsTraineeProfile() {
        when(traineeService.createProfile("John", "Smith", DATE_OF_BIRTH, ADDRESS)).thenReturn(created());
        when(loginService.issueFor("John.Smith")).thenReturn(TOKEN);

        RegisteredProfile registered = gymCrmFacade.createTraineeProfile("John", "Smith", DATE_OF_BIRTH, ADDRESS);

        Assertions.assertEquals("John.Smith", registered.username());
        Assertions.assertEquals(RAW_PASSWORD, registered.password());
        Assertions.assertEquals(TOKEN, registered.token());
    }

    @Test
    @DisplayName("createTrainerProfile registers and comes back with a token")
    void createsTrainerProfile() {
        when(trainerService.createProfile("Sarah", "Miller", "YOGA")).thenReturn(created());
        when(loginService.issueFor("John.Smith")).thenReturn(TOKEN);

        RegisteredProfile registered = gymCrmFacade.createTrainerProfile("Sarah", "Miller", "YOGA");

        Assertions.assertEquals(RAW_PASSWORD, registered.password());
        Assertions.assertEquals(TOKEN, registered.token());
    }

    @Test
    @DisplayName("selectTraineeProfile goes to the trainee service")
    void selectsTraineeProfile() {
        when(traineeService.selectByUsername("John.Smith")).thenReturn(trainee);

        Assertions.assertSame(trainee, gymCrmFacade.selectTraineeProfile("John.Smith"));
    }

    @Test
    @DisplayName("updateTraineeProfile goes to the trainee service")
    void updatesTraineeProfile() {
        when(traineeService.updateProfile("John.Smith", "John", "Smith", DATE_OF_BIRTH, ADDRESS, true))
                .thenReturn(trainee);

        Assertions.assertSame(trainee, gymCrmFacade.updateTraineeProfile(
                "John.Smith", "John", "Smith", DATE_OF_BIRTH, ADDRESS, true));
    }

    @Test
    @DisplayName("deleteTraineeProfile goes to the trainee service")
    void deletesTraineeProfile() {
        gymCrmFacade.deleteTraineeProfile("John.Smith");

        verify(traineeService).deleteByUsername("John.Smith");
    }

    @Test
    @DisplayName("updateTraineeTrainers goes to the trainee service")
    void updatesTraineeTrainers() {
        Set<String> trainers = Set.of("Sarah.Miller");
        when(traineeService.updateTrainers("John.Smith", trainers)).thenReturn(trainee);

        Assertions.assertSame(trainee, gymCrmFacade.updateTraineeTrainers("John.Smith", trainers));
    }

    @Test
    @DisplayName("getUnassignedTrainers goes to the trainee service")
    void getsUnassignedTrainers() {
        List<Trainer> trainers = List.of(trainer);
        when(traineeService.findUnassignedTrainers("John.Smith")).thenReturn(trainers);

        Assertions.assertSame(trainers, gymCrmFacade.getUnassignedTrainers("John.Smith"));
    }

    @Test
    @DisplayName("selectTrainerProfile goes to the trainer service")
    void selectsTrainerProfile() {
        when(trainerService.selectByUsername("Sarah.Miller")).thenReturn(trainer);

        Assertions.assertSame(trainer, gymCrmFacade.selectTrainerProfile("Sarah.Miller"));
    }

    @Test
    @DisplayName("updateTrainerProfile goes to the trainer service")
    void updatesTrainerProfile() {
        when(trainerService.updateProfile("Sarah.Miller", "Sarah", "Miller", true)).thenReturn(trainer);

        Assertions.assertSame(trainer,
                gymCrmFacade.updateTrainerProfile("Sarah.Miller", "Sarah", "Miller", true));
    }

    @Test
    @DisplayName("changePassword goes to the user service")
    void changesPassword() {
        gymCrmFacade.changePassword("John.Smith", RAW_PASSWORD, "bY8lR3nO0q");

        verify(userService).changePassword("John.Smith", RAW_PASSWORD, "bY8lR3nO0q");
    }

    @Test
    @DisplayName("changeStatus goes to the user service with the requested state")
    void changesStatus() {
        gymCrmFacade.changeStatus("John.Smith", false);

        verify(userService).changeStatus("John.Smith", false);
    }

    @Test
    @DisplayName("addTraining goes to the training service")
    void addsTraining() {
        when(trainingService.addTraining("John.Smith", "Sarah.Miller", "Morning Yoga", TRAINING_DATE, 60L))
                .thenReturn(training);

        Assertions.assertSame(training, gymCrmFacade.addTraining(
                "John.Smith", "Sarah.Miller", "Morning Yoga", TRAINING_DATE, 60L));
    }

    @Test
    @DisplayName("getTraineeTrainings goes to the training service")
    void getsTraineeTrainings() {
        List<Training> trainings = List.of(training);
        when(trainingService.getTraineeTrainings("John.Smith", TRAINING_DATE, TRAINING_DATE, "Sarah", "YOGA"))
                .thenReturn(trainings);

        Assertions.assertSame(trainings, gymCrmFacade.getTraineeTrainings(
                "John.Smith", TRAINING_DATE, TRAINING_DATE, "Sarah", "YOGA"));
    }

    @Test
    @DisplayName("getTrainerTrainings goes to the training service")
    void getsTrainerTrainings() {
        List<Training> trainings = List.of(training);
        when(trainingService.getTrainerTrainings("Sarah.Miller", TRAINING_DATE, TRAINING_DATE, "John"))
                .thenReturn(trainings);

        Assertions.assertSame(trainings, gymCrmFacade.getTrainerTrainings(
                "Sarah.Miller", TRAINING_DATE, TRAINING_DATE, "John"));
    }

    @Test
    @DisplayName("getTrainingTypes goes to the training type service")
    void getsTrainingTypes() {
        List<TrainingType> types = List.of(new TrainingType());
        when(trainingTypeService.findAll()).thenReturn(types);

        Assertions.assertSame(types, gymCrmFacade.getTrainingTypes());
    }

    @Test
    @DisplayName("login goes to the login service and gives back its token")
    void logsIn() {
        when(loginService.login("John.Smith", RAW_PASSWORD)).thenReturn(TOKEN);

        Assertions.assertEquals(TOKEN, gymCrmFacade.login("John.Smith", RAW_PASSWORD));
    }
}
