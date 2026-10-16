package com.giorgi.gymcrm;

import com.giorgi.gymcrm.config.RootConfig;
import com.giorgi.gymcrm.config.WebConfig;
import com.giorgi.gymcrm.facade.GymCrmFacade;
import com.giorgi.gymcrm.model.Trainee;
import com.giorgi.gymcrm.model.Trainer;
import com.giorgi.gymcrm.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.jdbc.JdbcTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// No @Transactional on purpose. The request has to run on its own transaction so that the
// response is built after the Hibernate session closes, which is what makes a missing
// join fetch in the profile queries show up as a failure here.
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextHierarchy({
        @ContextConfiguration(classes = RootConfig.class),
        @ContextConfiguration(classes = WebConfig.class)
})
class ProfileFetchIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private GymCrmFacade gymCrmFacade;

    @Autowired
    private DataSource dataSource;

    private MockMvc mockMvc;
    private User trainee;
    private User firstTrainer;
    private User secondTrainer;

    @BeforeEach
    void seedProfiles() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        Trainee seeded = gymCrmFacade.createTraineeProfile(
                "John", "Smith", LocalDate.of(1998, 5, 14), "123 Main St New York");
        trainee = seeded.getUser();

        Trainer adam = gymCrmFacade.createTrainerProfile("Adam", "Brown", "FITNESS");
        Trainer zoe = gymCrmFacade.createTrainerProfile("Zoe", "Adams", "YOGA");
        firstTrainer = adam.getUser();
        secondTrainer = zoe.getUser();

        gymCrmFacade.updateTraineeTrainers(trainee.getUsername(), trainee.getPassword(),
                Set.of(firstTrainer.getUsername(), secondTrainer.getUsername()));
    }

    // These rows are committed, so they have to go before the next test or the next test class.
    // training_types is left alone: data.sql seeds it as reference data.
    @AfterEach
    void clearSeededRows() {
        JdbcTestUtils.deleteFromTables(new JdbcTemplate(dataSource),
                "trainee2trainer", "trainings", "trainees", "trainers", "users");
    }

    private String basic(User user) {
        return "Basic " + Base64.getEncoder()
                .encodeToString((user.getUsername() + ":" + user.getPassword()).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("a trainee profile arrives with both trainers fully filled in")
    void servesTraineeWithTrainers() throws Exception {
        mockMvc.perform(get("/trainees/{username}", trainee.getUsername())
                        .header(HttpHeaders.AUTHORIZATION, basic(trainee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(trainee.getUsername()))
                .andExpect(jsonPath("$.address").value("123 Main St New York"))
                .andExpect(jsonPath("$.trainers.length()").value(2))
                .andExpect(jsonPath("$.trainers[0].username").value(firstTrainer.getUsername()))
                .andExpect(jsonPath("$.trainers[0].firstName").value(firstTrainer.getFirstName()))
                .andExpect(jsonPath("$.trainers[0].specialization").value("FITNESS"))
                .andExpect(jsonPath("$.trainers[1].username").value(secondTrainer.getUsername()))
                .andExpect(jsonPath("$.trainers[1].specialization").value("YOGA"));
    }

    @Test
    @DisplayName("a trainer profile arrives with the trainee listed")
    void servesTrainerWithTrainees() throws Exception {
        mockMvc.perform(get("/trainers/{username}", firstTrainer.getUsername())
                        .header(HttpHeaders.AUTHORIZATION, basic(firstTrainer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.specialization").value("FITNESS"))
                .andExpect(jsonPath("$.trainees.length()").value(1))
                .andExpect(jsonPath("$.trainees[0].username").value(trainee.getUsername()))
                .andExpect(jsonPath("$.trainees[0].firstName").value(trainee.getFirstName()));
    }

    @Test
    @DisplayName("an updated trainee still comes back with its trainers")
    void keepsTrainersAfterUpdate() throws Exception {
        mockMvc.perform(put("/trainees/{username}", trainee.getUsername())
                        .header(HttpHeaders.AUTHORIZATION, basic(trainee))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Johnny","lastName":"Smithson","address":"456 Park Avenue Boston",
                                "isActive":true}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Smithson"))
                .andExpect(jsonPath("$.trainers.length()").value(2))
                .andExpect(jsonPath("$.trainers[0].specialization").value("FITNESS"));
    }

    @Test
    @DisplayName("an updated trainer still comes back with its trainees")
    void keepsTraineesAfterUpdate() throws Exception {
        mockMvc.perform(put("/trainers/{username}", firstTrainer.getUsername())
                        .header(HttpHeaders.AUTHORIZATION, basic(firstTrainer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Adamo\",\"lastName\":\"Brown\",\"isActive\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Adamo"))
                .andExpect(jsonPath("$.specialization").value("FITNESS"))
                .andExpect(jsonPath("$.trainees.length()").value(1))
                .andExpect(jsonPath("$.trainees[0].username").value(trainee.getUsername()));
    }
}
