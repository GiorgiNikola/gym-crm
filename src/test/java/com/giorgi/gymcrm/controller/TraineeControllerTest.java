package com.giorgi.gymcrm.controller;

import com.giorgi.gymcrm.facade.GymCrmFacade;
import com.giorgi.gymcrm.model.Trainee;
import com.giorgi.gymcrm.model.Trainer;
import com.giorgi.gymcrm.model.TrainingType;
import com.giorgi.gymcrm.model.User;
import com.giorgi.gymcrm.web.BasicCredentialsResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TraineeControllerTest {

    private static final String USERNAME = "John.Smith";
    private static final String PASSWORD = "aX7kQ2mN9p";

    @Mock
    private GymCrmFacade gymCrmFacade;

    private MockMvc mockMvc;

    @BeforeEach
    void buildMockMvc() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TraineeController(gymCrmFacade))
                .setCustomArgumentResolvers(new BasicCredentialsResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private String basic(String username, String password) {
        return "Basic " + Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    private Trainee trainee() {
        return Trainee.builder()
                .user(User.builder().username(USERNAME).password(PASSWORD).build())
                .build();
    }

    private Trainer trainer(String firstName, String lastName, String specialization) {
        TrainingType type = new TrainingType();
        ReflectionTestUtils.setField(type, "name", specialization);
        return Trainer.builder()
                .user(User.builder()
                        .username(firstName + "." + lastName)
                        .firstName(firstName)
                        .lastName(lastName)
                        .build())
                .specialization(type)
                .build();
    }

    private Trainee johnWithTrainers() {
        Trainee john = Trainee.builder()
                .user(User.builder()
                        .username(USERNAME)
                        .firstName("John")
                        .lastName("Smith")
                        .isActive(true)
                        .build())
                .dateOfBirth(LocalDate.of(1998, 5, 14))
                .address("123 Main St New York")
                .build();
        john.setTrainers(new LinkedHashSet<>(List.of(
                trainer("Zoe", "Adams", "ZUMBA"),
                trainer("Adam", "Brown", "FITNESS"))));
        return john;
    }

    @Test
    @DisplayName("a registered trainee comes back as 201 with credentials and a location")
    void registersTrainee() throws Exception {
        when(gymCrmFacade.createTraineeProfile("John", "Smith", LocalDate.of(1998, 5, 14), "123 Main St New York"))
                .thenReturn(trainee());

        mockMvc.perform(post("/trainees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"John","lastName":"Smith",
                                "dateOfBirth":"1998-05-14","address":"123 Main St New York"}"""))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "http://localhost/trainees/John.Smith"))
                .andExpect(jsonPath("$.username").value(USERNAME))
                .andExpect(jsonPath("$.password").value(PASSWORD));
    }

    @Test
    @DisplayName("an ISO date of birth reaches the facade as a LocalDate")
    void bindsIsoDateOfBirth() throws Exception {
        when(gymCrmFacade.createTraineeProfile(any(), any(), any(), any())).thenReturn(trainee());

        mockMvc.perform(post("/trainees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"John\",\"lastName\":\"Smith\",\"dateOfBirth\":\"1998-05-14\"}"))
                .andExpect(status().isCreated());

        verify(gymCrmFacade).createTraineeProfile("John", "Smith", LocalDate.of(1998, 5, 14), null);
    }

    @Test
    @DisplayName("a registration without a first name comes back as 400 and names the field")
    void rejectsMissingFirstName() throws Exception {
        mockMvc.perform(post("/trainees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lastName\":\"Smith\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors.firstName").exists());
    }

    @Test
    @DisplayName("a date of birth in the future comes back as 400")
    void rejectsFutureDateOfBirth() throws Exception {
        mockMvc.perform(post("/trainees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"John\",\"lastName\":\"Smith\",\"dateOfBirth\":\"2999-01-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.dateOfBirth").exists());
    }

    @Test
    @DisplayName("a name longer than the column comes back as 400")
    void rejectsOversizedName() throws Exception {
        mockMvc.perform(post("/trainees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"" + "J".repeat(51) + "\",\"lastName\":\"Smith\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.firstName").exists());
    }

    @Test
    @DisplayName("a trainee profile comes back with its trainers")
    void returnsProfileWithTrainers() throws Exception {
        when(gymCrmFacade.selectTraineeProfile(USERNAME, PASSWORD)).thenReturn(johnWithTrainers());

        mockMvc.perform(get("/trainees/{username}", USERNAME)
                        .header(HttpHeaders.AUTHORIZATION, basic(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(USERNAME))
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.dateOfBirth").value("1998-05-14"))
                .andExpect(jsonPath("$.address").value("123 Main St New York"))
                .andExpect(jsonPath("$.isActive").value(true))
                .andExpect(jsonPath("$.trainers.length()").value(2))
                .andExpect(jsonPath("$.trainers[0].specialization").value("FITNESS"));
    }

    @Test
    @DisplayName("the trainer list is sorted by username")
    void sortsTrainersByUsername() throws Exception {
        when(gymCrmFacade.selectTraineeProfile(USERNAME, PASSWORD)).thenReturn(johnWithTrainers());

        mockMvc.perform(get("/trainees/{username}", USERNAME)
                        .header(HttpHeaders.AUTHORIZATION, basic(USERNAME, PASSWORD)))
                .andExpect(jsonPath("$.trainers[0].username").value("Adam.Brown"))
                .andExpect(jsonPath("$.trainers[1].username").value("Zoe.Adams"));
    }

    @Test
    @DisplayName("an update comes back as the updated profile")
    void updatesProfile() throws Exception {
        when(gymCrmFacade.updateTraineeProfile(USERNAME, PASSWORD, "Johnny", "Smithson",
                LocalDate.of(2000, 1, 1), "456 Park Avenue Boston", false))
                .thenReturn(johnWithTrainers());

        mockMvc.perform(put("/trainees/{username}", USERNAME)
                        .header(HttpHeaders.AUTHORIZATION, basic(USERNAME, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Johnny","lastName":"Smithson","dateOfBirth":"2000-01-01",
                                "address":"456 Park Avenue Boston","isActive":false}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(USERNAME))
                .andExpect(jsonPath("$.trainers.length()").value(2));
    }

    @Test
    @DisplayName("an update without isActive comes back as 400 and names the field")
    void rejectsUpdateWithoutActiveFlag() throws Exception {
        mockMvc.perform(put("/trainees/{username}", USERNAME)
                        .header(HttpHeaders.AUTHORIZATION, basic(USERNAME, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"John\",\"lastName\":\"Smith\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.isActive").exists());
    }

    @Test
    @DisplayName("a delete comes back as 200 with nothing in the body")
    void deletesProfile() throws Exception {
        mockMvc.perform(delete("/trainees/{username}", USERNAME)
                        .header(HttpHeaders.AUTHORIZATION, basic(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        verify(gymCrmFacade).deleteTraineeProfile(USERNAME, PASSWORD);
    }

    @Test
    @DisplayName("reading somebody else's profile comes back as 403")
    void rejectsOtherUsersProfile() throws Exception {
        mockMvc.perform(get("/trainees/{username}", "Sarah.Miller")
                        .header(HttpHeaders.AUTHORIZATION, basic(USERNAME, PASSWORD)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(gymCrmFacade);
    }

    @Test
    @DisplayName("reading a profile without credentials comes back as 401")
    void rejectsProfileWithoutCredentials() throws Exception {
        mockMvc.perform(get("/trainees/{username}", USERNAME))
                .andExpect(status().isUnauthorized());
    }
}
