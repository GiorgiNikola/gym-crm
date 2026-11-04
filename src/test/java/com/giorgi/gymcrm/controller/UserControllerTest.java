package com.giorgi.gymcrm.controller;

import com.giorgi.gymcrm.exception.StateConflictException;
import com.giorgi.gymcrm.facade.GymCrmFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private static final String USERNAME = "John.Smith";
    private static final String OLD_PASSWORD = "aX7kQ2mN9p";
    private static final String NEW_PASSWORD = "bY8lR3nO0q";

    @Mock
    private GymCrmFacade gymCrmFacade;

    private MockMvc mockMvc;

    @BeforeEach
    void buildMockMvc() {
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(gymCrmFacade))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("a user deactivates themselves")
    void changesOwnStatus() throws Exception {
        mockMvc.perform(patch("/users/{username}/status", USERNAME)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\":false}"))
                .andExpect(status().isOk());

        verify(gymCrmFacade).changeStatus(USERNAME, false);
    }

    @Test
    @DisplayName("asking for the state the user is already in comes back as 409")
    void reportsStatusConflict() throws Exception {
        doThrow(new StateConflictException("User John.Smith is already active"))
                .when(gymCrmFacade).changeStatus(USERNAME, true);

        mockMvc.perform(patch("/users/{username}/status", USERNAME)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("User John.Smith is already active"));
    }

    @Test
    @DisplayName("a status body without isActive comes back as 400 and names the field")
    void rejectsStatusWithoutFlag() throws Exception {
        mockMvc.perform(patch("/users/{username}/status", USERNAME)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors.isActive").exists());
    }

    @Test
    @DisplayName("a password change passes both passwords on to the facade")
    void changesPassword() throws Exception {
        mockMvc.perform(put("/users/{username}/password", USERNAME)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"aX7kQ2mN9p\",\"newPassword\":\"bY8lR3nO0q\"}"))
                .andExpect(status().isOk());

        verify(gymCrmFacade).changePassword(USERNAME, OLD_PASSWORD, NEW_PASSWORD);
    }

    @Test
    @DisplayName("a wrong old password comes back as 401")
    void rejectsWrongOldPassword() throws Exception {
        doThrow(new BadCredentialsException("The old password is not correct"))
                .when(gymCrmFacade).changePassword(USERNAME, "wrong", NEW_PASSWORD);

        mockMvc.perform(put("/users/{username}/password", USERNAME)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"wrong\",\"newPassword\":\"bY8lR3nO0q\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid username or password"));
    }

    @Test
    @DisplayName("a blank new password comes back as 400 and names the field")
    void rejectsBlankNewPassword() throws Exception {
        mockMvc.perform(put("/users/{username}/password", USERNAME)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"aX7kQ2mN9p\",\"newPassword\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newPassword").exists());
    }
}
