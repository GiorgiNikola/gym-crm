package com.giorgi.gymcrm.dao;

import com.giorgi.gymcrm.model.Training;
import com.giorgi.gymcrm.model.TrainingType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

class TrainingDaoImplTest {
    private Map<Long, Training> trainings;
    private TrainingDaoImpl trainingDao;

    @BeforeEach
    void setUp() {
        trainings = new HashMap<>();
        trainingDao = new TrainingDaoImpl();
        trainingDao.setTrainings(trainings);
    }

    private Training training(long id, String name, TrainingType type) {
        return Training.builder()
                .ID(id)
                .traineeID(1L)
                .trainerID(1L)
                .name(name)
                .type(type)
                .date(LocalDate.of(2026, 8, 1))
                .duration(60L)
                .build();
    }

    @Test
    @DisplayName("saves training and returns it back")
    void savesTrainingToStorage() {
        Training saved = trainingDao.save(training(1L, "Morning Fitness Session", TrainingType.FITNESS));

        Assertions.assertEquals("Morning Fitness Session", trainings.get(1L).getName());
        Assertions.assertEquals(1L, saved.getID());
        Assertions.assertEquals(TrainingType.FITNESS, saved.getType());
        Assertions.assertEquals(60L, saved.getDuration());
    }

    @Test
    @DisplayName("save throws on duplicate id and keeps the old training")
    void throwsOnDuplicateId() {
        trainings.put(1L, training(1L, "Morning Fitness Session", TrainingType.FITNESS));

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> trainingDao.save(training(1L, "Beginner Yoga Class", TrainingType.YOGA)));
        Assertions.assertEquals("Morning Fitness Session", trainings.get(1L).getName());
    }

    @Test
    @DisplayName("save does not keep the caller's object")
    void saveDoesNotKeepCallerObject() {
        Training morning = training(1L, "Morning Fitness Session", TrainingType.FITNESS);
        trainingDao.save(morning);

        morning.setDuration(999L);

        Assertions.assertEquals(60L, trainings.get(1L).getDuration());
    }

    @Test
    @DisplayName("editing what save returns does not touch storage")
    void saveResultIsDetached() {
        Training saved = trainingDao.save(training(1L, "Morning Fitness Session", TrainingType.FITNESS));

        saved.setDuration(999L);

        Assertions.assertEquals(60L, trainings.get(1L).getDuration());
    }

    @Test
    @DisplayName("finds training by id")
    void findsTrainingById() {
        trainings.put(3L, training(3L, "Zumba Cardio Blast", TrainingType.ZUMBA));

        Training found = trainingDao.findById(3L);

        Assertions.assertEquals("Zumba Cardio Blast", found.getName());
        Assertions.assertEquals(TrainingType.ZUMBA, found.getType());
    }

    @Test
    @DisplayName("findById returns null for unknown id")
    void returnsNullForUnknownId() {
        Assertions.assertNull(trainingDao.findById(999L));
    }

    @Test
    @DisplayName("editing what findById returns does not touch storage")
    void findByIdResultIsDetached() {
        trainings.put(1L, training(1L, "Morning Fitness Session", TrainingType.FITNESS));

        Training found = trainingDao.findById(1L);
        found.setName("Edited");

        Assertions.assertEquals("Morning Fitness Session", trainings.get(1L).getName());
    }

    @Test
    @DisplayName("findAll returns every training")
    void findAllReturnsEveryTraining() {
        trainings.put(1L, training(1L, "Morning Fitness Session", TrainingType.FITNESS));
        trainings.put(2L, training(2L, "Beginner Yoga Class", TrainingType.YOGA));

        Assertions.assertEquals(2, trainingDao.findAll().size());
    }

    @Test
    @DisplayName("editing what findAll returns does not touch storage")
    void findAllResultsAreDetached() {
        trainings.put(1L, training(1L, "Morning Fitness Session", TrainingType.FITNESS));

        trainingDao.findAll().get(0).setName("Edited");

        Assertions.assertEquals("Morning Fitness Session", trainings.get(1L).getName());
    }

    @Test
    @DisplayName("generateId starts at 1 on empty storage")
    void generatesFirstId() {
        Assertions.assertEquals(1L, trainingDao.generateId());
    }

    @Test
    @DisplayName("generateId does not reuse gaps between ids")
    void skipsGapsWhenGeneratingId() {
        trainings.put(1L, training(1L, "Morning Fitness Session", TrainingType.FITNESS));
        trainings.put(4L, training(4L, "Full Body Fitness", TrainingType.FITNESS));

        Assertions.assertEquals(5L, trainingDao.generateId());
    }
}
