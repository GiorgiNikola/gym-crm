package com.giorgi.gymcrm.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GymMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final GymMetrics gymMetrics = new GymMetrics(registry);

    private double counter(String name, String tag, String value) {
        return registry.get(name).tag(tag, value).counter().count();
    }

    @Test
    @DisplayName("every counter exists at zero before anything happens")
    void preRegistersEveryCounter() {
        Assertions.assertEquals(0, counter(GymMetrics.REGISTRATIONS, "role", "trainee"));
        Assertions.assertEquals(0, counter(GymMetrics.REGISTRATIONS, "role", "trainer"));
        Assertions.assertEquals(0, counter(GymMetrics.LOGINS, "outcome", "success"));
        Assertions.assertEquals(0, counter(GymMetrics.LOGINS, "outcome", "failure"));
        Assertions.assertEquals(0, registry.get(GymMetrics.TRAININGS_ADDED).counter().count());
        Assertions.assertEquals(0, registry.get(GymMetrics.TRAINING_DURATION).summary().count());
    }

    @Test
    @DisplayName("a registered trainee only moves the trainee counter")
    void countsTraineeRegistrations() {
        gymMetrics.traineeRegistered();

        Assertions.assertEquals(1, counter(GymMetrics.REGISTRATIONS, "role", "trainee"));
        Assertions.assertEquals(0, counter(GymMetrics.REGISTRATIONS, "role", "trainer"));
    }

    @Test
    @DisplayName("a registered trainer only moves the trainer counter")
    void countsTrainerRegistrations() {
        gymMetrics.trainerRegistered();

        Assertions.assertEquals(1, counter(GymMetrics.REGISTRATIONS, "role", "trainer"));
        Assertions.assertEquals(0, counter(GymMetrics.REGISTRATIONS, "role", "trainee"));
    }

    @Test
    @DisplayName("logins are counted by outcome")
    void countsLoginsByOutcome() {
        gymMetrics.loginSucceeded();
        gymMetrics.loginFailed();
        gymMetrics.loginFailed();

        Assertions.assertEquals(1, counter(GymMetrics.LOGINS, "outcome", "success"));
        Assertions.assertEquals(2, counter(GymMetrics.LOGINS, "outcome", "failure"));
    }

    @Test
    @DisplayName("a logged training counts once and records its minutes")
    void countsTrainingsAndRecordsTheDuration() {
        gymMetrics.trainingAdded(60L);
        gymMetrics.trainingAdded(45L);

        Assertions.assertEquals(2, registry.get(GymMetrics.TRAININGS_ADDED).counter().count());
        Assertions.assertEquals(2, registry.get(GymMetrics.TRAINING_DURATION).summary().count());
        Assertions.assertEquals(105, registry.get(GymMetrics.TRAINING_DURATION).summary().totalAmount());
    }

    @Test
    @DisplayName("the duration summary counts in minutes")
    void recordsDurationInMinutes() {
        Assertions.assertEquals("minutes",
                registry.get(GymMetrics.TRAINING_DURATION).summary().getId().getBaseUnit());
    }
}
