package com.giorgi.gymcrm.component;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.springframework.jms.core.JmsMessagingTemplate;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.messaging.Message;

import java.util.ArrayList;
import java.util.List;

public class WorkloadQueue {

    private static final String QUEUE = "trainer.workload";

    private final EmbeddedActiveMQ broker;
    private final JmsMessagingTemplate receiver;

    public WorkloadQueue(EmbeddedActiveMQ broker, JmsTemplate jmsTemplate) {
        this.broker = broker;
        this.receiver = new JmsMessagingTemplate(jmsTemplate);
    }

    public void drain() throws Exception {
        queue().deleteAllReferences();
    }

    public long pending() {
        return queue().getMessageCount();
    }

    public List<Message<?>> receiveAll() {
        List<Message<?>> messages = new ArrayList<>();
        while (pending() > 0) {
            messages.add(receiver.receive(QUEUE));
        }
        return messages;
    }

    private Queue queue() {
        return broker.getActiveMQServer().locateQueue(SimpleString.of(QUEUE));
    }
}
