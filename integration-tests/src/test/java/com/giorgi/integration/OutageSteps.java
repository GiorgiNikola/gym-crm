package com.giorgi.integration;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;

public class OutageSteps {

    private final ScenarioState state;

    public OutageSteps(ScenarioState state) {
        this.state = state;
    }

    @Given("the workload service is down")
    public void theWorkloadServiceIsDown() {
        stop(GymCrmSystem.WORKLOAD_SERVICE);
    }

    @Given("messaging is down")
    public void messagingIsDown() {
        stop(GymCrmSystem.BROKER);
    }

    @When("the workload service is back")
    public void theWorkloadServiceIsBack() {
        startAgain(GymCrmSystem.WORKLOAD_SERVICE);
    }

    @When("the workload service restarts")
    public void theWorkloadServiceRestarts() {
        stop(GymCrmSystem.WORKLOAD_SERVICE);
        startAgain(GymCrmSystem.WORKLOAD_SERVICE);
    }

    private void stop(String service) {
        state.stopped(service);
        GymCrmSystem.get().stop(service);
    }

    private void startAgain(String service) {
        GymCrmSystem.get().startAgain(service);
        state.restarted(service);
    }
}
