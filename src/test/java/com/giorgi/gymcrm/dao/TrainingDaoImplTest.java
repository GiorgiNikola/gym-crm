package com.giorgi.gymcrm.dao;

import com.giorgi.gymcrm.model.Trainee;
import com.giorgi.gymcrm.model.Trainer;
import com.giorgi.gymcrm.model.Training;
import com.giorgi.gymcrm.model.TrainingType;
import com.giorgi.gymcrm.model.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@SpringBootTest
@Transactional
class TrainingDaoImplTest {

    private static final LocalDate AUGUST_1 = LocalDate.of(2026, 8, 1);
    private static final LocalDate AUGUST_10 = LocalDate.of(2026, 8, 10);
    private static final LocalDate AUGUST_15 = LocalDate.of(2026, 8, 15);
    private static final LocalDate AUGUST_20 = LocalDate.of(2026, 8, 20);
    private static final LocalDate AUGUST_31 = LocalDate.of(2026, 8, 31);

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private TrainingDao trainingDao;

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
        Trainee trainee = Trainee.builder().user(user(firstName, lastName)).build();
        entityManager.persist(trainee);
        return trainee;
    }

    private Trainer trainer(String firstName, String lastName, String specialization) {
        Trainer trainer = Trainer.builder()
                .user(user(firstName, lastName))
                .specialization(trainingType(specialization))
                .build();
        entityManager.persist(trainer);
        return trainer;
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

    private List<String> names(List<Training> trainings) {
        return trainings.stream().map(Training::getName).toList();
    }

    @BeforeEach
    void setUp() {
        Trainee john = trainee("John", "Smith");
        Trainee emily = trainee("Emily", "Johnson");
        Trainer sarah = trainer("Sarah", "Miller", "YOGA");
        Trainer robert = trainer("Robert", "Taylor", "FITNESS");

        entityManager.persist(training(john, sarah, "Morning Yoga", AUGUST_1));
        entityManager.persist(training(john, robert, "Strength Session", AUGUST_15));
        entityManager.persist(training(emily, sarah, "Evening Yoga", AUGUST_20));
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("save stores the training")
    void savesTraining() {
        Trainee ana = trainee("Ana", "Kapanadze");
        Trainer giorgi = trainer("Giorgi", "Beridze", "ZUMBA");

        Training saved = trainingDao.save(training(ana, giorgi, "Zumba Party", AUGUST_20));
        entityManager.flush();
        entityManager.clear();

        Training found = entityManager.find(Training.class, saved.getId());
        Assertions.assertNotNull(found);
        Assertions.assertEquals("Zumba Party", found.getName());
        Assertions.assertEquals(60L, found.getDuration());
        Assertions.assertEquals("ZUMBA", found.getType().getName());
    }

    @Test
    @DisplayName("findTraineeTrainings without filters returns only that trainee's trainings")
    void findsAllTraineeTrainings() {
        List<Training> trainings = trainingDao.findTraineeTrainings("John.Smith", null, null, null, null);

        Assertions.assertEquals(2, trainings.size());
        Assertions.assertEquals(List.of("Morning Yoga", "Strength Session"), names(trainings));
    }

    @Test
    @DisplayName("findTraineeTrainings drops trainings before fromDate")
    void filtersTraineeTrainingsByFromDate() {
        List<Training> trainings = trainingDao.findTraineeTrainings("John.Smith", AUGUST_10, null, null, null);

        Assertions.assertEquals(List.of("Strength Session"), names(trainings));
    }

    @Test
    @DisplayName("findTraineeTrainings drops trainings after toDate")
    void filtersTraineeTrainingsByToDate() {
        List<Training> trainings = trainingDao.findTraineeTrainings("John.Smith", null, AUGUST_10, null, null);

        Assertions.assertEquals(List.of("Morning Yoga"), names(trainings));
    }

    @Test
    @DisplayName("findTraineeTrainings matches the trainer first name ignoring case")
    void filtersTraineeTrainingsByTrainerName() {
        List<Training> trainings = trainingDao.findTraineeTrainings("John.Smith", null, null, "sArAh", null);

        Assertions.assertEquals(List.of("Morning Yoga"), names(trainings));
    }

    @Test
    @DisplayName("findTraineeTrainings matches the training type")
    void filtersTraineeTrainingsByType() {
        List<Training> trainings = trainingDao.findTraineeTrainings("John.Smith", null, null, null, "FITNESS");

        Assertions.assertEquals(List.of("Strength Session"), names(trainings));
    }

    @Test
    @DisplayName("findTraineeTrainings applies every filter together")
    void filtersTraineeTrainingsByEverythingAtOnce() {
        List<Training> trainings = trainingDao.findTraineeTrainings(
                "John.Smith", AUGUST_1, AUGUST_31, "robert", "FITNESS");

        Assertions.assertEquals(List.of("Strength Session"), names(trainings));
    }

    @Test
    @DisplayName("findTrainerTrainings without filters returns every training of that trainer")
    void findsAllTrainerTrainings() {
        List<Training> trainings = trainingDao.findTrainerTrainings("Sarah.Miller", null, null, null);

        Assertions.assertEquals(2, trainings.size());
        Assertions.assertEquals(List.of("Morning Yoga", "Evening Yoga"), names(trainings));
    }

    @Test
    @DisplayName("findTrainerTrainings keeps only trainings inside the date range")
    void filtersTrainerTrainingsByDateRange() {
        List<Training> trainings = trainingDao.findTrainerTrainings(
                "Sarah.Miller", AUGUST_15, AUGUST_31, null);

        Assertions.assertEquals(List.of("Evening Yoga"), names(trainings));
    }

    @Test
    @DisplayName("findTrainerTrainings matches the trainee first name")
    void filtersTrainerTrainingsByTraineeName() {
        List<Training> trainings = trainingDao.findTrainerTrainings("Sarah.Miller", null, null, "emily");

        Assertions.assertEquals(List.of("Evening Yoga"), names(trainings));
    }
}
