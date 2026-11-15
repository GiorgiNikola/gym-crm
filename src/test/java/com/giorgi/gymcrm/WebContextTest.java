package com.giorgi.gymcrm;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.giorgi.gymcrm.dto.RegistrationResponse;
import com.giorgi.gymcrm.facade.GymCrmFacade;
import com.giorgi.gymcrm.facade.RegisteredProfile;
import com.giorgi.gymcrm.web.RequestLoggingFilter;
import com.giorgi.gymcrm.web.TransactionIdFilter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WebContextTest {

    private static final String THROWING_CONTROLLER =
            "com.giorgi.gymcrm.controller.GlobalExceptionHandlerTest$ThrowingController";

    @Autowired
    private GymCrmFacade gymCrmFacade;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private MockMvc mockMvc;

    private String username;
    private String token;

    @BeforeEach
    void seedTrainee() {
        RegisteredProfile seeded = gymCrmFacade.createTraineeProfile(
                "John", "Smith", LocalDate.of(1998, 5, 14), "123 Main St New York");
        username = seeded.username();
        token = seeded.token();
    }

    private String bearer() {
        return "Bearer " + token;
    }

    private RegistrationResponse registerTrainee(String firstName, String lastName) throws Exception {
        String body = mockMvc.perform(post("/trainees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"" + firstName + "\",\"lastName\":\"" + lastName + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new ObjectMapper().readValue(body, RegistrationResponse.class);
    }

    @Test
    @DisplayName("a trainee registered over http can log in right away")
    void registersTraineeThatCanLogIn() throws Exception {
        RegistrationResponse registered = registerTrainee("Mary", "Jones");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + registered.username() + "\""
                                + ",\"password\":\"" + registered.password() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    @DisplayName("the token handed out at registration already opens a protected endpoint")
    void registrationTokenWorksImmediately() throws Exception {
        RegistrationResponse registered = registerTrainee("Mary", "Jones");

        mockMvc.perform(get("/trainees/{username}", registered.username())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + registered.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(registered.username()));
    }

    @Test
    @DisplayName("the same name twice gets a numbered username the second time")
    void numbersTheSecondUsername() throws Exception {
        Assertions.assertEquals("Mary.Jones", registerTrainee("Mary", "Jones").username());
        Assertions.assertEquals("Mary.Jones1", registerTrainee("Mary", "Jones").username());
    }

    @Test
    @DisplayName("a trainer registers with a specialization that the training types list returns")
    void registersTrainerWithRealSpecialization() throws Exception {
        mockMvc.perform(post("/trainers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Sarah","lastName":"Miller","specialization":"YOGA"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("Sarah.Miller"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    @DisplayName("a deleted trainee cannot be read back")
    void deletesRegisteredTrainee() throws Exception {
        RegistrationResponse registered = registerTrainee("Mary", "Jones");
        String credentials = "Bearer " + registered.token();

        mockMvc.perform(delete("/trainees/{username}", registered.username())
                        .header(HttpHeaders.AUTHORIZATION, credentials))
                .andExpect(status().isOk());

        mockMvc.perform(get("/trainees/{username}", registered.username())
                        .header(HttpHeaders.AUTHORIZATION, credentials))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("neither the generated password nor the token reaches the logs")
    void keepsGeneratedSecretsOutOfTheLogs() throws Exception {
        Logger filterLogger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
        Level originalLevel = filterLogger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        filterLogger.setLevel(Level.DEBUG);
        filterLogger.addAppender(appender);

        try {
            RegistrationResponse registered = registerTrainee("Mary", "Jones");

            Assertions.assertFalse(appender.list.isEmpty());
            for (ILoggingEvent event : appender.list) {
                String line = event.getFormattedMessage();
                Assertions.assertFalse(line.contains(registered.password()), line);
                Assertions.assertFalse(line.contains(registered.token()), line);
            }
        } finally {
            filterLogger.detachAppender(appender);
            filterLogger.setLevel(originalLevel);
            appender.stop();
        }
    }

    @Test
    @DisplayName("GET /training-types goes through the real wiring to the database")
    void servesTrainingTypes() throws Exception {
        mockMvc.perform(get("/training-types").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    @DisplayName("a request without a token comes back as 401 and asks for a bearer one")
    void rejectsRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/training-types"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("a token that is not a token comes back as 401 and says the token was the problem")
    void rejectsGarbageToken() throws Exception {
        mockMvc.perform(get("/training-types").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\""));
    }

    @Test
    @DisplayName("the real validator rejects a status body without isActive")
    void rejectsStatusWithoutFlag() throws Exception {
        mockMvc.perform(patch("/users/{username}/status", username)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors.isActive").exists());
    }

    @Test
    @DisplayName("every response carries a transaction id")
    void tagsResponsesWithATransactionId() throws Exception {
        mockMvc.perform(get("/training-types").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(header().exists(TransactionIdFilter.TRANSACTION_ID_HEADER));
    }

    @Test
    @DisplayName("a 401 from the security chain still carries a transaction id")
    void tagsRejectedResponsesWithATransactionId() throws Exception {
        mockMvc.perform(get("/training-types"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(TransactionIdFilter.TRANSACTION_ID_HEADER));
    }

    @Test
    @DisplayName("an unknown path comes back as a problem detail once a token is presented")
    void reportsUnknownPathAsProblem() throws Exception {
        mockMvc.perform(get("/no-such-path").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("an unknown path without a token is a 401, so the url space stays hidden")
    void hidesUnknownPathsFromAnonymousCallers() throws Exception {
        mockMvc.perform(get("/no-such-path"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the throwing controller the error handler test uses is not a bean here")
    void keepsTheTestOnlyThrowingControllerOutOfTheContext() throws Exception {
        Class<?> throwingController = Class.forName(THROWING_CONTROLLER);

        Assertions.assertTrue(applicationContext.getBeansOfType(throwingController).isEmpty(),
                "a test fixture reached the application context as "
                        + applicationContext.getBeansOfType(throwingController).keySet());
    }

    @Test
    @DisplayName("none of the throwing controller's paths are mapped in the application")
    void leavesTheTestOnlyThrowingControllerPathsUnmapped() throws Exception {
        mockMvc.perform(get("/unexpected").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isNotFound());
    }
}
