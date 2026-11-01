package com.giorgi.gymcrm.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class ProblemAccessDeniedHandler implements AccessDeniedHandler {

    private final ProblemDetailWriter writer;

    public ProblemAccessDeniedHandler(ProblemDetailWriter writer) {
        this.writer = writer;
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        log.warn("Denied {} {} for user {}", request.getMethod(), request.getRequestURI(), currentUsername());
        writer.write(request, response, HttpStatus.FORBIDDEN, "Access to this resource is denied");
    }

    private static String currentUsername() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "anonymous" : authentication.getName();
    }
}
