package com.giorgi.workload;

import ch.qos.logback.classic.Level;
import com.giorgi.workload.security.AccessDenialLog;
import com.giorgi.workload.web.RequestLoggingFilter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTest {

    private static final String SUMMARY = "/workloads/Sarah.Miller";
    private static final String MONTH = "/workloads/Sarah.Miller/2026/8";

    @Autowired
    private MockMvc mockMvc;

    private String body(String username) {
        return """
                {"trainerUsername":"%s","trainerFirstName":"Sarah","trainerLastName":"Miller","isActive":true,
                 "trainingDate":"2026-08-01","trainingDuration":60,"actionType":"ADD"}""".formatted(username);
    }

    private MockHttpServletRequestBuilder postWorkload(String username) {
        return post("/workloads").contentType(MediaType.APPLICATION_JSON).content(body(username));
    }

    private String newUsername() {
        return "trainer." + UUID.randomUUID();
    }

    private void expectMissingToken(ResultActions result) throws Exception {
        result.andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Authentication is required"));
    }

    private void expectRejectedToken(String authorization) throws Exception {
        mockMvc.perform(get(SUMMARY).header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\""))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("The bearer token is not valid"));
    }

    private void expectDenied(ResultActions result, String path) throws Exception {
        result.andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value("Access to this resource is denied"))
                .andExpect(jsonPath("$.instance").value(path));
    }

    @Test
    @DisplayName("a post without a token comes back as 401 and asks for a bearer token")
    void rejectsPostWithoutToken() throws Exception {
        expectMissingToken(mockMvc.perform(postWorkload(newUsername())));
    }

    @Test
    @DisplayName("the summary without a token comes back as 401")
    void rejectsSummaryWithoutToken() throws Exception {
        expectMissingToken(mockMvc.perform(get(SUMMARY)));
    }

    @Test
    @DisplayName("a month without a token comes back as 401")
    void rejectsMonthWithoutToken() throws Exception {
        expectMissingToken(mockMvc.perform(get(MONTH)));
    }

    @Test
    @DisplayName("a token signed with another secret comes back as 401")
    void rejectsWrongSignature() throws Exception {
        expectRejectedToken(TestTokens.token().scope(TestTokens.READ)
                .secret("another-secret-of-thirty-two-bytes-or-more").bearer());
    }

    @Test
    @DisplayName("an expired token comes back as 401")
    void rejectsExpiredToken() throws Exception {
        expectRejectedToken(TestTokens.token().scope(TestTokens.READ)
                .expiresAt(Instant.now().minusSeconds(300)).bearer());
    }

    @Test
    @DisplayName("a token that never expires comes back as 401")
    void rejectsTokenWithoutExpiry() throws Exception {
        expectRejectedToken(TestTokens.token().scope(TestTokens.READ).expiresAt(null).bearer());
    }

    @Test
    @DisplayName("a token from another issuer comes back as 401")
    void rejectsWrongIssuer() throws Exception {
        expectRejectedToken(TestTokens.token().scope(TestTokens.READ).issuer("someone-else").bearer());
    }

    @Test
    @DisplayName("a token without an issuer comes back as 401")
    void rejectsMissingIssuer() throws Exception {
        expectRejectedToken(TestTokens.token().scope(TestTokens.READ).issuer(null).bearer());
    }

    @Test
    @DisplayName("a token for another audience comes back as 401")
    void rejectsWrongAudience() throws Exception {
        expectRejectedToken(TestTokens.token().scope(TestTokens.READ).audience("gym-crm").bearer());
    }

    @Test
    @DisplayName("a token without an audience comes back as 401")
    void rejectsMissingAudience() throws Exception {
        expectRejectedToken(TestTokens.token().scope(TestTokens.READ).audience().bearer());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Bearer not-a-token", "Bearer a.b.c"})
    @DisplayName("a token that is not a token comes back as 401")
    void rejectsGarbageToken(String authorization) throws Exception {
        expectRejectedToken(authorization);
    }

    @Test
    @DisplayName("a token with an unsigned header and the right claims comes back as 401")
    void rejectsUnsignedToken() throws Exception {
        String signed = TestTokens.token().scope(TestTokens.READ).sign();
        String claims = signed.split("\\.")[1];
        String unsignedHeader = "eyJhbGciOiJub25lIn0";

        expectRejectedToken("Bearer " + unsignedHeader + "." + claims + ".");
    }

    @Test
    @DisplayName("a read token cannot post and gets a 403")
    void deniesPostWithReadScope() throws Exception {
        expectDenied(mockMvc.perform(postWorkload(newUsername())
                .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.READ))), "/workloads");
    }

    @Test
    @DisplayName("a write token cannot read the summary and gets a 403")
    void deniesSummaryWithWriteScope() throws Exception {
        expectDenied(mockMvc.perform(get(SUMMARY)
                .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.WRITE))), SUMMARY);
    }

    @Test
    @DisplayName("a write token cannot read a month and gets a 403")
    void deniesMonthWithWriteScope() throws Exception {
        expectDenied(mockMvc.perform(get(MONTH)
                .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.WRITE))), MONTH);
    }

    @Test
    @DisplayName("a token without any scope gets a 403")
    void deniesTokenWithoutScope() throws Exception {
        expectDenied(mockMvc.perform(get(SUMMARY)
                .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(null))), SUMMARY);
    }

    @Test
    @DisplayName("a denial is logged with the method, the path and the subject, and without the token")
    void logsDenial() throws Exception {
        String token = TestTokens.token().scope(TestTokens.READ).sign();

        try (LogCapture denials = LogCapture.of(AccessDenialLog.class);
             LogCapture requests = LogCapture.of(RequestLoggingFilter.class)) {
            mockMvc.perform(postWorkload(newUsername()).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isForbidden());

            Assertions.assertEquals(List.of("Denied POST /workloads for subject gym-crm"),
                    denials.messages(Level.WARN));
            Assertions.assertEquals(1, requests.messages().size());
            Assertions.assertFalse(requests.messages().getFirst().contains(token));
        }
    }

    @Test
    @DisplayName("a write token posts a change")
    void allowsPostWithWriteScope() throws Exception {
        mockMvc.perform(postWorkload(newUsername())
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.WRITE)))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("a read token reads the summary and a month")
    void allowsReadsWithReadScope() throws Exception {
        String username = newUsername();
        mockMvc.perform(postWorkload(username)
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.WRITE)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/workloads/{username}", username)
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.READ)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trainerUsername").value(username));

        mockMvc.perform(get("/workloads/{username}/{year}/{month}", username, 2026, 8)
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.READ)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trainingSummaryDuration").value(60));
    }

    @Test
    @DisplayName("a token with both scopes and several audiences passes everywhere")
    void acceptsBothScopesAndSeveralAudiences() throws Exception {
        String username = newUsername();
        String authorization = TestTokens.token()
                .scope(TestTokens.WRITE + " " + TestTokens.READ)
                .audience("report-service", "workload-service")
                .bearer();

        mockMvc.perform(postWorkload(username).header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk());
        mockMvc.perform(get("/workloads/{username}", username).header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/health", "/v3/api-docs", "/v3/api-docs/swagger-config",
            "/swagger-ui/index.html"})
    @DisplayName("health, the spec and the swagger ui need no token")
    void servesPublicPathsWithoutToken(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("/swagger-ui.html redirects without a token")
    void redirectsToSwaggerUiWithoutToken() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("an unknown path without a token is a 401, so the url space stays hidden")
    void hidesUnknownPathsFromAnonymousCallers() throws Exception {
        expectMissingToken(mockMvc.perform(get("/no-such-path")));
    }

    @Test
    @DisplayName("an unknown path with a token is a 404 problem")
    void reportsUnknownPathToAuthenticatedCallers() throws Exception {
        mockMvc.perform(get("/no-such-path").header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.READ)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("requests create no session and need no csrf token")
    void staysStateless() throws Exception {
        mockMvc.perform(postWorkload(newUsername())
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.WRITE)))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(result -> Assertions.assertNull(result.getRequest().getSession(false)));
    }
}
