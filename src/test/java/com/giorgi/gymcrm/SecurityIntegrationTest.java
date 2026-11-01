package com.giorgi.gymcrm;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.giorgi.gymcrm.dto.RegistrationResponse;
import com.giorgi.gymcrm.security.AccessDenialLog;
import com.giorgi.gymcrm.security.JwtProperties;
import com.giorgi.gymcrm.security.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.jdbc.JdbcTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:3000";
    private static final String FOREIGN_ORIGIN = "http://evil.example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${gymcrm.jwt.secret}")
    private String jwtSecret;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void clearRegisteredRows() {
        JdbcTestUtils.deleteFromTables(new JdbcTemplate(dataSource),
                "trainee2trainer", "trainings", "trainees", "trainers", "users");
    }

    private RegistrationResponse registerTrainee(String firstName, String lastName) throws Exception {
        String body = mockMvc.perform(post("/trainees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"" + firstName + "\",\"lastName\":\"" + lastName + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, RegistrationResponse.class);
    }

    private RegistrationResponse registerTrainer(String firstName, String lastName) throws Exception {
        String body = mockMvc.perform(post("/trainers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"" + firstName + "\",\"lastName\":\"" + lastName + "\""
                                + ",\"specialization\":\"YOGA\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, RegistrationResponse.class);
    }

    private String login(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asString();
    }

    private void failLogin(String username) throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"definitely-wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("registration hands out a token that already opens a protected endpoint")
    void registrationTokenWorks() throws Exception {
        RegistrationResponse registered = registerTrainee("Fresh", "Token");

        Assertions.assertNotNull(registered.token());
        mockMvc.perform(get("/trainees/{username}", registered.username())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + registered.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(registered.username()));
    }

    @Test
    @DisplayName("the generated password logs in and the login hands out a working token")
    void loginReturnsAWorkingToken() throws Exception {
        RegistrationResponse registered = registerTrainee("Login", "Works");

        String token = login(registered.username(), registered.password());

        mockMvc.perform(get("/training-types").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    @DisplayName("username generation is untouched: First.Last and then First.Last1")
    void keepsGeneratingTheSameUsernames() throws Exception {
        Assertions.assertEquals("John.Smith", registerTrainee("John", "Smith").username());
        Assertions.assertEquals("John.Smith1", registerTrainee("John", "Smith").username());
    }

    @Test
    @DisplayName("what the database stores is a BCrypt hash, never the password itself")
    void storesOnlyAHash() throws Exception {
        RegistrationResponse registered = registerTrainee("Hash", "Stored");

        String stored = new JdbcTemplate(dataSource).queryForObject(
                "select password from users where username = ?", String.class, registered.username());

        Assertions.assertNotNull(stored);
        Assertions.assertTrue(stored.startsWith("$2a$"), stored);
        Assertions.assertNotEquals(registered.password(), stored);
        Assertions.assertTrue(passwordEncoder.matches(registered.password(), stored));
    }

    @Test
    @DisplayName("a request with no token is a 401 that asks for a bearer one")
    void rejectsRequestsWithoutAToken() throws Exception {
        mockMvc.perform(get("/training-types"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("a token that is not a token is a 401 that says the token was the problem")
    void rejectsGarbageTokens() throws Exception {
        mockMvc.perform(get("/training-types").header(HttpHeaders.AUTHORIZATION, "Bearer nonsense"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\""));
    }

    @Test
    @DisplayName("an expired token is a 401")
    void rejectsExpiredTokens() throws Exception {
        RegistrationResponse registered = registerTrainee("Expired", "Token");
        JwtService inThePast = new JwtService(
                new JwtProperties(jwtSecret, Duration.ofHours(1)),
                Clock.fixed(Instant.now().minus(Duration.ofHours(3)), ZoneOffset.UTC));

        mockMvc.perform(get("/trainees/{username}", registered.username())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + inThePast.issue(registered.username())))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\""));
    }

    @Test
    @DisplayName("a token signed with another secret is a 401")
    void rejectsForeignlySignedTokens() throws Exception {
        RegistrationResponse registered = registerTrainee("Foreign", "Signature");
        JwtService stranger = new JwtService(
                new JwtProperties("a-completely-different-secret-0123456789", Duration.ofHours(1)),
                Clock.systemUTC());

        mockMvc.perform(get("/trainees/{username}", registered.username())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + stranger.issue(registered.username())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("somebody else's profile is a 403, not a 500")
    void rejectsAnotherUsersProfile() throws Exception {
        RegistrationResponse john = registerTrainee("Owner", "One");
        RegistrationResponse mary = registerTrainee("Owner", "Two");

        mockMvc.perform(get("/trainees/{username}", mary.username())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + john.token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("a denial logs one warning with the method, the path and the caller, and no token")
    void logsTheDeniedRequestWithoutTheToken() throws Exception {
        RegistrationResponse john = registerTrainee("Logged", "Denial");
        RegistrationResponse mary = registerTrainee("Other", "Owner");

        Logger denialLogger = (Logger) LoggerFactory.getLogger(AccessDenialLog.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        denialLogger.addAppender(appender);

        try {
            mockMvc.perform(get("/trainees/{username}", mary.username())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + john.token()))
                    .andExpect(status().isForbidden());
        } finally {
            denialLogger.detachAppender(appender);
            appender.stop();
        }

        List<ILoggingEvent> warnings = appender.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .toList();
        Assertions.assertEquals(1, warnings.size(), appender.list.toString());

        String line = warnings.get(0).getFormattedMessage();
        Assertions.assertTrue(line.contains("GET"), line);
        Assertions.assertTrue(line.contains("/trainees/" + mary.username()), line);
        Assertions.assertTrue(line.contains(john.username()), line);
        Assertions.assertFalse(line.contains(john.token()), line);
    }

    @Test
    @DisplayName("logging a training between two other people is a 403")
    void rejectsATrainingBetweenStrangers() throws Exception {
        RegistrationResponse john = registerTrainee("Denied", "Trainee");
        RegistrationResponse sarah = registerTrainer("Denied", "Trainer");
        RegistrationResponse nosy = registerTrainee("Nosy", "Stranger");

        mockMvc.perform(post("/trainings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + nosy.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"traineeUsername\":\"" + john.username() + "\""
                                + ",\"trainerUsername\":\"" + sarah.username() + "\""
                                + ",\"trainingName\":\"Morning Yoga\""
                                + ",\"trainingDate\":\"2026-08-01\",\"trainingDuration\":60}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("the trainee in the body may log their own training")
    void allowsATrainingTheCallerIsPartOf() throws Exception {
        RegistrationResponse john = registerTrainee("Allowed", "Trainee");
        RegistrationResponse sarah = registerTrainer("Allowed", "Trainer");

        mockMvc.perform(post("/trainings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + john.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"traineeUsername\":\"" + john.username() + "\""
                                + ",\"trainerUsername\":\"" + sarah.username() + "\""
                                + ",\"trainingName\":\"Morning Yoga\""
                                + ",\"trainingDate\":\"2026-08-01\",\"trainingDuration\":60}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("three wrong passwords lock the username for five minutes")
    void locksAfterThreeFailures() throws Exception {
        RegistrationResponse registered = registerTrainee("Locked", "Out");

        failLogin(registered.username());
        failLogin(registered.username());
        failLogin(registered.username());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + registered.username() + "\""
                                + ",\"password\":\"" + registered.password() + "\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "300"))
                .andExpect(jsonPath("$.status").value(429));
    }

    @Test
    @DisplayName("an unknown username locks exactly like a real one, so neither answer gives it away")
    void locksUnknownUsernamesTheSameWay() throws Exception {
        failLogin("Ghost.Account");
        failLogin("Ghost.Account");
        failLogin("Ghost.Account");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"Ghost.Account\",\"password\":\"whatever\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("a successful login in between clears the counter")
    void successResetsTheCounter() throws Exception {
        RegistrationResponse registered = registerTrainee("Counter", "Reset");

        failLogin(registered.username());
        failLogin(registered.username());
        login(registered.username(), registered.password());
        failLogin(registered.username());
        failLogin(registered.username());

        Assertions.assertNotNull(login(registered.username(), registered.password()));
    }

    @Test
    @DisplayName("logout answers 204 and the same token stops working straight away")
    void revokesTheTokenOnLogout() throws Exception {
        RegistrationResponse registered = registerTrainee("Logged", "Out");
        String bearer = "Bearer " + registered.token();

        mockMvc.perform(get("/training-types").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        mockMvc.perform(get("/training-types").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\""));
    }

    @Test
    @DisplayName("logging out one token leaves the user's other token alive")
    void revokesOnlyTheTokenUsed() throws Exception {
        RegistrationResponse registered = registerTrainee("Second", "Token");
        String second = login(registered.username(), registered.password());

        mockMvc.perform(post("/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + registered.token()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/training-types").header(HttpHeaders.AUTHORIZATION, "Bearer " + second))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("logout without a token is a 401 problem detail, not a 204")
    void refusesAnonymousLogout() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("logging out an already revoked token is a 401, not a second 204")
    void refusesLogoutWithARevokedToken() throws Exception {
        RegistrationResponse registered = registerTrainee("Twice", "Out");
        String bearer = "Bearer " + registered.token();

        mockMvc.perform(post("/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\""));
    }

    @Test
    @DisplayName("logout with a garbage token is a 401")
    void refusesLogoutWithAGarbageToken() throws Exception {
        mockMvc.perform(post("/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer nonsense"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\""));
    }

    @Test
    @DisplayName("GET /auth/logout is not a logout, so the token survives it")
    void ignoresLogoutOnTheWrongMethod() throws Exception {
        RegistrationResponse registered = registerTrainee("Wrong", "Method");
        String bearer = "Bearer " + registered.token();

        mockMvc.perform(get("/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/training-types").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a password change needs the token and the old password, and the new one then logs in")
    void changesPasswordThroughTheEncoder() throws Exception {
        RegistrationResponse registered = registerTrainee("Password", "Changer");

        mockMvc.perform(put("/users/{username}/password", registered.username())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + registered.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"" + registered.password() + "\""
                                + ",\"newPassword\":\"bY8lR3nO0q\"}"))
                .andExpect(status().isOk());

        Assertions.assertNotNull(login(registered.username(), "bY8lR3nO0q"));
    }

    @Test
    @DisplayName("a password change with the wrong old password is a 401")
    void refusesAPasswordChangeWithTheWrongOldPassword() throws Exception {
        RegistrationResponse registered = registerTrainee("Wrong", "Old");

        mockMvc.perform(put("/users/{username}/password", registered.username())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + registered.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"definitely-wrong\",\"newPassword\":\"bY8lR3nO0q\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a deactivated user can still log in, otherwise nobody could switch back on")
    void letsADeactivatedUserLogIn() throws Exception {
        RegistrationResponse registered = registerTrainee("Switched", "Off");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/users/{username}/status", registered.username())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + registered.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\":false}"))
                .andExpect(status().isOk());

        Assertions.assertNotNull(login(registered.username(), registered.password()));
    }

    @Test
    @DisplayName("a deleted trainee's token no longer finds a profile")
    void invalidatesAProfileByDeletingIt() throws Exception {
        RegistrationResponse registered = registerTrainee("Deleted", "Profile");
        String bearer = "Bearer " + registered.token();

        mockMvc.perform(delete("/trainees/{username}", registered.username())
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk());

        mockMvc.perform(get("/trainees/{username}", registered.username())
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a preflight from an allowed origin passes without a token")
    void allowsPreflightFromAnAllowedOrigin() throws Exception {
        mockMvc.perform(options("/trainees/John.Smith")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN));
    }

    @Test
    @DisplayName("a preflight from any other origin is refused")
    void refusesPreflightFromAForeignOrigin() throws Exception {
        mockMvc.perform(options("/trainees/John.Smith")
                        .header(HttpHeaders.ORIGIN, FOREIGN_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @DisplayName("an allowed origin gets the transaction id and the Location back to read")
    void exposesTheHeadersAClientNeeds() throws Exception {
        mockMvc.perform(post("/trainees")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"John\",\"lastName\":\"Smith\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                        "X-Transaction-Id, Location"));
    }

    @Test
    @DisplayName("the two registrations and the login need no token")
    void keepsRegistrationAndLoginPublic() throws Exception {
        RegistrationResponse trainee = registerTrainee("Public", "Path");
        registerTrainer("Public", "Trainer");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + trainee.username() + "\""
                                + ",\"password\":\"" + trainee.password() + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("the actuator and the api docs need no token either")
    void keepsTheOperationalEndpointsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
