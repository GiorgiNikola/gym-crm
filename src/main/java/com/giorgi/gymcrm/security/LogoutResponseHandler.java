package com.giorgi.gymcrm.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class LogoutResponseHandler implements LogoutSuccessHandler {

    private final AuthenticationEntryPoint entryPoint;

    public LogoutResponseHandler(AuthenticationEntryPoint entryPoint) {
        this.entryPoint = entryPoint;
    }

    @Override
    public void onLogoutSuccess(HttpServletRequest request,
                                HttpServletResponse response,
                                Authentication authentication) throws IOException, ServletException {
        if (authentication == null) {
            entryPoint.commence(request, response,
                    new InsufficientAuthenticationException("A bearer token is required to log out"));
            return;
        }
        response.setStatus(HttpStatus.NO_CONTENT.value());
    }
}
