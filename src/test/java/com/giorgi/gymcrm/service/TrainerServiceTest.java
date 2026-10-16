package com.giorgi.gymcrm.service;

import com.giorgi.gymcrm.dao.TrainerDao;
import com.giorgi.gymcrm.dao.TrainingTypeDao;
import com.giorgi.gymcrm.exception.AuthenticationException;
import com.giorgi.gymcrm.exception.ProfileNotFoundException;
import com.giorgi.gymcrm.model.Trainer;
import com.giorgi.gymcrm.model.TrainingType;
import com.giorgi.gymcrm.model.User;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainerServiceTest {

    private static final String PASSWORD = "Hj2wE8rT4y";

    @Mock
    private TrainerDao trainerDao;

    @Mock
    private TrainingTypeDao trainingTypeDao;

    @Mock
    private AuthenticationService authenticationService;

    @Mock
    private UsernameResolver usernameResolver;

    @Mock
    private CredentialGenerator credentialGenerator;

    @InjectMocks
    private TrainerService trainerService;

    private TrainingType trainingType(String name) {
        TrainingType type = new TrainingType();
        ReflectionTestUtils.setField(type, "name", name);
        return type;
    }

    private Trainer sarah(boolean active) {
        User user = User.builder()
                .id(1L)
                .firstName("Sarah")
                .lastName("Miller")
                .username("Sarah.Miller")
                .password(PASSWORD)
                .isActive(active)
                .build();
        return Trainer.builder()
                .id(1L)
                .user(user)
                .specialization(new TrainingType())
                .build();
    }

    @Test
    @DisplayName("createProfile builds the trainer with a resolved username and generated password")
    void savesTrainerWithGeneratedCredentials() {
        TrainingType yoga = new TrainingType();
        when(trainingTypeDao.findByName("YOGA")).thenReturn(Optional.of(yoga));
        when(usernameResolver.generateUsername("Sarah", "Miller")).thenReturn("Sarah.Miller");
        when(credentialGenerator.generatePassword()).thenReturn(PASSWORD);

        trainerService.createProfile("Sarah", "Miller", "YOGA");

        ArgumentCaptor<Trainer> captor = ArgumentCaptor.forClass(Trainer.class);
        verify(trainerDao).save(captor.capture());
        Trainer saved = captor.getValue();

        Assertions.assertEquals("Sarah.Miller", saved.getUser().getUsername());
        Assertions.assertEquals(PASSWORD, saved.getUser().getPassword());
        Assertions.assertTrue(saved.getUser().isActive());
        Assertions.assertEquals("Sarah", saved.getUser().getFirstName());
        Assertions.assertEquals("Miller", saved.getUser().getLastName());
        Assertions.assertSame(yoga, saved.getSpecialization());
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
        when(trainingTypeDao.findByName("PILATES")).thenReturn(Optional.empty());
        when(trainingTypeDao.findAll()).thenReturn(List.of(trainingType("FITNESS"), trainingType("YOGA")));

        IllegalArgumentException thrown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> trainerService.createProfile("Sarah", "Miller", "PILATES"));

        Assertions.assertEquals("Unknown training type: PILATES. Valid values: FITNESS, YOGA",
                thrown.getMessage());
        verifyNoInteractions(trainerDao);
    }

    @Test
    @DisplayName("selectByUsername authenticates first and returns the full profile")
    void selectsTrainerAfterAuthentication() {
        Trainer sarah = sarah(true);
        when(trainerDao.findProfileByUsername("Sarah.Miller")).thenReturn(Optional.of(sarah));

        Trainer found = trainerService.selectByUsername("Sarah.Miller", PASSWORD);

        Assertions.assertSame(sarah, found);
        verify(authenticationService).authenticate("Sarah.Miller", PASSWORD);
    }

    @Test
    @DisplayName("selectByUsername throws when the trainer does not exist")
    void throwsWhenTrainerMissing() {
        when(trainerDao.findProfileByUsername("Nobody.Here")).thenReturn(Optional.empty());

        Assertions.assertThrows(ProfileNotFoundException.class,
                () -> trainerService.selectByUsername("Nobody.Here", PASSWORD));
    }

    @Test
    @DisplayName("updateProfile changes the name but leaves the specialization alone")
    void updatesNamesKeepsSpecialization() {
        Trainer sarah = sarah(true);
        TrainingType original = sarah.getSpecialization();
        when(trainerDao.findProfileByUsername("Sarah.Miller")).thenReturn(Optional.of(sarah));
        when(trainerDao.update(sarah)).thenReturn(sarah);

        Trainer updated = trainerService.updateProfile("Sarah.Miller", PASSWORD,
                "Sara", "Millerson", true);

        Assertions.assertEquals("Sara", updated.getUser().getFirstName());
        Assertions.assertEquals("Millerson", updated.getUser().getLastName());
        Assertions.assertSame(original, updated.getSpecialization());
        verifyNoInteractions(trainingTypeDao);
    }

    @Test
    @DisplayName("updateProfile deactivates the trainer when the body says so")
    void updatesActiveFlag() {
        Trainer sarah = sarah(true);
        when(trainerDao.findProfileByUsername("Sarah.Miller")).thenReturn(Optional.of(sarah));
        when(trainerDao.update(sarah)).thenReturn(sarah);

        Trainer updated = trainerService.updateProfile("Sarah.Miller", PASSWORD, "Sarah", "Miller", false);

        Assertions.assertFalse(updated.getUser().isActive());
    }

    @Test
    @DisplayName("updateProfile brings a deactivated trainer back")
    void reactivatesTrainer() {
        Trainer sarah = sarah(false);
        when(trainerDao.findProfileByUsername("Sarah.Miller")).thenReturn(Optional.of(sarah));
        when(trainerDao.update(sarah)).thenReturn(sarah);

        Trainer updated = trainerService.updateProfile("Sarah.Miller", PASSWORD, "Sarah", "Miller", true);

        Assertions.assertTrue(updated.getUser().isActive());
    }

    @Test
    @DisplayName("updateProfile accepts the state the trainer is already in")
    void keepsActiveFlagWithoutComplaining() {
        Trainer sarah = sarah(true);
        when(trainerDao.findProfileByUsername("Sarah.Miller")).thenReturn(Optional.of(sarah));
        when(trainerDao.update(sarah)).thenReturn(sarah);

        Trainer updated = Assertions.assertDoesNotThrow(
                () -> trainerService.updateProfile("Sarah.Miller", PASSWORD, "Sarah", "Miller", true));

        Assertions.assertTrue(updated.getUser().isActive());
    }

    @Test
    @DisplayName("a failed authentication stops the operation before the dao is touched")
    void authenticationFailureStopsTheOperation() {
        doThrow(new AuthenticationException("Invalid username or password"))
                .when(authenticationService).authenticate("Sarah.Miller", "wrongPassword");

        Assertions.assertThrows(AuthenticationException.class,
                () -> trainerService.selectByUsername("Sarah.Miller", "wrongPassword"));

        verifyNoInteractions(trainerDao);
    }
}