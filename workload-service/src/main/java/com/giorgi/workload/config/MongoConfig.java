package com.giorgi.workload.config;

import jakarta.validation.Validator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.mapping.event.ValidatingEntityCallback;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class MongoConfig {

    public static final int VERSION_CONFLICT_RETRIES = 3;

    private static final Duration VERSION_CONFLICT_DELAY = Duration.ofMillis(20);

    public static RetryPolicy versionConflictPolicy(Duration delay) {
        return RetryPolicy.builder()
                .includes(OptimisticLockingFailureException.class)
                .maxRetries(VERSION_CONFLICT_RETRIES)
                .delay(delay)
                .multiplier(2)
                .build();
    }

    @Bean
    public RetryTemplate versionConflictRetry() {
        return new RetryTemplate(versionConflictPolicy(VERSION_CONFLICT_DELAY));
    }

    @Bean
    public ValidatingEntityCallback validatingEntityCallback(Validator validator) {
        return new ValidatingEntityCallback(validator);
    }
}
