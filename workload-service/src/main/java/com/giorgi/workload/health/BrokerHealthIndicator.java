package com.giorgi.workload.health;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.jms.JmsException;
import org.springframework.jms.core.JmsTemplate;

public class BrokerHealthIndicator implements HealthIndicator {

    private final JmsTemplate jmsTemplate;

    public BrokerHealthIndicator(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
    }

    @Override
    public Health health() {
        try {
            jmsTemplate.execute(session -> null, true);
            return Health.up().withDetail("reachable", true).build();
        } catch (JmsException exception) {
            return Health.up()
                    .withDetail("reachable", false)
                    .withDetail("error", exception.getClass().getSimpleName() + ": " + exception.getMessage())
                    .build();
        }
    }
}
