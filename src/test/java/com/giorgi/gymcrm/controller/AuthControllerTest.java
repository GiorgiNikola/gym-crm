package com.giorgi.gymcrm.controller;

import com.giorgi.gymcrm.exception.LoginLockedException;
import com.giorgi.gymcrm.facade.GymCrmFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private static final String USERNAME = "John.Smith";
    private static final String PASSWORD = "aX7kQ2mN9p";
    private static final String TOKEN = "a.signed.token";

    @Mock
    private GymCrmFacade gymCrmFacade;

    private MockMvc mockMvc;

    @BeforeEach
    void buildMockMvc() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(gymCrmFacade))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private String body(String username, String password) {
        return "{\"username\":" + username + ",\"password\":" + password + "}";
    }

    @Test
    @DisplayName("valid credentials come back as 200 with a token")
    void logsIn() throws Exception {
        when(gymCrmFacade.login(USERNAME, PASSWORD)).thenReturn(TOKEN);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("\"John.Smith\"", "\"aX7kQ2mN9p\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(TOKEN));

        verify(gymCrmFacade).login(USERNAME, PASSWORD);
    }

    @Test
    @DisplayName("wrong credentials come back as 401 and say nothing about which half was wrong")
    void rejectsWrongCredentials() throws Exception {
        when(gymCrmFacade.login(USERNAME, "wrong")).thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("\"John.Smith\"", "\"wrong\"")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid username or password"));
    }

    @Test
    @DisplayName("a locked username comes back as 429 with Retry-After in seconds")
    void reportsLockedUsername() throws Exception {
        when(gymCrmFacade.login(USERNAME, PASSWORD)).thenThrow(new LoginLockedException(Duration.ofMinutes(5)));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("\"John.Smith\"", "\"aX7kQ2mN9p\"")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "300"))
                .andExpect(jsonPath("$.detail").value("Too many failed login attempts. Try again in 300 seconds"));
    }

    @Test
    @DisplayName("a part second of lock still rounds up to a whole one")
    void roundsRetryAfterUp() throws Exception {
        when(gymCrmFacade.login(USERNAME, PASSWORD))
                .thenThrow(new LoginLockedException(Duration.ofMillis(1200)));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("\"John.Smith\"", "\"aX7kQ2mN9p\"")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "2"))
                .andExpect(jsonPath("$.detail").value("Too many failed login attempts. Try again in 2 seconds"));
    }

    @Test
    @DisplayName("a login without a username comes back as 400 and names the field")
    void rejectsMissingUsername() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("null", "\"aX7kQ2mN9p\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username").exists());

        verifyNoInteractions(gymCrmFacade);
    }

    @Test
    @DisplayName("a login with a blank password comes back as 400 and names the field")
    void rejectsBlankPassword() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("\"John.Smith\"", "\"   \"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());

        verifyNoInteractions(gymCrmFacade);
    }
}
