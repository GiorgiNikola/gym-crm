package com.giorgi.gymcrm.service;

import com.giorgi.gymcrm.exception.LoginLockedException;
import com.giorgi.gymcrm.metrics.GymMetrics;
import com.giorgi.gymcrm.security.JwtService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
public class LoginService {

    private final AuthenticationManager authenticationManager;
    private final LoginAttemptService loginAttempts;
    private final JwtService jwtService;
    private final GymMetrics gymMetrics;

    public LoginService(AuthenticationManager authenticationManager,
                        LoginAttemptService loginAttempts,
                        JwtService jwtService,
                        GymMetrics gymMetrics) {
        this.authenticationManager = authenticationManager;
        this.loginAttempts = loginAttempts;
        this.jwtService = jwtService;
        this.gymMetrics = gymMetrics;
    }

    public String login(String username, String password) {
        Duration lock = loginAttempts.remainingLock(username);
        if (!lock.isZero()) {
            gymMetrics.loginLocked();
            log.warn("Rejected a login for locked username {}, {} seconds left", username, lock.toSeconds());
            throw new LoginLockedException(lock);
        }

        try {
            authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(username, password));
        } catch (AuthenticationException exception) {
            loginAttempts.recordFailure(username);
            gymMetrics.loginFailed();
            log.warn("Failed login for username {}", username);
            throw exception;
        }

        loginAttempts.reset(username);
        gymMetrics.loginSucceeded();
        log.info("Login succeeded for username {}", username);
        return jwtService.issue(username);
    }

    public String issueFor(String username) {
        return jwtService.issue(username);
    }
}
