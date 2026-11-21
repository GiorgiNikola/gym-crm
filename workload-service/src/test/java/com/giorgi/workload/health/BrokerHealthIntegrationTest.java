package com.giorgi.workload.health;

import org.awaitility.Awaitility;
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
    @DisplayName("health shows the running listener, the MongoDB contributor and no built-in jms contributor")
    void reportsBrokerInsteadOfJms() {
        Awaitility.await().untilAsserted(() -> mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.broker.status").value("UP"))
                .andExpect(jsonPath("$.components.broker.details.workloadEvents.running").value(true))
                .andExpect(jsonPath("$.components.broker.details.workloadEvents.connected").value(true))
                .andExpect(jsonPath("$.components.broker.details.workloadEvents.maxConsumers").value(1))
                .andExpect(jsonPath("$.components.mongo.status").value("UP"))
                .andExpect(jsonPath("$.components.jms").doesNotExist()));
    }
}
