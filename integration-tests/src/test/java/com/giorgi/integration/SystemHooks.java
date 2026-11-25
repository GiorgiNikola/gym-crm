package com.giorgi.integration;

import io.cucumber.java.After;
import io.cucumber.java.AfterAll;
import io.cucumber.java.BeforeAll;
import org.awaitility.Awaitility;

import java.time.Duration;

public class SystemHooks {

    private final ScenarioState state;

    public SystemHooks(ScenarioState state) {
        this.state = state;
    }

    @BeforeAll
    public static void startTheSystem() {
        Awaitility.setDefaultTimeout(Duration.ofMinutes(2));
        Awaitility.setDefaultPollInterval(Duration.ofSeconds(1));
        Awaitility.pollInSameThread();
        GymCrmSystem.start();
    }

    @AfterAll
    public static void stopTheSystem() {
        GymCrmSystem.stopAll();
    }

    @After
    public void restoreStoppedServices() {
        for (String service : state.stoppedServices()) {
            GymCrmSystem.get().startAgain(service);
        }
    }
}
