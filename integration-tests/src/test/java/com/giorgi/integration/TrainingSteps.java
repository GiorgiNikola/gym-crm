package com.giorgi.integration;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class TrainingSteps {

    private final ScenarioState state;
    private final GymCrmClient gymCrm;

    public TrainingSteps(ScenarioState state, GymCrmClient gymCrm) {
        this.state = state;
        this.gymCrm = gymCrm;
    }

    @When("the trainee logs a {int} minute training with the trainer on {date}")
    @Given("the trainee logged a {int} minute training with the trainer on {date}")
    public void logsATraining(int minutes, LocalDate date) {
        Account trainee = state.account("trainee");
        state.response(gymCrm.post("/trainings", Map.of(
                "traineeUsername", trainee.username(),
                "trainerUsername", state.account("trainer").username(),
                "trainingName", "Morning flow",
                "trainingDate", date.toString(),
                "trainingDuration", minutes), trainee.token()));
        assertThat(state.response().status()).as(state.response().body()).isEqualTo(200);
    }

    @When("the trainee deletes their profile")
    public void deletesTheirProfile() {
        Account trainee = state.account("trainee");
        ApiResponse response = gymCrm.delete("/trainees/" + trainee.username(), trainee.token());
        assertThat(response.status()).as(response.body()).isEqualTo(200);
    }

    @Then("the training is saved")
    public void theTrainingIsSaved() {
        Account trainee = state.account("trainee");
        ApiResponse trainings = gymCrm.get("/trainees/" + trainee.username() + "/trainings", trainee.token());
        assertThat(trainings.status()).isEqualTo(200);
        assertThat(gymCrm.json(trainings).valueStream()
                .map(training -> training.get("trainerUsername").asString()))
                .containsExactly(state.account("trainer").username());
    }

    @Then("the trainee waited less than {int} seconds")
    public void waitedLessThan(int seconds) {
        assertThat(state.response().took()).isLessThan(Duration.ofSeconds(seconds));
    }
}
