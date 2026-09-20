package com.giorgi.gymcrm.service;

import com.giorgi.gymcrm.dao.TraineeDao;
import com.giorgi.gymcrm.dao.TrainerDao;
import com.giorgi.gymcrm.util.CredentialGenerator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsernameResolverTest {

    @Mock
    private TraineeDao traineeDao;

    @Mock
    private TrainerDao trainerDao;

    @Mock
    private CredentialGenerator credentialGenerator;

    @InjectMocks
    private UsernameResolver usernameResolver;

    private final Set<String> traineeUsernames = new HashSet<>();
    private final Set<String> trainerUsernames = new HashSet<>();

    @BeforeEach
    void setUp() {
        when(credentialGenerator.generateUsername(anyString(), anyString()))
                .thenAnswer(call -> call.getArgument(0) + "." + call.getArgument(1));
        lenient().when(credentialGenerator.addSerialNumberToUsername(anyString(), anyInt()))
                .thenAnswer(call -> call.getArgument(0) + String.valueOf((int) call.getArgument(1)));
        when(traineeDao.existsByUsername(anyString()))
                .thenAnswer(call -> traineeUsernames.contains(call.getArgument(0)));
        when(trainerDao.existsByUsername(anyString()))
                .thenAnswer(call -> trainerUsernames.contains(call.getArgument(0)));
    }

    @Test
    @DisplayName("joins first and last name with a dot")
    void buildsUsernameFromNames() {
        Assertions.assertEquals("John.Smith", usernameResolver.generateUsername("John", "Smith"));
    }

    @Test
    @DisplayName("adds a serial number when a trainee has the same name")
    void addsSerialNumberOnTraineeClash() {
        traineeUsernames.add("John.Smith");

        Assertions.assertEquals("John.Smith1", usernameResolver.generateUsername("John", "Smith"));
    }

    @Test
    @DisplayName("adds a serial number when a trainer has the same name")
    void addsSerialNumberOnTrainerClash() {
        trainerUsernames.add("John.Smith");

        Assertions.assertEquals("John.Smith1", usernameResolver.generateUsername("John", "Smith"));
    }

    @Test
    @DisplayName("keeps counting up until a username is free")
    void countsUpUntilUsernameIsFree() {
        traineeUsernames.add("John.Smith");
        traineeUsernames.add("John.Smith1");
        traineeUsernames.add("John.Smith2");

        Assertions.assertEquals("John.Smith3", usernameResolver.generateUsername("John", "Smith"));
    }

    @Test
    @DisplayName("skips usernames taken by trainees or trainers")
    void skipsUsernamesTakenOnEitherSide() {
        traineeUsernames.add("John.Smith");
        trainerUsernames.add("John.Smith1");
        traineeUsernames.add("John.Smith2");

        Assertions.assertEquals("John.Smith3", usernameResolver.generateUsername("John", "Smith"));
    }

    @Test
    @DisplayName("suffixes the base username, not the previous candidate")
    void suffixesBaseUsername() {
        traineeUsernames.add("John.Smith");
        traineeUsernames.add("John.Smith1");

        usernameResolver.generateUsername("John", "Smith");

        verify(credentialGenerator).addSerialNumberToUsername("John.Smith", 2);
        verify(credentialGenerator, never()).addSerialNumberToUsername("John.Smith1", 2);
    }
}
