package com.giorgi.gymcrm.dao;

import com.giorgi.gymcrm.model.TrainingType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@SpringBootTest
@Transactional
class TrainingTypeDaoImplTest {

    @Autowired
    private TrainingTypeDao trainingTypeDao;

    @Test
    @DisplayName("findByName returns a type seeded by data.sql")
    void findsSeededTrainingType() {
        TrainingType yoga = trainingTypeDao.findByName("YOGA").orElseThrow();

        Assertions.assertEquals("YOGA", yoga.getName());
        Assertions.assertNotNull(yoga.getId());
    }

    @Test
    @DisplayName("findByName returns empty for a type that does not exist")
    void returnsEmptyForUnknownTrainingType() {
        Optional<TrainingType> found = trainingTypeDao.findByName("PILATES");

        Assertions.assertTrue(found.isEmpty());
    }
}
