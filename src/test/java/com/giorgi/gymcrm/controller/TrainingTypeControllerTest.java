package com.giorgi.gymcrm.controller;

import com.giorgi.gymcrm.facade.GymCrmFacade;
import com.giorgi.gymcrm.model.TrainingType;
import com.giorgi.gymcrm.web.BasicCredentialsResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TrainingTypeControllerTest {

    private static final String USERNAME = "John.Smith";
    private static final String PASSWORD = "aX7kQ2mN9p";

    @Mock
    private GymCrmFacade gymCrmFacade;

    private MockMvc mockMvc;

    @BeforeEach
    void buildMockMvc() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TrainingTypeController(gymCrmFacade))
                .setCustomArgumentResolvers(new BasicCredentialsResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private String basic(String username, String password) {
        return "Basic " + Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    private TrainingType trainingType(long id, String name) {
        TrainingType type = new TrainingType();
        ReflectionTestUtils.setField(type, "id", id);
        ReflectionTestUtils.setField(type, "name", name);
        return type;
    }

    @Test
    @DisplayName("GET /training-types returns the types as json")
    void returnsTypesAsJson() throws Exception {
        when(gymCrmFacade.getTrainingTypes(USERNAME, PASSWORD))
                .thenReturn(List.of(trainingType(1L, "FITNESS"), trainingType(2L, "YOGA")));

        mockMvc.perform(get("/training-types").header(HttpHeaders.AUTHORIZATION, basic(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("FITNESS"))
                .andExpect(jsonPath("$[1].name").value("YOGA"));
    }

    @Test
    @DisplayName("GET /training-types without credentials comes back as 401")
    void rejectsRequestWithoutCredentials() throws Exception {
        mockMvc.perform(get("/training-types"))
                .andExpect(status().isUnauthorized());
    }
}
