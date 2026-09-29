package com.giorgi.gymcrm.dao;

import com.giorgi.gymcrm.model.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@SpringBootTest
@Transactional
class UserDaoImplTest {

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private UserDao userDao;

    private void persistJohn() {
        entityManager.persist(User.builder()
                .firstName("John")
                .lastName("Smith")
                .username("John.Smith")
                .password("aX7kQ2mN9p")
                .isActive(true)
                .build());
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("existsByUsername is true for a stored username")
    void findsStoredUsername() {
        persistJohn();

        Assertions.assertTrue(userDao.existsByUsername("John.Smith"));
    }

    @Test
    @DisplayName("existsByUsername is false for a free username")
    void doesNotFindFreeUsername() {
        persistJohn();

        Assertions.assertFalse(userDao.existsByUsername("John.Smith1"));
    }

    @Test
    @DisplayName("findByUsername returns the stored user")
    void findsUserByUsername() {
        persistJohn();

        User found = userDao.findByUsername("John.Smith").orElseThrow();

        Assertions.assertEquals("John", found.getFirstName());
        Assertions.assertEquals("Smith", found.getLastName());
        Assertions.assertTrue(found.isActive());
    }

    @Test
    @DisplayName("findByUsername returns empty for an unknown username")
    void returnsEmptyForUnknownUsername() {
        Optional<User> found = userDao.findByUsername("Nobody.Here");

        Assertions.assertTrue(found.isEmpty());
    }
}
