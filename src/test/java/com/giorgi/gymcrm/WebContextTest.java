package com.giorgi.gymcrm;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.giorgi.gymcrm.config.RootConfig;
import com.giorgi.gymcrm.config.WebConfig;
import com.giorgi.gymcrm.dto.RegistrationResponse;
import com.giorgi.gymcrm.facade.GymCrmFacade;
import com.giorgi.gymcrm.model.Trainee;
import com.giorgi.gymcrm.web.RequestLoggingFilter;
import com.giorgi.gymcrm.web.TransactionIdFilter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextHierarchy({
        @ContextConfiguration(classes = RootConfig.class),
        @ContextConfiguration(classes = WebConfig.class)
})
@Transactional
class WebContextTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private GymCrmFacade gymCrmFacade;

    private MockMvc mockMvc;
    private String username;
    private String password;

    @BeforeEach
    void buildMockMvcAndSeedTrainee() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(new TransactionIdFilter(), new RequestLoggingFilter())
                .build();

        Trainee trainee = gymCrmFacade.createTraineeProfile(
                "John", "Smith", LocalDate.of(1998, 5, 14), "123 Main St New York");
        username = trainee.getUser().getUsername();
        password = trainee.getUser().getPassword();
    }

    private String basic() {
        return basic(username, password);
    }

    private String basic(String user, String secret) {
        return "Basic " + Base64.getEncoder()
                .encodeToString((user + ":" + secret).getBytes(StandardCharsets.UTF_8));
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

        mockMvc.perform(get("/auth/login")
                        .header(HttpHeaders.AUTHORIZATION, basic(registered.username(), registered.password())))
                .andExpect(status().isOk());
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
                .andExpect(jsonPath("$.username").value("Sarah.Miller"));
    }

    @Test
    @DisplayName("a deleted trainee cannot be read back")
    void deletesRegisteredTrainee() throws Exception {
        RegistrationResponse registered = registerTrainee("Mary", "Jones");
        String credentials = basic(registered.username(), registered.password());

        mockMvc.perform(delete("/trainees/{username}", registered.username())
                        .header(HttpHeaders.AUTHORIZATION, credentials))
                .andExpect(status().isOk());

        mockMvc.perform(get("/trainees/{username}", registered.username())
                        .header(HttpHeaders.AUTHORIZATION, credentials))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the generated password never reaches the logs")
    void keepsGeneratedPasswordOutOfTheLogs() throws Exception {
        Logger filterLogger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
        Level originalLevel = filterLogger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        filterLogger.setLevel(Level.DEBUG);
        filterLogger.addAppender(appender);

        try {
            String password = registerTrainee("Mary", "Jones").password();

            Assertions.assertFalse(appender.list.isEmpty());
            for (ILoggingEvent event : appender.list) {
                Assertions.assertFalse(event.getFormattedMessage().contains(password), event.getFormattedMessage());
            }
        } finally {
            filterLogger.detachAppender(appender);
            filterLogger.setLevel(originalLevel);
            appender.stop();
        }
    }

    @Test
    @DisplayName("GET /auth/login accepts the seeded trainee")
    void logsInWithRealCredentials() throws Exception {
        mockMvc.perform(get("/auth/login").header(HttpHeaders.AUTHORIZATION, basic()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /training-types goes through the real wiring to the database")
    void servesTrainingTypes() throws Exception {
        mockMvc.perform(get("/training-types").header(HttpHeaders.AUTHORIZATION, basic()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    @DisplayName("a request without credentials comes back as 401")
    void rejectsRequestWithoutCredentials() throws Exception {
        mockMvc.perform(get("/training-types"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"gym-crm\""));
    }

    @Test
    @DisplayName("Basic with a blank username comes back as 401")
    void rejectsBlankUsername() throws Exception {
        mockMvc.perform(get("/training-types").header(HttpHeaders.AUTHORIZATION, basic("   ", password)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the real validator rejects a status body without isActive")
    void rejectsStatusWithoutFlag() throws Exception {
        mockMvc.perform(patch("/users/{username}/status", username)
                        .header(HttpHeaders.AUTHORIZATION, basic())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors.isActive").exists());
    }

    @Test
    @DisplayName("every response carries a transaction id")
    void tagsResponsesWithATransactionId() throws Exception {
        mockMvc.perform(get("/training-types").header(HttpHeaders.AUTHORIZATION, basic()))
                .andExpect(header().exists(TransactionIdFilter.TRANSACTION_ID_HEADER));
    }

    @Test
    @DisplayName("an unknown path comes back as a problem detail")
    void reportsUnknownPathAsProblem() throws Exception {
        mockMvc.perform(get("/no-such-path"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }
}
