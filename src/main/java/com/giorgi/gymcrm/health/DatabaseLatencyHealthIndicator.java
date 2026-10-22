package com.giorgi.gymcrm.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.function.LongSupplier;

// Boot's own "db" indicator answers whether the database is reachable; this one answers
// whether it is still fast enough to serve requests.
@Slf4j
@Component
public class DatabaseLatencyHealthIndicator implements HealthIndicator {

    private static final long NANOS_PER_MILLI = 1_000_000L;

    private final JdbcTemplate jdbcTemplate;
    private final long thresholdMs;
    private final LongSupplier nanoClock;

    @Autowired
    public DatabaseLatencyHealthIndicator(JdbcTemplate jdbcTemplate, HealthProperties properties) {
        this(jdbcTemplate, properties, System::nanoTime);
    }

    DatabaseLatencyHealthIndicator(JdbcTemplate jdbcTemplate, HealthProperties properties, LongSupplier nanoClock) {
        this.jdbcTemplate = jdbcTemplate;
        this.thresholdMs = properties.dbLatencyThresholdMs();
        this.nanoClock = nanoClock;
    }

    @Override
    public Health health() {
        long startedAt = nanoClock.getAsLong();
        try {
            jdbcTemplate.queryForObject("select 1", Integer.class);
        } catch (RuntimeException exception) {
            return down("the database did not answer: " + exception.getMessage()).build();
        }

        long latencyMs = (nanoClock.getAsLong() - startedAt) / NANOS_PER_MILLI;
        if (latencyMs > thresholdMs) {
            return down("the database took " + latencyMs + " ms, over the " + thresholdMs + " ms threshold")
                    .withDetail("latencyMs", latencyMs)
                    .build();
        }

        return Health.up()
                .withDetail("latencyMs", latencyMs)
                .withDetail("thresholdMs", thresholdMs)
                .build();
    }

    private Health.Builder down(String reason) {
        log.warn("Health is DOWN: {}", reason);
        return Health.down().withDetail("reason", reason).withDetail("thresholdMs", thresholdMs);
    }
}
