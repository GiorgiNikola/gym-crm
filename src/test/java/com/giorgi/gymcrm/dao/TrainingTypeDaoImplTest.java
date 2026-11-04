package com.giorgi.gymcrm.dao;

import com.giorgi.gymcrm.model.TrainingType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.persistence.autoconfigure.PersistenceExceptionTranslationAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Optional;

// The slice leaves out @Repository beans and exception translation, so both come in explicitly;
// the datasource stays the H2 PostgreSQL-mode one from application.yml.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(PersistenceExceptionTranslationAutoConfiguration.class)
@Import(TrainingTypeDaoImpl.class)
class TrainingTypeDaoImplTest {

    @Autowired
    private TrainingTypeDao trainingTypeDao;

    @Test
    @DisplayName("findByName returns a type from the flyway seed")
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
