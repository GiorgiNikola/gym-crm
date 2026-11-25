package com.giorgi.integration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ScenarioState {

    private final Map<String, Account> accounts = new HashMap<>();
    private final Set<String> stoppedServices = new LinkedHashSet<>();
    private ApiResponse response;
    private String queuedTransactionId;

    public void remember(String role, Account account) {
        accounts.put(role, account);
    }

    public Account account(String role) {
        Account account = accounts.get(role);
        if (account == null) {
            throw new IllegalStateException("no " + role + " in this scenario");
        }
        return account;
    }

    public void stopped(String service) {
        stoppedServices.add(service);
    }

    public void restarted(String service) {
        stoppedServices.remove(service);
    }

    public List<String> stoppedServices() {
        return new ArrayList<>(stoppedServices);
    }

    public ApiResponse response() {
        return response;
    }

    public void response(ApiResponse response) {
        this.response = response;
    }

    public String queuedTransactionId() {
        return queuedTransactionId;
    }

    public void queuedTransactionId(String queuedTransactionId) {
        this.queuedTransactionId = queuedTransactionId;
    }
}
