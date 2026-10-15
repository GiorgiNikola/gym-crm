package com.giorgi.gymcrm.dao;

import com.giorgi.gymcrm.config.RootConfig;
import com.giorgi.gymcrm.model.Trainee;
import com.giorgi.gymcrm.model.Trainer;
import com.giorgi.gymcrm.model.TrainingType;
import com.giorgi.gymcrm.model.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RootConfig.class)
@Transactional
class TrainerDaoImplTest {

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private TrainerDao trainerDao;

    private TrainingType trainingType(String name) {
        return entityManager
                .createQuery("select tt from TrainingType tt where tt.name = :name", TrainingType.class)
                .setParameter("name", name)
                .getSingleResult();
    }

    private User user(String firstName, String lastName, boolean active) {
        return User.builder()
                .firstName(firstName)
                .lastName(lastName)
                .username(firstName + "." + lastName)
                .password("Hj2wE8rT4y")
                .isActive(active)
                .build();
    }

    private Trainer trainer(String firstName, String lastName, String specialization, boolean active) {
        return Trainer.builder()
                .user(user(firstName, lastName, active))
                .specialization(trainingType(specialization))
                .build();
    }

    private Trainee trainee(String firstName, String lastName) {
        return Trainee.builder()
                .user(user(firstName, lastName, true))
                .build();
    }

    private List<String> usernames(List<Trainer> trainers) {
        return trainers.stream().map(t -> t.getUser().getUsername()).toList();
    }

    @Test
    @DisplayName("save stores the trainer with its user and specialization")
    void savesTrainerWithUserAndSpecialization() {
        Trainer saved = trainerDao.save(trainer("Sarah", "Miller", "YOGA", true));
        entityManager.flush();
        entityManager.clear();

        Trainer found = entityManager.find(Trainer.class, saved.getId());
        Assertions.assertNotNull(found);
        Assertions.assertEquals("Sarah.Miller", found.getUser().getUsername());
        Assertions.assertEquals("YOGA", found.getSpecialization().getName());
    }

    @Test
    @DisplayName("findByUsername returns the trainer with user and specialization loaded")
    void findByUsernameLoadsUserAndSpecialization() {
        trainerDao.save(trainer("Sarah", "Miller", "YOGA", true));
        entityManager.flush();
        entityManager.clear();

        Trainer found = trainerDao.findByUsername("Sarah.Miller").orElseThrow();

        Assertions.assertTrue(Hibernate.isInitialized(found.getUser()));
        Assertions.assertTrue(Hibernate.isInitialized(found.getSpecialization()));
        Assertions.assertEquals("Sarah", found.getUser().getFirstName());
        Assertions.assertEquals("YOGA", found.getSpecialization().getName());
    }

    @Test
    @DisplayName("findByUsername returns empty for an unknown username")
    void findByUsernameReturnsEmptyForUnknownUsername() {
        Optional<Trainer> found = trainerDao.findByUsername("Nobody.Here");

        Assertions.assertTrue(found.isEmpty());
    }

    @Test
    @DisplayName("findNotAssignedToTrainee returns every active trainer when none are assigned")
    void findNotAssignedReturnsAllActiveTrainers() {
        trainerDao.save(trainer("Sarah", "Miller", "YOGA", true));
        trainerDao.save(trainer("Robert", "Taylor", "FITNESS", true));
        entityManager.persist(trainee("John", "Smith"));
        entityManager.flush();
        entityManager.clear();

        List<Trainer> unassigned = trainerDao.findNotAssignedToTrainee("John.Smith");

        Assertions.assertEquals(2, unassigned.size());
        Assertions.assertTrue(usernames(unassigned).containsAll(List.of("Sarah.Miller", "Robert.Taylor")));
    }

    @Test
    @DisplayName("findNotAssignedToTrainee leaves out trainers the trainee already has")
    void findNotAssignedExcludesAssignedTrainers() {
        Trainer sarah = trainerDao.save(trainer("Sarah", "Miller", "YOGA", true));
        trainerDao.save(trainer("Robert", "Taylor", "FITNESS", true));
        Trainee john = trainee("John", "Smith");
        john.getTrainers().add(sarah);
        entityManager.persist(john);
        entityManager.flush();
        entityManager.clear();

        List<Trainer> unassigned = trainerDao.findNotAssignedToTrainee("John.Smith");

        Assertions.assertEquals(1, unassigned.size());
        Assertions.assertEquals("Robert.Taylor", unassigned.get(0).getUser().getUsername());
    }

