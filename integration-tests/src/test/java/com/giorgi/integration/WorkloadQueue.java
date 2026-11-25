package com.giorgi.integration;

import jakarta.jms.JMSConsumer;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSException;
import jakarta.jms.JMSRuntimeException;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.awaitility.Awaitility;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class WorkloadQueue {

    private static final String QUEUE = "trainer.workload";
    private static final String DEAD_LETTER_QUEUE = "DLQ.trainer.workload";

    public void send(String body, Map<String, Object> properties) {
        try (ActiveMQConnectionFactory factory = connectionFactory(); JMSContext context = factory.createContext()) {
            TextMessage message = context.createTextMessage(body);
            for (Map.Entry<String, Object> property : properties.entrySet()) {
                message.setObjectProperty(property.getKey(), property.getValue());
            }
            context.createProducer().send(context.createQueue(QUEUE), message);
        } catch (JMSException failure) {
            throw new JMSRuntimeException(failure.getMessage());
        }
    }

    public Message awaitDeadLetter(String transactionId) {
        AtomicReference<Message> received = new AtomicReference<>();
        try (ActiveMQConnectionFactory factory = connectionFactory(); JMSContext context = factory.createContext();
             JMSConsumer consumer = context.createConsumer(context.createQueue(DEAD_LETTER_QUEUE),
                     "transactionId = '" + transactionId + "'")) {
            Awaitility.await("dead letter of " + transactionId).until(() -> {
                received.set(consumer.receive(500));
                return received.get() != null;
            });
        }
        return received.get();
    }

    private static ActiveMQConnectionFactory connectionFactory() {
        GymCrmSystem system = GymCrmSystem.get();
        URI broker = system.address(GymCrmSystem.BROKER, 61616);
        Map<String, String> environment = system.environment(GymCrmSystem.BROKER);
        return new ActiveMQConnectionFactory("tcp://" + broker.getHost() + ":" + broker.getPort(),
                environment.get("ARTEMIS_USER"), environment.get("ARTEMIS_PASSWORD"));
    }
}
