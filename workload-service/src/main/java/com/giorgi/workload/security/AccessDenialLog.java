package com.giorgi.workload.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Slf4j
public final class AccessDenialLog {

    private static final String ANONYMOUS = "anonymous";

    private AccessDenialLog() {
    }

    public static void warnDenied(HttpServletRequest request) {
        log.warn("Denied {} {} for subject {}", request.getMethod(), request.getRequestURI(), currentSubject());
    }

    private static String currentSubject() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? ANONYMOUS : authentication.getName();
    }
}
