package com.giorgi.gymcrm.controller;

import com.giorgi.gymcrm.exception.AuthenticationException;
import com.giorgi.gymcrm.facade.GymCrmFacade;
import com.giorgi.gymcrm.web.BasicCredentialsResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private static final String USERNAME = "John.Smith";
    private static final String PASSWORD = "aX7kQ2mN9p";

    @Mock
    private GymCrmFacade gymCrmFacade;

    private MockMvc mockMvc;

    @BeforeEach
    void buildMockMvc() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(gymCrmFacade))
                .setCustomArgumentResolvers(new BasicCredentialsResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private String basic(String username, String password) {
        return "Basic " + Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("valid credentials come back as 200")
    void logsIn() throws Exception {
        mockMvc.perform(get("/auth/login").header(HttpHeaders.AUTHORIZATION, basic(USERNAME, PASSWORD)))
                .andExpect(status().isOk());

        verify(gymCrmFacade).login(USERNAME, PASSWORD);
    }

    @Test
    @DisplayName("wrong credentials come back as 401 with a basic challenge")
    void rejectsWrongCredentials() throws Exception {
        doThrow(new AuthenticationException("Invalid username or password"))
                .when(gymCrmFacade).login(USERNAME, "wrong");

        mockMvc.perform(get("/auth/login").header(HttpHeaders.AUTHORIZATION, basic(USERNAME, "wrong")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"gym-crm\""));
    }
}
