package com.giorgi.gymcrm.service;

import com.giorgi.gymcrm.model.Trainee;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@SpringBootTest
@Transactional
class UsernameLengthTest {

    private static final String LONG_FIRST_NAME = "J".repeat(50);
    private static final String LONG_LAST_NAME = "S".repeat(50);

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private TraineeService traineeService;

    private Trainee register() {
        return traineeService.createProfile(
                LONG_FIRST_NAME, LONG_LAST_NAME, LocalDate.of(1998, 5, 14), "123 Main St New York");
    }

    @Test
    @DisplayName("two trainees with the longest allowed names both get a username that fits the column")
    void storesLongUsernamesWithCollisionSuffix() {
        String first = register().getUser().getUsername();
        String second = register().getUser().getUsername();
        entityManager.flush();

        Assertions.assertEquals(LONG_FIRST_NAME + "." + LONG_LAST_NAME, first);
        Assertions.assertEquals(first + "1", second);
        Assertions.assertEquals(101, first.length());
        Assertions.assertEquals(102, second.length());
    }
}
