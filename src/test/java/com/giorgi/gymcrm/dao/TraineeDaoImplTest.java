package com.giorgi.gymcrm.dao;

import com.giorgi.gymcrm.model.Trainee;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class TraineeDaoImplTest {
    private Map<Long, Trainee> trainees;
    private TraineeDaoImpl traineeDao;

    @BeforeEach
    void setUp() {
        trainees = new HashMap<>();
        traineeDao = new TraineeDaoImpl();
        traineeDao.setTrainees(trainees);
    }

    private Trainee trainee(long id, String firstName, String lastName) {
        return Trainee.builder()
                .userID(id)
                .firstName(firstName)
                .lastName(lastName)
                .username(firstName + "." + lastName)
                .password("Zt4rW8pL2q")
                .isActive(true)
                .dateOfBirth(LocalDate.of(1998, 5, 14))
                .address("123 Main St New York")
                .build();
    }

    @Test
    @DisplayName("saves trainee and returns it back")
    void savesTraineeToStorage() {
        Trainee saved = traineeDao.save(trainee(1L, "John", "Smith"));

        Assertions.assertEquals("John", trainees.get(1L).getFirstName());
        Assertions.assertEquals(1L, saved.getUserID());
        Assertions.assertEquals("John.Smith", saved.getUsername());
        Assertions.assertTrue(saved.isActive());
    }

    @Test
    @DisplayName("save throws on duplicate id and keeps the old trainee")
    void throwsOnDuplicateId() {
        trainees.put(1L, trainee(1L, "John", "Smith"));

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> traineeDao.save(trainee(1L, "Emily", "Johnson")));
        Assertions.assertEquals("John", trainees.get(1L).getFirstName());
    }

    @Test
    @DisplayName("save does not keep the caller's object")
    void saveDoesNotKeepCallerObject() {
        Trainee john = trainee(1L, "John", "Smith");
        traineeDao.save(john);

        john.setFirstName("Edited");

        Assertions.assertEquals("John", trainees.get(1L).getFirstName());
    }

    @Test
    @DisplayName("editing what save returns does not touch storage")
    void saveResultIsDetached() {
        Trainee saved = traineeDao.save(trainee(1L, "John", "Smith"));

        saved.setFirstName("Edited");

        Assertions.assertEquals("John", trainees.get(1L).getFirstName());
    }

    @Test
    @DisplayName("update replaces the stored trainee")
    void updatesStoredTrainee() {
        trainees.put(1L, trainee(1L, "John", "Smith"));

        Trainee updated = traineeDao.update(trainee(1L, "Johnny", "Smith"));

        Assertions.assertEquals("Johnny", trainees.get(1L).getFirstName());
        Assertions.assertEquals("Johnny", updated.getFirstName());
    }

    @Test
    @DisplayName("update throws for unknown id")
    void updateThrowsForUnknownId() {
        Trainee unknown = trainee(99L, "Ana", "Kapanadze");

        Assertions.assertThrows(IllegalArgumentException.class, () -> traineeDao.update(unknown));
    }

    @Test
    @DisplayName("update does not keep the caller's object")
    void updateDoesNotKeepCallerObject() {
        trainees.put(1L, trainee(1L, "John", "Smith"));
        Trainee johnny = trainee(1L, "Johnny", "Smith");
        traineeDao.update(johnny);

        johnny.setFirstName("Edited");

        Assertions.assertEquals("Johnny", trainees.get(1L).getFirstName());
    }

    @Test
    @DisplayName("deletes trainee by id")
    void deletesTrainee() {
        trainees.put(1L, trainee(1L, "John", "Smith"));

        traineeDao.delete(1L);

        Assertions.assertFalse(trainees.containsKey(1L));
    }

    @Test
    @DisplayName("delete ignores an unknown id")
    void deleteIgnoresUnknownId() {
        Assertions.assertDoesNotThrow(() -> traineeDao.delete(999L));
    }

    @Test
    @DisplayName("finds trainee by id")
    void findsTraineeById() {
        trainees.put(2L, trainee(2L, "Emily", "Johnson"));

        Trainee found = traineeDao.findById(2L);

        Assertions.assertEquals("Emily.Johnson", found.getUsername());
    }

    @Test
    @DisplayName("findById returns null for unknown id")
    void returnsNullForUnknownId() {
        Assertions.assertNull(traineeDao.findById(999L));
    }

    @Test
    @DisplayName("editing what findById returns does not touch storage")
    void findByIdResultIsDetached() {
        trainees.put(1L, trainee(1L, "John", "Smith"));

        Trainee found = traineeDao.findById(1L);
        found.setFirstName("Edited");

        Assertions.assertEquals("John", trainees.get(1L).getFirstName());
    }

    @Test
    @DisplayName("findAll returns empty list when storage is empty")
    void findAllReturnsEmptyList() {
        Assertions.assertTrue(traineeDao.findAll().isEmpty());
    }

    @Test
    @DisplayName("findAll returns every trainee")
    void findAllReturnsEveryTrainee() {
        trainees.put(1L, trainee(1L, "John", "Smith"));
        trainees.put(2L, trainee(2L, "Emily", "Johnson"));
        trainees.put(3L, trainee(3L, "Ana", "Kapanadze"));

        List<Trainee> all = traineeDao.findAll();

        Assertions.assertEquals(3, all.size());
    }

    @Test
    @DisplayName("editing what findAll returns does not touch storage")
    void findAllResultsAreDetached() {
        trainees.put(1L, trainee(1L, "John", "Smith"));

        traineeDao.findAll().get(0).setFirstName("Edited");

        Assertions.assertEquals("John", trainees.get(1L).getFirstName());
    }

    @Test
    @DisplayName("finds a taken username")
    void findsTakenUsername() {
        trainees.put(1L, trainee(1L, "John", "Smith"));

        Assertions.assertTrue(traineeDao.existsByUsername("John.Smith"));
    }

    @Test
    @DisplayName("does not find a free username")
    void doesNotFindFreeUsername() {
        trainees.put(1L, trainee(1L, "John", "Smith"));

        Assertions.assertFalse(traineeDao.existsByUsername("John.Smith1"));
    }

    @Test
    @DisplayName("existsByUsername survives a stored trainee without a username")
    void handlesNullUsernameInStorage() {
        trainees.put(1L, Trainee.builder().userID(1L).firstName("John").build());

        Assertions.assertFalse(traineeDao.existsByUsername("John.Smith"));
    }

    @Test
    @DisplayName("finds an existing id")
    void findsExistingId() {
        trainees.put(1L, trainee(1L, "John", "Smith"));

        Assertions.assertTrue(traineeDao.existsByID(1L));
    }

    @Test
    @DisplayName("does not find a missing id")
    void doesNotFindMissingId() {
        Assertions.assertFalse(traineeDao.existsByID(999L));
    }

    @Test
    @DisplayName("generateId starts at 1 on empty storage")
    void generatesFirstId() {
        Assertions.assertEquals(1L, traineeDao.generateId());
    }

    @Test
    @DisplayName("generateId does not reuse gaps between ids")
    void skipsGapsWhenGeneratingId() {
        trainees.put(1L, trainee(1L, "John", "Smith"));
        trainees.put(5L, trainee(5L, "Ana", "Kapanadze"));

        Assertions.assertEquals(6L, traineeDao.generateId());
    }
}
