package com.giorgi.gymcrm.service;

import com.giorgi.gymcrm.dao.TraineeDao;
import com.giorgi.gymcrm.model.Trainee;
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

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TraineeServiceTest {
    private static final LocalDate DATE_OF_BIRTH = LocalDate.of(1998, 5, 14);
    private static final String ADDRESS = "123 Main St New York";

    @Mock
    private TraineeDao traineeDao;

    @Mock
    private UsernameResolver usernameResolver;

    @Mock
    private CredentialGenerator credentialGenerator;

    @InjectMocks
    private TraineeService traineeService;

    @Test
    @DisplayName("creates trainee with generated id, username and password")
    void createsTraineeProfile() {
        when(traineeDao.generateId()).thenReturn(1L);
        when(usernameResolver.generateUsername("John", "Smith")).thenReturn("John.Smith");
        when(credentialGenerator.generatePassword()).thenReturn("aX7kQ2mN9p");
        when(traineeDao.save(any(Trainee.class))).thenAnswer(call -> call.getArgument(0));

        traineeService.createTraineeProfile("John", "Smith", true, DATE_OF_BIRTH, ADDRESS);

        ArgumentCaptor<Trainee> captor = ArgumentCaptor.forClass(Trainee.class);
        verify(traineeDao).save(captor.capture());
        Trainee saved = captor.getValue();

        Assertions.assertEquals(1L, saved.getUserID());
        Assertions.assertEquals("John.Smith", saved.getUsername());
        Assertions.assertEquals("aX7kQ2mN9p", saved.getPassword());
        Assertions.assertEquals("John", saved.getFirstName());
        Assertions.assertEquals("Smith", saved.getLastName());
        Assertions.assertTrue(saved.isActive());
        Assertions.assertEquals(DATE_OF_BIRTH, saved.getDateOfBirth());
        Assertions.assertEquals(ADDRESS, saved.getAddress());
    }

    @Test
    @DisplayName("creates trainee without date of birth and address")
    void createsTraineeWithoutOptionalFields() {
        when(traineeDao.generateId()).thenReturn(1L);
        when(usernameResolver.generateUsername("John", "Smith")).thenReturn("John.Smith");
        when(credentialGenerator.generatePassword()).thenReturn("aX7kQ2mN9p");
        when(traineeDao.save(any(Trainee.class))).thenAnswer(call -> call.getArgument(0));

        traineeService.createTraineeProfile("John", "Smith", false, null, null);

        ArgumentCaptor<Trainee> captor = ArgumentCaptor.forClass(Trainee.class);
        verify(traineeDao).save(captor.capture());
        Trainee saved = captor.getValue();

        Assertions.assertNull(saved.getDateOfBirth());
        Assertions.assertNull(saved.getAddress());
        Assertions.assertFalse(saved.isActive());
    }

    @Test
    @DisplayName("create returns what the dao saved")
    void createReturnsDaoResult() {
        when(traineeDao.generateId()).thenReturn(1L);
        when(usernameResolver.generateUsername("John", "Smith")).thenReturn("John.Smith");
        when(credentialGenerator.generatePassword()).thenReturn("aX7kQ2mN9p");
        Trainee persisted = Trainee.builder().userID(1L).username("John.Smith").build();
        when(traineeDao.save(any(Trainee.class))).thenReturn(persisted);

        Trainee created = traineeService.createTraineeProfile("John", "Smith", true, DATE_OF_BIRTH, ADDRESS);

        Assertions.assertSame(persisted, created);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("create rejects a missing first name")
    void rejectsBlankFirstName(String firstName) {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> traineeService.createTraineeProfile(firstName, "Smith", true, DATE_OF_BIRTH, ADDRESS));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("create rejects a missing last name")
    void rejectsBlankLastName(String lastName) {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> traineeService.createTraineeProfile("John", lastName, true, DATE_OF_BIRTH, ADDRESS));
    }

    @Test
    @DisplayName("create does not touch the dao when validation fails")
    void skipsStorageWhenValidationFails() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> traineeService.createTraineeProfile(null, "Smith", true, DATE_OF_BIRTH, ADDRESS));

        verifyNoInteractions(traineeDao, usernameResolver, credentialGenerator);
    }

    @Test
    @DisplayName("update passes the trainee to the dao")
    void updatesTraineeProfile() {
        Trainee john = Trainee.builder().userID(1L).firstName("John").build();
        when(traineeDao.update(john)).thenReturn(john);

        Trainee updated = traineeService.updateTraineeProfile(john);

        Assertions.assertSame(john, updated);
    }

    @Test
    @DisplayName("update rejects a null trainee")
    void rejectsNullTraineeOnUpdate() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> traineeService.updateTraineeProfile(null));

        verifyNoInteractions(traineeDao);
    }

    @Test
    @DisplayName("delete passes the id to the dao")
    void deletesTraineeProfile() {
        traineeService.deleteTraineeProfile(1L);

        verify(traineeDao).delete(1L);
    }

    @Test
    @DisplayName("select returns the trainee from the dao")
    void selectsTraineeProfile() {
        Trainee john = Trainee.builder().userID(1L).firstName("John").build();
        when(traineeDao.findById(1L)).thenReturn(john);

        Assertions.assertSame(john, traineeService.selectTraineeProfile(1L));
    }
}
