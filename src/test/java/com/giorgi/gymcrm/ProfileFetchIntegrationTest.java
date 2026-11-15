package com.giorgi.gymcrm;

import com.giorgi.gymcrm.facade.GymCrmFacade;
import com.giorgi.gymcrm.facade.RegisteredProfile;
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
import java.time.LocalDate;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProfileFetchIntegrationTest {

    @Autowired
    private GymCrmFacade gymCrmFacade;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private MockMvc mockMvc;

    private RegisteredProfile trainee;
    private RegisteredProfile firstTrainer;
    private RegisteredProfile secondTrainer;
    private RegisteredProfile thirdTrainer;
    private RegisteredProfile inactiveTrainer;

    @BeforeEach
    void seedProfiles() {
        trainee = gymCrmFacade.createTraineeProfile(
                "John", "Smith", LocalDate.of(1998, 5, 14), "123 Main St New York");

        firstTrainer = gymCrmFacade.createTrainerProfile("Adam", "Brown", "FITNESS");
        secondTrainer = gymCrmFacade.createTrainerProfile("Zoe", "Adams", "YOGA");
        thirdTrainer = gymCrmFacade.createTrainerProfile("Mia", "Clark", "ZUMBA");
        inactiveTrainer = gymCrmFacade.createTrainerProfile("Ian", "Dark", "STRETCHING");

        gymCrmFacade.changeStatus(inactiveTrainer.username(), false);
    }

    private void assignFirstTwoTrainers() {
        gymCrmFacade.updateTraineeTrainers(trainee.username(),
                Set.of(firstTrainer.username(), secondTrainer.username()));
    }

    private String trainerList(String... usernames) {
        return "{\"trainerUsernames\":[\""
                + String.join("\",\"", usernames)
                + "\"]}";
    }

    @AfterEach
    void clearSeededRows() {
        JdbcTestUtils.deleteFromTables(new JdbcTemplate(dataSource),
                "trainee2trainer", "trainings", "trainees", "trainers", "users");
    }

    private String bearer(RegisteredProfile profile) {
        return "Bearer " + profile.token();
    }

    @Test
    @DisplayName("a trainee profile arrives with both trainers fully filled in")
    void servesTraineeWithTrainers() throws Exception {
        assignFirstTwoTrainers();

        mockMvc.perform(get("/trainees/{username}", trainee.username())
                        .header(HttpHeaders.AUTHORIZATION, bearer(trainee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(trainee.username()))
                .andExpect(jsonPath("$.address").value("123 Main St New York"))
                .andExpect(jsonPath("$.trainers.length()").value(2))
                .andExpect(jsonPath("$.trainers[0].username").value(firstTrainer.username()))
                .andExpect(jsonPath("$.trainers[0].firstName").value("Adam"))
                .andExpect(jsonPath("$.trainers[0].specialization").value("FITNESS"))
                .andExpect(jsonPath("$.trainers[1].username").value(secondTrainer.username()))
                .andExpect(jsonPath("$.trainers[1].specialization").value("YOGA"));
    }

    @Test
    @DisplayName("a trainer profile arrives with the trainee listed")
    void servesTrainerWithTrainees() throws Exception {
        assignFirstTwoTrainers();

        mockMvc.perform(get("/trainers/{username}", firstTrainer.username())
                        .header(HttpHeaders.AUTHORIZATION, bearer(firstTrainer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.specialization").value("FITNESS"))
                .andExpect(jsonPath("$.trainees.length()").value(1))
                .andExpect(jsonPath("$.trainees[0].username").value(trainee.username()))
                .andExpect(jsonPath("$.trainees[0].firstName").value("John"));
    }

    @Test
    @DisplayName("an updated trainee still comes back with its trainers")
    void keepsTrainersAfterUpdate() throws Exception {
        assignFirstTwoTrainers();

        mockMvc.perform(put("/trainees/{username}", trainee.username())
                        .header(HttpHeaders.AUTHORIZATION, bearer(trainee))
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

        mockMvc.perform(put("/trainers/{username}", firstTrainer.username())
                        .header(HttpHeaders.AUTHORIZATION, bearer(firstTrainer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Adamo\",\"lastName\":\"Brown\",\"isActive\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Adamo"))
                .andExpect(jsonPath("$.specialization").value("FITNESS"))
                .andExpect(jsonPath("$.trainees.length()").value(1))
                .andExpect(jsonPath("$.trainees[0].username").value(trainee.username()));
    }

    private void logTraining(String name, LocalDate date) throws Exception {
        mockMvc.perform(post("/trainings")
                        .header(HttpHeaders.AUTHORIZATION, bearer(trainee))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"traineeUsername\":\"" + trainee.username() + "\""
                                + ",\"trainerUsername\":\"" + firstTrainer.username() + "\""
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

        mockMvc.perform(get("/trainees/{username}/trainings", trainee.username())
                        .header(HttpHeaders.AUTHORIZATION, bearer(trainee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].trainingName").value("Morning strength"))
                .andExpect(jsonPath("$[0].trainingDate").value("2026-08-01"))
                .andExpect(jsonPath("$[0].trainingType").value("FITNESS"))
                .andExpect(jsonPath("$[0].trainingDuration").value(60))
                .andExpect(jsonPath("$[0].trainerUsername").value(firstTrainer.username()))
                .andExpect(jsonPath("$[0].trainerName").value("Adam Brown"))
                .andExpect(jsonPath("$[1].trainingName").value("Evening strength"));
    }

    @Test
    @DisplayName("the training list can be narrowed by date and by the trainer's last name")
    void filtersLoggedTrainings() throws Exception {
        logTraining("Morning strength", LocalDate.of(2026, 8, 1));
        logTraining("Evening strength", LocalDate.of(2026, 8, 15));

        mockMvc.perform(get("/trainees/{username}/trainings", trainee.username())
                        .header(HttpHeaders.AUTHORIZATION, bearer(trainee))
                        .param("from", "2026-08-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].trainingName").value("Evening strength"));

        mockMvc.perform(get("/trainees/{username}/trainings", trainee.username())
                        .header(HttpHeaders.AUTHORIZATION, bearer(trainee))
                        .param("trainerName", "Brown"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("the trainer sees the trainee on their own training list")
    void listsTrainingsForTheTrainer() throws Exception {
        logTraining("Morning strength", LocalDate.of(2026, 8, 1));

        mockMvc.perform(get("/trainers/{username}/trainings", firstTrainer.username())
                        .header(HttpHeaders.AUTHORIZATION, bearer(firstTrainer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].traineeUsername").value(trainee.username()))
                .andExpect(jsonPath("$[0].traineeName").value("John Smith"))
                .andExpect(jsonPath("$[0].trainingType").value("FITNESS"));
    }

    @Test
    @DisplayName("deleting the trainee takes their trainings off the trainer's list")
    void cascadesTrainingsWhenTheTraineeGoes() throws Exception {
        logTraining("Morning strength", LocalDate.of(2026, 8, 1));

        mockMvc.perform(delete("/trainees/{username}", trainee.username())
                        .header(HttpHeaders.AUTHORIZATION, bearer(trainee)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/trainers/{username}/trainings", firstTrainer.username())
                        .header(HttpHeaders.AUTHORIZATION, bearer(firstTrainer)))
                .andExpect(status().isOk())
                .andExpect(content().string("[]"));
    }

    @Test
    @DisplayName("the unassigned list holds every active trainer and skips the inactive one")
    void listsActiveUnassignedTrainers() throws Exception {
        String body = mockMvc.perform(get("/trainees/{username}/unassigned-trainers", trainee.username())
                        .header(HttpHeaders.AUTHORIZATION, bearer(trainee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].username").value(firstTrainer.username()))
                .andExpect(jsonPath("$[0].specialization").value("FITNESS"))
                .andExpect(jsonPath("$[1].username").value(thirdTrainer.username()))
                .andExpect(jsonPath("$[2].username").value(secondTrainer.username()))
                .andReturn().getResponse().getContentAsString();

        Assertions.assertFalse(body.contains(inactiveTrainer.username()), body);
    }

    @Test
    @DisplayName("assigning two trainers comes back as those two, sorted")
    void assignsTwoTrainers() throws Exception {
        mockMvc.perform(put("/trainees/{username}/trainers", trainee.username())
                        .header(HttpHeaders.AUTHORIZATION, bearer(trainee))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(trainerList(secondTrainer.username(), firstTrainer.username())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].username").value(firstTrainer.username()))
                .andExpect(jsonPath("$[0].specialization").value("FITNESS"))
                .andExpect(jsonPath("$[1].username").value(secondTrainer.username()));
    }

    @Test
    @DisplayName("an assigned trainer drops out of the unassigned list")
    void dropsAssignedTrainersFromTheUnassignedList() throws Exception {
        assignFirstTwoTrainers();

        mockMvc.perform(get("/trainees/{username}/unassigned-trainers", trainee.username())
                        .header(HttpHeaders.AUTHORIZATION, bearer(trainee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].username").value(thirdTrainer.username()));
    }

    @Test
    @DisplayName("the profile shows the trainers assigned over http")
    void showsAssignedTrainersOnTheProfile() throws Exception {
        mockMvc.perform(put("/trainees/{username}/trainers", trainee.username())
                        .header(HttpHeaders.AUTHORIZATION, bearer(trainee))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(trainerList(firstTrainer.username(), secondTrainer.username())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/trainees/{username}", trainee.username())
                        .header(HttpHeaders.AUTHORIZATION, bearer(trainee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trainers.length()").value(2))
                .andExpect(jsonPath("$.trainers[0].username").value(firstTrainer.username()))
                .andExpect(jsonPath("$.trainers[1].username").value(secondTrainer.username()));
    }

    @Test
    @DisplayName("sending the same assignment twice gives the same answer")
    void repeatsTheSameAssignment() throws Exception {
        String body = trainerList(firstTrainer.username(), secondTrainer.username());

        String first = mockMvc.perform(put("/trainees/{username}/trainers", trainee.username())
                        .header(HttpHeaders.AUTHORIZATION, bearer(trainee))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String second = mockMvc.perform(put("/trainees/{username}/trainers", trainee.username())
                        .header(HttpHeaders.AUTHORIZATION, bearer(trainee))
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

        mockMvc.perform(put("/trainees/{username}/trainers", trainee.username())
                        .header(HttpHeaders.AUTHORIZATION, bearer(trainee))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trainerUsernames\":[]}"))
                .andExpect(status().isOk())
                .andExpect(content().string("[]"));

        mockMvc.perform(get("/trainees/{username}", trainee.username())
                        .header(HttpHeaders.AUTHORIZATION, bearer(trainee)))
                .andExpect(jsonPath("$.trainers.length()").value(0));
    }
}
