package com.giorgi.gymcrm;

import com.giorgi.gymcrm.facade.GymCrmFacade;
import com.giorgi.gymcrm.model.Trainee;
import com.giorgi.gymcrm.model.Trainer;
import com.giorgi.gymcrm.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.jdbc.JdbcTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// No @Transactional on purpose. The request has to run on its own transaction so that the
// response is built after the Hibernate session closes, which is what makes a missing
// join fetch in the profile queries show up as a failure here. That also relies on
// spring.jpa.open-in-view being false: with it on, the session would stay open for rendering.
@SpringBootTest
@AutoConfigureMockMvc
class ProfileFetchIntegrationTest {

    @Autowired
    private GymCrmFacade gymCrmFacade;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private MockMvc mockMvc;

    private User trainee;
    private User firstTrainer;
    private User secondTrainer;
    private User thirdTrainer;
    private User inactiveTrainer;

    @BeforeEach
    void seedProfiles() {
        Trainee seeded = gymCrmFacade.createTraineeProfile(
                "John", "Smith", LocalDate.of(1998, 5, 14), "123 Main St New York");
        trainee = seeded.getUser();

        firstTrainer = gymCrmFacade.createTrainerProfile("Adam", "Brown", "FITNESS").getUser();
        secondTrainer = gymCrmFacade.createTrainerProfile("Zoe", "Adams", "YOGA").getUser();
        thirdTrainer = gymCrmFacade.createTrainerProfile("Mia", "Clark", "ZUMBA").getUser();
        inactiveTrainer = gymCrmFacade.createTrainerProfile("Ian", "Dark", "STRETCHING").getUser();

        gymCrmFacade.changeStatus(inactiveTrainer.getUsername(), inactiveTrainer.getPassword(), false);
    }

    private void assignFirstTwoTrainers() {
        gymCrmFacade.updateTraineeTrainers(trainee.getUsername(), trainee.getPassword(),
                Set.of(firstTrainer.getUsername(), secondTrainer.getUsername()));
    }

    private String trainerList(String... usernames) {
        return "{\"trainerUsernames\":[\""
                + String.join("\",\"", usernames)
                + "\"]}";
    }

    // These rows are committed, so they have to go before the next test or the next test class.
    // training_types is left alone: the V2 migration seeds it as reference data.
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
        assignFirstTwoTrainers();

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
        assignFirstTwoTrainers();

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
        assignFirstTwoTrainers();

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
        assignFirstTwoTrainers();

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

