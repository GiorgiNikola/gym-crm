package com.giorgi.workload.messaging;

import com.giorgi.workload.TestTokens;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import org.springframework.jms.core.JmsMessagingTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TestEvent {

    private final Map<String, Object> headers = new HashMap<>();
    private UUID eventId = UUID.randomUUID();
    private final String username;
    private String trainingDate = "2026-08-01";
    private int minutes = 60;
    private String actionType = "ADD";
    private String body;

    private TestEvent(String username) {
        this.username = username;
        headers.put(WorkloadEventHeaders.TYPE_ID, WorkloadEventHeaders.TYPE);
        headers.put(WorkloadEventHeaders.EVENT_VERSION, 1);
        headers.put(WorkloadEventHeaders.TRANSACTION_ID, "gym-crm-" + UUID.randomUUID());
        headers.put(WorkloadEventHeaders.AUTHORIZATION, TestTokens.token().scope(TestTokens.WRITE).sign());
        headers.put(WorkloadEventHeaders.GROUP_ID, username);
    }

    public static TestEvent forTrainer(String username) {
        return new TestEvent(username);
    }

    public static String newUsername() {
        return "trainer." + UUID.randomUUID();
    }

    public TestEvent eventId(UUID eventId) {
        this.eventId = eventId;
        return this;
    }

    public TestEvent minutes(int minutes) {
        this.minutes = minutes;
        return this;
    }

    public TestEvent action(String actionType) {
        this.actionType = actionType;
        return this;
    }

    public TestEvent trainingDate(String trainingDate) {
        this.trainingDate = trainingDate;
        return this;
    }

    public TestEvent body(String body) {
        this.body = body;
        return this;
    }

    public TestEvent header(String name, Object value) {
        headers.put(name, value);
        return this;
    }

    public TestEvent withoutHeader(String name) {
        headers.remove(name);
        return this;
    }

    public UUID eventId() {
        return eventId;
    }

    public String transactionId() {
        return String.valueOf(headers.get(WorkloadEventHeaders.TRANSACTION_ID));
    }

    public String text() {
        if (body != null) {
            return body;
        }
        return """
                {"eventId":"%s","occurredAt":"2026-11-17T10:15:30Z","trainerUsername":"%s",\
                "trainerFirstName":"Sarah","trainerLastName":"Miller","isActive":true,"trainingDate":"%s",\
                "trainingDuration":%d,"actionType":"%s"}""".formatted(
                eventId, username, trainingDate, minutes, actionType);
    }

    public <M extends Message> M withPropertiesOn(M message) throws JMSException {
        for (Map.Entry<String, Object> header : headers.entrySet()) {
            message.setObjectProperty(header.getKey(), header.getValue());
        }
        return message;
    }

    public TestEvent sendTo(JmsMessagingTemplate messaging) {
        messaging.convertAndSend(EmbeddedBrokerTestConfig.QUEUE, text(), headers);
        return this;
    }

    public TestEvent sendAt(BrokerProbe broker, Instant sentAt) throws Exception {
        broker.send(EmbeddedBrokerTestConfig.QUEUE, text(), headers, sentAt);
        return this;
    }
}
