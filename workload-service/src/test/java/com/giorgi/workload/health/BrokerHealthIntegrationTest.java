package com.giorgi.workload.health;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BrokerHealthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("health shows the embedded broker as reachable and has no built-in jms contributor")
    void reportsBrokerInsteadOfJms() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.broker.status").value("UP"))
                .andExpect(jsonPath("$.components.broker.details.reachable").value(true))
                .andExpect(jsonPath("$.components.jms").doesNotExist());
    }
}
