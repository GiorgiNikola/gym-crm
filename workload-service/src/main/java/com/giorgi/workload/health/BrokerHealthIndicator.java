package com.giorgi.workload.health;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.jms.config.JmsListenerEndpointRegistry;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.jms.listener.MessageListenerContainer;

import java.util.LinkedHashMap;
import java.util.Map;

public class BrokerHealthIndicator implements HealthIndicator {

    private final JmsListenerEndpointRegistry listenerRegistry;

    public BrokerHealthIndicator(JmsListenerEndpointRegistry listenerRegistry) {
        this.listenerRegistry = listenerRegistry;
    }

    @Override
    public Health health() {
        Health.Builder health = Health.up();
        for (String id : listenerRegistry.getListenerContainerIds()) {
            health.withDetail(id, state(listenerRegistry.getListenerContainer(id)));
        }
        return health.build();
    }

    private static Map<String, Object> state(MessageListenerContainer container) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("running", container.isRunning());
        if (container instanceof DefaultMessageListenerContainer listener) {
            state.put("connected", listener.isRegisteredWithDestination());
            state.put("recovering", listener.isRecovering());
            state.put("activeConsumers", listener.getActiveConsumerCount());
            state.put("scheduledConsumers", listener.getScheduledConsumerCount());
            state.put("maxConsumers", listener.getMaxConcurrentConsumers());
        }
        return state;
    }
}
