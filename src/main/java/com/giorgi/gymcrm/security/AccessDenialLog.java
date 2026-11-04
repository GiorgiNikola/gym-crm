package com.giorgi.gymcrm.security;

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
        log.warn("Denied {} {} for user {}", request.getMethod(), request.getRequestURI(), currentUsername());
    }

    private static String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? ANONYMOUS : authentication.getName();
    }
}
