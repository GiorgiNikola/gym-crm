package com.giorgi.gymcrm.service;

import com.giorgi.gymcrm.dao.TrainerDao;
import com.giorgi.gymcrm.exception.ProfileNotFoundException;
import com.giorgi.gymcrm.metrics.GymMetrics;
import com.giorgi.gymcrm.model.Trainer;
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

import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainerServiceTest {

    private static final String RAW_PASSWORD = "Hj2wE8rT4y";
    private static final String HASH = "$2a$10$abcdefghijklmnopqrstuv";

    @Mock
    private TrainerDao trainerDao;

    @Mock
    private TrainingTypeService trainingTypeService;

    @Mock
    private UserFactory userFactory;

    @Mock
    private GymMetrics gymMetrics;

    @InjectMocks
    private TrainerService trainerService;

    private Trainer sarah(boolean active) {
        User user = User.builder()
                .id(1L)
                .firstName("Sarah")
                .lastName("Miller")
                .username("Sarah.Miller")
                .password(HASH)
                .isActive(active)
                .build();
        return Trainer.builder()
                .id(1L)
                .user(user)
                .specialization(new TrainingType())
                .build();
    }

    private NewUser newSarah() {
        return new NewUser(sarah(true).getUser(), RAW_PASSWORD);
    }

    @Test
    @DisplayName("createProfile attaches the user the factory built and the resolved specialization")
    void savesTrainerWithGeneratedCredentials() {
        TrainingType yoga = new TrainingType();
        when(trainingTypeService.requireByName("YOGA")).thenReturn(yoga);
        when(userFactory.create("Sarah", "Miller")).thenReturn(newSarah());

        NewUser created = trainerService.createProfile("Sarah", "Miller", "YOGA");

        ArgumentCaptor<Trainer> captor = ArgumentCaptor.forClass(Trainer.class);
        verify(trainerDao).save(captor.capture());
        Trainer saved = captor.getValue();

        Assertions.assertEquals("Sarah.Miller", saved.getUser().getUsername());
        Assertions.assertEquals(HASH, saved.getUser().getPassword());
        Assertions.assertTrue(saved.getUser().isActive());
        Assertions.assertSame(yoga, saved.getSpecialization());
        Assertions.assertEquals(RAW_PASSWORD, created.rawPassword());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("createProfile rejects a missing first name")
    void rejectsMissingFirstName(String firstName) {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> trainerService.createProfile(firstName, "Miller", "YOGA"));

        verifyNoInteractions(trainerDao);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("createProfile rejects a missing last name")
    void rejectsMissingLastName(String lastName) {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> trainerService.createProfile("Sarah", lastName, "YOGA"));

        verifyNoInteractions(trainerDao);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("createProfile rejects a missing specialization")
    void rejectsMissingSpecialization(String specialization) {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> trainerService.createProfile("Sarah", "Miller", specialization));

        verifyNoInteractions(trainerDao);
    }

    @Test
    @DisplayName("createProfile throws for an unknown specialization and lists the valid names")
    void throwsForUnknownSpecialization() {
        when(trainingTypeService.requireByName("PILATES")).thenThrow(new IllegalArgumentException(
                "Unknown training type: PILATES. Valid values: FITNESS, YOGA"));

        IllegalArgumentException thrown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> trainerService.createProfile("Sarah", "Miller", "PILATES"));

        Assertions.assertEquals("Unknown training type: PILATES. Valid values: FITNESS, YOGA",
                thrown.getMessage());
        verifyNoInteractions(trainerDao);
    }

    @Test
    @DisplayName("selectByUsername returns the full profile")
    void selectsTrainerProfile() {
        Trainer sarah = sarah(true);
        when(trainerDao.findProfileByUsername("Sarah.Miller")).thenReturn(Optional.of(sarah));

        Assertions.assertSame(sarah, trainerService.selectByUsername("Sarah.Miller"));
    }

    @Test
    @DisplayName("selectByUsername throws when the trainer does not exist")
    void throwsWhenTrainerMissing() {
        when(trainerDao.findProfileByUsername("Nobody.Here")).thenReturn(Optional.empty());

        Assertions.assertThrows(ProfileNotFoundException.class,
                () -> trainerService.selectByUsername("Nobody.Here"));
    }

    @Test
    @DisplayName("updateProfile changes the name but leaves the specialization alone")
    void updatesNamesKeepsSpecialization() {
        Trainer sarah = sarah(true);
        TrainingType original = sarah.getSpecialization();
        when(trainerDao.findProfileByUsername("Sarah.Miller")).thenReturn(Optional.of(sarah));
        when(trainerDao.update(sarah)).thenReturn(sarah);

        Trainer updated = trainerService.updateProfile("Sarah.Miller", "Sara", "Millerson", true);

        Assertions.assertEquals("Sara", updated.getUser().getFirstName());
        Assertions.assertEquals("Millerson", updated.getUser().getLastName());
        Assertions.assertSame(original, updated.getSpecialization());
        verifyNoInteractions(trainingTypeService);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("updateProfile rejects a missing last name")
    void rejectsUpdateWithoutLastName(String lastName) {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> trainerService.updateProfile("Sarah.Miller", "Sarah", lastName, true));

        verifyNoInteractions(trainerDao);
    }

    @Test
    @DisplayName("updateProfile deactivates the trainer when the body says so")
    void updatesActiveFlag() {
        Trainer sarah = sarah(true);
        when(trainerDao.findProfileByUsername("Sarah.Miller")).thenReturn(Optional.of(sarah));
        when(trainerDao.update(sarah)).thenReturn(sarah);

        Trainer updated = trainerService.updateProfile("Sarah.Miller", "Sarah", "Miller", false);

        Assertions.assertFalse(updated.getUser().isActive());
    }

    @Test
    @DisplayName("updateProfile brings a deactivated trainer back")
    void reactivatesTrainer() {
        Trainer sarah = sarah(false);
        when(trainerDao.findProfileByUsername("Sarah.Miller")).thenReturn(Optional.of(sarah));
        when(trainerDao.update(sarah)).thenReturn(sarah);

        Trainer updated = trainerService.updateProfile("Sarah.Miller", "Sarah", "Miller", true);

        Assertions.assertTrue(updated.getUser().isActive());
    }

    @Test
    @DisplayName("updateProfile accepts the state the trainer is already in")
    void keepsActiveFlagWithoutComplaining() {
        Trainer sarah = sarah(true);
        when(trainerDao.findProfileByUsername("Sarah.Miller")).thenReturn(Optional.of(sarah));
        when(trainerDao.update(sarah)).thenReturn(sarah);

        Trainer updated = Assertions.assertDoesNotThrow(
                () -> trainerService.updateProfile("Sarah.Miller", "Sarah", "Miller", true));

        Assertions.assertTrue(updated.getUser().isActive());
    }

    @Test
    @DisplayName("a saved trainer is counted as a registration")
    void countsTrainerRegistration() {
        when(trainingTypeService.requireByName("YOGA")).thenReturn(new TrainingType());
        when(userFactory.create("Sarah", "Miller")).thenReturn(newSarah());

        trainerService.createProfile("Sarah", "Miller", "YOGA");

        verify(gymMetrics).trainerRegistered();
    }
}
