package com.giorgi.gymcrm.controller;

import com.giorgi.gymcrm.exception.AuthenticationException;
import com.giorgi.gymcrm.exception.ProfileNotFoundException;
import com.giorgi.gymcrm.exception.StateConflictException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    @DisplayName("a bad argument becomes a 400")
    void mapsIllegalArgumentToBadRequest() throws Exception {
        mockMvc.perform(get("/illegal-argument"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("firstName must not be blank"));
    }

    @Test
    @DisplayName("a failed login becomes a 401")
    void mapsAuthenticationToUnauthorized() throws Exception {
        mockMvc.perform(get("/authentication"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Wrong username or password"));
    }

    @Test
    @DisplayName("a missing profile becomes a 404")
    void mapsProfileNotFoundToNotFound() throws Exception {
        mockMvc.perform(get("/profile-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Trainee John.Smith not found"));
    }

    @Test
    @DisplayName("a state conflict becomes a 409")
    void mapsStateConflictToConflict() throws Exception {
        mockMvc.perform(get("/state-conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Trainee John.Smith is already active"));
    }

    @Test
    @DisplayName("a constraint violation becomes a 409 that hides the sql")
    void mapsDataIntegrityViolationToConflict() throws Exception {
        MvcResult result = mockMvc.perform(get("/data-integrity"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Request conflicts with existing data"))
                .andReturn();

        Assertions.assertFalse(result.getResponse().getContentAsString().contains("users.username"));
    }

    @Test
    @DisplayName("anything else becomes a 500 that says nothing about the cause")
    void mapsUnknownExceptionToServerError() throws Exception {
        MvcResult result = mockMvc.perform(get("/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Unexpected error"))
                .andReturn();

        Assertions.assertFalse(result.getResponse().getContentAsString().contains("connection pool exhausted"));
    }

    // Deliberately an inner class, not a static one: the component scan in WebConfig would
    // otherwise pick this controller up and expose these paths in WebContextTest.
    @RestController
    class ThrowingController {

        @GetMapping("/illegal-argument")
        void illegalArgument() {
            throw new IllegalArgumentException("firstName must not be blank");
        }

        @GetMapping("/authentication")
        void authentication() {
            throw new AuthenticationException("Wrong username or password");
        }

        @GetMapping("/profile-not-found")
        void profileNotFound() {
            throw new ProfileNotFoundException("Trainee John.Smith not found");
        }

        @GetMapping("/state-conflict")
        void stateConflict() {
            throw new StateConflictException("Trainee John.Smith is already active");
        }

        @GetMapping("/data-integrity")
        void dataIntegrity() {
            throw new DataIntegrityViolationException("could not execute statement, unique index on users.username");
        }

        @GetMapping("/unexpected")
        void unexpected() {
            throw new IllegalStateException("connection pool exhausted");
        }
    }
}