    private void logTraining(String name, LocalDate date) throws Exception {
        mockMvc.perform(post("/trainings")
                        .header(HttpHeaders.AUTHORIZATION, basic(trainee))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"traineeUsername\":\"" + trainee.getUsername() + "\""
                                + ",\"trainerUsername\":\"" + firstTrainer.getUsername() + "\""
                                + ",\"trainingName\":\"" + name + "\""
                                + ",\"trainingDate\":\"" + date + "\""
                                + ",\"trainingDuration\":60}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("logged trainings come back in date order with the trainer's own type")
    void listsLoggedTrainings() throws Exception {
        logTraining("Evening strength", LocalDate.of(2026, 8, 15));
        logTraining("Morning strength", LocalDate.of(2026, 8, 1));

        mockMvc.perform(get("/trainees/{username}/trainings", trainee.getUsername())
                        .header(HttpHeaders.AUTHORIZATION, basic(trainee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].trainingName").value("Morning strength"))
                .andExpect(jsonPath("$[0].trainingDate").value("2026-08-01"))
                .andExpect(jsonPath("$[0].trainingType").value("FITNESS"))
                .andExpect(jsonPath("$[0].trainingDuration").value(60))
                .andExpect(jsonPath("$[0].trainerUsername").value(firstTrainer.getUsername()))
                .andExpect(jsonPath("$[0].trainerName").value("Adam Brown"))
                .andExpect(jsonPath("$[1].trainingName").value("Evening strength"));
    }

    @Test
    @DisplayName("the training list can be narrowed by date and by the trainer's last name")
    void filtersLoggedTrainings() throws Exception {
        logTraining("Morning strength", LocalDate.of(2026, 8, 1));
        logTraining("Evening strength", LocalDate.of(2026, 8, 15));

        mockMvc.perform(get("/trainees/{username}/trainings", trainee.getUsername())
                        .header(HttpHeaders.AUTHORIZATION, basic(trainee))
                        .param("from", "2026-08-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].trainingName").value("Evening strength"));

        mockMvc.perform(get("/trainees/{username}/trainings", trainee.getUsername())
                        .header(HttpHeaders.AUTHORIZATION, basic(trainee))
                        .param("trainerName", "Brown"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("the trainer sees the trainee on their own training list")
    void listsTrainingsForTheTrainer() throws Exception {
        logTraining("Morning strength", LocalDate.of(2026, 8, 1));

        mockMvc.perform(get("/trainers/{username}/trainings", firstTrainer.getUsername())
                        .header(HttpHeaders.AUTHORIZATION, basic(firstTrainer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].traineeUsername").value(trainee.getUsername()))
                .andExpect(jsonPath("$[0].traineeName").value("John Smith"))
                .andExpect(jsonPath("$[0].trainingType").value("FITNESS"));
    }

    @Test
    @DisplayName("deleting the trainee takes their trainings off the trainer's list")
    void cascadesTrainingsWhenTheTraineeGoes() throws Exception {
        logTraining("Morning strength", LocalDate.of(2026, 8, 1));

        mockMvc.perform(delete("/trainees/{username}", trainee.getUsername())
                        .header(HttpHeaders.AUTHORIZATION, basic(trainee)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/trainers/{username}/trainings", firstTrainer.getUsername())
                        .header(HttpHeaders.AUTHORIZATION, basic(firstTrainer)))
                .andExpect(status().isOk())
                .andExpect(content().string("[]"));
    }

    @Test
    @DisplayName("the unassigned list holds every active trainer and skips the inactive one")
    void listsActiveUnassignedTrainers() throws Exception {
        String body = mockMvc.perform(get("/trainees/{username}/unassigned-trainers", trainee.getUsername())
                        .header(HttpHeaders.AUTHORIZATION, basic(trainee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].username").value(firstTrainer.getUsername()))
                .andExpect(jsonPath("$[0].specialization").value("FITNESS"))
                .andExpect(jsonPath("$[1].username").value(thirdTrainer.getUsername()))
                .andExpect(jsonPath("$[2].username").value(secondTrainer.getUsername()))
                .andReturn().getResponse().getContentAsString();

        Assertions.assertFalse(body.contains(inactiveTrainer.getUsername()), body);
    }

    @Test
    @DisplayName("assigning two trainers comes back as those two, sorted")
    void assignsTwoTrainers() throws Exception {
        mockMvc.perform(put("/trainees/{username}/trainers", trainee.getUsername())
                        .header(HttpHeaders.AUTHORIZATION, basic(trainee))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(trainerList(secondTrainer.getUsername(), firstTrainer.getUsername())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].username").value(firstTrainer.getUsername()))
                .andExpect(jsonPath("$[0].specialization").value("FITNESS"))
                .andExpect(jsonPath("$[1].username").value(secondTrainer.getUsername()));
    }

    @Test
    @DisplayName("an assigned trainer drops out of the unassigned list")
    void dropsAssignedTrainersFromTheUnassignedList() throws Exception {
        assignFirstTwoTrainers();

        mockMvc.perform(get("/trainees/{username}/unassigned-trainers", trainee.getUsername())
                        .header(HttpHeaders.AUTHORIZATION, basic(trainee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].username").value(thirdTrainer.getUsername()));
    }

    @Test
    @DisplayName("the profile shows the trainers assigned over http")
    void showsAssignedTrainersOnTheProfile() throws Exception {
        mockMvc.perform(put("/trainees/{username}/trainers", trainee.getUsername())
                        .header(HttpHeaders.AUTHORIZATION, basic(trainee))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(trainerList(firstTrainer.getUsername(), secondTrainer.getUsername())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/trainees/{username}", trainee.getUsername())
                        .header(HttpHeaders.AUTHORIZATION, basic(trainee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trainers.length()").value(2))
                .andExpect(jsonPath("$.trainers[0].username").value(firstTrainer.getUsername()))
                .andExpect(jsonPath("$.trainers[1].username").value(secondTrainer.getUsername()));
    }

    @Test
    @DisplayName("sending the same assignment twice gives the same answer")
    void repeatsTheSameAssignment() throws Exception {
        String body = trainerList(firstTrainer.getUsername(), secondTrainer.getUsername());

        String first = mockMvc.perform(put("/trainees/{username}/trainers", trainee.getUsername())
                        .header(HttpHeaders.AUTHORIZATION, basic(trainee))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String second = mockMvc.perform(put("/trainees/{username}/trainers", trainee.getUsername())
                        .header(HttpHeaders.AUTHORIZATION, basic(trainee))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Assertions.assertEquals(first, second);
    }

    @Test
    @DisplayName("an empty list takes every trainer off the trainee")
    void clearsTrainersWithAnEmptyList() throws Exception {
        assignFirstTwoTrainers();

        mockMvc.perform(put("/trainees/{username}/trainers", trainee.getUsername())
                        .header(HttpHeaders.AUTHORIZATION, basic(trainee))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trainerUsernames\":[]}"))
                .andExpect(status().isOk())
                .andExpect(content().string("[]"));

        mockMvc.perform(get("/trainees/{username}", trainee.getUsername())
                        .header(HttpHeaders.AUTHORIZATION, basic(trainee)))
                .andExpect(jsonPath("$.trainers.length()").value(0));
    }
}
