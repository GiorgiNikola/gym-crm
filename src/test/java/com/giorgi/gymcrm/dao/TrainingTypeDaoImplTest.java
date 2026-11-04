package com.giorgi.gymcrm.dao;

import com.giorgi.gymcrm.config.RootConfig;
import com.giorgi.gymcrm.model.TrainingType;
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

    @Test
    @DisplayName("findAll returns the five seeded types, FITNESS first")
    void findsAllTypes() {
        List<TrainingType> types = trainingTypeDao.findAll();

        Assertions.assertEquals(5, types.size());
        Assertions.assertEquals("FITNESS", types.get(0).getName());
    }
}
