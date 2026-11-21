package com.giorgi.workload.messaging;

import ch.qos.logback.classic.Level;
import com.giorgi.workload.LogCapture;
import com.giorgi.workload.TestTokens;
import com.giorgi.workload.logging.TransactionIds;
import com.giorgi.workload.service.WorkloadService;
import jakarta.jms.BytesMessage;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jms.core.JmsMessagingTemplate;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

@SpringBootTest
@ActiveProfiles("test")
class WorkloadEventListenerIntegrationTest {

    private static final String MDC_KEY = TransactionIds.MDC_KEY;

    @Autowired
    private JmsTemplate jmsTemplate;

    @Autowired
    private EmbeddedActiveMQ embeddedBroker;

    @Autowired
    private WorkloadService workloadService;

    private JmsMessagingTemplate messaging;
    private BrokerProbe broker;
    private LogCapture listenerLogs;

    @BeforeEach
    void connect() throws Exception {
        messaging = new JmsMessagingTemplate(jmsTemplate);
        broker = new BrokerProbe(embeddedBroker);
        broker.purge(EmbeddedBrokerTestConfig.DEAD_LETTER_QUEUE);
        listenerLogs = LogCapture.of(WorkloadEventListener.class);
    }

    @AfterEach
    void releaseLogs() {
        listenerLogs.close();
    }

    private void awaitMinutes(String username, long expected) {
        Awaitility.await().ignoreExceptions().untilAsserted(() ->
                Assertions.assertEquals(expected, workloadService.getMonthlyDuration(username, 2026, 8)));
    }

    private Message<?> deadLetter() {
        Message<?> received = messaging.receive(EmbeddedBrokerTestConfig.DEAD_LETTER_QUEUE);
        Assertions.assertNotNull(received, "nothing reached the dead letter queue");
        return received;
    }

    @Test
    @DisplayName("a valid event is applied to the trainer's month")
    void appliesValidEvent() {
        String username = TestEvent.newUsername();
        TestEvent event = TestEvent.forTrainer(username).minutes(75).sendTo(messaging);

        awaitMinutes(username, 75);
        Assertions.assertEquals(1, listenerLogs.messagesTagged(MDC_KEY, event.transactionId()).size());
        String line = listenerLogs.messages(Level.INFO).getLast();
        Assertions.assertTrue(line.startsWith("trainer.workload event " + event.eventId() + " ADD for trainer "
                + username + ": APPLIED in "), line);
        Assertions.assertTrue(line.endsWith(" ms, delivery 1"), line);
    }

    @Test
    @DisplayName("a token sent with the Bearer prefix is accepted too")
    void acceptsBearerPrefix() {
        String username = TestEvent.newUsername();
        TestEvent.forTrainer(username)
                .header(WorkloadEventHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.WRITE))
                .sendTo(messaging);

