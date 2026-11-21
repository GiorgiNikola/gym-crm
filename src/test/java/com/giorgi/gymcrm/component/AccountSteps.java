package com.giorgi.gymcrm.component;

import com.giorgi.gymcrm.dto.RegistrationResponse;
import com.giorgi.gymcrm.dto.TokenResponse;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.jdbc.JdbcTestUtils;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

public class AccountSteps {

    private final ScenarioState state;
    private final GymCrmApi api;
    private final JdbcTemplate jdbcTemplate;
    private final Map<String, Consumer<Map<String, Object>>> invalidDetails = Map.of(
            "no first name", details -> details.remove("firstName"),
            "no last name", details -> details.remove("lastName"),
            "a first name longer than 50 characters", details -> details.put("firstName", "A".repeat(51)),
            "a birth date in the future", details -> details.put("dateOfBirth", LocalDate.now().plusDays(1)),
            "no specialization", details -> details.remove("specialization"),
            "an unknown specialization", details -> details.put("specialization", "UNDERWATER_CHESS"));

    public AccountSteps(ScenarioState state, GymCrmApi api, JdbcTemplate jdbcTemplate) {
        this.state = state;
        this.api = api;
        this.jdbcTemplate = jdbcTemplate;
    }

    @When("a new {role} named {word} {word} registers")
    public void aNewUserRegisters(String role, String firstName, String lastName) {
        register(role, role, firstName, lastName);
    }

    @Given("a registered {role} named {word} {word}")
    public void aRegisteredUser(String role, String firstName, String lastName) {
        assertThat(register(role, role, firstName, lastName).status()).isEqualTo(201);
    }

    @Given("another registered {role} named {word} {word}")
    public void anotherRegisteredUser(String role, String firstName, String lastName) {
        assertThat(register("other " + role, role, firstName, lastName).status()).isEqualTo(201);
    }

    @When("a new {role} registers with {}")
    public void aNewUserRegistersWith(String role, String problem) {
        assertThat(invalidDetails).containsKey(problem);
        Map<String, Object> details = details(role, "Nina", "Hart" + uniqueSuffix());
        invalidDetails.get(problem).accept(details);
        state.response(api.post("/" + role + "s", details, null));
    }

    @Then("the {role} receives a username made of their name and a password")
    public void receivesCredentials(String role) {
        assertThat(state.response().status()).isEqualTo(201);
        Account account = state.account(role);
        assertThat(account.username()).isEqualTo(account.firstName() + "." + account.lastName());
        assertThat(account.password()).isNotBlank();
        assertThat(account.token()).isNotBlank();
    }

    @Then("the {role} can log in with those credentials")
    public void canLogIn(String role) {
        Account account = state.account(role);
        logIn(account.username(), account.password());
        assertThat(state.response().status()).isEqualTo(200);
        assertThat(api.read(state.response(), TokenResponse.class).token()).isNotBlank();
    }

    @When("the {role} logs in with a wrong password")
    public void logsInWithWrongPassword(String role) {
        logIn(state.account(role).username(), "not-" + state.account(role).password());
    }

    @Given("the {role} failed to log in {int} times")
    public void failedToLogIn(String role, int times) {
        for (int attempt = 0; attempt < times; attempt++) {
            logsInWithWrongPassword(role);
            assertThat(state.response().status()).isEqualTo(401);
        }
    }

    @When("the {role} logs in with the right password")
    public void logsInWithRightPassword(String role) {
        logIn(state.account(role).username(), state.account(role).password());
    }

    @Then("the request is rejected as {rejection}")
    public void theRequestIsRejected(HttpStatus status) {
        ApiResponse response = state.response();
        assertThat(response.status()).isEqualTo(status.value());
        assertThat(response.contentType()).isNotNull();
        assertThat(response.contentType().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isTrue();
        assertThat(api.tree(response.body()).path("status").asInt()).isEqualTo(status.value());
    }

    @Then("no account is created")
    public void noAccountIsCreated() {
        assertThat(JdbcTestUtils.countRowsInTable(jdbcTemplate, "users")).isZero();
    }

    private ApiResponse register(String alias, String role, String firstName, String lastName) {
        String uniqueLastName = lastName + uniqueSuffix();
        ApiResponse response = api.post("/" + role + "s", details(role, firstName, uniqueLastName), null);
        state.response(response);
        if (response.status() == 201) {
            RegistrationResponse registered = api.read(response, RegistrationResponse.class);
            state.remember(alias, new Account(role, firstName, uniqueLastName, registered.username(),
                    registered.password(), registered.token()));
        }
        return response;
    }

    private void logIn(String username, String password) {
        state.response(api.post("/auth/login", Map.of("username", username, "password", password), null));
    }

    private static Map<String, Object> details(String role, String firstName, String lastName) {
        Map<String, Object> details = new HashMap<>();
        details.put("firstName", firstName);
        details.put("lastName", lastName);
        if (role.equals("trainer")) {
            details.put("specialization", "YOGA");
        } else {
            details.put("dateOfBirth", LocalDate.of(1995, 4, 12));
        }
        return details;
    }

    // Letters only, so the username stays first.last and the login lock of one scenario never meets another.
    private static String uniqueSuffix() {
        StringBuilder suffix = new StringBuilder();
        for (int letter = 0; letter < 6; letter++) {
            suffix.append((char) ('a' + ThreadLocalRandom.current().nextInt(26)));
        }
        return suffix.toString();
    }
}
