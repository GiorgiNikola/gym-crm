package com.giorgi.gymcrm.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

// The only place meter names and tag values live. Tags stay low cardinality on purpose:
// a username in a tag would create one time series per user.
@Component
public class GymMetrics {

    static final String REGISTRATIONS = "gymcrm.registrations";
    static final String LOGINS = "gymcrm.logins";
    static final String TRAININGS_ADDED = "gymcrm.trainings.added";
    static final String TRAINING_DURATION = "gymcrm.training.duration";

    private static final String TRAINEE = "trainee";
    private static final String TRAINER = "trainer";
    private static final String SUCCESS = "success";
    private static final String FAILURE = "failure";

    private final MeterRegistry registry;

    public GymMetrics(MeterRegistry registry) {
        this.registry = registry;

        // Touch every series once so a scrape sees them at 0 instead of not at all.
        registrations(TRAINEE);
        registrations(TRAINER);
        logins(SUCCESS);
        logins(FAILURE);
        trainingsAdded();
        trainingDuration();
    }

    public void traineeRegistered() {
        registrations(TRAINEE).increment();
    }

    public void trainerRegistered() {
        registrations(TRAINER).increment();
    }

    public void loginSucceeded() {
        logins(SUCCESS).increment();
    }

    public void loginFailed() {
        logins(FAILURE).increment();
    }

    public void trainingAdded(long durationMinutes) {
        trainingsAdded().increment();
        trainingDuration().record(durationMinutes);
    }

    private Counter registrations(String role) {
        return Counter.builder(REGISTRATIONS)
                .description("Profiles registered through the api")
                .tag("role", role)
                .register(registry);
    }

    private Counter logins(String outcome) {
        return Counter.builder(LOGINS)
                .description("Authentication attempts")
                .tag("outcome", outcome)
                .register(registry);
    }

    private Counter trainingsAdded() {
        return Counter.builder(TRAININGS_ADDED)
                .description("Trainings logged through the api")
                .register(registry);
    }

    private DistributionSummary trainingDuration() {
        return DistributionSummary.builder(TRAINING_DURATION)
                .description("How long the logged trainings last")
                .baseUnit("minutes")
                .register(registry);
    }
}
