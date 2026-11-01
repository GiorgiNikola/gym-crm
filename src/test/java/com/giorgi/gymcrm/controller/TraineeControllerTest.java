package com.giorgi.gymcrm.controller;

import com.giorgi.gymcrm.exception.ProfileNotFoundException;
import com.giorgi.gymcrm.facade.GymCrmFacade;
import com.giorgi.gymcrm.facade.RegisteredProfile;
import com.giorgi.gymcrm.model.Trainee;
import com.giorgi.gymcrm.model.Trainer;
import com.giorgi.gymcrm.model.Training;
import com.giorgi.gymcrm.model.TrainingType;
import com.giorgi.gymcrm.model.User;
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

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anySet;
import static org.mockito.Mockito.eq;
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
    private static final String TOKEN = "a.signed.token";

    @Mock
    private GymCrmFacade gymCrmFacade;

    private MockMvc mockMvc;

    @BeforeEach
    void buildMockMvc() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TraineeController(gymCrmFacade))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private RegisteredProfile registered() {
        return new RegisteredProfile(USERNAME, PASSWORD, TOKEN);
    }

    private Trainee trainee() {
        return Trainee.builder()
                .user(User.builder().username(USERNAME).build())
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
    @DisplayName("a registered trainee comes back as 201 with credentials, a token and a location")
    void registersTrainee() throws Exception {
        when(gymCrmFacade.createTraineeProfile("John", "Smith", LocalDate.of(1998, 5, 14), "123 Main St New York"))
                .thenReturn(registered());

        mockMvc.perform(post("/trainees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"John","lastName":"Smith",
                                "dateOfBirth":"1998-05-14","address":"123 Main St New York"}"""))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "http://localhost/trainees/John.Smith"))
                .andExpect(jsonPath("$.username").value(USERNAME))
                .andExpect(jsonPath("$.password").value(PASSWORD))
                .andExpect(jsonPath("$.token").value(TOKEN));
    }

    @Test
    @DisplayName("an ISO date of birth reaches the facade as a LocalDate")
    void bindsIsoDateOfBirth() throws Exception {
        when(gymCrmFacade.createTraineeProfile(any(), any(), any(), any())).thenReturn(registered());

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
        when(gymCrmFacade.selectTraineeProfile(USERNAME)).thenReturn(johnWithTrainers());

        mockMvc.perform(get("/trainees/{username}", USERNAME))
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
        when(gymCrmFacade.selectTraineeProfile(USERNAME)).thenReturn(johnWithTrainers());

        mockMvc.perform(get("/trainees/{username}", USERNAME))
                .andExpect(jsonPath("$.trainers[0].username").value("Adam.Brown"))
                .andExpect(jsonPath("$.trainers[1].username").value("Zoe.Adams"));
    }

    @Test
    @DisplayName("an update comes back as the updated profile")
    void updatesProfile() throws Exception {
        when(gymCrmFacade.updateTraineeProfile(USERNAME, "Johnny", "Smithson",
                LocalDate.of(2000, 1, 1), "456 Park Avenue Boston", false))
                .thenReturn(johnWithTrainers());

        mockMvc.perform(put("/trainees/{username}", USERNAME)
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"John\",\"lastName\":\"Smith\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.isActive").exists());
    }

    @Test
    @DisplayName("a delete comes back as 200 with nothing in the body")
    void deletesProfile() throws Exception {
        mockMvc.perform(delete("/trainees/{username}", USERNAME))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        verify(gymCrmFacade).deleteTraineeProfile(USERNAME);
    }

    @Test
    @DisplayName("the unassigned trainers come back with their specialization")
    void listsUnassignedTrainers() throws Exception {
        when(gymCrmFacade.getUnassignedTrainers(USERNAME))
                .thenReturn(List.of(trainer("Adam", "Brown", "FITNESS"), trainer("Zoe", "Adams", "ZUMBA")));

        mockMvc.perform(get("/trainees/{username}/unassigned-trainers", USERNAME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].username").value("Adam.Brown"))
                .andExpect(jsonPath("$[0].firstName").value("Adam"))
                .andExpect(jsonPath("$[0].specialization").value("FITNESS"))
                .andExpect(jsonPath("$[1].username").value("Zoe.Adams"));
    }

    @Test
    @DisplayName("assigning trainers comes back as the sorted list")
    void assignsTrainers() throws Exception {
        when(gymCrmFacade.updateTraineeTrainers(eq(USERNAME), anySet())).thenReturn(johnWithTrainers());

        mockMvc.perform(put("/trainees/{username}/trainers", USERNAME)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trainerUsernames\":[\"Zoe.Adams\",\"Adam.Brown\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].username").value("Adam.Brown"))
                .andExpect(jsonPath("$[1].username").value("Zoe.Adams"));

        verify(gymCrmFacade).updateTraineeTrainers(USERNAME,
                new LinkedHashSet<>(List.of("Zoe.Adams", "Adam.Brown")));
    }

    @Test
    @DisplayName("an empty list reaches the facade as an empty set")
    void clearsTrainers() throws Exception {
        when(gymCrmFacade.updateTraineeTrainers(eq(USERNAME), anySet())).thenReturn(trainee());

        mockMvc.perform(put("/trainees/{username}/trainers", USERNAME)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trainerUsernames\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verify(gymCrmFacade).updateTraineeTrainers(USERNAME, Set.of());
    }

    @Test
    @DisplayName("a repeated username reaches the facade once")
    void deduplicatesTrainerUsernames() throws Exception {
        when(gymCrmFacade.updateTraineeTrainers(eq(USERNAME), anySet())).thenReturn(johnWithTrainers());

        mockMvc.perform(put("/trainees/{username}/trainers", USERNAME)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trainerUsernames\":[\"Adam.Brown\",\"Adam.Brown\"]}"))
                .andExpect(status().isOk());

        verify(gymCrmFacade).updateTraineeTrainers(USERNAME, Set.of("Adam.Brown"));
    }

    @Test
    @DisplayName("a body without the trainer list comes back as 400")
    void rejectsMissingTrainerList() throws Exception {
        mockMvc.perform(put("/trainees/{username}/trainers", USERNAME)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.trainerUsernames").exists());
    }

    @Test
    @DisplayName("a blank entry in the trainer list comes back as 400")
    void rejectsBlankTrainerUsername() throws Exception {
        mockMvc.perform(put("/trainees/{username}/trainers", USERNAME)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trainerUsernames\":[\"Adam.Brown\",\"  \"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());

        verifyNoInteractions(gymCrmFacade);
    }

    @Test
    @DisplayName("an unknown trainer comes back as 404 naming the missing one")
    void rejectsUnknownTrainer() throws Exception {
        when(gymCrmFacade.updateTraineeTrainers(eq(USERNAME), anySet()))
                .thenThrow(new ProfileNotFoundException("Trainers not found: Nobody.Here"));

        mockMvc.perform(put("/trainees/{username}/trainers", USERNAME)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trainerUsernames\":[\"Nobody.Here\"]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Trainers not found: Nobody.Here"));
    }

    private Training training(String name, LocalDate date) {
        TrainingType type = new TrainingType();
        ReflectionTestUtils.setField(type, "name", "YOGA");

        return Training.builder()
                .trainer(Trainer.builder()
                        .user(User.builder()
                                .username("Sarah.Miller")
                                .firstName("Sarah")
                                .lastName("Miller")
                                .build())
                        .build())
                .name(name)
                .type(type)
                .date(date)
                .duration(60L)
                .build();
    }

    @Test
    @DisplayName("the trainings come back with the trainer and the type")
    void listsTrainings() throws Exception {
        when(gymCrmFacade.getTraineeTrainings(USERNAME, null, null, null, null))
                .thenReturn(List.of(training("Morning Yoga", LocalDate.of(2026, 8, 1))));

        mockMvc.perform(get("/trainees/{username}/trainings", USERNAME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].trainingName").value("Morning Yoga"))
                .andExpect(jsonPath("$[0].trainingDate").value("2026-08-01"))
                .andExpect(jsonPath("$[0].trainingType").value("YOGA"))
                .andExpect(jsonPath("$[0].trainingDuration").value(60))
                .andExpect(jsonPath("$[0].trainerUsername").value("Sarah.Miller"))
                .andExpect(jsonPath("$[0].trainerName").value("Sarah Miller"));
    }

    @Test
    @DisplayName("every training filter reaches the facade, dates included")
    void passesTrainingFiltersToTheFacade() throws Exception {
        when(gymCrmFacade.getTraineeTrainings(USERNAME,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), "Miller", "YOGA"))
                .thenReturn(List.of());

        mockMvc.perform(get("/trainees/{username}/trainings", USERNAME)
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-31")
                        .param("trainerName", "Miller")
                        .param("trainingType", "YOGA"))
                .andExpect(status().isOk());

        verify(gymCrmFacade).getTraineeTrainings(USERNAME,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), "Miller", "YOGA");
    }
}
