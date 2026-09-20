package com.giorgi.gymcrm.dao;

import com.giorgi.gymcrm.model.Trainer;
import com.giorgi.gymcrm.model.TrainingType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class TrainerDaoImplTest {
    private Map<Long, Trainer> trainers;
    private TrainerDaoImpl trainerDao;

    @BeforeEach
    void setUp() {
        trainers = new HashMap<>();
        trainerDao = new TrainerDaoImpl();
        trainerDao.setTrainers(trainers);
    }

    private Trainer trainer(long id, String firstName, String lastName, TrainingType specialization) {
        return Trainer.builder()
                .userID(id)
                .firstName(firstName)
                .lastName(lastName)
                .username(firstName + "." + lastName)
                .password("Hj2wE8rT4y")
                .isActive(true)
                .specialization(specialization)
                .build();
    }

    @Test
    @DisplayName("saves trainer and returns it back")
    void savesTrainerToStorage() {
        Trainer saved = trainerDao.save(trainer(1L, "Robert", "Taylor", TrainingType.FITNESS));

        Assertions.assertEquals("Robert", trainers.get(1L).getFirstName());
        Assertions.assertEquals(1L, saved.getUserID());
        Assertions.assertEquals("Robert.Taylor", saved.getUsername());
        Assertions.assertEquals(TrainingType.FITNESS, saved.getSpecialization());
    }

    @Test
    @DisplayName("save throws on duplicate id and keeps the old trainer")
    void throwsOnDuplicateId() {
        trainers.put(2L, trainer(2L, "Sarah", "Miller", TrainingType.YOGA));

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> trainerDao.save(trainer(2L, "Giorgi", "Beridze", TrainingType.ZUMBA)));
        Assertions.assertEquals(TrainingType.YOGA, trainers.get(2L).getSpecialization());
    }

    @Test
    @DisplayName("save does not keep the caller's object")
    void saveDoesNotKeepCallerObject() {
        Trainer sarah = trainer(2L, "Sarah", "Miller", TrainingType.YOGA);
        trainerDao.save(sarah);

        sarah.setSpecialization(TrainingType.ZUMBA);

        Assertions.assertEquals(TrainingType.YOGA, trainers.get(2L).getSpecialization());
    }

    @Test
    @DisplayName("editing what save returns does not touch storage")
    void saveResultIsDetached() {
        Trainer saved = trainerDao.save(trainer(2L, "Sarah", "Miller", TrainingType.YOGA));

        saved.setSpecialization(TrainingType.ZUMBA);

        Assertions.assertEquals(TrainingType.YOGA, trainers.get(2L).getSpecialization());
    }

    @Test
    @DisplayName("update replaces the stored trainer")
    void updatesStoredTrainer() {
        trainers.put(3L, trainer(3L, "Giorgi", "Beridze", TrainingType.ZUMBA));

        Trainer updated = trainerDao.update(trainer(3L, "Giorgi", "Beridze", TrainingType.RESISTANCE));

        Assertions.assertEquals(TrainingType.RESISTANCE, trainers.get(3L).getSpecialization());
        Assertions.assertEquals(TrainingType.RESISTANCE, updated.getSpecialization());
    }

    @Test
    @DisplayName("update throws for unknown id")
    void updateThrowsForUnknownId() {
        Trainer unknown = trainer(99L, "Nino", "Gelashvili", TrainingType.YOGA);

        Assertions.assertThrows(IllegalArgumentException.class, () -> trainerDao.update(unknown));
    }

    @Test
    @DisplayName("update does not keep the caller's object")
    void updateDoesNotKeepCallerObject() {
        trainers.put(3L, trainer(3L, "Giorgi", "Beridze", TrainingType.ZUMBA));
        Trainer updated = trainer(3L, "Giorgi", "Beridze", TrainingType.RESISTANCE);
        trainerDao.update(updated);

        updated.setSpecialization(TrainingType.FITNESS);

        Assertions.assertEquals(TrainingType.RESISTANCE, trainers.get(3L).getSpecialization());
    }

    @Test
    @DisplayName("finds trainer by id")
    void findsTrainerById() {
        trainers.put(3L, trainer(3L, "Giorgi", "Beridze", TrainingType.ZUMBA));

        Trainer found = trainerDao.findById(3L);

        Assertions.assertEquals("Giorgi.Beridze", found.getUsername());
        Assertions.assertEquals(TrainingType.ZUMBA, found.getSpecialization());
    }

    @Test
    @DisplayName("findById returns null for unknown id")
    void returnsNullForUnknownId() {
        Assertions.assertNull(trainerDao.findById(999L));
    }

    @Test
    @DisplayName("editing what findById returns does not touch storage")
    void findByIdResultIsDetached() {
        trainers.put(1L, trainer(1L, "Robert", "Taylor", TrainingType.FITNESS));

        Trainer found = trainerDao.findById(1L);
        found.setSpecialization(TrainingType.YOGA);

        Assertions.assertEquals(TrainingType.FITNESS, trainers.get(1L).getSpecialization());
    }

    @Test
    @DisplayName("findAll returns empty list when storage is empty")
    void findAllReturnsEmptyList() {
        Assertions.assertTrue(trainerDao.findAll().isEmpty());
    }

    @Test
    @DisplayName("findAll returns every trainer")
    void findAllReturnsEveryTrainer() {
        trainers.put(1L, trainer(1L, "Robert", "Taylor", TrainingType.FITNESS));
        trainers.put(2L, trainer(2L, "Sarah", "Miller", TrainingType.YOGA));

        List<Trainer> all = trainerDao.findAll();

        Assertions.assertEquals(2, all.size());
    }

    @Test
    @DisplayName("editing what findAll returns does not touch storage")
    void findAllResultsAreDetached() {
        trainers.put(1L, trainer(1L, "Robert", "Taylor", TrainingType.FITNESS));

        trainerDao.findAll().get(0).setSpecialization(TrainingType.YOGA);

        Assertions.assertEquals(TrainingType.FITNESS, trainers.get(1L).getSpecialization());
    }

    @Test
    @DisplayName("finds a taken username")
    void findsTakenUsername() {
        trainers.put(1L, trainer(1L, "Robert", "Taylor", TrainingType.FITNESS));

        Assertions.assertTrue(trainerDao.existsByUsername("Robert.Taylor"));
    }

    @Test
    @DisplayName("does not find a free username")
    void doesNotFindFreeUsername() {
        trainers.put(1L, trainer(1L, "Robert", "Taylor", TrainingType.FITNESS));

        Assertions.assertFalse(trainerDao.existsByUsername("Robert.Taylor1"));
    }

    @Test
    @DisplayName("existsByUsername survives a stored trainer without a username")
    void handlesNullUsernameInStorage() {
        trainers.put(1L, Trainer.builder().userID(1L).firstName("Robert").build());

        Assertions.assertFalse(trainerDao.existsByUsername("Robert.Taylor"));
    }

    @Test
    @DisplayName("finds an existing id")
    void findsExistingId() {
        trainers.put(1L, trainer(1L, "Robert", "Taylor", TrainingType.FITNESS));

        Assertions.assertTrue(trainerDao.existsByID(1L));
    }

    @Test
    @DisplayName("does not find a missing id")
    void doesNotFindMissingId() {
        Assertions.assertFalse(trainerDao.existsByID(999L));
    }

    @Test
    @DisplayName("generateId starts at 1 on empty storage")
    void generatesFirstId() {
        Assertions.assertEquals(1L, trainerDao.generateId());
    }

    @Test
    @DisplayName("generateId does not reuse gaps between ids")
    void skipsGapsWhenGeneratingId() {
        trainers.put(2L, trainer(2L, "Sarah", "Miller", TrainingType.YOGA));
        trainers.put(7L, trainer(7L, "Nino", "Gelashvili", TrainingType.STRETCHING));

        Assertions.assertEquals(8L, trainerDao.generateId());
    }
}
