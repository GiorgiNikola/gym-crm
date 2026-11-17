package com.giorgi.workload.health;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.jms.UncategorizedJmsException;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.SessionCallback;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BrokerHealthIndicatorTest {

    private final JmsTemplate jmsTemplate = mock(JmsTemplate.class);
    private final BrokerHealthIndicator indicator = new BrokerHealthIndicator(jmsTemplate);

    @Test
    @DisplayName("a broker that opens a session is reported reachable")
    void reportsReachableBroker() {
        Health health = indicator.health();

        Assertions.assertEquals(Status.UP, health.getStatus());
        Assertions.assertEquals(Map.of("reachable", true), health.getDetails());
    }

    @Test
    @DisplayName("an unreachable broker keeps the service up and says why in the details")
    void staysUpWhenBrokerIsUnreachable() {
        when(jmsTemplate.execute(any(SessionCallback.class), eq(true)))
                .thenThrow(new UncategorizedJmsException("Cannot connect to server"));

        Health health = indicator.health();

        Assertions.assertEquals(Status.UP, health.getStatus());
        Assertions.assertEquals(Map.of(
                "reachable", false,
                "error", "UncategorizedJmsException: Cannot connect to server"), health.getDetails());
    }
}
