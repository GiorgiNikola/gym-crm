package com.giorgi.integration;

import io.cucumber.java.en.Given;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

public class AccountSteps {

    private final ScenarioState state;
    private final GymCrmClient gymCrm;

    public AccountSteps(ScenarioState state, GymCrmClient gymCrm) {
        this.state = state;
        this.gymCrm = gymCrm;
    }

    @Given("a registered {role} named {word} {word}")
    public void aRegisteredUser(String role, String firstName, String lastName) {
        String uniqueLastName = lastName + uniqueSuffix();
        Map<String, Object> details = new HashMap<>(Map.of("firstName", firstName, "lastName", uniqueLastName));
        if (role.equals("trainer")) {
            details.put("specialization", "YOGA");
        }
        ApiResponse response = gymCrm.post("/" + role + "s", details, null);
        assertThat(response.status()).as(response.body()).isEqualTo(201);
        JsonNode registered = gymCrm.json(response);
        state.remember(role, new Account(firstName, uniqueLastName, registered.get("username").asString(),
                registered.get("token").asString()));
    }

    // Letters only, so every scenario has its own users and the username stays first.last.
    private static String uniqueSuffix() {
        StringBuilder suffix = new StringBuilder();
        for (int letter = 0; letter < 6; letter++) {
            suffix.append((char) ('a' + ThreadLocalRandom.current().nextInt(26)));
        }
        return suffix.toString();
    }
}
