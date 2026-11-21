package com.giorgi.workload.component;

import com.giorgi.workload.TestTokens;
import com.giorgi.workload.messaging.DeadLetterReason;
import com.giorgi.workload.messaging.TestEvent;
import com.giorgi.workload.messaging.WorkloadEventHeaders;
import com.giorgi.workload.persistence.TrainerWorkloadDocument;
import com.giorgi.workload.persistence.TrainerWorkloadMongoRepository;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.awaitility.Awaitility;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.messaging.Message;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class WorkloadEventSteps {

    private final ScenarioState state;
    private final ComponentBroker broker;
    private final TrainerWorkloadMongoRepository documents;
    private final MongoTemplate mongoTemplate;
    private final List<String> saveAttempts = new CopyOnWriteArrayList<>();
    private final Map<String, Function<String, TestEvent>> rejectedEvents = Map.of(
            "without a service token", username -> valid(username)
                    .withoutHeader(WorkloadEventHeaders.AUTHORIZATION),
            "with an expired service token", username -> valid(username)
                    .header(WorkloadEventHeaders.AUTHORIZATION, expiredToken()),
            "with a token that may only read workloads", username -> valid(username)
                    .header(WorkloadEventHeaders.AUTHORIZATION, TestTokens.token().scope(TestTokens.READ).sign()),
            "with an unreadable body", username -> valid(username).body("a training happened"),
            "of an unknown event type", username -> valid(username)
                    .header(WorkloadEventHeaders.TYPE_ID, "trainerRenamed"),
            "in an unsupported event version", username -> valid(username)
                    .header(WorkloadEventHeaders.EVENT_VERSION, 2),
            "with a blank trainer username", username -> valid(" "),
            "with a training date in month 13", username -> valid(username).trainingDate("2026-13-05"),
            "with a negative training duration", username -> valid(username).minutes(-30));

    public WorkloadEventSteps(ScenarioState state, ComponentBroker broker,
                              TrainerWorkloadMongoRepository documents, MongoTemplate mongoTemplate) {
        this.state = state;
        this.broker = broker;
        this.documents = documents;
        this.mongoTemplate = mongoTemplate;
    }

    @Given("a {int} minute training on {date} was added for the trainer")
    public void aTrainingWasAdded(int minutes, LocalDate date) {
        aTrainingIsAdded(minutes, date);
        broker.awaitProcessed();
    }

    @When("a {int} minute training on {date} is added for the trainer")
    public void aTrainingIsAdded(int minutes, LocalDate date) {
        send(state.event("ADD", minutes, date));
    }

    @When("a {int} minute training on {date} is cancelled for the trainer")
    public void aTrainingIsCancelled(int minutes, LocalDate date) {
        send(state.event("DELETE", minutes, date));
    }

    @When("the same {int} minute training on {date} is delivered twice")
    public void theSameTrainingIsDeliveredTwice(int minutes, LocalDate date) {
        TestEvent event = state.event("ADD", minutes, date);
        send(event);
        send(event);
    }

    @When("gym-crm sends a training event {}")
    public void gymCrmSendsARejectedEvent(String problem) {
        assertThat(rejectedEvents).containsKey(problem);
        send(rejectedEvents.get(problem).apply(state.username()));
    }

    @Given("the workload storage fails on the next save for the trainer")
    public void theStorageFailsOnce() {
        List<String> attempts = saveAttempts;
        clearInvocations(mongoTemplate);
        doAnswer(invocation -> {
            if (attempts.isEmpty()) {
                attempts.add("failed");
                throw new DataAccessResourceFailureException("storage briefly unavailable");
            }
            Object saved = invocation.callRealMethod();
            attempts.add("saved");
            return saved;
        }).when(mongoTemplate).save(forTrainer(state.username()), eq(TrainerWorkloadDocument.COLLECTION));
    }

    @Then("the event is dead-lettered as {deadLetterReason}")
    public void theEventIsDeadLettered(DeadLetterReason reason) {
        Message<?> deadLetter = broker.receiveDeadLetter();
        assertThat(deadLetter).as("dead letter").isNotNull();
        assertThat(deadLetter.getPayload()).isEqualTo(state.lastEvent().text());
        assertThat(deadLetter.getHeaders())
                .containsEntry(WorkloadEventHeaders.DLQ_REASON, reason.name())
                .containsEntry(WorkloadEventHeaders.TRANSACTION_ID, state.lastEvent().transactionId())
                .containsKey(WorkloadEventHeaders.DLQ_DETAIL);
    }

    @Then("no trainer workload is stored")
    public void noTrainerWorkloadIsStored() {
        broker.awaitProcessed();
        assertThat(documents.count()).isZero();
    }

    @Then("the storage saved the training on the second attempt")
    public void savedOnSecondAttempt() {
        Awaitility.await().pollInSameThread().untilAsserted(() -> assertThat(saveAttempts)
                .containsExactly("failed", "saved"));
        verify(mongoTemplate, times(2)).save(forTrainer(state.username()), eq(TrainerWorkloadDocument.COLLECTION));
    }

    private static TrainerWorkloadDocument forTrainer(String username) {
        return argThat(document -> document != null && username.equals(document.getUsername()));
    }

    private TestEvent valid(String username) {
        return state.eventFor(username).trainingDate("2026-08-04");
    }

    private void send(TestEvent event) {
        state.lastEvent(broker.send(event));
    }

    private static String expiredToken() {
        Instant issuedAt = Instant.now().minus(1, ChronoUnit.HOURS);
        return TestTokens.token().scope(TestTokens.WRITE)
                .issuedAt(issuedAt).expiresAt(issuedAt.plus(30, ChronoUnit.MINUTES)).sign();
    }
}
