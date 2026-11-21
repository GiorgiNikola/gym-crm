package com.giorgi.workload.component;

import com.giorgi.workload.TestTokens;
import com.giorgi.workload.component.ParameterTypes.MonthTotal;
import com.giorgi.workload.dto.MonthWorkloadResponse;
import com.giorgi.workload.dto.MonthlyWorkloadResponse;
import com.giorgi.workload.dto.TrainerWorkloadResponse;
import com.giorgi.workload.dto.YearWorkloadResponse;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.awaitility.Awaitility;
import org.awaitility.core.ThrowingRunnable;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

public class TrainerWorkloadSteps {

    private final ScenarioState state;
    private final ComponentBroker broker;
    private final WorkloadApi api;

    public TrainerWorkloadSteps(ScenarioState state, ComponentBroker broker, WorkloadApi api) {
        this.state = state;
        this.broker = broker;
        this.api = api;
    }

    @Given("a trainer named {word} {word}")
    public void aTrainerNamed(String firstName, String lastName) {
        state.trainer(firstName, lastName);
    }

    @Given("the trainer is inactive")
    public void theTrainerIsInactive() {
        state.inactive();
    }

    @When("gym-crm asks for the trainer's workload in {month}")
    public void asksForMonth(YearMonth month) {
        state.requestedMonth(month);
        state.response(api.monthly(state.username(), month.getYear(), month.getMonthValue(),
                TestTokens.bearer(TestTokens.READ)));
    }

    @When("gym-crm asks for the trainer's workload summary")
    public void asksForSummary() {
        state.response(api.summary(state.username(), TestTokens.bearer(TestTokens.READ)));
    }

    @Then("the trainer has {int} minutes in {month}")
    public void trainerHasMinutesIn(int minutes, YearMonth month) {
        eventually(() -> {
            asksForMonth(month);
            theMonthlyTotalIs(minutes);
        });
    }

    @Then("the monthly total is {int} minutes")
    public void theMonthlyTotalIs(int minutes) {
        assertThat(state.response().status()).isEqualTo(200);
        YearMonth month = state.requestedMonth();
        assertThat(api.read(state.response(), MonthlyWorkloadResponse.class)).isEqualTo(new MonthlyWorkloadResponse(
                state.username(), month.getYear(), month.getMonthValue(), minutes));
    }

    @Then("the trainer's workload is:")
    public void theTrainersWorkloadIs(List<MonthTotal> months) {
        eventually(() -> assertThat(summary().years()).isEqualTo(years(months)));
    }

    @Then("the trainer is stored as {word}")
    public void theTrainerIsStoredAs(String status) {
        eventually(() -> assertThat(summary().trainerStatus()).isEqualTo(status.equals("active")));
    }

    @Then("the summary shows the trainer as active with these months:")
    public void theSummaryShows(List<MonthTotal> months) {
        assertThat(state.response().status()).isEqualTo(200);
        assertThat(api.read(state.response(), TrainerWorkloadResponse.class)).isEqualTo(new TrainerWorkloadResponse(
                state.username(), state.firstName(), state.lastName(), true, years(months)));
    }

    private void eventually(ThrowingRunnable assertion) {
        broker.awaitProcessed();
        Awaitility.await().pollInSameThread().untilAsserted(assertion);
    }

    private TrainerWorkloadResponse summary() {
        asksForSummary();
        assertThat(state.response().status()).isEqualTo(200);
        TrainerWorkloadResponse summary = api.read(state.response(), TrainerWorkloadResponse.class);
        assertThat(summary.trainerFirstName()).isEqualTo(state.firstName());
        assertThat(summary.trainerLastName()).isEqualTo(state.lastName());
        return summary;
    }

    private static List<YearWorkloadResponse> years(List<MonthTotal> totals) {
        Map<Integer, List<MonthWorkloadResponse>> months = new TreeMap<>();
        for (MonthTotal total : totals) {
            months.computeIfAbsent(total.month().getYear(), year -> new ArrayList<>())
                    .add(new MonthWorkloadResponse(total.month().getMonthValue(), total.minutes()));
        }
        return months.entrySet().stream()
                .map(year -> new YearWorkloadResponse(year.getKey(), year.getValue()))
                .toList();
    }
}
