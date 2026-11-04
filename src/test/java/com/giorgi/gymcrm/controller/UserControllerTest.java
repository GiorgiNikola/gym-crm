package com.giorgi.gymcrm.controller;

import com.giorgi.gymcrm.exception.StateConflictException;
import com.giorgi.gymcrm.facade.GymCrmFacade;
import com.giorgi.gymcrm.web.BasicCredentialsResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private static final String USERNAME = "John.Smith";
    private static final String PASSWORD = "aX7kQ2mN9p";

    @Mock
    private GymCrmFacade gymCrmFacade;

    private MockMvc mockMvc;

    @BeforeEach
    void buildMockMvc() {
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(gymCrmFacade))
                .setCustomArgumentResolvers(new BasicCredentialsResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private String basic(String username, String password) {
        return "Basic " + Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("a user deactivates themselves")
    void changesOwnStatus() throws Exception {
        mockMvc.perform(patch("/users/{username}/status", USERNAME)
                        .header(HttpHeaders.AUTHORIZATION, basic(USERNAME, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\":false}"))
                .andExpect(status().isOk());

        verify(gymCrmFacade).changeStatus(USERNAME, PASSWORD, false);
    }

    @Test
    @DisplayName("asking for the state the user is already in comes back as 409")
    void reportsStatusConflict() throws Exception {
        doThrow(new StateConflictException("User John.Smith is already active"))
                .when(gymCrmFacade).changeStatus(USERNAME, PASSWORD, true);

        mockMvc.perform(patch("/users/{username}/status", USERNAME)
                        .header(HttpHeaders.AUTHORIZATION, basic(USERNAME, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("User John.Smith is already active"));
    }

    @Test
    @DisplayName("touching somebody else's status comes back as 403")
    void rejectsStatusChangeForOtherUser() throws Exception {
        mockMvc.perform(patch("/users/{username}/status", "Sarah.Miller")
                        .header(HttpHeaders.AUTHORIZATION, basic(USERNAME, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\":false}"))
                .andExpect(status().isForbidden());

        verify(gymCrmFacade, never()).changeStatus(anyString(), anyString(), anyBoolean());
    }

    @Test
    @DisplayName("a status body without isActive comes back as 400 and names the field")
    void rejectsStatusWithoutFlag() throws Exception {
        mockMvc.perform(patch("/users/{username}/status", USERNAME)
                        .header(HttpHeaders.AUTHORIZATION, basic(USERNAME, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors.isActive").exists());
    }

    @Test
    @DisplayName("a password change goes through with no authorization header")
    void changesPassword() throws Exception {
        mockMvc.perform(put("/users/{username}/password", USERNAME)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"aX7kQ2mN9p\",\"newPassword\":\"bY8lR3nO0q\"}"))
                .andExpect(status().isOk());

        verify(gymCrmFacade).changePassword(USERNAME, PASSWORD, "bY8lR3nO0q");
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
