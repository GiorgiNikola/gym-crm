package com.giorgi.integration;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import jakarta.jms.JMSException;
import jakarta.jms.Message;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class QueueSteps {

    private final ScenarioState state;
    private final WorkloadQueue queue;

    public QueueSteps(ScenarioState state, WorkloadQueue queue) {
        this.state = state;
        this.queue = queue;
    }

    @When("someone puts a {int} minute training on {date} for the trainer straight on the queue with a forged token")
    public void putsAForgedEvent(int minutes, LocalDate date) {
        Account trainer = state.account("trainer");
        String transactionId = "it-forged-" + UUID.randomUUID();
        String body = """
                {"eventId":"%s","occurredAt":"%s","trainerUsername":"%s","trainerFirstName":"%s",\
                "trainerLastName":"%s","isActive":true,"trainingDate":"%s","trainingDuration":%d,\
                "actionType":"ADD"}""".formatted(UUID.randomUUID(), Instant.now(), trainer.username(),
                trainer.firstName(), trainer.lastName(), date, minutes);
        queue.send(body, Map.of(
                "eventType", "workloadChange",
                "eventVersion", 1,
                "transactionId", transactionId,
                "authorization", ForgedToken.writeToken(),
                "JMSXGroupID", trainer.username()));
        state.queuedTransactionId(transactionId);
    }

    @Then("the event is set aside as {word}")
    public void theEventIsSetAside(String reason) throws JMSException {
        Message deadLetter = queue.awaitDeadLetter(state.queuedTransactionId());
        assertThat(deadLetter.getStringProperty("dlqReason")).isEqualToIgnoringCase(reason);
    }
}
