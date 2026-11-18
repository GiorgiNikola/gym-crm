package com.giorgi.gymcrm;

import ch.qos.logback.classic.Level;
import com.giorgi.gymcrm.dto.RegistrationResponse;
import com.giorgi.gymcrm.service.TraineeService;
import com.giorgi.gymcrm.service.TrainingService;
import com.giorgi.gymcrm.web.TransactionIdFilter;
import com.giorgi.gymcrm.workload.WorkloadMessage;
import com.giorgi.gymcrm.workload.WorkloadPublisher;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jms.UncategorizedJmsException;
import org.springframework.jms.core.JmsMessagingTemplate;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.support.JmsHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.jdbc.JdbcTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WorkloadPublishingIntegrationTest {

    private static final String QUEUE = "trainer.workload";
    private static final String WORKLOAD_SECRET = "test-only-workload-secret-0123456789abcdef";
    private static final LocalDate AUGUST_FIRST = LocalDate.of(2026, 8, 1);
    private static final LocalDate SEPTEMBER_TENTH = LocalDate.of(2026, 9, 10);
    private static final Set<String> VARIABLE_BODY_FIELDS = Set.of("eventId", "occurredAt");
    private static final Set<String> VARIABLE_HEADERS = Set.of("authorization", "transactionId");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private TrainingService trainingService;

    @Autowired
    private TraineeService traineeService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EmbeddedActiveMQ broker;

    @Autowired
    private JmsTemplate jmsTemplate;

    @MockitoSpyBean
    private JmsMessagingTemplate workloadMessagingTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JmsMessagingTemplate receiver;

    @BeforeEach
    void emptyTheQueue() throws Exception {
        broker.getActiveMQServer().locateQueue(SimpleString.of(QUEUE)).deleteAllReferences();
        receiver = new JmsMessagingTemplate(jmsTemplate);
    }

    @AfterEach
    void clearRegisteredRows() {
        JdbcTestUtils.deleteFromTables(new JdbcTemplate(dataSource),
                "trainee2trainer", "trainings", "trainees", "trainers", "users");
    }

    private long queued() {
        return broker.getActiveMQServer().locateQueue(SimpleString.of(QUEUE)).getMessageCount();
    }

    private Message<?> receive() {
        Message<?> message = receiver.receive(QUEUE);
        Assertions.assertNotNull(message, "nothing was published to " + QUEUE);
        return message;
    }

    private JsonNode body(Message<?> message) {
        return objectMapper.readTree((String) message.getPayload());
    }

    private JsonNode sample(String name) throws IOException {
        return objectMapper.readTree(new ClassPathResource("contract/" + name)
                .getContentAsString(StandardCharsets.UTF_8));
    }

    private Claims claims(String token) {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(WORKLOAD_SECRET.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private RegistrationResponse register(String path, String body) throws Exception {
        String response = mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(response, RegistrationResponse.class);
    }

    private RegistrationResponse registerTrainee() throws Exception {
        return register("/trainees", "{\"firstName\":\"John\",\"lastName\":\"Smith\"}");
    }

    private RegistrationResponse registerTrainer() throws Exception {
        return register("/trainers", "{\"firstName\":\"Sarah\",\"lastName\":\"Miller\",\"specialization\":\"YOGA\"}");
    }

    private void postTraining(RegistrationResponse trainee, RegistrationResponse trainer, String transactionId)
            throws Exception {
        mockMvc.perform(post("/trainings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + trainee.token())
                        .header(TransactionIdFilter.TRANSACTION_ID_HEADER, transactionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"traineeUsername\":\"" + trainee.username() + "\",\"trainerUsername\":\""
                                + trainer.username() + "\",\"trainingName\":\"Morning flow\","
                                + "\"trainingDate\":\"2026-08-01\",\"trainingDuration\":60}"))
                .andExpect(status().isOk());
    }

    private static MessagingException brokerFailure(String cause) {
        return new MessagingException(cause, new UncategorizedJmsException(cause));
    }

    private void failEverySend() {
        doThrow(brokerFailure("broker unreachable"))
                .when(workloadMessagingTemplate).convertAndSend(anyString(), any(), anyMap());
    }

    @Test
    @DisplayName("adding a training publishes one ADD whose body and headers match the contract sample")
    void addMatchesTheContract() throws Exception {
        RegistrationResponse trainee = registerTrainee();
        RegistrationResponse trainer = registerTrainer();

        postTraining(trainee, trainer, "contract-check-0001");

        Message<?> message = receive();
        ObjectNode published = (ObjectNode) body(message);
        ObjectNode expected = (ObjectNode) sample("workload-change-body.json");
        Assertions.assertEquals(Set.copyOf(expected.propertyNames()), Set.copyOf(published.propertyNames()));
        Assertions.assertDoesNotThrow(() -> UUID.fromString(published.get("eventId").asString()));
        Assertions.assertDoesNotThrow(() -> Instant.parse(published.get("occurredAt").asString()));
        Assertions.assertEquals(expected.without(VARIABLE_BODY_FIELDS), published.without(VARIABLE_BODY_FIELDS));

        JsonNode expectedHeaders = sample("workload-change-headers.json");
        for (String header : expectedHeaders.propertyNames()) {
            Object value = message.getHeaders().get(header);
            Assertions.assertNotNull(value, "missing header " + header);
            if (!VARIABLE_HEADERS.contains(header)) {
                Assertions.assertEquals(expectedHeaders.get(header).asString(), String.valueOf(value), header);
            }
        }
        Assertions.assertInstanceOf(Integer.class, message.getHeaders().get("eventVersion"));
        Assertions.assertEquals(2, message.getHeaders().get(JmsHeaders.DELIVERY_MODE), "not persistent");
        Assertions.assertEquals(0, queued());
    }

    @Test
    @DisplayName("the message token validates with WORKLOAD_JWT_SECRET and may only write")
    void tokenOnlyWrites() throws Exception {
        trainingService.addTraining(registerTrainee().username(), registerTrainer().username(), "Morning flow",
                AUGUST_FIRST, 60L);

        String token = (String) receive().getHeaders().get("authorization");

        Assertions.assertFalse(token.startsWith("Bearer"), token);
        Claims claims = claims(token);
        Assertions.assertEquals("workload:write", claims.get("scope", String.class));
        Assertions.assertEquals("gym-crm", claims.getIssuer());
        Assertions.assertEquals(Set.of("workload-service"), claims.getAudience());
    }

    @Test
    @DisplayName("the transactionId header is the request's transaction id")
    void carriesTheRequestTransactionId() throws Exception {
        RegistrationResponse trainee = registerTrainee();
        RegistrationResponse trainer = registerTrainer();

        postTraining(trainee, trainer, "demo-add-training-0001");

        Assertions.assertEquals("demo-add-training-0001", receive().getHeaders().get("transactionId"));
    }

    @Test
    @DisplayName("a published event is logged once at INFO with queue, event id, action, trainer and attempts")
    void logsThePublishedEvent() throws Exception {
        RegistrationResponse trainee = registerTrainee();
        RegistrationResponse trainer = registerTrainer();

        try (LogCapture logs = LogCapture.of(WorkloadPublisher.class)) {
            postTraining(trainee, trainer, "demo-info-0001");

            String eventId = body(receive()).get("eventId").asString();
            List<String> info = logs.messages(Level.INFO);
            Assertions.assertEquals(1, info.size(), info.toString());
            Assertions.assertTrue(info.getFirst().matches("Published workload event: transactionId=demo-info-0001 "
                    + "queue=trainer.workload eventId=" + eventId + " action=ADD trainer=Sarah.Miller attempts=1 "
                    + "durationMs=\\d+"), info.getFirst());
        }
    }

    @Test
    @DisplayName("deleting a trainee publishes one DELETE per training, each with its own event id")
    void traineeDeletePublishesADeletePerTraining() throws Exception {
        RegistrationResponse trainee = registerTrainee();
        RegistrationResponse trainer = registerTrainer();
        trainingService.addTraining(trainee.username(), trainer.username(), "Morning flow", AUGUST_FIRST, 60L);
        trainingService.addTraining(trainee.username(), trainer.username(), "Evening flow", SEPTEMBER_TENTH, 45L);
        receive();
        receive();

        mockMvc.perform(delete("/trainees/" + trainee.username())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + trainee.token()))
                .andExpect(status().isOk());

        JsonNode first = body(receive());
        JsonNode second = body(receive());
        Assertions.assertEquals(0, queued());
        Assertions.assertEquals(List.of("DELETE:2026-08-01:60", "DELETE:2026-09-10:45"), List.of(first, second)
                .stream()
                .map(node -> node.get("actionType").asString() + ":" + node.get("trainingDate").asString() + ":"
                        + node.get("trainingDuration").asInt())
                .sorted()
                .toList());
        Assertions.assertNotEquals(first.get("eventId"), second.get("eventId"));
    }

    @Test
    @DisplayName("deleting a trainee without trainings publishes nothing")
    void traineeWithoutTrainingsPublishesNothing() throws Exception {
        traineeService.deleteByUsername(registerTrainee().username());

        Assertions.assertEquals(0, queued());
    }

    @Test
    @DisplayName("a training whose transaction rolls back is never published")
    void rollbackPublishesNothing() throws Exception {
        RegistrationResponse trainee = registerTrainee();
        RegistrationResponse trainer = registerTrainer();

        transactionTemplate.executeWithoutResult(status -> {
            trainingService.addTraining(trainee.username(), trainer.username(), "Morning flow", AUGUST_FIRST, 60L);
            status.setRollbackOnly();
        });

        Assertions.assertEquals(0, queued());
        Assertions.assertEquals(0, JdbcTestUtils.countRowsInTable(new JdbcTemplate(dataSource), "trainings"));
    }

    @Test
    @DisplayName("a send that fails once is retried, published once with the same event id, and warned about")
    void retriesAFailedSend() throws Exception {
        RegistrationResponse trainee = registerTrainee();
        RegistrationResponse trainer = registerTrainer();
        doThrow(brokerFailure("broker hiccup")).doCallRealMethod()
                .when(workloadMessagingTemplate).convertAndSend(anyString(), any(), anyMap());

        try (LogCapture logs = LogCapture.of(WorkloadPublisher.class)) {
            postTraining(trainee, trainer, "demo-retry-0001");

            String eventId = body(receive()).get("eventId").asString();
            Assertions.assertEquals(0, queued());
            ArgumentCaptor<WorkloadMessage> attempts = ArgumentCaptor.forClass(WorkloadMessage.class);
            verify(workloadMessagingTemplate, times(2)).convertAndSend(eq(QUEUE), attempts.capture(), anyMap());
            Assertions.assertEquals(List.of(eventId, eventId),
                    attempts.getAllValues().stream().map(attempt -> attempt.eventId().toString()).toList());
            Assertions.assertEquals(List.of("Workload event send failed: transactionId=demo-retry-0001 eventId="
                    + eventId + " attempt=1 cause=broker hiccup"), logs.messages(Level.WARN));
            Assertions.assertTrue(logs.messages(Level.INFO).getFirst().contains("attempts=2"));
        }
    }

    @Test
    @DisplayName("exhausted retries log one ERROR without the token and the request still succeeds")
    void exhaustedRetriesStillSucceed() throws Exception {
        RegistrationResponse trainee = registerTrainee();
        RegistrationResponse trainer = registerTrainer();
        failEverySend();

        try (LogCapture logs = LogCapture.of(WorkloadPublisher.class)) {
            postTraining(trainee, trainer, "demo-down-0001");

            List<String> errors = logs.messages(Level.ERROR);
            Assertions.assertEquals(1, errors.size(), errors.toString());
            Assertions.assertTrue(errors.getFirst().matches("Workload event not published: "
                    + "transactionId=demo-down-0001 queue=trainer.workload eventId=[0-9a-f-]{36} action=ADD "
                    + "trainer=Sarah.Miller attempts=4 durationMs=\\d+ cause=broker unreachable"), errors.getFirst());
            Assertions.assertEquals(4, logs.messages(Level.WARN).size());
            Assertions.assertTrue(logs.messages(Level.INFO).isEmpty());
            Assertions.assertFalse((String.join("\n", logs.messages(Level.WARN)) + errors.getFirst()).contains("eyJ"),
                    "a token reached the log");
        }
        Assertions.assertEquals(1, JdbcTestUtils.countRowsInTable(new JdbcTemplate(dataSource), "trainings"));
        Assertions.assertEquals(0, queued());
    }

    @Test
    @DisplayName("a trainee delete still succeeds while the broker cannot be reached")
    void traineeDeleteSucceedsWhileBrokerIsDown() throws Exception {
        RegistrationResponse trainee = registerTrainee();
        RegistrationResponse trainer = registerTrainer();
        trainingService.addTraining(trainee.username(), trainer.username(), "Morning flow", AUGUST_FIRST, 60L);
        receive();
        failEverySend();

        mockMvc.perform(delete("/trainees/" + trainee.username())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + trainee.token()))
                .andExpect(status().isOk());

        Assertions.assertEquals(0, JdbcTestUtils.countRowsInTable(new JdbcTemplate(dataSource), "trainees"));
    }

    @Test
    @DisplayName("the publisher uses the message converter's eventType mapping, not a Java class name")
    void typeIdIsLogical() throws Exception {
        trainingService.addTraining(registerTrainee().username(), registerTrainer().username(), "Morning flow",
                AUGUST_FIRST, 60L);

        Map<String, Object> headers = receive().getHeaders();

        Assertions.assertEquals("workloadChange", headers.get("eventType"));
        Assertions.assertFalse(headers.values().stream().map(String::valueOf)
                .anyMatch(value -> value.contains("com.giorgi")), headers.toString());
    }
}
