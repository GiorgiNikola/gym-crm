package com.giorgi.workload;

import ch.qos.logback.classic.Level;
import com.giorgi.workload.logging.TransactionIds;
import com.giorgi.workload.security.AccessDenialLog;
import com.giorgi.workload.service.WorkloadService;
import com.giorgi.workload.web.RequestLoggingFilter;
import com.giorgi.workload.web.TransactionIdFilter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransactionLoggingIntegrationTest {

    private static final String HEADER = TransactionIdFilter.TRANSACTION_ID_HEADER;
    private static final String MDC_KEY = TransactionIds.MDC_KEY;

    @Autowired
    private MockMvc mockMvc;

    private MockHttpServletRequestBuilder postWorkload(String username) {
        return post("/workloads")
                .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.WRITE))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"trainerUsername":"%s","trainerFirstName":"Sarah","trainerLastName":"Miller",
                         "isActive":true,"trainingDate":"2026-08-01","trainingDuration":60,"actionType":"ADD"}"""
                        .formatted(username));
    }

    private String newUsername() {
        return "trainer." + UUID.randomUUID();
    }

    private String echoedId(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request).andReturn().getResponse().getHeader(HEADER);
    }

    @Test
    @DisplayName("a valid incoming transaction id is reused and echoed")
    void reusesIncomingId() throws Exception {
        mockMvc.perform(postWorkload(newUsername()).header(HEADER, "gym-crm-7f3a-0001"))
                .andExpect(status().isOk())
                .andExpect(header().string(HEADER, "gym-crm-7f3a-0001"));
    }

    @Test
    @DisplayName("a request without a transaction id gets a generated one")
    void generatesMissingId() throws Exception {
        String generated = echoedId(postWorkload(newUsername()));

        Assertions.assertDoesNotThrow(() -> UUID.fromString(generated));
    }

    @Test
    @DisplayName("an invalid incoming transaction id is replaced")
    void replacesInvalidId() throws Exception {
        String replaced = echoedId(postWorkload(newUsername()).header(HEADER, "not a valid id!"));

        Assertions.assertNotEquals("not a valid id!", replaced);
        Assertions.assertDoesNotThrow(() -> UUID.fromString(replaced));
    }

    @Test
    @DisplayName("the request line and the operation line of one request carry the same transaction id")
    void tagsBothLogLevelsWithTheSameId() throws Exception {
        try (LogCapture requests = LogCapture.of(RequestLoggingFilter.class);
             LogCapture operations = LogCapture.of(WorkloadService.class)) {
            mockMvc.perform(postWorkload(newUsername()).header(HEADER, "gym-crm-7f3a-0002"))
                    .andExpect(status().isOk());

            Assertions.assertEquals(1, requests.messages(Level.INFO).size());
            Assertions.assertEquals(1, operations.messages(Level.INFO).size());
            Assertions.assertEquals(List.of("gym-crm-7f3a-0002"), requests.mdcValues(MDC_KEY));
            Assertions.assertEquals(List.of("gym-crm-7f3a-0002"), operations.mdcValues(MDC_KEY));
        }
    }

    @Test
    @DisplayName("a generated transaction id reaches the log lines and the response alike")
    void tagsLogLinesWithTheGeneratedId() throws Exception {
        try (LogCapture requests = LogCapture.of(RequestLoggingFilter.class);
             LogCapture operations = LogCapture.of(WorkloadService.class)) {
            String generated = echoedId(postWorkload(newUsername()));

            Assertions.assertEquals(List.of(generated), requests.mdcValues(MDC_KEY));
            Assertions.assertEquals(List.of(generated), operations.mdcValues(MDC_KEY));
        }
    }

    @Test
    @DisplayName("a 401 carries a transaction id and is logged with it")
    void tagsUnauthorizedRequests() throws Exception {
        try (LogCapture requests = LogCapture.of(RequestLoggingFilter.class)) {
            mockMvc.perform(get("/workloads/Sarah.Miller").header(HEADER, "gym-crm-7f3a-0003"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(HEADER, "gym-crm-7f3a-0003"));

            Assertions.assertEquals(1, requests.messages(Level.WARN).size());
            Assertions.assertEquals(List.of("gym-crm-7f3a-0003"), requests.mdcValues(MDC_KEY));
        }
    }

    @Test
    @DisplayName("a 403 carries a transaction id and its denial line has it too")
    void tagsDeniedRequests() throws Exception {
        try (LogCapture requests = LogCapture.of(RequestLoggingFilter.class);
             LogCapture denials = LogCapture.of(AccessDenialLog.class)) {
            mockMvc.perform(get("/workloads/Sarah.Miller")
                            .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.WRITE))
                            .header(HEADER, "gym-crm-7f3a-0004"))
                    .andExpect(status().isForbidden())
                    .andExpect(header().string(HEADER, "gym-crm-7f3a-0004"));

            Assertions.assertEquals(List.of("gym-crm-7f3a-0004"), requests.mdcValues(MDC_KEY));
            Assertions.assertEquals(List.of("gym-crm-7f3a-0004"), denials.mdcValues(MDC_KEY));
        }
    }

    @Test
    @DisplayName("the request line shows the body with the names masked and the username kept")
    void logsRequestBodyWithoutNames() throws Exception {
        String username = newUsername();

        try (LogCapture requests = LogCapture.of(RequestLoggingFilter.class)) {
            mockMvc.perform(postWorkload(username)).andExpect(status().isOk());

            String line = requests.messages(Level.INFO).getFirst();
            Assertions.assertTrue(line.startsWith("POST /workloads returned 200 in "), line);
            Assertions.assertTrue(line.contains("\"trainerUsername\":\"" + username + "\""), line);
            Assertions.assertTrue(line.contains("\"trainerFirstName\":\"****\""), line);
            Assertions.assertTrue(line.contains("\"trainerLastName\":\"****\""), line);
            Assertions.assertFalse(line.contains("Sarah"), line);
            Assertions.assertFalse(line.contains("Miller"), line);
            Assertions.assertFalse(line.contains("\n"), line);
        }
    }

    @Test
    @DisplayName("the transaction id is gone from the thread once the request is over")
    void clearsTheIdAfterTheRequest() throws Exception {
        mockMvc.perform(postWorkload(newUsername()).header(HEADER, "gym-crm-7f3a-0005"));

        Assertions.assertNull(MDC.get(MDC_KEY));
    }
}
