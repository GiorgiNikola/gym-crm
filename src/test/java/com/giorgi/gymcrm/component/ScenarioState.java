package com.giorgi.gymcrm.component;

import io.cucumber.spring.ScenarioScope;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ScenarioScope
public class ScenarioState {

    private final Map<String, Account> accounts = new HashMap<>();
    private final List<ApiResponse> responses = new ArrayList<>();
    private int markedAt;

    public void remember(String alias, Account account) {
        accounts.put(alias, account);
    }

    public Account account(String alias) {
        Account account = accounts.get(alias);
        if (account == null) {
            throw new IllegalStateException("no " + alias + " in this scenario");
        }
        return account;
    }

    public void response(ApiResponse response) {
        responses.add(response);
    }

    public ApiResponse response() {
        return responses.getLast();
    }

    public void markResponses() {
        markedAt = responses.size();
    }

    public List<ApiResponse> responsesSinceMark() {
        return List.copyOf(responses.subList(markedAt, responses.size()));
    }
}