    @Test
    @DisplayName("findNotAssignedToTrainee leaves out inactive trainers")
    void findNotAssignedExcludesInactiveTrainers() {
        trainerDao.save(trainer("Sarah", "Miller", "YOGA", true));
        trainerDao.save(trainer("Laura", "Davis", "STRETCHING", false));
        entityManager.persist(trainee("John", "Smith"));
        entityManager.flush();
        entityManager.clear();

        List<Trainer> unassigned = trainerDao.findNotAssignedToTrainee("John.Smith");

        Assertions.assertEquals(1, unassigned.size());
        Assertions.assertEquals("Sarah.Miller", unassigned.get(0).getUser().getUsername());
    }

    @Test
    @DisplayName("update persists a changed specialization")
    void updateChangesSpecialization() {
        Trainer saved = trainerDao.save(trainer("Sarah", "Miller", "YOGA", true));
        entityManager.flush();

        saved.setSpecialization(trainingType("STRETCHING"));
        trainerDao.update(saved);
        entityManager.flush();
        entityManager.clear();

        Assertions.assertEquals("STRETCHING",
                entityManager.find(Trainer.class, saved.getId()).getSpecialization().getName());
    }

    @Test
    @DisplayName("findAllByUsernames returns every match in one call")
    void findsSeveralTrainersByUsername() {
        trainerDao.save(trainer("Sarah", "Miller", "YOGA", true));
        trainerDao.save(trainer("Robert", "Taylor", "FITNESS", true));
        trainerDao.save(trainer("Laura", "Davis", "STRETCHING", true));
        entityManager.flush();
        entityManager.clear();

        List<Trainer> found = trainerDao.findAllByUsernames(List.of("Sarah.Miller", "Laura.Davis"));

        Assertions.assertEquals(2, found.size());
        Assertions.assertTrue(usernames(found).containsAll(List.of("Sarah.Miller", "Laura.Davis")));
        Assertions.assertTrue(Hibernate.isInitialized(found.get(0).getUser()));
        Assertions.assertTrue(Hibernate.isInitialized(found.get(0).getSpecialization()));
    }

    @Test
    @DisplayName("findAllByUsernames returns an empty list for an empty collection")
    void findsNothingForEmptyCollection() {
        trainerDao.save(trainer("Sarah", "Miller", "YOGA", true));
        entityManager.flush();

        Assertions.assertTrue(trainerDao.findAllByUsernames(List.of()).isEmpty());
    }

    @Test
    @DisplayName("findProfileByUsername loads every trainee with their user")
    void profileComesWithTrainees() {
        Trainer sarah = trainer("Sarah", "Miller", "YOGA", true);
        entityManager.persist(sarah);

        Trainee john = trainee("John", "Smith");
        Trainee anna = trainee("Anna", "Brown");
        john.getTrainers().add(sarah);
        anna.getTrainers().add(sarah);
        entityManager.persist(john);
        entityManager.persist(anna);

        entityManager.flush();
        entityManager.clear();

        Trainer found = trainerDao.findProfileByUsername("Sarah.Miller").orElseThrow();

        Assertions.assertTrue(Hibernate.isInitialized(found.getSpecialization()));
        Assertions.assertTrue(Hibernate.isInitialized(found.getTrainees()));
        Assertions.assertEquals(2, found.getTrainees().size());
        for (Trainee trainee : found.getTrainees()) {
            Assertions.assertTrue(Hibernate.isInitialized(trainee.getUser()));
        }
    }

    @Test
    @DisplayName("a trainer with no trainees is still found")
    void trainerWithoutTraineesIsFound() {
        entityManager.persist(trainer("Sarah", "Miller", "YOGA", true));

        entityManager.flush();
        entityManager.clear();

        Optional<Trainer> found = trainerDao.findProfileByUsername("Sarah.Miller");

        Assertions.assertTrue(found.isPresent());
        Assertions.assertTrue(found.get().getTrainees().isEmpty());
    }
}
