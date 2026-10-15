package com.giorgi.gymcrm.dao;

import com.giorgi.gymcrm.model.Trainee;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Slf4j
@Repository
public class TraineeDaoImpl implements TraineeDao {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Trainee save(Trainee trainee) {
        entityManager.persist(trainee);
        log.debug("Persisted trainee with id {}", trainee.getId());
        return trainee;
    }

    @Override
    public Trainee update(Trainee trainee) {
        return entityManager.merge(trainee);
    }

    @Override
    public void delete(Trainee trainee) {
        entityManager.remove(entityManager.contains(trainee) ? trainee : entityManager.merge(trainee));
    }

    @Override
    public Optional<Trainee> findByUsername(String username) {
        return entityManager.createQuery("""
                    select t from Trainee t
                    join fetch t.user u
                    where u.username = :username
                    """, Trainee.class)
                .setParameter("username", username)
                .getResultStream()
                .findFirst();
    }
}