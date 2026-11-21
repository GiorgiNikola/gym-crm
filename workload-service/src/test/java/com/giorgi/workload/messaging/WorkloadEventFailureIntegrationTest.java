package com.giorgi.workload.messaging;

import ch.qos.logback.classic.Level;
import com.giorgi.workload.LogCapture;
import com.giorgi.workload.TestTokens;
import com.giorgi.workload.config.MessagingConfig;
import com.giorgi.workload.logging.TransactionIds;
import com.giorgi.workload.model.WorkloadChange;
import com.giorgi.workload.persistence.MonthDocument;
import com.giorgi.workload.persistence.TrainerWorkloadDocument;
import com.giorgi.workload.persistence.TrainerWorkloadMongoRepository;
import com.giorgi.workload.persistence.YearDocument;
import com.giorgi.workload.service.WorkloadService;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jms.core.JmsMessagingTemplate;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class WorkloadEventFailureIntegrationTest {

    private static final String MDC_KEY = TransactionIds.MDC_KEY;
    private static final String ORIGINAL_ADDRESS = "_AMQ_ORIG_ADDRESS";

    @MockitoSpyBean
    private WorkloadService workloadService;

    @MockitoSpyBean(name = "deadLetterTemplate")
    private JmsMessagingTemplate deadLetterTemplate;

    @MockitoSpyBean
    private MongoTemplate mongoTemplate;

    @Autowired
    private TrainerWorkloadMongoRepository documents;

    @Autowired
    private JmsTemplate jmsTemplate;

    @Autowired
    private EmbeddedActiveMQ embeddedBroker;

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

    private static WorkloadChange forTrainer(String username) {
        return argThat(change -> change != null && username.equals(change.trainerUsername()));
    }

    private Message<?> deadLetter() {
        Message<?> received = messaging.receive(EmbeddedBrokerTestConfig.DEAD_LETTER_QUEUE);
        Assertions.assertNotNull(received, "nothing reached the dead letter queue");
        return received;
    }

    private void assertFailedAttempts(TestEvent event, int attempts, String error) {
        List<String> lines = listenerLogs.messagesTagged(MDC_KEY, event.transactionId());
        Assertions.assertEquals(attempts, lines.size(), lines.toString());
        for (int attempt = 1; attempt <= attempts; attempt++) {
            String line = lines.get(attempt - 1);
            Assertions.assertTrue(line.contains(": FAILED IllegalStateException: " + error + " in "), line);
            Assertions.assertTrue(line.endsWith(" ms, delivery " + attempt), line);
        }
    }

    @Test
    @DisplayName("a failure the first time is rolled back, redelivered and applied once on the second delivery")
    void redeliversTransientFailure() {
        String username = TestEvent.newUsername();
        doThrow(new IllegalStateException("store busy")).doCallRealMethod()
                .when(workloadService).apply(forTrainer(username), any());

        TestEvent event;
        try (LogCapture container = LogCapture.of(DefaultMessageListenerContainer.class);
             LogCapture errorHandler = LogCapture.of(MessagingConfig.class)) {
            event = TestEvent.forTrainer(username).minutes(45).sendTo(messaging);

            Awaitility.await().ignoreExceptions().untilAsserted(() ->
                    Assertions.assertEquals(45, workloadService.getMonthlyDuration(username, 2026, 8)));
            Assertions.assertEquals(List.of(), container.messages(Level.WARN));
            Assertions.assertEquals(List.of("Listener failure, already logged as FAILED"),
                    errorHandler.messages(Level.DEBUG));
        }
        verify(workloadService, times(2)).apply(forTrainer(username), any());
        Assertions.assertEquals(List.of(event.eventId().toString()),
                documents.findByUsername(username).orElseThrow().getRecentEventIds());

        List<String> lines = listenerLogs.messagesTagged(MDC_KEY, event.transactionId());
        Assertions.assertEquals(2, lines.size(), lines.toString());
        Assertions.assertTrue(lines.get(0).contains(": FAILED IllegalStateException: store busy in "), lines.get(0));
        Assertions.assertTrue(lines.get(0).endsWith("delivery 1"), lines.get(0));
        Assertions.assertTrue(lines.get(1).contains(": APPLIED in "), lines.get(1));
        Assertions.assertTrue(lines.get(1).endsWith("delivery 2"), lines.get(1));
        Assertions.assertEquals(List.of(lines.get(0)), listenerLogs.messages(Level.ERROR));
    }

    @Test
    @DisplayName("a version conflict that outlasted the retries rolls back and the redelivery applies it once")
    void redeliversExhaustedVersionConflict() {
        String username = TestEvent.newUsername();
        doThrow(new OptimisticLockingFailureException("stale version")).doCallRealMethod()
                .when(workloadService).apply(forTrainer(username), any());

        TestEvent event = TestEvent.forTrainer(username).minutes(30).sendTo(messaging);

        Awaitility.await().ignoreExceptions().untilAsserted(() ->
                Assertions.assertEquals(30, workloadService.getMonthlyDuration(username, 2026, 8)));
        verify(workloadService, times(2)).apply(forTrainer(username), any());
        List<String> lines = listenerLogs.messagesTagged(MDC_KEY, event.transactionId());
        Assertions.assertTrue(lines.get(0).contains(": FAILED OptimisticLockingFailureException: stale version in "),
                lines.get(0));
        Assertions.assertTrue(lines.get(1).contains(": APPLIED in "), lines.get(1));
        Assertions.assertTrue(lines.get(1).endsWith("delivery 2"), lines.get(1));
    }

    @Test
    @DisplayName("a save that fails rolls back, and the redelivery applies the event and records its id exactly once")
    void redeliversFailedSaveOnce() {
        String username = TestEvent.newUsername();
        TestEvent created = TestEvent.forTrainer(username).minutes(60).sendTo(messaging);
        Awaitility.await().until(() -> documents.findByUsername(username).isPresent());
        doThrow(new DataAccessResourceFailureException("write concern not satisfied")).doCallRealMethod()
                .when(mongoTemplate).save(argThat(document -> document instanceof TrainerWorkloadDocument workload
                        && username.equals(workload.getUsername())), eq(TrainerWorkloadDocument.COLLECTION));

        TestEvent event = TestEvent.forTrainer(username).minutes(30).sendTo(messaging);

        Awaitility.await().until(() -> documents.findByUsername(username).orElseThrow().getVersion() == 1L);
        TrainerWorkloadDocument stored = documents.findByUsername(username).orElseThrow();
        Assertions.assertEquals(List.of(new YearDocument(2026, List.of(new MonthDocument(8, 90)))), stored.getYears());
        Assertions.assertEquals(List.of(created.eventId().toString(), event.eventId().toString()),
                stored.getRecentEventIds());
        List<String> lines = listenerLogs.messagesTagged(MDC_KEY, event.transactionId());
        Assertions.assertEquals(2, lines.size(), lines.toString());
        Assertions.assertTrue(lines.get(0).contains(": FAILED DataAccessResourceFailureException: "), lines.get(0));
        Assertions.assertTrue(lines.get(1).contains(": APPLIED in "), lines.get(1));
        Assertions.assertTrue(lines.get(1).endsWith("delivery 2"), lines.get(1));
    }

    @Test
    @DisplayName("a failure on every delivery is dead-lettered by the broker after three attempts")
    void deadLettersPermanentFailure() {
        String username = TestEvent.newUsername();
        doThrow(new IllegalStateException("store down")).when(workloadService).apply(forTrainer(username), any());

        TestEvent event = TestEvent.forTrainer(username).sendTo(messaging);

        Message<?> received = deadLetter();
        MessageHeaders headers = received.getHeaders();
        Assertions.assertEquals(event.text(), received.getPayload());
        Assertions.assertEquals(EmbeddedBrokerTestConfig.QUEUE, headers.get(ORIGINAL_ADDRESS));
        Assertions.assertFalse(headers.containsKey(WorkloadEventHeaders.DLQ_REASON));
        verify(workloadService, times(EmbeddedBrokerTestConfig.MAX_DELIVERY_ATTEMPTS)).apply(forTrainer(username), any());
        Assertions.assertTrue(documents.findByUsername(username).isEmpty());
        assertFailedAttempts(event, EmbeddedBrokerTestConfig.MAX_DELIVERY_ATTEMPTS, "store down");
    }

    @Test
    @DisplayName("a dead-lettered event re-driven after its token expired is applied, since it was valid when sent")
    void appliesRedriveAfterTokenExpiry() throws Exception {
        String username = TestEvent.newUsername();
        doThrow(new IllegalStateException("store down")).doThrow(new IllegalStateException("store down"))
                .doThrow(new IllegalStateException("store down")).doCallRealMethod()
                .when(workloadService).apply(forTrainer(username), any());
        Instant sentAt = Instant.now().minus(Duration.ofHours(1));
        TestEvent event = TestEvent.forTrainer(username).minutes(25)
                .header(WorkloadEventHeaders.AUTHORIZATION, TestTokens.token().scope(TestTokens.WRITE)
                        .issuedAt(sentAt).expiresAt(sentAt.plusSeconds(300)).sign())
                .sendAt(broker, sentAt);
        Awaitility.await().until(() -> broker.messageCount(EmbeddedBrokerTestConfig.DEAD_LETTER_QUEUE) == 1);

        Assertions.assertEquals(1, broker.retryAll(EmbeddedBrokerTestConfig.DEAD_LETTER_QUEUE));

        Awaitility.await().ignoreExceptions().untilAsserted(() ->
                Assertions.assertEquals(25, workloadService.getMonthlyDuration(username, 2026, 8)));
        List<String> lines = listenerLogs.messagesTagged(MDC_KEY, event.transactionId());
        Assertions.assertEquals(EmbeddedBrokerTestConfig.MAX_DELIVERY_ATTEMPTS + 1, lines.size(), lines.toString());
        Assertions.assertTrue(lines.getLast().contains(": APPLIED in "), lines.getLast());
        Assertions.assertEquals(0, broker.messageCount(EmbeddedBrokerTestConfig.DEAD_LETTER_QUEUE));
    }

    @Test
    @DisplayName("a failure after the forward rolls back the dead letter copy together with the acknowledgement")
    void rollsBackForwardWithAcknowledgement() {
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("after the forward");
        }).when(deadLetterTemplate).convertAndSend(eq(EmbeddedBrokerTestConfig.DEAD_LETTER_QUEUE), any(), anyMap());

        TestEvent event = TestEvent.forTrainer(TestEvent.newUsername())
                .withoutHeader(WorkloadEventHeaders.AUTHORIZATION)
                .sendTo(messaging);

        MessageHeaders headers = deadLetter().getHeaders();
        Assertions.assertEquals(EmbeddedBrokerTestConfig.QUEUE, headers.get(ORIGINAL_ADDRESS));
        Assertions.assertFalse(headers.containsKey(WorkloadEventHeaders.DLQ_REASON));
        Assertions.assertEquals(0, broker.messageCount(EmbeddedBrokerTestConfig.DEAD_LETTER_QUEUE));
        Assertions.assertEquals(0, broker.messageCount(EmbeddedBrokerTestConfig.QUEUE));
        assertFailedAttempts(event, EmbeddedBrokerTestConfig.MAX_DELIVERY_ATTEMPTS, "after the forward");
    }
}
