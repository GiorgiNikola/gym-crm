package com.giorgi.gymcrm.service;

import com.giorgi.gymcrm.dao.TraineeDao;
import com.giorgi.gymcrm.dao.TrainerDao;
import com.giorgi.gymcrm.exception.AuthenticationException;
import com.giorgi.gymcrm.exception.ProfileNotFoundException;
import com.giorgi.gymcrm.model.Trainee;
import com.giorgi.gymcrm.model.Trainer;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TraineeServiceTest {

    private static final LocalDate DATE_OF_BIRTH = LocalDate.of(1998, 5, 14);
    private static final String ADDRESS = "123 Main St New York";
    private static final String PASSWORD = "aX7kQ2mN9p";

    @Mock
    private TraineeDao traineeDao;

    @Mock
    private TrainerDao trainerDao;

    @Mock
    private AuthenticationService authenticationService;

    @Mock
    private UsernameResolver usernameResolver;

    @Mock
    private CredentialGenerator credentialGenerator;

    @InjectMocks
    private TraineeService traineeService;

    private Trainee john(boolean active) {
        User user = User.builder()
                .id(1L)
                .firstName("John")
                .lastName("Smith")
                .username("John.Smith")
                .password(PASSWORD)
                .isActive(active)
                .build();
        return Trainee.builder()
                .id(1L)
                .user(user)
                .dateOfBirth(DATE_OF_BIRTH)
                .address(ADDRESS)
                .build();
    }

    private Trainer trainer(String firstName, String lastName) {
        return Trainer.builder()
                .user(User.builder().firstName(firstName).lastName(lastName)
                        .username(firstName + "." + lastName).password("Hj2wE8rT4y").isActive(true).build())
                .build();
    }

    @Test
    @DisplayName("createProfile builds the trainee with a resolved username and generated password")
    void savesTraineeWithGeneratedCredentials() {
        when(usernameResolver.generateUsername("John", "Smith")).thenReturn("John.Smith");
        when(credentialGenerator.generatePassword()).thenReturn(PASSWORD);

        traineeService.createProfile("John", "Smith", DATE_OF_BIRTH, ADDRESS);

        ArgumentCaptor<Trainee> captor = ArgumentCaptor.forClass(Trainee.class);
        verify(traineeDao).save(captor.capture());
        Trainee saved = captor.getValue();

        Assertions.assertEquals("John.Smith", saved.getUser().getUsername());
        Assertions.assertEquals(PASSWORD, saved.getUser().getPassword());
        Assertions.assertTrue(saved.getUser().isActive());
        Assertions.assertEquals("John", saved.getUser().getFirstName());
        Assertions.assertEquals("Smith", saved.getUser().getLastName());
        Assertions.assertEquals(DATE_OF_BIRTH, saved.getDateOfBirth());
        Assertions.assertEquals(ADDRESS, saved.getAddress());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("createProfile rejects a missing first name")
    void rejectsMissingFirstName(String firstName) {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> traineeService.createProfile(firstName, "Smith", DATE_OF_BIRTH, ADDRESS));

        verifyNoInteractions(traineeDao);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("createProfile rejects a missing last name")
    void rejectsMissingLastName(String lastName) {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> traineeService.createProfile("John", lastName, DATE_OF_BIRTH, ADDRESS));

        verifyNoInteractions(traineeDao);
    }

    @Test
    @DisplayName("selectByUsername authenticates first and returns the trainee")
    void selectsTraineeAfterAuthentication() {
        Trainee john = john(true);
        when(traineeDao.findByUsername("John.Smith")).thenReturn(Optional.of(john));

        Trainee found = traineeService.selectByUsername("John.Smith", PASSWORD);

        Assertions.assertSame(john, found);
        verify(authenticationService).authenticate("John.Smith", PASSWORD);
    }

    @Test
    @DisplayName("selectByUsername throws when the trainee does not exist")
    void throwsWhenTraineeMissing() {
        when(traineeDao.findByUsername("Nobody.Here")).thenReturn(Optional.empty());

        Assertions.assertThrows(ProfileNotFoundException.class,
                () -> traineeService.selectByUsername("Nobody.Here", PASSWORD));
    }

    @Test
    @DisplayName("changePassword stores the new password")
    void changesPassword() {
        Trainee john = john(true);
        when(traineeDao.findByUsername("John.Smith")).thenReturn(Optional.of(john));

        traineeService.changePassword("John.Smith", PASSWORD, "newPassword1");

        Assertions.assertEquals("newPassword1", john.getUser().getPassword());
        verify(traineeDao).update(john);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("changePassword rejects a blank new password")
    void rejectsBlankNewPassword(String newPassword) {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> traineeService.changePassword("John.Smith", PASSWORD, newPassword));

        verify(traineeDao, never()).update(any(Trainee.class));
    }

    @Test
    @DisplayName("updateProfile overwrites the name, date of birth and address")
    void updatesProfileFields() {
        Trainee john = john(true);
        when(traineeDao.findByUsername("John.Smith")).thenReturn(Optional.of(john));
        when(traineeDao.update(john)).thenReturn(john);

        Trainee updated = traineeService.updateProfile("John.Smith", PASSWORD,
                "Johnny", "Smithson", LocalDate.of(2000, 1, 1), "456 Park Avenue Boston");

        Assertions.assertEquals("Johnny", updated.getUser().getFirstName());
        Assertions.assertEquals("Smithson", updated.getUser().getLastName());
        Assertions.assertEquals(LocalDate.of(2000, 1, 1), updated.getDateOfBirth());
        Assertions.assertEquals("456 Park Avenue Boston", updated.getAddress());
    }

    @Test
    @DisplayName("activate throws when the trainee is already active")
    void activateThrowsWhenAlreadyActive() {
        when(traineeDao.findByUsername("John.Smith")).thenReturn(Optional.of(john(true)));

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> traineeService.activate("John.Smith", PASSWORD));

        verify(traineeDao, never()).update(any(Trainee.class));
    }

    @Test
    @DisplayName("deactivate clears the active flag")
    void deactivateClearsActiveFlag() {
        Trainee john = john(true);
        when(traineeDao.findByUsername("John.Smith")).thenReturn(Optional.of(john));

        traineeService.deactivate("John.Smith", PASSWORD);

        Assertions.assertFalse(john.getUser().isActive());
        verify(traineeDao).update(john);
    }

    @Test
    @DisplayName("deleteByUsername passes the loaded trainee to the dao")
    void deletesLoadedTrainee() {
        Trainee john = john(true);
        when(traineeDao.findByUsername("John.Smith")).thenReturn(Optional.of(john));

        traineeService.deleteByUsername("John.Smith", PASSWORD);

        verify(traineeDao).delete(john);
    }

    @Test
    @DisplayName("updateTrainers replaces the whole trainer set")
    void updateTrainersReplacesTheSet() {
        Trainee john = john(true);
        john.getTrainers().add(trainer("Laura", "Davis"));
        Trainer sarah = trainer("Sarah", "Miller");
        Trainer robert = trainer("Robert", "Taylor");
        when(traineeDao.findByUsername("John.Smith")).thenReturn(Optional.of(john));
        when(trainerDao.findByUsername("Sarah.Miller")).thenReturn(Optional.of(sarah));
        when(trainerDao.findByUsername("Robert.Taylor")).thenReturn(Optional.of(robert));
        when(traineeDao.update(john)).thenReturn(john);

        Trainee updated = traineeService.updateTrainers("John.Smith", PASSWORD,
                Set.of("Sarah.Miller", "Robert.Taylor"));

        Assertions.assertEquals(2, updated.getTrainers().size());
        Assertions.assertTrue(updated.getTrainers().containsAll(Set.of(sarah, robert)));
    }

    @Test
    @DisplayName("updateTrainers throws when one trainer username is unknown")
    void updateTrainersThrowsForUnknownTrainer() {
        when(traineeDao.findByUsername("John.Smith")).thenReturn(Optional.of(john(true)));
        when(trainerDao.findByUsername("Nobody.Here")).thenReturn(Optional.empty());

        Assertions.assertThrows(ProfileNotFoundException.class,
                () -> traineeService.updateTrainers("John.Smith", PASSWORD, Set.of("Nobody.Here")));

        verify(traineeDao, never()).update(any(Trainee.class));
    }

    @Test
    @DisplayName("a failed authentication stops the operation before the dao is touched")
    void authenticationFailureStopsTheOperation() {
        doThrow(new AuthenticationException("Invalid username or password"))
                .when(authenticationService).authenticate("John.Smith", "wrongPassword");

        Assertions.assertThrows(AuthenticationException.class,
                () -> traineeService.selectByUsername("John.Smith", "wrongPassword"));

        verifyNoInteractions(traineeDao);
    }

    @Test
    @DisplayName("activate sets the flag on an inactive trainee")
    void activateSetsActiveFlag() {
        Trainee john = john(false);
        when(traineeDao.findByUsername("John.Smith")).thenReturn(Optional.of(john));

        traineeService.activate("John.Smith", PASSWORD);

        Assertions.assertTrue(john.getUser().isActive());
        verify(traineeDao).update(john);
    }

    @Test
    @DisplayName("updateTrainers rejects a null set")
    void updateTrainersRejectsNullSet() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> traineeService.updateTrainers("John.Smith", PASSWORD, null));

        verify(traineeDao, never()).update(any(Trainee.class));
    }

    @Test
    @DisplayName("findUnassignedTrainers returns what the trainer dao gives back")
    void findsUnassignedTrainers() {
        List<Trainer> unassigned = List.of(trainer("Sarah", "Miller"));
        when(traineeDao.findByUsername("John.Smith")).thenReturn(Optional.of(john(true)));
        when(trainerDao.findNotAssignedToTrainee("John.Smith")).thenReturn(unassigned);

        Assertions.assertSame(unassigned, traineeService.findUnassignedTrainers("John.Smith", PASSWORD));
    }
}
