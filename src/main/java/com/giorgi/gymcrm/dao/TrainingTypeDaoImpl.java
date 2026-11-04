package com.giorgi.gymcrm.dao;

import com.giorgi.gymcrm.model.TrainingType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class TrainingTypeDaoImpl implements TrainingTypeDao {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Optional<TrainingType> findByName(String name) {
        return entityManager.createQuery(
                        "select tt from TrainingType tt where tt.name = :name", TrainingType.class)
                .setParameter("name", name)
                .getResultStream()
                .findFirst();
    }

    @Override
    public List<TrainingType> findAll() {
        return entityManager.createQuery(
                        "select tt from TrainingType tt order by tt.id", TrainingType.class)
                .getResultList();
    }
}