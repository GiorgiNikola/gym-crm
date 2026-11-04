package com.giorgi.gymcrm.web;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.stream.Collectors;

class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/training-types");
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private Logger filterLogger;
    private Level originalLevel;

    @BeforeEach
    void captureLogs() {
        filterLogger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
        originalLevel = filterLogger.getLevel();
        filterLogger.setLevel(Level.DEBUG);
        appender.start();
        filterLogger.addAppender(appender);
    }

    @AfterEach
    void releaseLogs() {
        filterLogger.detachAppender(appender);
        filterLogger.setLevel(originalLevel);
        appender.stop();
    }

    private List<ILoggingEvent> events() {
        return appender.list;
    }

    private String loggedText() {
        return events().stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));
    }

    @Test
    @DisplayName("the response body still reaches the client")
    void copiesResponseBodyBack() throws Exception {
        filter.doFilter(request, response, (req, res) -> res.getWriter().write("pong"));

        Assertions.assertEquals("pong", response.getContentAsString());
    }

    @Test
    @DisplayName("a password in the request body never reaches the logs")
    void masksPasswordsInLoggedBodies() throws Exception {
        request.setMethod("PUT");
        request.setRequestURI("/users/John.Smith/password");
        request.setContent("{\"password\":\"secret123\"}".getBytes());

        filter.doFilter(request, response, (req, res) -> req.getInputStream().readAllBytes());

        Assertions.assertFalse(loggedText().contains("secret123"), loggedText());
        Assertions.assertTrue(loggedText().contains("\"password\":\"****\""), loggedText());
    }

    @Test
    @DisplayName("a successful call is one info line with the status and the duration")
    void logsSuccessAtInfo() throws Exception {
        filter.doFilter(request, response, (req, res) -> {
        });

        ILoggingEvent summary = events().get(0);
        Assertions.assertEquals(Level.INFO, summary.getLevel());
        Assertions.assertTrue(summary.getFormattedMessage().startsWith("GET /training-types returned 200 in "),
                summary.getFormattedMessage());
    }

    @Test
    @DisplayName("the query string is part of the logged uri")
    void logsTheQueryString() throws Exception {
        request.setQueryString("periodFrom=2024-01-01");

        filter.doFilter(request, response, (req, res) -> {
        });

        Assertions.assertTrue(loggedText().contains("GET /training-types?periodFrom=2024-01-01"), loggedText());
    }

    @Test
    @DisplayName("a rejected call is a warning that carries the error body")
    void logsClientErrorAtWarn() throws Exception {
        filter.doFilter(request, response, (req, res) -> {
            ((HttpServletResponse) res).setStatus(400);
            res.getWriter().write("{\"detail\":\"firstName must not be blank\"}");
        });

        ILoggingEvent summary = events().get(0);
        Assertions.assertEquals(Level.WARN, summary.getLevel());
        Assertions.assertTrue(summary.getFormattedMessage().contains("firstName must not be blank"),
                summary.getFormattedMessage());
    }

    @Test
    @DisplayName("a failed call is an error line without a stack trace")
    void logsServerErrorAtError() throws Exception {
        filter.doFilter(request, response, (req, res) -> ((HttpServletResponse) res).setStatus(500));

        ILoggingEvent summary = events().get(0);
        Assertions.assertEquals(Level.ERROR, summary.getLevel());
        Assertions.assertNull(summary.getThrowableProxy());
    }

    @Test
    @DisplayName("bodies are left out when debug is off")
    void skipsBodiesAtInfoLevel() throws Exception {
        filterLogger.setLevel(Level.INFO);

        filter.doFilter(request, response, (req, res) -> res.getWriter().write("pong"));

        Assertions.assertEquals(1, events().size(), loggedText());
    }

    @Test
    @DisplayName("the body is copied back even when the chain blows up")
    void copiesResponseBodyAfterFailure() throws Exception {
        FilterChain failing = (req, res) -> {
            res.getWriter().write("half written");
            throw new IllegalStateException("boom");
        };

        Assertions.assertThrows(IllegalStateException.class, () -> filter.doFilter(request, response, failing));
        Assertions.assertEquals("half written", response.getContentAsString());
    }
}
