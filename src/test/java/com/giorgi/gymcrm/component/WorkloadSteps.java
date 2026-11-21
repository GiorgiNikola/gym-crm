package com.giorgi.gymcrm.component;

import com.giorgi.gymcrm.dto.TrainerWorkloadResponse;
import com.giorgi.gymcrm.web.TransactionIdFilter;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class WorkloadSteps {

    private static final String UNAVAILABLE = "Workload service is temporarily unavailable";

    private final ScenarioState state;
    private final GymCrmApi api;
    private final WorkloadServiceStub workloadService;
    private final Environment environment;

    public WorkloadSteps(ScenarioState state, GymCrmApi api, WorkloadServiceStub workloadService,
                         Environment environment) {
        this.state = state;
        this.api = api;
        this.workloadService = workloadService;
        this.environment = environment;
    }

    @Given("the workload service reports {int} minutes for the trainer in {month}")
    public void theWorkloadServiceReports(int minutes, YearMonth month) {
        workloadService.reports(state.account("trainer").username(), month.getYear(), month.getMonthValue(),
                minutes);
    }

    @Given("the workload service does not know the trainer")
    public void theWorkloadServiceDoesNotKnowTheTrainer() {
        workloadService.doesNotKnow(state.account("trainer").username());
    }

    @Given("the workload service is down")
    public void theWorkloadServiceIsDown() {
        workloadService.isDown();
    }

    @Given("the workload service drops the connection")
    public void theWorkloadServiceDropsTheConnection() {
        workloadService.dropsConnections();
    }

    @Given("the workload service fails with a server error")
    public void theWorkloadServiceFails() {
        workloadService.failsWithServerError();
    }

    @Given("the workload service answers too slowly")
    public void theWorkloadServiceIsTooSlow() {
        Duration readTimeout = environment.getRequiredProperty("gymcrm.workload.read-timeout", Duration.class);
        workloadService.answersAfter(readTimeout.plusSeconds(1));
    }

    @When("the trainer asks for their workload in {month}")
    public void theTrainerAsks(YearMonth month) {
        ask("trainer", month);
    }

    @When("the trainer asks for their workload in {month} {int} times")
    public void theTrainerAsksRepeatedly(YearMonth month, int times) {
        state.markResponses();
        for (int attempt = 0; attempt < times; attempt++) {
            ask("trainer", month);
        }
    }

    @When("the other {role} asks for the trainer's workload in {month}")
    public void someoneElseAsks(String role, YearMonth month) {
        ask("other " + role, month);
    }

    @Then("the trainer sees {int} minutes for {month}")
    public void theTrainerSees(int minutes, YearMonth month) {
        assertThat(state.response().status()).isEqualTo(200);
        assertThat(api.read(state.response(), TrainerWorkloadResponse.class)).isEqualTo(new TrainerWorkloadResponse(
                state.account("trainer").username(), month.getYear(), month.getMonthValue(), minutes));
    }

    @Then("the workload service was asked with gym-crm's read token and the request's transaction id")
    public void askedWithServiceTokenAndTransactionId() {
        List<LoggedRequest> reads = workloadService.received(workloadService.anyRead());
        assertThat(reads).hasSize(1);
        assertThat(reads.getFirst().getHeader(TransactionIdFilter.TRANSACTION_ID_HEADER))
                .isEqualTo(state.response().transactionId());
        Claims token = serviceToken(reads.getFirst().getHeader(HttpHeaders.AUTHORIZATION));
        assertThat(token.getIssuer()).isEqualTo("gym-crm");
        assertThat(token.getAudience()).containsExactly("workload-service");
        assertThat(token.get("scope", String.class)).isEqualTo("workload:read");
    }

    @Then("the trainer is told the workload is temporarily unavailable")
    public void toldTemporarilyUnavailable() {
        assertUnavailable(state.response());
    }

    @Then("every answer says the workload is temporarily unavailable")
    public void everyAnswerIsUnavailable() {
        assertThat(state.responsesSinceMark()).isNotEmpty().allSatisfy(WorkloadSteps.this::assertUnavailable);
    }

    @Then("the workload service was called only {int} times")
    public void calledOnly(int times) {
        workloadService.verifyCalls(times, workloadService.anyRead());
    }

    @Then("the workload service was not called")
    public void notCalled() {
        workloadService.verifyCalls(0, workloadService.anyRead());
    }

    private void assertUnavailable(ApiResponse response) {
        assertThat(response.status()).isEqualTo(503);
        assertThat(api.tree(response.body()).path("detail").asString()).isEqualTo(UNAVAILABLE);
    }

    private void ask(String alias, YearMonth month) {
        Account caller = state.account(alias);
        state.response(api.get("/trainers/%s/workload/%d/%d".formatted(state.account("trainer").username(),
                month.getYear(), month.getMonthValue()), caller.token()));
    }

    private Claims serviceToken(String authorization) {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(environment.getRequiredProperty("gymcrm.workload.jwt-secret")
                        .getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(authorization.substring("Bearer ".length()))
                .getPayload();
    }
}
