package com.giorgi.gymcrm.dao;

import com.giorgi.gymcrm.model.Trainee;
import com.giorgi.gymcrm.model.Trainer;
import com.giorgi.gymcrm.model.Training;
import com.giorgi.gymcrm.model.TrainingType;
import com.giorgi.gymcrm.model.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

@SpringBootTest
@Transactional
class TraineeDaoImplTest {

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private TraineeDao traineeDao;

    private TrainingType trainingType(String name) {
        return entityManager
                .createQuery("select tt from TrainingType tt where tt.name = :name", TrainingType.class)
                .setParameter("name", name)
                .getSingleResult();
    }

    private User user(String firstName, String lastName) {
        return User.builder()
                .firstName(firstName)
                .lastName(lastName)
                .username(firstName + "." + lastName)
                .password("aX7kQ2mN9p")
                .isActive(true)
                .build();
    }

    private Trainee trainee(String firstName, String lastName) {
        return Trainee.builder()
                .user(user(firstName, lastName))
                .dateOfBirth(LocalDate.of(1998, 5, 14))
                .address("123 Main St New York")
                .build();
    }

    private Trainer trainer(String firstName, String lastName, String specialization) {
        return Trainer.builder()
                .user(user(firstName, lastName))
                .specialization(trainingType(specialization))
                .build();
    }

    private Training training(Trainee trainee, Trainer trainer, String name, LocalDate date) {
        return Training.builder()
                .trainee(trainee)
                .trainer(trainer)
                .name(name)
                .type(trainer.getSpecialization())
                .date(date)
                .duration(60L)
                .build();
    }

    private int joinTableRows(long traineeId) {
        Number count = (Number) entityManager
                .createNativeQuery("select count(*) from trainee2trainer where trainee_id = :id")
                .setParameter("id", traineeId)
                .getSingleResult();
        return count.intValue();
    }

    @Test
    @DisplayName("save stores the trainee and its user in one call")
    void savesTraineeWithItsUser() {
        Trainee saved = traineeDao.save(trainee("John", "Smith"));
        entityManager.flush();
        entityManager.clear();

        Trainee found = entityManager.find(Trainee.class, saved.getId());
        Assertions.assertNotNull(found);
        Assertions.assertEquals("123 Main St New York", found.getAddress());
        Assertions.assertNotNull(entityManager.find(User.class, saved.getUser().getId()));
        Assertions.assertEquals("John.Smith", found.getUser().getUsername());
    }

    @Test
    @DisplayName("findByUsername returns the trainee with its user already loaded")
    void findByUsernameLoadsUser() {
        traineeDao.save(trainee("John", "Smith"));
        entityManager.flush();
        entityManager.clear();

        Trainee found = traineeDao.findByUsername("John.Smith").orElseThrow();

        Assertions.assertTrue(Hibernate.isInitialized(found.getUser()));
        Assertions.assertEquals("John", found.getUser().getFirstName());
        Assertions.assertEquals("Smith", found.getUser().getLastName());
    }

    @Test
    @DisplayName("findByUsername returns empty for an unknown username")
    void findByUsernameReturnsEmptyForUnknownUsername() {
        Optional<Trainee> found = traineeDao.findByUsername("Nobody.Here");

        Assertions.assertTrue(found.isEmpty());
    }

    @Test
    @DisplayName("update persists a changed field")
    void updateChangesAddress() {
        Trainee saved = traineeDao.save(trainee("John", "Smith"));
        entityManager.flush();

        saved.setAddress("456 Park Avenue Boston");
        traineeDao.update(saved);
        entityManager.flush();
        entityManager.clear();

        Assertions.assertEquals("456 Park Avenue Boston",
                entityManager.find(Trainee.class, saved.getId()).getAddress());
    }

    @Test
    @DisplayName("delete removes the trainee, its user row, its trainings and its trainer links")
    void deleteRemovesTrainingsAndUser() {
        Trainee john = trainee("John", "Smith");
        Trainer sarah = trainer("Sarah", "Miller", "YOGA");
        entityManager.persist(sarah);
        john.getTrainers().add(sarah);
        traineeDao.save(john);

        Training morning = training(john, sarah, "Morning Yoga", LocalDate.of(2026, 8, 1));
        Training evening = training(john, sarah, "Evening Yoga", LocalDate.of(2026, 8, 2));
        entityManager.persist(morning);
        entityManager.persist(evening);
        entityManager.flush();

        long traineeId = john.getId();
        long traineeUserId = john.getUser().getId();
        long trainerId = sarah.getId();
        long trainerUserId = sarah.getUser().getId();
        long morningId = morning.getId();
        long eveningId = evening.getId();
        entityManager.clear();

        traineeDao.delete(traineeDao.findByUsername("John.Smith").orElseThrow());
        entityManager.flush();
        entityManager.clear();

        Assertions.assertNull(entityManager.find(Trainee.class, traineeId));
        Assertions.assertNull(entityManager.find(User.class, traineeUserId));
        Assertions.assertNull(entityManager.find(Training.class, morningId));
        Assertions.assertNull(entityManager.find(Training.class, eveningId));
        Assertions.assertEquals(0, joinTableRows(traineeId));
        Assertions.assertNotNull(entityManager.find(Trainer.class, trainerId));
        Assertions.assertNotNull(entityManager.find(User.class, trainerUserId));
    }
}
