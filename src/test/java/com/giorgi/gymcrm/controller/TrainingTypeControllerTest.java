package com.giorgi.gymcrm.controller;

import com.giorgi.gymcrm.facade.GymCrmFacade;
import com.giorgi.gymcrm.model.TrainingType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TrainingTypeControllerTest {

    @Mock
    private GymCrmFacade gymCrmFacade;

    private TrainingType trainingType(long id, String name) {
        TrainingType type = new TrainingType();
        ReflectionTestUtils.setField(type, "id", id);
        ReflectionTestUtils.setField(type, "name", name);
        return type;
    }

    @Test
    @DisplayName("GET /training-types returns the types as json")
    void returnsTypesAsJson() throws Exception {
        when(gymCrmFacade.getTrainingTypes())
                .thenReturn(List.of(trainingType(1L, "FITNESS"), trainingType(2L, "YOGA")));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new TrainingTypeController(gymCrmFacade)).build();

        mockMvc.perform(get("/training-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("FITNESS"))
                .andExpect(jsonPath("$[1].name").value("YOGA"));
    }
}
