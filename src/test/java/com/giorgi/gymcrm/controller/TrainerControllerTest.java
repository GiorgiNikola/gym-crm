package com.giorgi.gymcrm.controller;

import com.giorgi.gymcrm.facade.GymCrmFacade;
import com.giorgi.gymcrm.model.Trainee;
import com.giorgi.gymcrm.model.Trainer;
import com.giorgi.gymcrm.model.Training;
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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TrainerControllerTest {

    private static final String USERNAME = "Sarah.Miller";
    private static final String PASSWORD = "bY8lR3nO0q";

    @Mock
    private GymCrmFacade gymCrmFacade;

    private MockMvc mockMvc;

    @BeforeEach
    void buildMockMvc() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TrainerController(gymCrmFacade))
                .setCustomArgumentResolvers(new BasicCredentialsResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private String basic(String username, String password) {
        return "Basic " + Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    private Trainee trainee(String firstName, String lastName) {
        return Trainee.builder()
                .user(User.builder()
                        .username(firstName + "." + lastName)
                        .firstName(firstName)
                        .lastName(lastName)
                        .build())
                .build();
    }

    private Trainer sarahWithTrainees() {
        TrainingType yoga = new TrainingType();
        ReflectionTestUtils.setField(yoga, "name", "YOGA");

        Trainer sarah = Trainer.builder()
                .user(User.builder()
                        .username(USERNAME)
                        .firstName("Sarah")
                        .lastName("Miller")
                        .isActive(true)
                        .build())
                .specialization(yoga)
                .build();
        sarah.setTrainees(new LinkedHashSet<>(List.of(
                trainee("Zoe", "Adams"),
                trainee("Adam", "Brown"))));
        return sarah;
    }

    @Test
    @DisplayName("a registered trainer comes back as 201 with credentials and a location")
    void registersTrainer() throws Exception {
        Trainer trainer = Trainer.builder()
                .user(User.builder().username(USERNAME).password(PASSWORD).build())
                .build();
        when(gymCrmFacade.createTrainerProfile("Sarah", "Miller", "YOGA")).thenReturn(trainer);

        mockMvc.perform(post("/trainers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Sarah\",\"lastName\":\"Miller\",\"specialization\":\"YOGA\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "http://localhost/trainers/Sarah.Miller"))
                .andExpect(jsonPath("$.username").value(USERNAME))
                .andExpect(jsonPath("$.password").value(PASSWORD));
    }

    @Test
    @DisplayName("a registration without a specialization comes back as 400 and names the field")
    void rejectsMissingSpecialization() throws Exception {
        mockMvc.perform(post("/trainers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Sarah\",\"lastName\":\"Miller\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors.specialization").exists());
    }

    @Test
    @DisplayName("an unknown specialization comes back as 400 listing the valid ones")
    void rejectsUnknownSpecialization() throws Exception {
        when(gymCrmFacade.createTrainerProfile("Sarah", "Miller", "PILATES"))
                .thenThrow(new IllegalArgumentException(
                        "Unknown training type: PILATES. Valid values: FITNESS, YOGA, ZUMBA"));

        mockMvc.perform(post("/trainers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Sarah\",\"lastName\":\"Miller\",\"specialization\":\"PILATES\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail")
                        .value("Unknown training type: PILATES. Valid values: FITNESS, YOGA, ZUMBA"));
    }

    @Test
    @DisplayName("a trainer profile comes back with its trainees sorted by username")
    void returnsProfileWithTrainees() throws Exception {
        when(gymCrmFacade.selectTrainerProfile(USERNAME, PASSWORD)).thenReturn(sarahWithTrainees());

        mockMvc.perform(get("/trainers/{username}", USERNAME)
                        .header(HttpHeaders.AUTHORIZATION, basic(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(USERNAME))
                .andExpect(jsonPath("$.specialization").value("YOGA"))
                .andExpect(jsonPath("$.isActive").value(true))
                .andExpect(jsonPath("$.trainees.length()").value(2))
                .andExpect(jsonPath("$.trainees[0].username").value("Adam.Brown"))
                .andExpect(jsonPath("$.trainees[1].username").value("Zoe.Adams"));
    }

    @Test
    @DisplayName("an update comes back as the updated profile")
    void updatesProfile() throws Exception {
        when(gymCrmFacade.updateTrainerProfile(USERNAME, PASSWORD, "Sara", "Millerson", false))
                .thenReturn(sarahWithTrainees());

        mockMvc.perform(put("/trainers/{username}", USERNAME)
                        .header(HttpHeaders.AUTHORIZATION, basic(USERNAME, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Sara\",\"lastName\":\"Millerson\",\"isActive\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(USERNAME))
                .andExpect(jsonPath("$.trainees.length()").value(2));
    }

    @Test
    @DisplayName("a specialization sent in an update body is ignored")
    void ignoresSpecializationInUpdateBody() throws Exception {
        when(gymCrmFacade.updateTrainerProfile(USERNAME, PASSWORD, "Sarah", "Miller", true))
                .thenReturn(sarahWithTrainees());

        mockMvc.perform(put("/trainers/{username}", USERNAME)
                        .header(HttpHeaders.AUTHORIZATION, basic(USERNAME, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Sarah","lastName":"Miller","isActive":true,
                                "specialization":"ZUMBA","username":"somebody.else"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.specialization").value("YOGA"));

        verify(gymCrmFacade).updateTrainerProfile(USERNAME, PASSWORD, "Sarah", "Miller", true);
        verifyNoMoreInteractions(gymCrmFacade);
    }

    @Test
    @DisplayName("an update without isActive comes back as 400 and names the field")
    void rejectsUpdateWithoutActiveFlag() throws Exception {
        mockMvc.perform(put("/trainers/{username}", USERNAME)
                        .header(HttpHeaders.AUTHORIZATION, basic(USERNAME, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Sarah\",\"lastName\":\"Miller\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.isActive").exists());
    }

    @Test
    @DisplayName("reading somebody else's profile comes back as 403")
    void rejectsOtherUsersProfile() throws Exception {
        mockMvc.perform(get("/trainers/{username}", "John.Smith")
                        .header(HttpHeaders.AUTHORIZATION, basic(USERNAME, PASSWORD)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(gymCrmFacade);
    }

    @Test
    @DisplayName("reading a profile without credentials comes back as 401")
    void rejectsProfileWithoutCredentials() throws Exception {
        mockMvc.perform(get("/trainers/{username}", USERNAME))
                .andExpect(status().isUnauthorized());
    }

    private Training training(String name, LocalDate date) {
        TrainingType type = new TrainingType();
        ReflectionTestUtils.setField(type, "name", "YOGA");

        return Training.builder()
                .trainee(trainee("John", "Smith"))
                .name(name)
                .type(type)
                .date(date)
                .duration(90L)
                .build();
    }

    @Test
    @DisplayName("the trainings come back with the trainee and the type")
    void listsTrainings() throws Exception {
        when(gymCrmFacade.getTrainerTrainings(USERNAME, PASSWORD, null, null, null))
                .thenReturn(List.of(training("Morning Yoga", LocalDate.of(2026, 8, 1))));

        mockMvc.perform(get("/trainers/{username}/trainings", USERNAME)
                        .header(HttpHeaders.AUTHORIZATION, basic(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].trainingName").value("Morning Yoga"))
                .andExpect(jsonPath("$[0].trainingDate").value("2026-08-01"))
                .andExpect(jsonPath("$[0].trainingType").value("YOGA"))
                .andExpect(jsonPath("$[0].trainingDuration").value(90))
                .andExpect(jsonPath("$[0].traineeUsername").value("John.Smith"))
                .andExpect(jsonPath("$[0].traineeName").value("John Smith"));
    }

    @Test
    @DisplayName("every training filter reaches the facade, dates included")
    void passesTrainingFiltersToTheFacade() throws Exception {
        when(gymCrmFacade.getTrainerTrainings(USERNAME, PASSWORD,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), "Smith"))
                .thenReturn(List.of());

        mockMvc.perform(get("/trainers/{username}/trainings", USERNAME)
                        .header(HttpHeaders.AUTHORIZATION, basic(USERNAME, PASSWORD))
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-31")
                        .param("traineeName", "Smith"))
                .andExpect(status().isOk());

        verify(gymCrmFacade).getTrainerTrainings(USERNAME, PASSWORD,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), "Smith");
    }

    @Test
    @DisplayName("reading somebody else's trainings comes back as 403")
    void rejectsTrainingsForOtherUser() throws Exception {
        mockMvc.perform(get("/trainers/{username}/trainings", "John.Smith")
                        .header(HttpHeaders.AUTHORIZATION, basic(USERNAME, PASSWORD)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(gymCrmFacade);
    }

    @Test
    @DisplayName("reading trainings without credentials comes back as 401")
    void rejectsTrainingsWithoutCredentials() throws Exception {
        mockMvc.perform(get("/trainers/{username}/trainings", USERNAME))
                .andExpect(status().isUnauthorized());
    }
}
