package com.giorgi.gymcrm.facade;

import com.giorgi.gymcrm.model.Trainee;
import com.giorgi.gymcrm.model.Trainer;
import com.giorgi.gymcrm.model.Training;
import com.giorgi.gymcrm.model.TrainingType;
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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GymCrmFacadeTest {
    private static final LocalDate DATE_OF_BIRTH = LocalDate.of(1998, 5, 14);
    private static final LocalDate TRAINING_DATE = LocalDate.of(2026, 8, 1);
    private static final String ADDRESS = "123 Main St New York";

    @Mock
    private TraineeService traineeService;

    @Mock
    private TrainerService trainerService;

    @Mock
    private TrainingService trainingService;

    @InjectMocks
    private GymCrmFacade gymCrmFacade;

    @Test
    @DisplayName("passes trainee creation to the trainee service")
    void createsTraineeProfile() {
        Trainee john = Trainee.builder().userID(1L).build();
        when(traineeService.createTraineeProfile("John", "Smith", true, DATE_OF_BIRTH, ADDRESS)).thenReturn(john);

        Trainee created = gymCrmFacade.createTraineeProfile("John", "Smith", true, DATE_OF_BIRTH, ADDRESS);

        Assertions.assertSame(john, created);
    }

    @Test
    @DisplayName("passes trainee update to the trainee service")
    void updatesTraineeProfile() {
        Trainee john = Trainee.builder().userID(1L).build();
        when(traineeService.updateTraineeProfile(john)).thenReturn(john);

        Assertions.assertSame(john, gymCrmFacade.updateTraineeProfile(john));
    }

    @Test
    @DisplayName("passes trainee deletion to the trainee service")
    void deletesTraineeProfile() {
        gymCrmFacade.deleteTraineeProfile(1L);

        verify(traineeService).deleteTraineeProfile(1L);
    }

    @Test
    @DisplayName("passes trainee lookup to the trainee service")
    void selectsTraineeProfile() {
        Trainee john = Trainee.builder().userID(1L).build();
        when(traineeService.selectTraineeProfile(1L)).thenReturn(john);

        Assertions.assertSame(john, gymCrmFacade.selectTraineeProfile(1L));
    }

    @Test
    @DisplayName("passes trainer creation to the trainer service")
    void createsTrainerProfile() {
        Trainer robert = Trainer.builder().userID(2L).build();
        when(trainerService.createTrainerProfile("Robert", "Taylor", true, TrainingType.FITNESS)).thenReturn(robert);

        Trainer created = gymCrmFacade.createTrainerProfile("Robert", "Taylor", true, TrainingType.FITNESS);

        Assertions.assertSame(robert, created);
    }

    @Test
    @DisplayName("passes trainer update to the trainer service")
    void updatesTrainerProfile() {
        Trainer robert = Trainer.builder().userID(2L).build();
        when(trainerService.updateTrainerProfile(robert)).thenReturn(robert);

        Assertions.assertSame(robert, gymCrmFacade.updateTrainerProfile(robert));
    }

    @Test
    @DisplayName("passes trainer lookup to the trainer service")
    void selectsTrainerProfile() {
        Trainer robert = Trainer.builder().userID(2L).build();
        when(trainerService.selectTrainerProfile(2L)).thenReturn(robert);

        Assertions.assertSame(robert, gymCrmFacade.selectTrainerProfile(2L));
    }

    @Test
    @DisplayName("passes training creation to the training service")
    void createsTrainingProfile() {
        Training yoga = Training.builder().ID(3L).build();
        when(trainingService.createTrainingProfile(1L, 2L, "Beginner Yoga Class", TrainingType.YOGA, TRAINING_DATE, 45L))
                .thenReturn(yoga);

        Training created = gymCrmFacade.createTrainingProfile(
                1L, 2L, "Beginner Yoga Class", TrainingType.YOGA, TRAINING_DATE, 45L);

        Assertions.assertSame(yoga, created);
    }

    @Test
    @DisplayName("passes training lookup to the training service")
    void selectsTrainingProfile() {
        Training yoga = Training.builder().ID(3L).build();
        when(trainingService.selectTrainingProfile(3L)).thenReturn(yoga);

        Assertions.assertSame(yoga, gymCrmFacade.selectTrainingProfile(3L));
    }
}
