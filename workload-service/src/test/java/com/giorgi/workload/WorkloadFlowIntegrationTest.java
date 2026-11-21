package com.giorgi.workload;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkloadFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private ResultActions read(String path, Object... variables) throws Exception {
        return mockMvc.perform(get(path, variables)
                .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.READ)));
    }

    private String newUsername() {
        return "trainer." + UUID.randomUUID();
    }

    private void send(String username, String lastName, boolean active,
                      String date, int minutes, String actionType) throws Exception {
        mockMvc.perform(post("/workloads")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.WRITE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"trainerUsername":"%s","trainerFirstName":"Sarah","trainerLastName":"%s",
                                 "isActive":%s,"trainingDate":"%s","trainingDuration":%d,"actionType":"%s"}"""
                                .formatted(username, lastName, active, date, minutes, actionType)))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("adds and a delete in one month end up as one total in the summary and the month")
    void sumsChangesOfOneMonth() throws Exception {
        String username = newUsername();
        send(username, "Miller", true, "2026-08-01", 60, "ADD");
        send(username, "Miller", true, "2026-08-20", 90, "ADD");
        send(username, "Miller", true, "2026-08-01", 45, "DELETE");

        read("/workloads/{username}", username)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trainerUsername").value(username))
                .andExpect(jsonPath("$.years.length()").value(1))
                .andExpect(jsonPath("$.years[0].year").value(2026))
                .andExpect(jsonPath("$.years[0].months.length()").value(1))
                .andExpect(jsonPath("$.years[0].months[0].month").value(8))
                .andExpect(jsonPath("$.years[0].months[0].trainingSummaryDuration").value(105));

        read("/workloads/{username}/{year}/{month}", username, 2026, 8)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trainingSummaryDuration").value(105));
    }

    @Test
    @DisplayName("the summary lists years and months ascending and shows the latest names and status")
    void sortsSummaryAndKeepsLatestDetails() throws Exception {
        String username = newUsername();
        send(username, "Miller", true, "2027-01-10", 30, "ADD");
        send(username, "Miller", true, "2026-09-05", 45, "ADD");
        send(username, "Miller-Jones", false, "2026-08-01", 60, "ADD");

        read("/workloads/{username}", username)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trainerLastName").value("Miller-Jones"))
                .andExpect(jsonPath("$.trainerStatus").value(false))
                .andExpect(jsonPath("$.years[0].year").value(2026))
                .andExpect(jsonPath("$.years[0].months[0].month").value(8))
                .andExpect(jsonPath("$.years[0].months[1].month").value(9))
                .andExpect(jsonPath("$.years[1].year").value(2027))
                .andExpect(jsonPath("$.years[1].months[0].month").value(1));
    }

    @Test
    @DisplayName("a delete larger than the month empties it and the trainer stays known")
    void clampsDeleteAndKeepsTrainer() throws Exception {
        String username = newUsername();
        send(username, "Miller", true, "2026-08-01", 60, "ADD");
        send(username, "Miller", true, "2026-08-01", 200, "DELETE");

        read("/workloads/{username}", username)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.years.length()").value(0));

        read("/workloads/{username}/{year}/{month}", username, 2026, 8)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trainingSummaryDuration").value(0));
    }

    @Test
    @DisplayName("a delete for an unknown trainer is accepted and leaves the trainer unknown")
    void acceptsDeleteForUnknownTrainer() throws Exception {
        String username = newUsername();
        send(username, "Miller", true, "2026-08-01", 60, "DELETE");

        read("/workloads/{username}", username)
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("an unknown path comes back as a 404 problem")
    void reportsUnknownPathAsProblem() throws Exception {
        read("/no-such-path")
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("none of the throwing controller's paths are mapped in the application")
    void leavesTheTestOnlyThrowingControllerPathsUnmapped() throws Exception {
        read("/unexpected")
                .andExpect(status().isNotFound());
    }
}
