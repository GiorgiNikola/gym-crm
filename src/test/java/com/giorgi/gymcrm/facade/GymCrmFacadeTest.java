package com.giorgi.gymcrm.facade;

import com.giorgi.gymcrm.model.Trainee;
import com.giorgi.gymcrm.model.Trainer;
import com.giorgi.gymcrm.model.Training;
import com.giorgi.gymcrm.service.TraineeService;
import com.giorgi.gymcrm.service.TrainerService;
import com.giorgi.gymcrm.service.TrainingService;
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
    private static final String PASSWORD = "aX7kQ2mN9p";

    @Mock
    private TraineeService traineeService;

    @Mock
    private TrainerService trainerService;

    @Mock
    private TrainingService trainingService;

    @InjectMocks
    private GymCrmFacade gymCrmFacade;

    private final Trainee trainee = Trainee.builder().id(1L).build();
    private final Trainer trainer = Trainer.builder().id(2L).build();
    private final Training training = Training.builder().id(3L).build();

    @Test
    @DisplayName("createTraineeProfile goes to the trainee service")
    void createsTraineeProfile() {
        when(traineeService.createProfile("John", "Smith", DATE_OF_BIRTH, ADDRESS)).thenReturn(trainee);

        Assertions.assertSame(trainee,
                gymCrmFacade.createTraineeProfile("John", "Smith", DATE_OF_BIRTH, ADDRESS));
    }

    @Test
    @DisplayName("selectTraineeProfile goes to the trainee service")
    void selectsTraineeProfile() {
        when(traineeService.selectByUsername("John.Smith", PASSWORD)).thenReturn(trainee);

        Assertions.assertSame(trainee, gymCrmFacade.selectTraineeProfile("John.Smith", PASSWORD));
    }

    @Test
    @DisplayName("changeTraineePassword goes to the trainee service")
    void changesTraineePassword() {
        gymCrmFacade.changeTraineePassword("John.Smith", PASSWORD, "newPassword1");

        verify(traineeService).changePassword("John.Smith", PASSWORD, "newPassword1");
    }

    @Test
    @DisplayName("updateTraineeProfile goes to the trainee service")
    void updatesTraineeProfile() {
        when(traineeService.updateProfile("John.Smith", PASSWORD, "John", "Smith", DATE_OF_BIRTH, ADDRESS))
                .thenReturn(trainee);

        Assertions.assertSame(trainee, gymCrmFacade.updateTraineeProfile(
                "John.Smith", PASSWORD, "John", "Smith", DATE_OF_BIRTH, ADDRESS));
    }

    @Test
    @DisplayName("activateTrainee goes to the trainee service")
    void activatesTrainee() {
        gymCrmFacade.activateTrainee("John.Smith", PASSWORD);

        verify(traineeService).activate("John.Smith", PASSWORD);
    }

    @Test
    @DisplayName("deactivateTrainee goes to the trainee service")
    void deactivatesTrainee() {
        gymCrmFacade.deactivateTrainee("John.Smith", PASSWORD);

        verify(traineeService).deactivate("John.Smith", PASSWORD);
    }

    @Test
    @DisplayName("deleteTraineeProfile goes to the trainee service")
    void deletesTraineeProfile() {
        gymCrmFacade.deleteTraineeProfile("John.Smith", PASSWORD);

        verify(traineeService).deleteByUsername("John.Smith", PASSWORD);
    }

    @Test
    @DisplayName("updateTraineeTrainers goes to the trainee service")
    void updatesTraineeTrainers() {
        Set<String> trainers = Set.of("Sarah.Miller");
        when(traineeService.updateTrainers("John.Smith", PASSWORD, trainers)).thenReturn(trainee);

        Assertions.assertSame(trainee, gymCrmFacade.updateTraineeTrainers("John.Smith", PASSWORD, trainers));
    }

    @Test
    @DisplayName("getUnassignedTrainers goes to the trainee service")
    void getsUnassignedTrainers() {
        List<Trainer> trainers = List.of(trainer);
        when(traineeService.findUnassignedTrainers("John.Smith", PASSWORD)).thenReturn(trainers);

        Assertions.assertSame(trainers, gymCrmFacade.getUnassignedTrainers("John.Smith", PASSWORD));
    }

    @Test
    @DisplayName("createTrainerProfile goes to the trainer service")
    void createsTrainerProfile() {
        when(trainerService.createProfile("Sarah", "Miller", "YOGA")).thenReturn(trainer);

        Assertions.assertSame(trainer, gymCrmFacade.createTrainerProfile("Sarah", "Miller", "YOGA"));
    }

    @Test
    @DisplayName("selectTrainerProfile goes to the trainer service")
    void selectsTrainerProfile() {
        when(trainerService.selectByUsername("Sarah.Miller", PASSWORD)).thenReturn(trainer);

        Assertions.assertSame(trainer, gymCrmFacade.selectTrainerProfile("Sarah.Miller", PASSWORD));
    }

    @Test
    @DisplayName("changeTrainerPassword goes to the trainer service")
    void changesTrainerPassword() {
        gymCrmFacade.changeTrainerPassword("Sarah.Miller", PASSWORD, "newPassword1");

        verify(trainerService).changePassword("Sarah.Miller", PASSWORD, "newPassword1");
    }

    @Test
    @DisplayName("updateTrainerProfile goes to the trainer service")
    void updatesTrainerProfile() {
        when(trainerService.updateProfile("Sarah.Miller", PASSWORD, "Sarah", "Miller", "STRETCHING"))
                .thenReturn(trainer);

        Assertions.assertSame(trainer, gymCrmFacade.updateTrainerProfile(
                "Sarah.Miller", PASSWORD, "Sarah", "Miller", "STRETCHING"));
    }

    @Test
    @DisplayName("activateTrainer goes to the trainer service")
    void activatesTrainer() {
        gymCrmFacade.activateTrainer("Sarah.Miller", PASSWORD);

        verify(trainerService).activate("Sarah.Miller", PASSWORD);
    }

    @Test
    @DisplayName("deactivateTrainer goes to the trainer service")
    void deactivatesTrainer() {
        gymCrmFacade.deactivateTrainer("Sarah.Miller", PASSWORD);

        verify(trainerService).deactivate("Sarah.Miller", PASSWORD);
    }

    @Test
    @DisplayName("addTraining goes to the training service")
    void addsTraining() {
        when(trainingService.addTraining("John.Smith", PASSWORD, "John.Smith", "Sarah.Miller",
                "Morning Yoga", "YOGA", TRAINING_DATE, 60L)).thenReturn(training);

        Assertions.assertSame(training, gymCrmFacade.addTraining("John.Smith", PASSWORD,
                "John.Smith", "Sarah.Miller", "Morning Yoga", "YOGA", TRAINING_DATE, 60L));
    }

    @Test
    @DisplayName("getTraineeTrainings goes to the training service")
    void getsTraineeTrainings() {
        List<Training> trainings = List.of(training);
        when(trainingService.getTraineeTrainings("John.Smith", PASSWORD,
                TRAINING_DATE, TRAINING_DATE, "Sarah", "YOGA")).thenReturn(trainings);

        Assertions.assertSame(trainings, gymCrmFacade.getTraineeTrainings("John.Smith", PASSWORD,
                TRAINING_DATE, TRAINING_DATE, "Sarah", "YOGA"));
    }

    @Test
    @DisplayName("getTrainerTrainings goes to the training service")
    void getsTrainerTrainings() {
        List<Training> trainings = List.of(training);
        when(trainingService.getTrainerTrainings("Sarah.Miller", PASSWORD,
                TRAINING_DATE, TRAINING_DATE, "John")).thenReturn(trainings);

        Assertions.assertSame(trainings, gymCrmFacade.getTrainerTrainings("Sarah.Miller", PASSWORD,
                TRAINING_DATE, TRAINING_DATE, "John"));
    }
}
