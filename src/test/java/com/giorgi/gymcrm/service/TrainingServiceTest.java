package com.giorgi.gymcrm.service;

import com.giorgi.gymcrm.dao.TraineeDao;
import com.giorgi.gymcrm.dao.TrainerDao;
import com.giorgi.gymcrm.dao.TrainingDao;
import com.giorgi.gymcrm.model.Trainer;
import com.giorgi.gymcrm.model.Training;
import com.giorgi.gymcrm.model.TrainingType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainingServiceTest {
    private static final long TRAINEE_ID = 1L;
    private static final long TRAINER_ID = 2L;
    private static final LocalDate TRAINING_DATE = LocalDate.of(2026, 8, 1);

    @Mock
    private TrainingDao trainingDao;

    @Mock
    private TraineeDao traineeDao;

    @Mock
    private TrainerDao trainerDao;

    @InjectMocks
    private TrainingService trainingService;

    private Trainer yogaTrainer() {
        return Trainer.builder().userID(TRAINER_ID).specialization(TrainingType.YOGA).build();
    }

    @Test
    @DisplayName("creates training with a generated id")
    void createsTrainingProfile() {
        when(traineeDao.existsByID(TRAINEE_ID)).thenReturn(true);
        when(trainerDao.findById(TRAINER_ID)).thenReturn(yogaTrainer());
        when(trainingDao.generateId()).thenReturn(7L);
        when(trainingDao.save(any(Training.class))).thenAnswer(call -> call.getArgument(0));

        trainingService.createTrainingProfile(
                TRAINEE_ID, TRAINER_ID, "Beginner Yoga Class", TrainingType.YOGA, TRAINING_DATE, 45L);

        ArgumentCaptor<Training> captor = ArgumentCaptor.forClass(Training.class);
        verify(trainingDao).save(captor.capture());
        Training saved = captor.getValue();

        Assertions.assertEquals(7L, saved.getID());
        Assertions.assertEquals(TRAINEE_ID, saved.getTraineeID());
        Assertions.assertEquals(TRAINER_ID, saved.getTrainerID());
        Assertions.assertEquals("Beginner Yoga Class", saved.getName());
        Assertions.assertEquals(TrainingType.YOGA, saved.getType());
        Assertions.assertEquals(TRAINING_DATE, saved.getDate());
        Assertions.assertEquals(45L, saved.getDuration());
    }

    @Test
    @DisplayName("create returns what the dao saved")
    void createReturnsDaoResult() {
        when(traineeDao.existsByID(TRAINEE_ID)).thenReturn(true);
        when(trainerDao.findById(TRAINER_ID)).thenReturn(yogaTrainer());
        when(trainingDao.generateId()).thenReturn(7L);
        Training persisted = Training.builder().ID(7L).name("Beginner Yoga Class").build();
        when(trainingDao.save(any(Training.class))).thenReturn(persisted);

        Training created = trainingService.createTrainingProfile(
                TRAINEE_ID, TRAINER_ID, "Beginner Yoga Class", TrainingType.YOGA, TRAINING_DATE, 45L);

        Assertions.assertSame(persisted, created);
    }

    @Test
    @DisplayName("create rejects an unknown trainee")
    void rejectsUnknownTrainee() {
        when(traineeDao.existsByID(TRAINEE_ID)).thenReturn(false);

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> trainingService.createTrainingProfile(
                        TRAINEE_ID, TRAINER_ID, "Beginner Yoga Class", TrainingType.YOGA, TRAINING_DATE, 45L));
        verifyNoInteractions(trainingDao);
    }

    @Test
    @DisplayName("create rejects an unknown trainer")
    void rejectsUnknownTrainer() {
        when(traineeDao.existsByID(TRAINEE_ID)).thenReturn(true);
        when(trainerDao.findById(TRAINER_ID)).thenReturn(null);

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> trainingService.createTrainingProfile(
                        TRAINEE_ID, TRAINER_ID, "Beginner Yoga Class", TrainingType.YOGA, TRAINING_DATE, 45L));
        verifyNoInteractions(trainingDao);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("create rejects a missing training name")
    void rejectsBlankName(String name) {
        when(traineeDao.existsByID(TRAINEE_ID)).thenReturn(true);
        when(trainerDao.findById(TRAINER_ID)).thenReturn(yogaTrainer());

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> trainingService.createTrainingProfile(
                        TRAINEE_ID, TRAINER_ID, name, TrainingType.YOGA, TRAINING_DATE, 45L));
    }

    @Test
    @DisplayName("create rejects a null training type")
    void rejectsNullType() {
        when(traineeDao.existsByID(TRAINEE_ID)).thenReturn(true);
        when(trainerDao.findById(TRAINER_ID)).thenReturn(yogaTrainer());

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> trainingService.createTrainingProfile(
                        TRAINEE_ID, TRAINER_ID, "Beginner Yoga Class", null, TRAINING_DATE, 45L));
    }

    @Test
    @DisplayName("create rejects a null training date")
    void rejectsNullDate() {
        when(traineeDao.existsByID(TRAINEE_ID)).thenReturn(true);
        when(trainerDao.findById(TRAINER_ID)).thenReturn(yogaTrainer());

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> trainingService.createTrainingProfile(
                        TRAINEE_ID, TRAINER_ID, "Beginner Yoga Class", TrainingType.YOGA, null, 45L));
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, -45L})
    @DisplayName("create rejects a duration that is not positive")
    void rejectsNonPositiveDuration(long duration) {
        when(traineeDao.existsByID(TRAINEE_ID)).thenReturn(true);
        when(trainerDao.findById(TRAINER_ID)).thenReturn(yogaTrainer());

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> trainingService.createTrainingProfile(
                        TRAINEE_ID, TRAINER_ID, "Beginner Yoga Class", TrainingType.YOGA, TRAINING_DATE, duration));
    }

    @Test
    @DisplayName("create rejects a type the trainer does not teach")
    void rejectsTypeOutsideTrainerSpecialization() {
        when(traineeDao.existsByID(TRAINEE_ID)).thenReturn(true);
        when(trainerDao.findById(TRAINER_ID)).thenReturn(yogaTrainer());

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> trainingService.createTrainingProfile(
                        TRAINEE_ID, TRAINER_ID, "Zumba Cardio Blast", TrainingType.ZUMBA, TRAINING_DATE, 50L));
        verifyNoInteractions(trainingDao);
    }

    @Test
    @DisplayName("select returns the training from the dao")
    void selectsTrainingProfile() {
        Training yoga = Training.builder().ID(7L).name("Beginner Yoga Class").build();
        when(trainingDao.findById(7L)).thenReturn(yoga);

        Assertions.assertSame(yoga, trainingService.selectTrainingProfile(7L));
    }
}