        awaitMinutes(username, 60);
    }

    @Test
    @DisplayName("the same event id twice is applied once and the repeat is logged as a duplicate")
    void skipsDuplicateEvent() {
        String username = TestEvent.newUsername();
        UUID eventId = UUID.randomUUID();
        TestEvent first = TestEvent.forTrainer(username).eventId(eventId).sendTo(messaging);
        TestEvent repeat = TestEvent.forTrainer(username).eventId(eventId).sendTo(messaging);
        TestEvent.forTrainer(username).minutes(1).sendTo(messaging);

        awaitMinutes(username, 61);
        Assertions.assertTrue(listenerLogs.messagesTagged(MDC_KEY, first.transactionId()).getFirst()
                .contains(": APPLIED in "));
        List<String> repeatLines = listenerLogs.messagesTagged(MDC_KEY, repeat.transactionId());
        Assertions.assertEquals(1, repeatLines.size());
        Assertions.assertTrue(repeatLines.getFirst().contains(": DUPLICATE in "), repeatLines.getFirst());
    }

    static Stream<Arguments> rejectedEvents() {
        return Stream.of(
                Arguments.of("no token", DeadLetterReason.UNAUTHENTICATED, "authorization header is missing",
                        (UnaryOperator<TestEvent>) event -> event.withoutHeader(WorkloadEventHeaders.AUTHORIZATION)),
                Arguments.of("a blank token", DeadLetterReason.UNAUTHENTICATED, "authorization header is missing",
                        (UnaryOperator<TestEvent>) event -> event.header(WorkloadEventHeaders.AUTHORIZATION, "  ")),
                Arguments.of("a token that is not text", DeadLetterReason.UNAUTHENTICATED,
                        "authorization header is missing",
                        (UnaryOperator<TestEvent>) event -> event.header(WorkloadEventHeaders.AUTHORIZATION, 42)),
                Arguments.of("a token already expired when it was sent", DeadLetterReason.UNAUTHENTICATED,
                        "Jwt expired at",
                        (UnaryOperator<TestEvent>) event -> event.header(WorkloadEventHeaders.AUTHORIZATION,
                                TestTokens.token().scope(TestTokens.WRITE)
                                        .issuedAt(Instant.now().minusSeconds(600))
                                        .expiresAt(Instant.now().minusSeconds(120)).sign())),
                Arguments.of("a token signed with another secret", DeadLetterReason.UNAUTHENTICATED,
                        "Invalid signature",
                        (UnaryOperator<TestEvent>) event -> event.header(WorkloadEventHeaders.AUTHORIZATION,
                                TestTokens.token().scope(TestTokens.WRITE)
                                        .secret("another-secret-that-is-long-enough-0123456789").sign())),
                Arguments.of("a token for another audience", DeadLetterReason.UNAUTHENTICATED,
                        "The aud claim is not valid",
                        (UnaryOperator<TestEvent>) event -> event.header(WorkloadEventHeaders.AUTHORIZATION,
                                TestTokens.token().scope(TestTokens.WRITE).audience("gym-crm").sign())),
                Arguments.of("a read-only token", DeadLetterReason.FORBIDDEN,
                        "service token lacks scope workload:write",
                        (UnaryOperator<TestEvent>) event -> event.header(WorkloadEventHeaders.AUTHORIZATION,
                                TestTokens.token().scope(TestTokens.READ).sign())),
                Arguments.of("a body that is not JSON", DeadLetterReason.MALFORMED,
                        "body cannot be read as a workload event",
                        (UnaryOperator<TestEvent>) event -> event.body("{not json")),
                Arguments.of("an unknown type id", DeadLetterReason.MALFORMED, "eventType must be workloadChange",
                        (UnaryOperator<TestEvent>) event -> event.header(WorkloadEventHeaders.TYPE_ID, "trainerChange")),
                Arguments.of("a newer event version", DeadLetterReason.MALFORMED, "eventVersion must be 1",
                        (UnaryOperator<TestEvent>) event -> event.header(WorkloadEventHeaders.EVENT_VERSION, 2)),
                Arguments.of("a field that fails validation", DeadLetterReason.INVALID,
                        "trainingDuration: must be greater than 0",
                        (UnaryOperator<TestEvent>) event -> event.minutes(0)));
    }

    @ParameterizedTest(name = "{0} is dead-lettered as {1}")
    @MethodSource("rejectedEvents")
    @DisplayName("a rejected event lands once in the dead letter queue with its reason and is not redelivered")
    void deadLettersRejectedEvent(String description, DeadLetterReason reason, String detail,
                                  UnaryOperator<TestEvent> change) {
        TestEvent event = change.apply(TestEvent.forTrainer(TestEvent.newUsername())).sendTo(messaging);

        Message<?> received = deadLetter();
        MessageHeaders headers = received.getHeaders();
        Assertions.assertEquals(event.text(), received.getPayload());
        Assertions.assertEquals(reason.name(), headers.get(WorkloadEventHeaders.DLQ_REASON));
        Assertions.assertTrue(String.valueOf(headers.get(WorkloadEventHeaders.DLQ_DETAIL)).contains(detail),
                String.valueOf(headers.get(WorkloadEventHeaders.DLQ_DETAIL)));
        Assertions.assertEquals(EmbeddedBrokerTestConfig.QUEUE, headers.get(WorkloadEventHeaders.ORIGINAL_QUEUE));
        Assertions.assertEquals(event.transactionId(), headers.get(WorkloadEventHeaders.TRANSACTION_ID));
        Assertions.assertFalse(headers.containsKey(WorkloadEventHeaders.AUTHORIZATION));
        Assertions.assertEquals(0, broker.messageCount(EmbeddedBrokerTestConfig.QUEUE));

        List<String> lines = listenerLogs.messagesTagged(MDC_KEY, event.transactionId());
        Assertions.assertEquals(1, lines.size(), lines.toString());
        Assertions.assertTrue(lines.getFirst().contains(": DEAD_LETTERED " + reason + " ("), lines.getFirst());
        Assertions.assertTrue(lines.getFirst().endsWith(" ms, delivery 1"), lines.getFirst());
        Assertions.assertEquals(List.of(), listenerLogs.messages(Level.ERROR));
    }

    @Test
    @DisplayName("a token that was valid when the broker accepted the message is accepted after it expired")
    void appliesTokenThatExpiredWhileQueued() throws Exception {
        String username = TestEvent.newUsername();
        Instant sentAt = Instant.now().minus(Duration.ofHours(1));
        TestEvent event = TestEvent.forTrainer(username)
                .header(WorkloadEventHeaders.AUTHORIZATION, TestTokens.token().scope(TestTokens.WRITE)
                        .issuedAt(sentAt.minusSeconds(1)).expiresAt(sentAt.plusSeconds(300)).sign())
                .sendAt(broker, sentAt);

        awaitMinutes(username, 60);
        Assertions.assertTrue(listenerLogs.messagesTagged(MDC_KEY, event.transactionId()).getFirst()
                .contains(": APPLIED in "));
    }

    static Stream<Arguments> tokensInvalidAtSendTime() {
        Instant sentAt = Instant.now().minus(Duration.ofHours(1));
        return Stream.of(
                Arguments.of("expired before the send", sentAt, "Jwt expired at",
                        TestTokens.token().scope(TestTokens.WRITE).issuedAt(sentAt.minusSeconds(600))
                                .expiresAt(sentAt.minusSeconds(120)).sign()),
                Arguments.of("a message without a timestamp", Instant.EPOCH, "message timestamp is missing",
                        TestTokens.token().scope(TestTokens.WRITE).sign()));
    }

    @ParameterizedTest(name = "{0} is unauthenticated")
    @MethodSource("tokensInvalidAtSendTime")
    @DisplayName("a token that was not valid when the broker accepted the message is dead-lettered")
    void deadLettersTokenInvalidAtSendTime(String description, Instant sentAt, String detail, String token)
            throws Exception {
        TestEvent event = TestEvent.forTrainer(TestEvent.newUsername())
                .header(WorkloadEventHeaders.AUTHORIZATION, token)
                .sendAt(broker, sentAt);

        MessageHeaders headers = deadLetter().getHeaders();
        Assertions.assertEquals(DeadLetterReason.UNAUTHENTICATED.name(), headers.get(WorkloadEventHeaders.DLQ_REASON));
        Assertions.assertTrue(String.valueOf(headers.get(WorkloadEventHeaders.DLQ_DETAIL)).contains(detail),
                String.valueOf(headers.get(WorkloadEventHeaders.DLQ_DETAIL)));
        Assertions.assertEquals(event.transactionId(), headers.get(WorkloadEventHeaders.TRANSACTION_ID));
    }

    @Test
    @DisplayName("an invalid event keeps its event id on the dead letter copy, and the log never shows the token")
    void keepsEventIdOfInvalidEvent() {
        TestEvent event = TestEvent.forTrainer(" ").sendTo(messaging);

        MessageHeaders headers = deadLetter().getHeaders();
        Assertions.assertEquals(event.eventId().toString(), headers.get(WorkloadEventHeaders.EVENT_ID));
        Assertions.assertEquals("trainerUsername: must not be blank", headers.get(WorkloadEventHeaders.DLQ_DETAIL));
        String line = listenerLogs.messagesTagged(MDC_KEY, event.transactionId()).getFirst();
        Assertions.assertTrue(line.startsWith("trainer.workload event " + event.eventId() + " ADD for trainer  : "),
                line);
        Assertions.assertFalse(line.contains("eyJ"), line);
        Assertions.assertFalse(line.contains("Sarah"), line);
    }

    @Test
    @DisplayName("an empty event lists every missing field and has no event id to pass on")
    void listsEveryMissingField() {
        TestEvent event = TestEvent.forTrainer(TestEvent.newUsername()).body("{}").sendTo(messaging);

        MessageHeaders headers = deadLetter().getHeaders();
        Assertions.assertEquals(DeadLetterReason.INVALID.name(), headers.get(WorkloadEventHeaders.DLQ_REASON));
        Assertions.assertEquals("actionType: must not be null; eventId: must not be null; isActive: must not be "
                + "null; occurredAt: must not be null; trainerFirstName: must not be blank; trainerLastName: must "
                + "not be blank; trainerUsername: must not be blank; trainingDate: must not be null; "
                + "trainingDuration: must not be null", headers.get(WorkloadEventHeaders.DLQ_DETAIL));
        Assertions.assertFalse(headers.containsKey(WorkloadEventHeaders.EVENT_ID));
        String line = listenerLogs.messagesTagged(MDC_KEY, event.transactionId()).getFirst();
        Assertions.assertTrue(line.startsWith("trainer.workload event - - for trainer -: DEAD_LETTERED INVALID"),
                line);
    }

    @Test
    @DisplayName("the transaction id from the header tags the listener line and the operation line")
    void tagsLinesWithIncomingTransactionId() {
        String username = TestEvent.newUsername();
        try (LogCapture operations = LogCapture.of(WorkloadService.class)) {
            TestEvent.forTrainer(username).header(WorkloadEventHeaders.TRANSACTION_ID, "gym-crm-msg-0001")
                    .sendTo(messaging);

            awaitMinutes(username, 60);
            Assertions.assertEquals(1, listenerLogs.messagesTagged(MDC_KEY, "gym-crm-msg-0001").size());
            Assertions.assertTrue(operations.messagesTagged(MDC_KEY, "gym-crm-msg-0001").getFirst()
                    .startsWith("Applied ADD of 60 min in 2026-08 for trainer " + username));
        }
    }

    @Test
    @DisplayName("a message without a usable transaction id gets a generated one")
    void generatesMissingTransactionId() {
        String username = TestEvent.newUsername();
        TestEvent.forTrainer(username).header(WorkloadEventHeaders.TRANSACTION_ID, 7).sendTo(messaging);

        awaitMinutes(username, 60);
        String generated = listenerLogs.mdcValues(MDC_KEY).getLast();
        Assertions.assertDoesNotThrow(() -> UUID.fromString(generated));
    }

    private void assertNonTextDeadLettered(TestEvent event, String messageType) {
        Message<?> received = deadLetter();
        MessageHeaders headers = received.getHeaders();
        Assertions.assertEquals("", received.getPayload());
        Assertions.assertEquals(DeadLetterReason.MALFORMED.name(), headers.get(WorkloadEventHeaders.DLQ_REASON));
        Assertions.assertEquals("payload is a " + messageType + ", not text",
                headers.get(WorkloadEventHeaders.DLQ_DETAIL));
        Assertions.assertEquals(event.transactionId(), headers.get(WorkloadEventHeaders.TRANSACTION_ID));
        Assertions.assertEquals(0, broker.messageCount(EmbeddedBrokerTestConfig.QUEUE));
        List<String> lines = listenerLogs.messagesTagged(MDC_KEY, event.transactionId());
        Assertions.assertEquals(1, lines.size(), lines.toString());
        Assertions.assertTrue(lines.getFirst().endsWith(" ms, delivery 1"), lines.getFirst());
    }

    @Test
    @DisplayName("a bytes message is dead-lettered as malformed on its first delivery, without its body")
    void deadLettersBytesMessage() {
        TestEvent event = TestEvent.forTrainer(TestEvent.newUsername());
        jmsTemplate.send(EmbeddedBrokerTestConfig.QUEUE, session -> {
            BytesMessage message = session.createBytesMessage();
            message.writeBytes(event.text().getBytes(StandardCharsets.UTF_8));
            return event.withPropertiesOn(message);
        });

        assertNonTextDeadLettered(event, "BytesMessage");
    }

    @Test
    @DisplayName("an object message is dead-lettered as malformed and its object is never deserialized")
    void deadLettersObjectMessageWithoutDeserializing() {
        Tripwire.DESERIALIZED.set(false);
        TestEvent event = TestEvent.forTrainer(TestEvent.newUsername());
        jmsTemplate.send(EmbeddedBrokerTestConfig.QUEUE,
                session -> event.withPropertiesOn(session.createObjectMessage(new Tripwire())));

        assertNonTextDeadLettered(event, "ObjectMessage");
        Assertions.assertFalse(Tripwire.DESERIALIZED.get());
    }

    static final class Tripwire implements Serializable {

        static final AtomicBoolean DESERIALIZED = new AtomicBoolean();

        @Serial
        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            DESERIALIZED.set(true);
            in.defaultReadObject();
        }
    }

    @Test
    @DisplayName("a JSON null body is malformed, not a crash")
    void deadLettersNullBody() {
        TestEvent.forTrainer(TestEvent.newUsername()).body("null").sendTo(messaging);

        MessageHeaders headers = deadLetter().getHeaders();
        Assertions.assertEquals(DeadLetterReason.MALFORMED.name(), headers.get(WorkloadEventHeaders.DLQ_REASON));
        Assertions.assertEquals("body is empty", headers.get(WorkloadEventHeaders.DLQ_DETAIL));
    }

    @Test
    @DisplayName("an unreadable field is named in the detail without echoing its value")
    void namesUnreadableField() {
        TestEvent.forTrainer(TestEvent.newUsername()).trainingDate("Sarah-Miller").sendTo(messaging);

        MessageHeaders headers = deadLetter().getHeaders();
        Assertions.assertEquals(DeadLetterReason.MALFORMED.name(), headers.get(WorkloadEventHeaders.DLQ_REASON));
        Assertions.assertEquals("trainingDate cannot be read", headers.get(WorkloadEventHeaders.DLQ_DETAIL));
    }
}
