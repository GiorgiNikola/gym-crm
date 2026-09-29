package com.giorgi.gymcrm.dao;

import com.giorgi.gymcrm.model.Trainer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Slf4j
@Repository
public class TrainerDaoImpl implements TrainerDao {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Trainer save(Trainer trainer) {
        entityManager.persist(trainer);
        log.debug("Persisted trainer with id {}", trainer.getId());
        return trainer;
    }

    @Override
    public Trainer update(Trainer trainer) {
        return entityManager.merge(trainer);
    }

    @Override
    public Optional<Trainer> findByUsername(String username) {
        return entityManager.createQuery("""
                    select t from Trainer t
                    join fetch t.user u
                    join fetch t.specialization
                    where u.username = :username
                    """, Trainer.class)
                .setParameter("username", username)
                .getResultStream()
                .findFirst();
    }

    @Override
    public List<Trainer> findNotAssignedToTrainee(String traineeUsername) {
        return entityManager.createQuery("""
                    select tr from Trainer tr
                    join fetch tr.user u
                    join fetch tr.specialization
                    where u.isActive = true
                      and tr not in (
                          select assigned from Trainee t
                          join t.trainers assigned
                          where t.user.username = :username
                      )
                    """, Trainer.class)
                .setParameter("username", traineeUsername)
                .getResultList();
    }
}