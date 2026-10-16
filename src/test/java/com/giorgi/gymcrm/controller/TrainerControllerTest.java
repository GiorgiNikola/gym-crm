package com.giorgi.gymcrm.controller;

import com.giorgi.gymcrm.facade.GymCrmFacade;
import com.giorgi.gymcrm.model.Trainer;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
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
}
