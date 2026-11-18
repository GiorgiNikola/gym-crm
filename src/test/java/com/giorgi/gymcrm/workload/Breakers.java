package com.giorgi.gymcrm.workload;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigurationProperties;

final class Breakers {

    private Breakers() {
    }

    static Resilience4JCircuitBreakerFactory onCallerThread(CircuitBreakerRegistry registry) {
        Resilience4JConfigurationProperties properties = new Resilience4JConfigurationProperties();
        properties.setDisableThreadPool(true);
        properties.setDisableTimeLimiter(true);
        return new Resilience4JCircuitBreakerFactory(registry, TimeLimiterRegistry.ofDefaults(), null, properties);
    }
}
