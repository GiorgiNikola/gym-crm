package com.giorgi.gymcrm.health;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.function.LongSupplier;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatabaseLatencyHealthIndicatorTest {

    private static final long THRESHOLD_MS = 500L;
    private static final long MILLI = 1_000_000L;

    @Mock
    private JdbcTemplate jdbcTemplate;

    // Two readings per health() call, so the latency is whatever the test says it is.
    private LongSupplier clockTaking(long millis) {
        return new LongSupplier() {
            private int reading;

            @Override
            public long getAsLong() {
                return reading++ == 0 ? 0L : millis * MILLI;
            }
        };
    }

    private DatabaseLatencyHealthIndicator indicatorTaking(long millis) {
        return new DatabaseLatencyHealthIndicator(jdbcTemplate, new HealthProperties(THRESHOLD_MS),
                clockTaking(millis));
    }

    @Test
    @DisplayName("a quick query reports UP with the measured latency")
    void reportsUpBelowTheThreshold() {
        Health health = indicatorTaking(12L).health();

        Assertions.assertEquals(Status.UP, health.getStatus());
        Assertions.assertEquals(12L, health.getDetails().get("latencyMs"));
        Assertions.assertEquals(THRESHOLD_MS, health.getDetails().get("thresholdMs"));
    }

    @Test
    @DisplayName("a query right on the threshold is still UP")
    void acceptsTheThresholdItself() {
        Assertions.assertEquals(Status.UP, indicatorTaking(THRESHOLD_MS).health().getStatus());
    }

    @Test
    @DisplayName("a slow query reports DOWN and names the threshold")
    void reportsDownAboveTheThreshold() {
        Health health = indicatorTaking(THRESHOLD_MS + 1).health();

        Assertions.assertEquals(Status.DOWN, health.getStatus());
        Assertions.assertEquals(501L, health.getDetails().get("latencyMs"));
        Assertions.assertEquals("the database took 501 ms, over the 500 ms threshold",
                health.getDetails().get("reason"));
    }

    @Test
    @DisplayName("a failing query reports DOWN with what went wrong")
    void reportsDownWhenTheQueryFails() {
        when(jdbcTemplate.queryForObject("select 1", Integer.class))
                .thenThrow(new QueryTimeoutException("statement timed out"));

        Health health = indicatorTaking(1L).health();

        Assertions.assertEquals(Status.DOWN, health.getStatus());
        Assertions.assertTrue(String.valueOf(health.getDetails().get("reason")).contains("statement timed out"),
                String.valueOf(health.getDetails().get("reason")));
        Assertions.assertFalse(health.getDetails().containsKey("latencyMs"));
    }

    @Test
    @DisplayName("a tighter configured threshold is the one that counts")
    void usesTheConfiguredThreshold() {
        Health health = new DatabaseLatencyHealthIndicator(jdbcTemplate, new HealthProperties(5L),
                clockTaking(10L)).health();

        Assertions.assertEquals(Status.DOWN, health.getStatus());
        Assertions.assertEquals(5L, health.getDetails().get("thresholdMs"));
    }
}
