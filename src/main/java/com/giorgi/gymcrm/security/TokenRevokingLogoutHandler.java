package com.giorgi.gymcrm.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TokenRevokingLogoutHandler implements LogoutHandler {

    private final TokenRevocationService revocations;

    public TokenRevokingLogoutHandler(TokenRevocationService revocations) {
        this.revocations = revocations;
    }

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken token) {
            revocations.revoke(token.claims());
            log.info("Logout for username {}", token.claims().username());
        }
    }
}
