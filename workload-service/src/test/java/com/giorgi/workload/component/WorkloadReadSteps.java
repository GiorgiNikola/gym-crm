package com.giorgi.workload.component;

import com.giorgi.workload.TestTokens;
import com.giorgi.workload.component.ParameterTypes.Training;
import com.giorgi.workload.component.ParameterTypes.WorkloadView;
import com.giorgi.workload.messaging.TestEvent;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class WorkloadReadSteps {

    private final ScenarioState state;
    private final WorkloadApi api;

    public WorkloadReadSteps(ScenarioState state, WorkloadApi api) {
        this.state = state;
        this.api = api;
    }

    @Given("these trainings were recorded for the trainer:")
    public void theseTrainingsWereRecorded(List<Training> trainings) {
        for (Training training : trainings) {
            assertThat(api.record(state, training.date(), training.minutes()).status()).isEqualTo(200);
        }
    }

    @When("gym-crm asks for the {view} of a trainer who never trained")
    public void asksForAnUnknownTrainer(WorkloadView view) {
        ask(view, TestEvent.newUsername(), TestTokens.bearer(TestTokens.READ));
    }

    @When("gym-crm asks for the trainer's {view} without a token")
    public void asksWithoutToken(WorkloadView view) {
        ask(view, state.username(), null);
    }

    @When("gym-crm asks for the trainer's {view} with a token that may only record workloads")
    public void asksWithWriteOnlyToken(WorkloadView view) {
        ask(view, state.username(), TestTokens.bearer(TestTokens.WRITE));
    }

    @When("gym-crm asks for the trainer's workload in month {int} of {int}")
    public void asksForMonthNumber(int month, int year) {
        state.response(api.monthly(state.username(), year, month, TestTokens.bearer(TestTokens.READ)));
    }

    @Then("the request is rejected as {rejection}")
    public void theRequestIsRejected(HttpStatus status) {
        ApiResponse response = state.response();
        assertThat(response.status()).isEqualTo(status.value());
        assertThat(response.contentType()).isNotNull();
        assertThat(response.contentType().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isTrue();
        assertThat(api.tree(response).path("status").asInt()).isEqualTo(status.value());
    }

    private void ask(WorkloadView view, String username, String authorization) {
        state.response(view == WorkloadView.MONTHLY
                ? api.monthly(username, 2026, 8, authorization)
                : api.summary(username, authorization));
    }
}
