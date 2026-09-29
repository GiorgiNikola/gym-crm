package com.giorgi.gymcrm.dao;

import com.giorgi.gymcrm.model.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserDaoImpl implements UserDao {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public boolean existsByUsername(String username) {
        return entityManager.createQuery(
                        "select count(u) from User u where u.username = :username", Long.class)
                .setParameter("username", username)
                .getSingleResult() > 0;
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return entityManager.createQuery(
                        "select u from User u where u.username = :username", User.class)
                .setParameter("username", username)
                .getResultStream()
                .findFirst();
    }
}