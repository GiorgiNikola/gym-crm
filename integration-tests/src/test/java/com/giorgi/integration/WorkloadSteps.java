package com.giorgi.integration;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.awaitility.Awaitility;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

public class WorkloadSteps {

    private final ScenarioState state;
    private final GymCrmClient gymCrm;

    public WorkloadSteps(ScenarioState state, GymCrmClient gymCrm) {
        this.state = state;
        this.gymCrm = gymCrm;
    }

    @When("the trainer asks for their workload in {month}")
    public void asksForTheirWorkload(YearMonth month) {
        state.response(askFor(month));
    }

    @Then("the trainer (still )sees {int} minutes in {month}")
    public void theTrainerSees(int minutes, YearMonth month) {
        Awaitility.await("%s shows %d minutes".formatted(month, minutes)).untilAsserted(() -> {
            ApiResponse response = askFor(month);
            assertThat(response.status()).as(response.body()).isEqualTo(200);
            assertThat(gymCrm.json(response).get("trainingSummaryDuration").asLong()).isEqualTo(minutes);
        });
    }

    @Then("the trainer is told the workload is temporarily unavailable")
    public void toldTemporarilyUnavailable() {
        Awaitility.await("the workload answer is unavailable").untilAsserted(() -> {
            assertThat(state.response().status()).as(state.response().body()).isEqualTo(503);
            assertThat(gymCrm.json(state.response()).get("detail").asString())
                    .isEqualTo("Workload service is temporarily unavailable");
        });
    }

    private ApiResponse askFor(YearMonth month) {
        Account trainer = state.account("trainer");
        return gymCrm.get("/trainers/%s/workload/%d/%d".formatted(trainer.username(), month.getYear(),
                month.getMonthValue()), trainer.token());
    }
}
