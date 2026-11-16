package com.giorgi.gymcrm.health;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.TreeMap;

@Component
public class CircuitBreakersHealthIndicator implements HealthIndicator {

    private final CircuitBreakerRegistry registry;

    public CircuitBreakersHealthIndicator(CircuitBreakerRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Health health() {
        Map<String, Object> breakers = new TreeMap<>();
        registry.getAllCircuitBreakers().forEach(breaker -> breakers.put(breaker.getName(), describe(breaker)));
        return Health.up().withDetails(breakers).build();
    }

    private static Map<String, Object> describe(CircuitBreaker breaker) {
        CircuitBreaker.Metrics metrics = breaker.getMetrics();
        return Map.of(
                "state", breaker.getState().name(),
                "failureRate", metrics.getFailureRate() + "%",
                "bufferedCalls", metrics.getNumberOfBufferedCalls(),
                "failedCalls", metrics.getNumberOfFailedCalls(),
                "notPermittedCalls", metrics.getNumberOfNotPermittedCalls());
    }
}
