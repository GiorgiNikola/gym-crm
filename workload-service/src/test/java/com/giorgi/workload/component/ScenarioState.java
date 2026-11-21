package com.giorgi.workload.component;

import com.giorgi.workload.messaging.TestEvent;
import io.cucumber.spring.ScenarioScope;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

@ScenarioScope
public class ScenarioState {

    private String firstName;
    private String lastName;
    private String username;
    private boolean active = true;
    private TestEvent lastEvent;
    private YearMonth requestedMonth;
    private ApiResponse response;

    public void trainer(String firstName, String lastName) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.username = firstName + "." + lastName + "." + UUID.randomUUID().toString().substring(0, 8);
    }

    public TestEvent event(String actionType, int minutes, LocalDate trainingDate) {
        return eventFor(username).action(actionType).minutes(minutes).trainingDate(trainingDate.toString());
    }

    public TestEvent eventFor(String trainerUsername) {
        return TestEvent.forTrainer(trainerUsername).names(firstName, lastName).active(active);
    }

    public String firstName() {
        return firstName;
    }

    public String lastName() {
        return lastName;
    }

    public String username() {
        return username;
    }

    public void inactive() {
        active = false;
    }

    public TestEvent lastEvent() {
        return lastEvent;
    }

    public void lastEvent(TestEvent lastEvent) {
        this.lastEvent = lastEvent;
    }

    public YearMonth requestedMonth() {
        return requestedMonth;
    }

    public void requestedMonth(YearMonth requestedMonth) {
        this.requestedMonth = requestedMonth;
    }

    public ApiResponse response() {
        return response;
    }

    public void response(ApiResponse response) {
        this.response = response;
    }
}
