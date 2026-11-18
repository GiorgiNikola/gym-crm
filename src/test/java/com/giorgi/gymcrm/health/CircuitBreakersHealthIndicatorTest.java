package com.giorgi.gymcrm.health;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import java.util.Map;
import java.util.concurrent.TimeUnit;

class CircuitBreakersHealthIndicatorTest {

    private final CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
    private final CircuitBreakersHealthIndicator indicator = new CircuitBreakersHealthIndicator(registry);

    @Test
    @DisplayName("every breaker is listed with its state and call counts")
    void describesEveryBreaker() {
        CircuitBreaker workload = registry.circuitBreaker("workload");
        workload.onSuccess(0, TimeUnit.MILLISECONDS);
        workload.onError(0, TimeUnit.MILLISECONDS, new IllegalStateException());
        registry.circuitBreaker("other").transitionToOpenState();

        Health health = indicator.health();

        Assertions.assertEquals(Status.UP, health.getStatus());
        Assertions.assertEquals(Map.of(
                "state", "CLOSED",
                "failureRate", "-1.0%",
                "bufferedCalls", 2,
                "failedCalls", 1,
                "notPermittedCalls", 0L), health.getDetails().get("workload"));
        Assertions.assertEquals("OPEN", ((Map<?, ?>) health.getDetails().get("other")).get("state"));
    }

    @Test
    @DisplayName("an open breaker does not take gym-crm down with it")
    void openBreakerStaysUp() {
        registry.circuitBreaker("workload").transitionToOpenState();

        Assertions.assertEquals(Status.UP, indicator.health().getStatus());
    }

    @Test
    @DisplayName("no breaker yet is an empty but healthy report")
    void noBreakersIsUp() {
        Health health = indicator.health();

        Assertions.assertEquals(Status.UP, health.getStatus());
        Assertions.assertTrue(health.getDetails().isEmpty());
    }
}
