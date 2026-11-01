package com.giorgi.gymcrm.controller;

import com.giorgi.gymcrm.exception.ProfileNotFoundException;
import com.giorgi.gymcrm.facade.GymCrmFacade;
import com.giorgi.gymcrm.model.Training;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TrainingControllerTest {

    @Mock
    private GymCrmFacade gymCrmFacade;

    private MockMvc mockMvc;

    @BeforeEach
    void buildMockMvc() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TrainingController(gymCrmFacade))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private String body(String trainee, String trainer, String name, String date, String duration) {
        return "{\"traineeUsername\":" + trainee
                + ",\"trainerUsername\":" + trainer
                + ",\"trainingName\":" + name
                + ",\"trainingDate\":" + date
                + ",\"trainingDuration\":" + duration + "}";
    }

    private String validBody() {
        return body("\"John.Smith\"", "\"Sarah.Miller\"", "\"Morning Yoga\"", "\"2026-08-01\"", "60");
    }

    @Test
    @DisplayName("a logged training comes back as 200 with nothing in the body")
    void addsTraining() throws Exception {
        when(gymCrmFacade.addTraining("John.Smith", "Sarah.Miller",
                "Morning Yoga", LocalDate.of(2026, 8, 1), 60L))
                .thenReturn(Training.builder().id(1L).build());

        mockMvc.perform(post("/trainings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        verify(gymCrmFacade).addTraining("John.Smith", "Sarah.Miller",
                "Morning Yoga", LocalDate.of(2026, 8, 1), 60L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"\"", "\"   \"", "null"})
    @DisplayName("a missing trainee username comes back as 400")
    void rejectsBlankTraineeUsername(String traineeUsername) throws Exception {
        mockMvc.perform(post("/trainings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(traineeUsername, "\"Sarah.Miller\"", "\"Morning Yoga\"",
                                "\"2026-08-01\"", "60")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.traineeUsername").exists());

        verifyNoInteractions(gymCrmFacade);
    }

    @Test
    @DisplayName("a blank trainer username comes back as 400")
    void rejectsBlankTrainerUsername() throws Exception {
        mockMvc.perform(post("/trainings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("\"John.Smith\"", "\"  \"", "\"Morning Yoga\"", "\"2026-08-01\"", "60")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.trainerUsername").exists());
    }

    @Test
    @DisplayName("a blank training name comes back as 400")
    void rejectsBlankTrainingName() throws Exception {
        mockMvc.perform(post("/trainings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("\"John.Smith\"", "\"Sarah.Miller\"", "\"\"", "\"2026-08-01\"", "60")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.trainingName").exists());
    }

    @Test
    @DisplayName("a missing training date comes back as 400")
    void rejectsMissingDate() throws Exception {
        mockMvc.perform(post("/trainings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("\"John.Smith\"", "\"Sarah.Miller\"", "\"Morning Yoga\"", "null", "60")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.trainingDate").exists());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-45"})
    @DisplayName("a duration that is not positive comes back as 400")
    void rejectsNonPositiveDuration(String duration) throws Exception {
        mockMvc.perform(post("/trainings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("\"John.Smith\"", "\"Sarah.Miller\"", "\"Morning Yoga\"",
                                "\"2026-08-01\"", duration)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.trainingDuration").exists());
    }

    @Test
    @DisplayName("an unknown trainee or trainer comes back as 404")
    void rejectsUnknownProfile() throws Exception {
        when(gymCrmFacade.addTraining(any(), any(), any(), any(), any()))
                .thenThrow(new ProfileNotFoundException("Trainee not found: John.Smith"));

        mockMvc.perform(post("/trainings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Trainee not found: John.Smith"));
    }

}
