package com.giorgi.workload.security;

import ch.qos.logback.classic.Level;
import com.giorgi.workload.LogCapture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

class AccessDenialLogTest {

    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/workloads");

    private LogCapture logs;

    @BeforeEach
    void captureLogs() {
        SecurityContextHolder.clearContext();
        logs = LogCapture.of(AccessDenialLog.class);
    }

    @AfterEach
    void releaseLogs() {
        logs.close();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("a denial names the method, the path and the subject")
    void logsSubject() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("gym-crm", "n/a"));

        AccessDenialLog.warnDenied(request);

        Assertions.assertEquals(List.of("Denied POST /workloads for subject gym-crm"), logs.messages(Level.WARN));
    }

    @Test
    @DisplayName("a denial without an authentication is logged as anonymous")
    void logsAnonymous() {
        AccessDenialLog.warnDenied(request);

        Assertions.assertEquals(List.of("Denied POST /workloads for subject anonymous"), logs.messages(Level.WARN));
    }
}
