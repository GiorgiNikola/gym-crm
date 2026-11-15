package com.giorgi.gymcrm.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String BEARER = "Bearer";
    private static final String INVALID_TOKEN = "Bearer error=\"invalid_token\"";

    private final ProblemDetailWriter writer;

    public ProblemAuthenticationEntryPoint(ProblemDetailWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        boolean tokenWasRejected = exception instanceof InvalidTokenException;

        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, tokenWasRejected ? INVALID_TOKEN : BEARER);
        writer.write(request, response, HttpStatus.UNAUTHORIZED, tokenWasRejected
                ? "The bearer token is not valid"
                : "Authentication is required");
    }
}
