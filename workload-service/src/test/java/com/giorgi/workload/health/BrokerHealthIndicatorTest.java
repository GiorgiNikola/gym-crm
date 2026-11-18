package com.giorgi.workload.health;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.jms.config.JmsListenerEndpointRegistry;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.jms.listener.MessageListenerContainer;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BrokerHealthIndicatorTest {

    private final JmsListenerEndpointRegistry registry = mock(JmsListenerEndpointRegistry.class);
    private final BrokerHealthIndicator indicator = new BrokerHealthIndicator(registry);

    private void register(String id, MessageListenerContainer container) {
        LinkedHashSet<String> ids = new LinkedHashSet<>(registry.getListenerContainerIds());
        ids.add(id);
        when(registry.getListenerContainerIds()).thenReturn(ids);
        when(registry.getListenerContainer(id)).thenReturn(container);
    }

    @Test
    @DisplayName("a consuming listener reports its connection and consumer counts")
    void reportsConsumingListener() {
        DefaultMessageListenerContainer listener = mock(DefaultMessageListenerContainer.class);
        when(listener.isRunning()).thenReturn(true);
        when(listener.isRegisteredWithDestination()).thenReturn(true);
        when(listener.getActiveConsumerCount()).thenReturn(2);
        when(listener.getScheduledConsumerCount()).thenReturn(2);
        when(listener.getMaxConcurrentConsumers()).thenReturn(4);
        register("workloadEvents", listener);

        Health health = indicator.health();

        Assertions.assertEquals(Status.UP, health.getStatus());
        Assertions.assertEquals(Map.of("workloadEvents", Map.of("running", true, "connected", true,
                "recovering", false, "activeConsumers", 2, "scheduledConsumers", 2, "maxConsumers", 4)),
                health.getDetails());
    }

    @Test
    @DisplayName("a listener that lost the broker keeps the service up and shows it is recovering")
    void staysUpWhileRecovering() {
        DefaultMessageListenerContainer listener = mock(DefaultMessageListenerContainer.class);
        when(listener.isRunning()).thenReturn(true);
        when(listener.isRecovering()).thenReturn(true);
        register("workloadEvents", listener);

        Map<?, ?> state = (Map<?, ?>) indicator.health().getDetails().get("workloadEvents");

        Assertions.assertEquals(List.of(false, true), List.of(state.get("connected"), state.get("recovering")));
        Assertions.assertEquals(Status.UP, indicator.health().getStatus());
    }

    @Test
    @DisplayName("another container type only says whether it runs, and no listeners means plain UP")
    void otherContainersAndNone() {
        Assertions.assertEquals(Health.up().build(), indicator.health());

        register("other", mock(MessageListenerContainer.class));

        Assertions.assertEquals(Map.of("other", Map.of("running", false)), indicator.health().getDetails());
    }
}
