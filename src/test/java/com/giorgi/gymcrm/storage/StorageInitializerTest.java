package com.giorgi.gymcrm.storage;

import com.giorgi.gymcrm.model.Trainee;
import com.giorgi.gymcrm.model.Trainer;
import com.giorgi.gymcrm.model.Training;
import com.giorgi.gymcrm.model.TrainingType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StorageInitializerTest {
    private static final String TRAINEE_PATH = "classpath:data/trainees.csv";
    private static final String TRAINER_PATH = "classpath:data/trainers.csv";
    private static final String TRAINING_PATH = "classpath:data/trainings.csv";

    private static final String TRAINEE_HEADER =
            "userID,firstName,lastName,username,password,isActive,dateOfBirth,address\n";
    private static final String TRAINER_HEADER =
            "userID,firstName,lastName,username,password,isActive,specialization\n";
    private static final String TRAINING_HEADER =
            "id,traineeID,trainerID,name,type,date,duration\n";

    @Mock
    private ResourceLoader resourceLoader;

    @Mock
    private Resource resource;

    private StorageInitializer storageInitializer;

    @BeforeEach
    void setUp() {
        storageInitializer = new StorageInitializer();
        storageInitializer.setResourceLoader(resourceLoader);
        ReflectionTestUtils.setField(storageInitializer, "traineeFilePath", TRAINEE_PATH);
        ReflectionTestUtils.setField(storageInitializer, "trainerFilePath", TRAINER_PATH);
        ReflectionTestUtils.setField(storageInitializer, "trainingFilePath", TRAINING_PATH);
    }

    private void stubFile(String path, String content) throws IOException {
        when(resourceLoader.getResource(path)).thenReturn(resource);
        when(resource.getInputStream()).thenReturn(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("loads trainees and maps every column")
    void loadsTrainees() throws IOException {
        stubFile(TRAINEE_PATH, TRAINEE_HEADER
                + "1,John,Smith,John.Smith,aX7kQ2mN9p,true,1998-05-14,123 Main St New York\n"
                + "2,Emily,Johnson,Emily.Johnson,Zt4rW8pL2q,true,2000-11-02,45 Oak Avenue Boston\n");
        Map<Long, Trainee> storage = new HashMap<>();

        storageInitializer.postProcessAfterInitialization(storage, "traineeStorage");

        Assertions.assertEquals(2, storage.size());
        Trainee john = storage.get(1L);
        Assertions.assertEquals(1L, john.getUserID());
        Assertions.assertEquals("John", john.getFirstName());
        Assertions.assertEquals("Smith", john.getLastName());
        Assertions.assertEquals("John.Smith", john.getUsername());
        Assertions.assertEquals("aX7kQ2mN9p", john.getPassword());
        Assertions.assertTrue(john.isActive());
        Assertions.assertEquals(LocalDate.of(1998, 5, 14), john.getDateOfBirth());
        Assertions.assertEquals("123 Main St New York", john.getAddress());
    }

    @Test
    @DisplayName("later row wins when two rows share an id")
    void collapsesDuplicateIds() throws IOException {
        stubFile(TRAINEE_PATH, TRAINEE_HEADER
                + "1,John,Smith,John.Smith,aX7kQ2mN9p,true,1998-05-14,123 Main St New York\n"
                + "1,Emily,Johnson,Emily.Johnson,Zt4rW8pL2q,true,2000-11-02,45 Oak Avenue Boston\n");
        Map<Long, Trainee> storage = new HashMap<>();

        storageInitializer.postProcessAfterInitialization(storage, "traineeStorage");

        Assertions.assertEquals(1, storage.size());
        Assertions.assertEquals("Emily", storage.get(1L).getFirstName());
    }

    @Test
    @DisplayName("reads an inactive trainee as not active")
    void readsInactiveTrainee() throws IOException {
        stubFile(TRAINEE_PATH, TRAINEE_HEADER + "3,Michael,Brown,Michael.Brown,Bn6yU3dF7s,false,1995-03-27,\n");
        Map<Long, Trainee> storage = new HashMap<>();

        storageInitializer.postProcessAfterInitialization(storage, "traineeStorage");

        Assertions.assertFalse(storage.get(3L).isActive());
    }

    @Test
    @DisplayName("stores a blank date of birth as null")
    void storesBlankDateOfBirthAsNull() throws IOException {
        stubFile(TRAINEE_PATH, TRAINEE_HEADER + "2,Emily,Johnson,Emily.Johnson,Zt4rW8pL2q,true,,\n");
        Map<Long, Trainee> storage = new HashMap<>();

        storageInitializer.postProcessAfterInitialization(storage, "traineeStorage");

        Assertions.assertNull(storage.get(2L).getDateOfBirth());
    }

    @Test
    @DisplayName("loads trainers and maps every column")
    void loadsTrainers() throws IOException {
        stubFile(TRAINER_PATH, TRAINER_HEADER + "2,Sarah,Miller,Sarah.Miller,Vb5nM1qA9z,true,YOGA\n");
        Map<Long, Trainer> storage = new HashMap<>();

        storageInitializer.postProcessAfterInitialization(storage, "trainerStorage");

        Trainer sarah = storage.get(2L);
        Assertions.assertEquals(2L, sarah.getUserID());
        Assertions.assertEquals("Sarah", sarah.getFirstName());
        Assertions.assertEquals("Miller", sarah.getLastName());
        Assertions.assertEquals("Sarah.Miller", sarah.getUsername());
        Assertions.assertTrue(sarah.isActive());
        Assertions.assertEquals(TrainingType.YOGA, sarah.getSpecialization());
    }

    @Test
    @DisplayName("throws for an unknown specialization")
    void throwsOnUnknownSpecialization() throws IOException {
        stubFile(TRAINER_PATH, TRAINER_HEADER + "1,Robert,Taylor,Robert.Taylor,Hj2wE8rT4y,true,PILATES\n");

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> storageInitializer.postProcessAfterInitialization(new HashMap<Long, Trainer>(), "trainerStorage"));
    }

    @Test
    @DisplayName("loads trainings and maps every column")
    void loadsTrainings() throws IOException {
        stubFile(TRAINING_PATH, TRAINING_HEADER + "1,1,1,Morning Fitness Session,FITNESS,2026-08-01,60\n");
        Map<Long, Training> storage = new HashMap<>();

        storageInitializer.postProcessAfterInitialization(storage, "trainingStorage");

        Training morning = storage.get(1L);
        Assertions.assertEquals(1L, morning.getID());
        Assertions.assertEquals(1L, morning.getTraineeID());
        Assertions.assertEquals(1L, morning.getTrainerID());
        Assertions.assertEquals("Morning Fitness Session", morning.getName());
        Assertions.assertEquals(TrainingType.FITNESS, morning.getType());
        Assertions.assertEquals(LocalDate.of(2026, 8, 1), morning.getDate());
        Assertions.assertEquals(60L, morning.getDuration());
    }

    @Test
    @DisplayName("stores a blank training date as null")
    void storesBlankTrainingDateAsNull() throws IOException {
        stubFile(TRAINING_PATH, TRAINING_HEADER + "1,1,1,Morning Fitness Session,FITNESS,,60\n");
        Map<Long, Training> storage = new HashMap<>();

        storageInitializer.postProcessAfterInitialization(storage, "trainingStorage");

        Assertions.assertNull(storage.get(1L).getDate());
    }

    @Test
    @DisplayName("leaves storage empty for a header only file")
    void leavesStorageEmptyForHeaderOnlyFile() throws IOException {
        stubFile(TRAINEE_PATH, TRAINEE_HEADER);
        Map<Long, Trainee> storage = new HashMap<>();

        storageInitializer.postProcessAfterInitialization(storage, "traineeStorage");

        Assertions.assertTrue(storage.isEmpty());
    }

    @Test
    @DisplayName("returns the same bean instance")
    void returnsSameBeanInstance() throws IOException {
        stubFile(TRAINEE_PATH, TRAINEE_HEADER);
        Map<Long, Trainee> storage = new HashMap<>();

        Object returned = storageInitializer.postProcessAfterInitialization(storage, "traineeStorage");

        Assertions.assertSame(storage, returned);
    }

    @Test
    @DisplayName("ignores beans that are not a storage map")
    void ignoresOtherBeans() {
        Object unrelatedBean = new Object();

        Object returned = storageInitializer.postProcessAfterInitialization(unrelatedBean, "someOtherBean");

        Assertions.assertSame(unrelatedBean, returned);
        verifyNoInteractions(resourceLoader);
    }

    @Test
    @DisplayName("wraps a read failure in UncheckedIOException")
    void wrapsReadFailure() throws IOException {
        when(resourceLoader.getResource(TRAINEE_PATH)).thenReturn(resource);
        when(resource.getInputStream()).thenThrow(new IOException("file not found"));

        Assertions.assertThrows(UncheckedIOException.class,
                () -> storageInitializer.postProcessAfterInitialization(new HashMap<Long, Trainee>(), "traineeStorage"));
    }
}
