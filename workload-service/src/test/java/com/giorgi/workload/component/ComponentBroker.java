package com.giorgi.workload.component;

import com.giorgi.workload.messaging.BrokerProbe;
import com.giorgi.workload.messaging.EmbeddedBrokerTestConfig;
import com.giorgi.workload.messaging.TestEvent;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.awaitility.Awaitility;
import org.springframework.jms.core.JmsMessagingTemplate;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.messaging.Message;

public class ComponentBroker {

    private final BrokerProbe probe;
    private final JmsMessagingTemplate messaging;

    public ComponentBroker(EmbeddedActiveMQ embeddedBroker, JmsTemplate jmsTemplate) {
        this.probe = new BrokerProbe(embeddedBroker);
        this.messaging = new JmsMessagingTemplate(jmsTemplate);
    }

    public void drain() throws Exception {
        probe.purge(EmbeddedBrokerTestConfig.QUEUE);
        probe.purge(EmbeddedBrokerTestConfig.DEAD_LETTER_QUEUE);
    }

    public TestEvent send(TestEvent event) {
        return event.sendTo(messaging);
    }

    public void awaitProcessed() {
        Awaitility.await().until(() -> probe.messageCount(EmbeddedBrokerTestConfig.QUEUE) == 0);
    }

    public Message<?> receiveDeadLetter() {
        return messaging.receive(EmbeddedBrokerTestConfig.DEAD_LETTER_QUEUE);
    }
}
