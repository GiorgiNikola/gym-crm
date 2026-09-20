package com.giorgi.gymcrm.service;

import com.giorgi.gymcrm.dao.TrainerDao;
import com.giorgi.gymcrm.model.Trainer;
import com.giorgi.gymcrm.model.TrainingType;
import com.giorgi.gymcrm.util.CredentialGenerator;
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

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainerServiceTest {

    @Mock
    private TrainerDao trainerDao;

    @Mock
    private UsernameResolver usernameResolver;

    @Mock
    private CredentialGenerator credentialGenerator;

    @InjectMocks
    private TrainerService trainerService;

    @Test
    @DisplayName("creates trainer with generated id, username and password")
    void createsTrainerProfile() {
        when(trainerDao.generateId()).thenReturn(1L);
        when(usernameResolver.generateUsername("Robert", "Taylor")).thenReturn("Robert.Taylor");
        when(credentialGenerator.generatePassword()).thenReturn("Hj2wE8rT4y");
        when(trainerDao.save(any(Trainer.class))).thenAnswer(call -> call.getArgument(0));

        trainerService.createTrainerProfile("Robert", "Taylor", true, TrainingType.FITNESS);

        ArgumentCaptor<Trainer> captor = ArgumentCaptor.forClass(Trainer.class);
        verify(trainerDao).save(captor.capture());
        Trainer saved = captor.getValue();

        Assertions.assertEquals(1L, saved.getUserID());
        Assertions.assertEquals("Robert.Taylor", saved.getUsername());
        Assertions.assertEquals("Hj2wE8rT4y", saved.getPassword());
        Assertions.assertEquals("Robert", saved.getFirstName());
        Assertions.assertEquals("Taylor", saved.getLastName());
        Assertions.assertTrue(saved.isActive());
        Assertions.assertEquals(TrainingType.FITNESS, saved.getSpecialization());
    }

    @Test
    @DisplayName("create returns what the dao saved")
    void createReturnsDaoResult() {
        when(trainerDao.generateId()).thenReturn(1L);
        when(usernameResolver.generateUsername("Robert", "Taylor")).thenReturn("Robert.Taylor");
        when(credentialGenerator.generatePassword()).thenReturn("Hj2wE8rT4y");
        Trainer persisted = Trainer.builder().userID(1L).username("Robert.Taylor").build();
        when(trainerDao.save(any(Trainer.class))).thenReturn(persisted);

        Trainer created = trainerService.createTrainerProfile("Robert", "Taylor", true, TrainingType.FITNESS);

        Assertions.assertSame(persisted, created);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("create rejects a missing first name")
    void rejectsBlankFirstName(String firstName) {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> trainerService.createTrainerProfile(firstName, "Taylor", true, TrainingType.FITNESS));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("create rejects a missing last name")
    void rejectsBlankLastName(String lastName) {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> trainerService.createTrainerProfile("Robert", lastName, true, TrainingType.FITNESS));
    }

    @Test
    @DisplayName("create rejects a null specialization")
    void rejectsNullSpecialization() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> trainerService.createTrainerProfile("Robert", "Taylor", true, null));
    }

    @Test
    @DisplayName("create does not touch the dao when validation fails")
    void skipsStorageWhenValidationFails() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> trainerService.createTrainerProfile("Robert", "Taylor", true, null));

        verifyNoInteractions(trainerDao, usernameResolver, credentialGenerator);
    }

    @Test
    @DisplayName("update passes the trainer to the dao")
    void updatesTrainerProfile() {
        Trainer robert = Trainer.builder().userID(1L).firstName("Robert").build();
        when(trainerDao.update(robert)).thenReturn(robert);

        Trainer updated = trainerService.updateTrainerProfile(robert);

        Assertions.assertSame(robert, updated);
    }

    @Test
    @DisplayName("update rejects a null trainer")
    void rejectsNullTrainerOnUpdate() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> trainerService.updateTrainerProfile(null));

        verifyNoInteractions(trainerDao);
    }

    @Test
    @DisplayName("select returns the trainer from the dao")
    void selectsTrainerProfile() {
        Trainer robert = Trainer.builder().userID(1L).firstName("Robert").build();
        when(trainerDao.findById(1L)).thenReturn(robert);

        Assertions.assertSame(robert, trainerService.selectTrainerProfile(1L));
    }
}
