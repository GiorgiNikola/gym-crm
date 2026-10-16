package com.giorgi.gymcrm.controller;

import com.giorgi.gymcrm.facade.GymCrmFacade;
import com.giorgi.gymcrm.model.Trainee;
import com.giorgi.gymcrm.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private Trainee trainee() {
        return Trainee.builder()
                .user(User.builder().username(USERNAME).password(PASSWORD).build())
                .build();
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
}
