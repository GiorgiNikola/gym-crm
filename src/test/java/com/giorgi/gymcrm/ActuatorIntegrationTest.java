package com.giorgi.gymcrm;

import com.giorgi.gymcrm.dto.RegistrationResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.jdbc.JdbcTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Not @Transactional: the registration has to be committed for the metrics to be worth reading,
// and the actuator endpoints are read over http like a scraper would.
@SpringBootTest
@AutoConfigureMockMvc
class ActuatorIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    // The registration is committed, so it has to go before the next test class.
    @AfterEach
    void clearRegisteredRows() {
        JdbcTestUtils.deleteFromTables(new JdbcTemplate(dataSource),
                "trainee2trainer", "trainings", "trainees", "trainers", "users");
    }

    private String prometheus() throws Exception {
        return mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private RegistrationResponse registerTrainee() throws Exception {
        String body = mockMvc.perform(post("/trainees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Ada\",\"lastName\":\"Metrics\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new ObjectMapper().readValue(body, RegistrationResponse.class);
    }

    @Test
    @DisplayName("health is UP and names both custom components")
    void reportsHealthWithCustomComponents() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.trainingTypes.status").value("UP"))
                .andExpect(jsonPath("$.components.trainingTypes.details.count").value(5))
                .andExpect(jsonPath("$.components.databaseLatency.status").value("UP"))
                .andExpect(jsonPath("$.components.databaseLatency.details.latencyMs").exists());
    }

    @Test
    @DisplayName("the gym counters are scrapeable before anything happens")
    void exposesPreRegisteredCounters() throws Exception {
        String scrape = prometheus();

        Assertions.assertTrue(scrape.contains("gymcrm_registrations_total{application=\"gym-crm\",role=\"trainee\"}"),
                "no pre-registered trainee counter");
        Assertions.assertTrue(scrape.contains("gymcrm_logins_total{application=\"gym-crm\",outcome=\"success\"}"),
                "no pre-registered login counter");
    }

    @Test
    @DisplayName("a registration and a login show up in the scrape")
    void countsRegistrationAndLogin() throws Exception {
        RegistrationResponse registered = registerTrainee();
        String credentials = "Basic " + Base64.getEncoder().encodeToString(
                (registered.username() + ":" + registered.password()).getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get("/auth/login").header(HttpHeaders.AUTHORIZATION, credentials))
                .andExpect(status().isOk());

        String scrape = prometheus();
        Assertions.assertTrue(scrape.lines()
                        .anyMatch(line -> line.startsWith("gymcrm_registrations_total")
                                && line.contains("role=\"trainee\"") && !line.endsWith(" 0.0")),
                scrape.lines().filter(line -> line.startsWith("gymcrm_registrations_total")).toList().toString());
        Assertions.assertTrue(scrape.lines()
                        .anyMatch(line -> line.startsWith("gymcrm_logins_total")
                                && line.contains("outcome=\"success\"") && !line.endsWith(" 0.0")),
                scrape.lines().filter(line -> line.startsWith("gymcrm_logins_total")).toList().toString());
    }
}
