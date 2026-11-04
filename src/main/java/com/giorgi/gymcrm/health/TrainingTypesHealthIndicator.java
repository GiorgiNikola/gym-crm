package com.giorgi.gymcrm.health;

import com.giorgi.gymcrm.dao.TrainingTypeDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

// Every trainer needs a specialization, so an empty training_types table makes registration
// impossible even though the database itself is fine.
@Slf4j
@Component
public class TrainingTypesHealthIndicator implements HealthIndicator {

    private final TrainingTypeDao trainingTypeDao;

    public TrainingTypesHealthIndicator(TrainingTypeDao trainingTypeDao) {
        this.trainingTypeDao = trainingTypeDao;
    }

    @Override
    public Health health() {
        long count;
        try {
            count = trainingTypeDao.count();
        } catch (RuntimeException exception) {
            return down("the training types could not be counted: " + exception.getMessage());
        }

        if (count == 0) {
            return down("the training types table is empty");
        }

        return Health.up().withDetail("count", count).build();
    }

    private Health down(String reason) {
        log.warn("Health is DOWN: {}", reason);
        return Health.down().withDetail("reason", reason).build();
    }
}
