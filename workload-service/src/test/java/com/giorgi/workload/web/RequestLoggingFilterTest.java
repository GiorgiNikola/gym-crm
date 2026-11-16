package com.giorgi.workload.web;

import ch.qos.logback.classic.Level;
import com.giorgi.workload.LogCapture;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

class RequestLoggingFilterTest {

    private static final String BODY = "{\"trainerUsername\":\"Sarah.Miller\",\"trainerFirstName\":\"Sarah\","
            + "\"trainerLastName\":\"Miller\",\"trainingDuration\":60}";
    private static final String MASKED_BODY = "{\"trainerUsername\":\"Sarah.Miller\",\"trainerFirstName\":\"****\","
            + "\"trainerLastName\":\"****\",\"trainingDuration\":60}";

    private final RequestLoggingFilter filter = new RequestLoggingFilter();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/workloads/Sarah.Miller");
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    private LogCapture logs;

    @BeforeEach
    void captureLogs() {
        logs = LogCapture.of(RequestLoggingFilter.class);
    }

    @AfterEach
    void releaseLogs() {
        logs.close();
    }

    private void post(String body) {
        request.setMethod("POST");
        request.setRequestURI("/workloads");
        request.setContent(body.getBytes());
    }

    private FilterChain readingBodyAndAnswering(int status, String responseBody) {
        return (req, res) -> {
            req.getInputStream().readAllBytes();
            ((HttpServletResponse) res).setStatus(status);
            res.getWriter().write(responseBody);
        };
    }

    private String onlyLine(Level level) {
        Assertions.assertEquals(1, logs.messages().size(), logs.messages().toString());
        List<String> lines = logs.messages(level);
        Assertions.assertEquals(1, lines.size(), logs.messages().toString());
        return lines.getFirst();
    }

    @Test
    @DisplayName("the response body still reaches the client")
    void copiesResponseBodyBack() throws Exception {
        filter.doFilter(request, response, (req, res) -> res.getWriter().write("pong"));

        Assertions.assertEquals("pong", response.getContentAsString());
    }

    @Test
    @DisplayName("a successful call without a body is one info line with the status and the duration")
    void logsSuccessAtInfo() throws Exception {
        filter.doFilter(request, response, (req, res) -> {
        });

        String line = onlyLine(Level.INFO);
        Assertions.assertTrue(line.matches("GET /workloads/Sarah\\.Miller returned 200 in \\d+ ms, request -"), line);
    }

    @Test
    @DisplayName("a successful post is one info line with the masked request body")
    void logsRequestBodyMasked() throws Exception {
        post(BODY);

        filter.doFilter(request, response, readingBodyAndAnswering(200, ""));

        String line = onlyLine(Level.INFO);
        Assertions.assertTrue(line.matches("POST /workloads returned 200 in \\d+ ms, request .*"), line);
        Assertions.assertTrue(line.endsWith("request " + MASKED_BODY), line);
    }

    @Test
    @DisplayName("a body spread over several lines is logged on one line")
    void flattensRequestBody() throws Exception {
        post("{\n  \"trainerUsername\": \"Sarah.Miller\",\r\n\t\"actionType\": \"ADD\"\n}");

        filter.doFilter(request, response, readingBodyAndAnswering(200, ""));

        Assertions.assertTrue(onlyLine(Level.INFO)
                .endsWith("request { \"trainerUsername\": \"Sarah.Miller\", \"actionType\": \"ADD\" }"));
    }

    @Test
    @DisplayName("the query string is part of the logged uri")
    void logsTheQueryString() throws Exception {
        request.setQueryString("verbose=true");

        filter.doFilter(request, response, (req, res) -> {
        });

        Assertions.assertTrue(onlyLine(Level.INFO).startsWith("GET /workloads/Sarah.Miller?verbose=true returned"));
    }

    @Test
    @DisplayName("a rejected call is one warning with the request and the problem detail")
    void logsClientErrorAtWarn() throws Exception {
        post(BODY);

        filter.doFilter(request, response,
                readingBodyAndAnswering(400, "{\"detail\":\"Validation failed\",\"status\":400}"));

        String line = onlyLine(Level.WARN);
        Assertions.assertTrue(line.matches("POST /workloads rejected with 400 in \\d+ ms, request .*"), line);
        Assertions.assertTrue(line.endsWith("request " + MASKED_BODY
                + ", response {\"detail\":\"Validation failed\",\"status\":400}"), line);
    }

    @Test
    @DisplayName("a rejection before the body was read shows no request body")
    void logsUnreadBodyAsMissing() throws Exception {
        post(BODY);

        filter.doFilter(request, response, (req, res) -> {
            ((HttpServletResponse) res).setStatus(401);
            res.getWriter().write("{\"detail\":\"Authentication is required\"}");
        });

        Assertions.assertTrue(onlyLine(Level.WARN)
                .endsWith("request -, response {\"detail\":\"Authentication is required\"}"));
    }

    @Test
    @DisplayName("a failed call is one error with the request and the problem detail")
    void logsServerErrorAtError() throws Exception {
        post(BODY);

        filter.doFilter(request, response,
                readingBodyAndAnswering(500, "{\"detail\":\"Unexpected error\",\"status\":500}"));

        String line = onlyLine(Level.ERROR);
        Assertions.assertTrue(line.matches("POST /workloads failed with 500 in \\d+ ms, request .*"), line);
        Assertions.assertTrue(line.endsWith("request " + MASKED_BODY
                + ", response {\"detail\":\"Unexpected error\",\"status\":500}"), line);
    }

    @Test
    @DisplayName("the call is logged even when the chain blows up")
    void logsWhenChainThrows() {
        FilterChain failing = (req, res) -> {
            throw new IllegalStateException("boom");
        };

        Assertions.assertThrows(IllegalStateException.class, () -> filter.doFilter(request, response, failing));
        Assertions.assertEquals(1, logs.messages().size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/health", "/swagger-ui.html", "/swagger-ui/index.html", "/v3/api-docs",
            "/v3/api-docs/swagger-config"})
    @DisplayName("health checks and the api docs are not logged")
    void skipsInfrastructurePaths(String path) throws Exception {
        request.setRequestURI(path);

        filter.doFilter(request, response, (req, res) -> {
        });

        Assertions.assertTrue(logs.messages().isEmpty(), logs.messages().toString());
    }
}
