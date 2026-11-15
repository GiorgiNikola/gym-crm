package com.giorgi.gymcrm.service;

import com.giorgi.gymcrm.exception.LoginLockedException;
import com.giorgi.gymcrm.metrics.GymMetrics;
import com.giorgi.gymcrm.security.JwtService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    private static final String USERNAME = "John.Smith";
    private static final String PASSWORD = "aX7kQ2mN9p";
    private static final String TOKEN = "a.signed.token";

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private LoginAttemptService loginAttempts;

    @Mock
    private JwtService jwtService;

    @Mock
    private GymMetrics gymMetrics;

    @InjectMocks
    private LoginService loginService;

    private void unlocked() {
        when(loginAttempts.remainingLock(USERNAME)).thenReturn(Duration.ZERO);
    }

    @Test
    @DisplayName("a good password comes back as a freshly issued token")
    void issuesATokenOnSuccess() {
        unlocked();
        when(jwtService.issue(USERNAME)).thenReturn(TOKEN);

        Assertions.assertEquals(TOKEN, loginService.login(USERNAME, PASSWORD));
    }

    @Test
    @DisplayName("the credentials go to the authentication manager rather than being compared here")
    void delegatesTheCheckToSpring() {
        unlocked();
        when(jwtService.issue(USERNAME)).thenReturn(TOKEN);

        loginService.login(USERNAME, PASSWORD);

        verify(authenticationManager).authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(USERNAME, PASSWORD));
    }

    @Test
    @DisplayName("a successful login clears the failures counted so far")
    void resetsTheCounterOnSuccess() {
        unlocked();
        when(jwtService.issue(USERNAME)).thenReturn(TOKEN);

        loginService.login(USERNAME, PASSWORD);

        verify(loginAttempts).reset(USERNAME);
        verify(gymMetrics).loginSucceeded();
    }

    @Test
    @DisplayName("a wrong password is counted and passed on to the caller")
    void countsAFailure() {
        unlocked();
        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        Assertions.assertThrows(BadCredentialsException.class, () -> loginService.login(USERNAME, "wrong"));

        verify(loginAttempts).recordFailure(USERNAME);
        verify(gymMetrics).loginFailed();
        verify(jwtService, never()).issue(USERNAME);
    }

    @Test
    @DisplayName("a locked username is turned away before the password is looked at")
    void refusesALockedUsername() {
        when(loginAttempts.remainingLock(USERNAME)).thenReturn(Duration.ofMinutes(4));

        LoginLockedException thrown = Assertions.assertThrows(LoginLockedException.class,
                () -> loginService.login(USERNAME, PASSWORD));

        Assertions.assertEquals(240, thrown.retryAfterSeconds());
        verifyNoInteractions(authenticationManager, jwtService);
        verify(gymMetrics).loginLocked();
    }

    @Test
    @DisplayName("an attempt while locked is not counted again")
    void doesNotCountAttemptsWhileLocked() {
        when(loginAttempts.remainingLock(USERNAME)).thenReturn(Duration.ofMinutes(4));

        Assertions.assertThrows(LoginLockedException.class, () -> loginService.login(USERNAME, PASSWORD));

        verify(loginAttempts, never()).recordFailure(USERNAME);
    }

    @Test
    @DisplayName("issueFor hands out a token without checking any password")
    void issuesForANewlyRegisteredUser() {
        when(jwtService.issue(USERNAME)).thenReturn(TOKEN);

        Assertions.assertEquals(TOKEN, loginService.issueFor(USERNAME));

        verifyNoInteractions(authenticationManager, loginAttempts, gymMetrics);
    }
}
