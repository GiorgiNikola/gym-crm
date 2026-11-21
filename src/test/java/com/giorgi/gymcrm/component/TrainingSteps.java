package com.giorgi.gymcrm.component;

import com.giorgi.gymcrm.component.ParameterTypes.Training;
import com.giorgi.gymcrm.workload.WorkloadMessageHeaders;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.jms.UncategorizedJmsException;
import org.springframework.jms.core.JmsMessagingTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

public class TrainingSteps {

    private static final LocalDate SOME_DAY = LocalDate.of(2026, 8, 1);

    private final ScenarioState state;
    private final GymCrmApi api;
    private final WorkloadQueue queue;
    private final JmsMessagingTemplate workloadMessagingTemplate;

    public TrainingSteps(ScenarioState state, GymCrmApi api, WorkloadQueue queue,
                         JmsMessagingTemplate workloadMessagingTemplate) {
        this.state = state;
        this.api = api;
        this.queue = queue;
        this.workloadMessagingTemplate = workloadMessagingTemplate;
    }

    @When("the trainee logs a {int} minute training with the trainer on {date}")
    public void logsATraining(int minutes, LocalDate date) {
        logTraining("trainee", state.account("trainer").username(), date, minutes);
    }

    @Given("the trainee logged these trainings with the trainer:")
    public void loggedTrainings(List<Training> trainings) {
        for (Training training : trainings) {
            logTraining("trainee", state.account("trainer").username(), training.date(), training.minutes());
            assertThat(state.response().status()).isEqualTo(200);
        }
        assertThat(queue.receiveAll()).hasSize(trainings.size());
    }

    @When("the trainee logs a training with a trainer who does not exist")
    public void logsATrainingWithAnUnknownTrainer() {
        logTraining("trainee", "Nobody." + UUID.randomUUID(), SOME_DAY, 60);
    }

    @When("the trainee logs a training lasting zero minutes")
    public void logsAZeroMinuteTraining() {
        logTraining("trainee", state.account("trainer").username(), SOME_DAY, 0);
    }

    @When("the trainee logs a {int} minute training with the trainer for the other trainee")
    public void logsATrainingForSomeoneElse(int minutes) {
        state.response(api.post("/trainings", training(state.account("other trainee").username(),
                state.account("trainer").username(), SOME_DAY, minutes), state.account("trainee").token()));
    }

    @When("the trainee deletes their profile")
    public void deletesTheirProfile() {
        Account trainee = state.account("trainee");
        state.response(api.delete("/trainees/" + trainee.username(), trainee.token()));
        assertThat(state.response().status()).isEqualTo(200);
    }

    @Given("the message broker cannot be reached")
    public void theBrokerCannotBeReached() {
        doThrow(new MessagingException("broker unreachable", new UncategorizedJmsException("broker unreachable")))
                .when(workloadMessagingTemplate).convertAndSend(anyString(), any(), anyMap());
    }

    @Then("the training is accepted")
    public void theTrainingIsAccepted() {
        assertThat(state.response().status()).isEqualTo(200);
    }

    @Then("one workload event is published that adds {int} minutes on {date} for the trainer")
    public void oneAddEventIsPublished(int minutes, LocalDate date) {
        List<Message<?>> messages = queue.receiveAll();
        assertThat(messages).hasSize(1);
        Account trainer = state.account("trainer");
        JsonNode body = body(messages.getFirst());
        assertThat(body.get("actionType").asString()).isEqualTo("ADD");
        assertThat(body.get("trainerUsername").asString()).isEqualTo(trainer.username());
        assertThat(body.get("trainerFirstName").asString()).isEqualTo(trainer.firstName());
        assertThat(body.get("trainerLastName").asString()).isEqualTo(trainer.lastName());
        assertThat(body.get("isActive").asBoolean()).isTrue();
        assertThat(body.get("trainingDate").asString()).isEqualTo(date.toString());
        assertThat(body.get("trainingDuration").asInt()).isEqualTo(minutes);
        assertThat(messages.getFirst().getHeaders())
                .containsEntry(WorkloadMessageHeaders.TYPE_ID, WorkloadMessageHeaders.TYPE)
                .containsEntry(WorkloadMessageHeaders.TRANSACTION_ID, state.response().transactionId())
                .containsEntry(WorkloadMessageHeaders.GROUP_ID, trainer.username())
                .containsKey(WorkloadMessageHeaders.AUTHORIZATION);
    }

    @Then("workload events are published that remove:")
    public void removeEventsArePublished(List<Training> trainings) {
        List<JsonNode> bodies = queue.receiveAll().stream().map(this::body).toList();
        assertThat(bodies).allSatisfy(body -> {
            assertThat(body.get("actionType").asString()).isEqualTo("DELETE");
            assertThat(body.get("trainerUsername").asString()).isEqualTo(state.account("trainer").username());
        });
        assertThat(bodies.stream().map(TrainingSteps::training).toList())
                .containsExactlyInAnyOrderElementsOf(trainings);
        assertThat(bodies.stream().map(body -> body.get("eventId").asString()).distinct()).hasSize(bodies.size());
    }

    @Then("no workload event is published")
    public void noWorkloadEventIsPublished() {
        assertThat(queue.pending()).isZero();
    }

    @Then("the trainee's training list shows a {int} minute training on {date}")
    public void theTrainingListShows(int minutes, LocalDate date) {
        Account trainee = state.account("trainee");
        ApiResponse response = api.get("/trainees/" + trainee.username() + "/trainings", trainee.token());
        assertThat(response.status()).isEqualTo(200);
        assertThat(api.tree(response.body()).valueStream()
                .map(training -> training.get("trainingDate").asString() + " " + training.get("trainingDuration")
                        .asLong()))
                .containsExactly(date + " " + minutes);
    }

    private void logTraining(String alias, String trainerUsername, LocalDate date, long minutes) {
        Account trainee = state.account(alias);
        state.response(api.post("/trainings", training(trainee.username(), trainerUsername, date, minutes),
                trainee.token()));
    }

    private static Map<String, Object> training(String traineeUsername, String trainerUsername, LocalDate date,
                                                long minutes) {
        return Map.of("traineeUsername", traineeUsername, "trainerUsername", trainerUsername,
                "trainingName", "Morning flow", "trainingDate", date, "trainingDuration", minutes);
    }

    private JsonNode body(Message<?> message) {
        return api.tree((String) message.getPayload());
    }

    private static Training training(JsonNode body) {
        return new Training(LocalDate.parse(body.get("trainingDate").asString()), body.get("trainingDuration").asLong());
    }
}
