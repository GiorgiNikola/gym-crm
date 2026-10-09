package com.giorgi.gymcrm.dao;

import com.giorgi.gymcrm.model.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Repository
public class TrainingDaoImpl implements TrainingDao {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Training save(Training training) {
        entityManager.persist(training);
        log.debug("Persisted training with id {}", training.getId());
        return training;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Training> findTraineeTrainings(String traineeUsername,
                                               LocalDate fromDate,
                                               LocalDate toDate,
                                               String trainerName,
                                               String trainingTypeName) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Training> query = cb.createQuery(Training.class);
        Root<Training> training = query.from(Training.class);

        Join<Training, Trainee> trainee = (Join<Training, Trainee>) training.<Training, Trainee>fetch("trainee");
        Join<Trainee, User> traineeUser = (Join<Trainee, User>) trainee.<Trainee, User>fetch("user");
        Join<Training, Trainer> trainer = (Join<Training, Trainer>) training.<Training, Trainer>fetch("trainer");
        Join<Trainer, User> trainerUser = (Join<Trainer, User>) trainer.<Trainer, User>fetch("user");
        Join<Training, TrainingType> type = (Join<Training, TrainingType>) training.<Training, TrainingType>fetch("type");

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(traineeUser.get("username"), traineeUsername));

        if (fromDate != null) {
            predicates.add(cb.greaterThanOrEqualTo(training.get("date"), fromDate));
        }
        if (toDate != null) {
            predicates.add(cb.lessThanOrEqualTo(training.get("date"), toDate));
        }
        if (trainerName != null && !trainerName.isBlank()) {
            predicates.add(cb.like(cb.lower(trainerUser.get("firstName")),
                    "%" + trainerName.toLowerCase() + "%"));
        }
        if (trainingTypeName != null && !trainingTypeName.isBlank()) {
            predicates.add(cb.equal(type.get("name"), trainingTypeName));
        }

        query.select(training)
                .where(predicates.toArray(new Predicate[0]))
                .orderBy(cb.asc(training.get("date")));

        return entityManager.createQuery(query).getResultList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Training> findTrainerTrainings(String trainerUsername,
                                               LocalDate fromDate,
                                               LocalDate toDate,
                                               String traineeName) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Training> query = cb.createQuery(Training.class);
        Root<Training> training = query.from(Training.class);

        Join<Training, Trainer> trainer = (Join<Training, Trainer>) training.<Training, Trainer>fetch("trainer");
        Join<Trainer, User> trainerUser = (Join<Trainer, User>) trainer.<Trainer, User>fetch("user");
        Join<Training, Trainee> trainee = (Join<Training, Trainee>) training.<Training, Trainee>fetch("trainee");
        Join<Trainee, User> traineeUser = (Join<Trainee, User>) trainee.<Trainee, User>fetch("user");
        training.fetch("type");

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(trainerUser.get("username"), trainerUsername));

        if (fromDate != null) {
            predicates.add(cb.greaterThanOrEqualTo(training.get("date"), fromDate));
        }
        if (toDate != null) {
            predicates.add(cb.lessThanOrEqualTo(training.get("date"), toDate));
        }
        if (traineeName != null && !traineeName.isBlank()) {
            predicates.add(cb.like(cb.lower(traineeUser.get("firstName")),
                    "%" + traineeName.toLowerCase() + "%"));
        }

        query.select(training)
                .where(predicates.toArray(new Predicate[0]))
                .orderBy(cb.asc(training.get("date")));

        return entityManager.createQuery(query).getResultList();
    }
}
