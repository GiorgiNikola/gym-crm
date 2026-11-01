package com.giorgi.gymcrm.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

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
        AccessDenialLog.warnDenied(request);
        writer.write(request, response, HttpStatus.FORBIDDEN, "Access to this resource is denied");
    }
}
