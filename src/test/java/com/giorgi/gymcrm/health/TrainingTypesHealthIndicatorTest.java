package com.giorgi.gymcrm.health;

import com.giorgi.gymcrm.dao.TrainingTypeDao;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrainingTypesHealthIndicatorTest {

    @Mock
    private TrainingTypeDao trainingTypeDao;

    @InjectMocks
    private TrainingTypesHealthIndicator indicator;

    @Test
    @DisplayName("seeded training types report UP with how many there are")
    void reportsUpWithTheCount() {
        when(trainingTypeDao.count()).thenReturn(5L);

        Health health = indicator.health();

        Assertions.assertEquals(Status.UP, health.getStatus());
        Assertions.assertEquals(5L, health.getDetails().get("count"));
    }

    @Test
    @DisplayName("an empty table reports DOWN")
    void reportsDownWhenTableIsEmpty() {
        when(trainingTypeDao.count()).thenReturn(0L);

        Health health = indicator.health();

        Assertions.assertEquals(Status.DOWN, health.getStatus());
        Assertions.assertEquals("the training types table is empty", health.getDetails().get("reason"));
    }

    @Test
    @DisplayName("a failing query reports DOWN with what went wrong")
    void reportsDownWhenTheQueryFails() {
        when(trainingTypeDao.count()).thenThrow(new DataAccessResourceFailureException("connection refused"));

        Health health = indicator.health();

        Assertions.assertEquals(Status.DOWN, health.getStatus());
        Assertions.assertTrue(String.valueOf(health.getDetails().get("reason")).contains("connection refused"),
                String.valueOf(health.getDetails().get("reason")));
    }
}
