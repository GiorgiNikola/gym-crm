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
import com.giorgi.gymcrm.model.User;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainingServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 1);
    private static final String PASSWORD = "aX7kQ2mN9p";

    @Mock
    private TrainingDao trainingDao;

    @Mock
    private TraineeDao traineeDao;

    @Mock
    private TrainerDao trainerDao;

    @Mock
    private TrainingTypeDao trainingTypeDao;

    @Mock
    private AuthenticationService authenticationService;

    @InjectMocks
    private TrainingService trainingService;

    // TrainingType is immutable with no setters, so the id goes in by reflection
    private TrainingType trainingType(long id) {
        TrainingType type = new TrainingType();
        ReflectionTestUtils.setField(type, "id", id);
        return type;
    }

    private Trainee john() {
        return Trainee.builder()
                .id(1L)
                .user(User.builder().firstName("John").lastName("Smith")
                        .username("John.Smith").password(PASSWORD).isActive(true).build())
                .build();
    }

    private Trainer sarah(TrainingType specialization) {
        return Trainer.builder()
                .id(1L)
                .user(User.builder().firstName("Sarah").lastName("Miller")
                        .username("Sarah.Miller").password("Hj2wE8rT4y").isActive(true).build())
                .specialization(specialization)
                .build();
    }

    @Test
    @DisplayName("addTraining saves the training with the resolved trainee, trainer and type")
    void addsTrainingWithResolvedEntities() {
        TrainingType yoga = trainingType(2L);
        Trainee john = john();
        Trainer sarah = sarah(yoga);
        when(traineeDao.findByUsername("John.Smith")).thenReturn(Optional.of(john));
        when(trainerDao.findByUsername("Sarah.Miller")).thenReturn(Optional.of(sarah));
        when(trainingTypeDao.findByName("YOGA")).thenReturn(Optional.of(yoga));

        trainingService.addTraining("John.Smith", PASSWORD, "John.Smith", "Sarah.Miller",
                "Morning Yoga", "YOGA", DATE, 60L);

        ArgumentCaptor<Training> captor = ArgumentCaptor.forClass(Training.class);
        verify(trainingDao).save(captor.capture());
        Training saved = captor.getValue();

        Assertions.assertSame(john, saved.getTrainee());
        Assertions.assertSame(sarah, saved.getTrainer());
        Assertions.assertSame(yoga, saved.getType());
        Assertions.assertEquals("Morning Yoga", saved.getName());
        Assertions.assertEquals(DATE, saved.getDate());
        Assertions.assertEquals(60L, saved.getDuration());
    }

    @Test
    @DisplayName("addTraining throws for an unknown trainee")
    void throwsWhenTraineeMissing() {
        when(traineeDao.findByUsername("Nobody.Here")).thenReturn(Optional.empty());

        Assertions.assertThrows(ProfileNotFoundException.class,
                () -> trainingService.addTraining("John.Smith", PASSWORD, "Nobody.Here", "Sarah.Miller",
                        "Morning Yoga", "YOGA", DATE, 60L));

        verifyNoInteractions(trainingDao);
    }

    @Test
    @DisplayName("addTraining throws for an unknown trainer")
    void throwsWhenTrainerMissing() {
        when(traineeDao.findByUsername("John.Smith")).thenReturn(Optional.of(john()));
        when(trainerDao.findByUsername("Nobody.Here")).thenReturn(Optional.empty());

        Assertions.assertThrows(ProfileNotFoundException.class,
                () -> trainingService.addTraining("John.Smith", PASSWORD, "John.Smith", "Nobody.Here",
                        "Morning Yoga", "YOGA", DATE, 60L));

        verifyNoInteractions(trainingDao);
    }

    @Test
    @DisplayName("addTraining throws for an unknown training type")
    void throwsForUnknownTrainingType() {
        when(traineeDao.findByUsername("John.Smith")).thenReturn(Optional.of(john()));
        when(trainerDao.findByUsername("Sarah.Miller")).thenReturn(Optional.of(sarah(trainingType(2L))));
        when(trainingTypeDao.findByName("PILATES")).thenReturn(Optional.empty());

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> trainingService.addTraining("John.Smith", PASSWORD, "John.Smith", "Sarah.Miller",
                        "Morning Yoga", "PILATES", DATE, 60L));

        verifyNoInteractions(trainingDao);
    }

    @Test
    @DisplayName("addTraining rejects a null date")
    void rejectsNullDate() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> trainingService.addTraining("John.Smith", PASSWORD, "John.Smith", "Sarah.Miller",
                        "Morning Yoga", "YOGA", null, 60L));

        verifyNoInteractions(trainingDao);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -45L})
    @DisplayName("addTraining rejects a duration that is not positive")
    void rejectsInvalidDuration(Long duration) {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> trainingService.addTraining("John.Smith", PASSWORD, "John.Smith", "Sarah.Miller",
                        "Morning Yoga", "YOGA", DATE, duration));

        verifyNoInteractions(trainingDao);
    }

    @Test
    @DisplayName("addTraining throws when the type does not match the trainer's specialization")
    void throwsWhenTypeDoesNotMatchSpecialization() {
        when(traineeDao.findByUsername("John.Smith")).thenReturn(Optional.of(john()));
        when(trainerDao.findByUsername("Sarah.Miller")).thenReturn(Optional.of(sarah(trainingType(2L))));
        when(trainingTypeDao.findByName("ZUMBA")).thenReturn(Optional.of(trainingType(3L)));

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> trainingService.addTraining("John.Smith", PASSWORD, "John.Smith", "Sarah.Miller",
                        "Zumba Class", "ZUMBA", DATE, 50L));

        verifyNoInteractions(trainingDao);
    }

    @Test
    @DisplayName("getTraineeTrainings passes the authenticated username and the filters to the dao")
    void passesTraineeFiltersToDao() {
        List<Training> expected = List.of(Training.builder().id(1L).name("Morning Yoga").build());
        when(trainingDao.findTraineeTrainings("John.Smith", DATE, DATE, "Sarah", "YOGA")).thenReturn(expected);

        List<Training> trainings = trainingService.getTraineeTrainings(
                "John.Smith", PASSWORD, DATE, DATE, "Sarah", "YOGA");

        Assertions.assertSame(expected, trainings);
        verify(authenticationService).authenticate("John.Smith", PASSWORD);
    }

    @Test
    @DisplayName("getTrainerTrainings passes the authenticated username and the filters to the dao")
    void passesTrainerFiltersToDao() {
        List<Training> expected = List.of(Training.builder().id(1L).name("Morning Yoga").build());
        when(trainingDao.findTrainerTrainings("Sarah.Miller", DATE, DATE, "John")).thenReturn(expected);

        List<Training> trainings = trainingService.getTrainerTrainings(
                "Sarah.Miller", PASSWORD, DATE, DATE, "John");

        Assertions.assertSame(expected, trainings);
        verify(authenticationService).authenticate("Sarah.Miller", PASSWORD);
    }
}
