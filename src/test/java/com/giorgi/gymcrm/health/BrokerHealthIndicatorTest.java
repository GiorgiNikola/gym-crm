package com.giorgi.gymcrm.health;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
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

    @Test
    @DisplayName("a reachable broker is UP and says so")
    void reachableBroker() {
        JmsTemplate jmsTemplate = mock(JmsTemplate.class);

        Health health = new BrokerHealthIndicator(jmsTemplate).health();

        Assertions.assertEquals(Status.UP, health.getStatus());
        Assertions.assertEquals(Map.of("reachable", true), health.getDetails());
    }

    @Test
    @DisplayName("an unreachable broker is still UP, with the failure in the details")
    void unreachableBroker() {
        JmsTemplate jmsTemplate = mock(JmsTemplate.class);
        when(jmsTemplate.execute(any(SessionCallback.class), eq(true)))
                .thenThrow(new UncategorizedJmsException("Connection refused"));

        Health health = new BrokerHealthIndicator(jmsTemplate).health();

        Assertions.assertEquals(Status.UP, health.getStatus());
        Assertions.assertEquals(Map.of("reachable", false,
                "error", "UncategorizedJmsException: Connection refused"), health.getDetails());
    }

    @Test
    @DisplayName("a broker that refuses the connection is reported as unreachable")
    void refusedConnection() {
        ActiveMQConnectionFactory nothingListening = new ActiveMQConnectionFactory("tcp://localhost:1");

        Health health = new BrokerHealthIndicator(new JmsTemplate(nothingListening)).health();

        Assertions.assertEquals(Status.UP, health.getStatus());
        Assertions.assertEquals(false, health.getDetails().get("reachable"));
        Assertions.assertNotNull(health.getDetails().get("error"));
    }
}
