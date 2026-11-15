package com.giorgi.gymcrm;

import com.giorgi.gymcrm.facade.GymCrmFacade;
import com.giorgi.gymcrm.model.Trainee;
import com.giorgi.gymcrm.model.Trainer;
import com.giorgi.gymcrm.model.TrainingType;
import com.giorgi.gymcrm.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MethodSecurityTest {

    private static final String SELF = "John.Smith";
    private static final String SOMEBODY_ELSE = "Sarah.Miller";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GymCrmFacade gymCrmFacade;

    private Trainee trainee() {
        return Trainee.builder()
                .user(User.builder().username(SELF).firstName("John").lastName("Smith").isActive(true).build())
                .build();
    }

    private Trainer trainer() {
        TrainingType yoga = new TrainingType();
        ReflectionTestUtils.setField(yoga, "name", "YOGA");
        return Trainer.builder()
                .user(User.builder().username(SELF).firstName("John").lastName("Smith").isActive(true).build())
                .specialization(yoga)
                .build();
    }

    private String trainingBody(String traineeUsername, String trainerUsername) {
        return "{\"traineeUsername\":\"" + traineeUsername + "\""
                + ",\"trainerUsername\":\"" + trainerUsername + "\""
                + ",\"trainingName\":\"Morning Yoga\""
                + ",\"trainingDate\":\"2026-08-01\""
                + ",\"trainingDuration\":60}";
    }

    @Test
    @WithMockUser(username = SELF)
    @DisplayName("a trainee reading their own profile is let through")
    void allowsReadingYourOwnProfile() throws Exception {
        when(gymCrmFacade.selectTraineeProfile(SELF)).thenReturn(trainee());

        mockMvc.perform(get("/trainees/{username}", SELF))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = SELF)
    @DisplayName("reading somebody else's trainee profile is a 403 and not a 500")
    void deniesReadingAnotherTraineeProfile() throws Exception {
        mockMvc.perform(get("/trainees/{username}", SOMEBODY_ELSE))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value("Access to this resource is denied"));

        verify(gymCrmFacade, never()).selectTraineeProfile(anyString());
    }

    @Test
    @WithMockUser(username = SELF)
    @DisplayName("updating somebody else's trainee profile is a 403")
    void deniesUpdatingAnotherTrainee() throws Exception {
        mockMvc.perform(put("/trainees/{username}", SOMEBODY_ELSE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"John\",\"lastName\":\"Smith\",\"isActive\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = SELF)
    @DisplayName("deleting somebody else's trainee profile is a 403")
    void deniesDeletingAnotherTrainee() throws Exception {
        mockMvc.perform(delete("/trainees/{username}", SOMEBODY_ELSE))
                .andExpect(status().isForbidden());

        verify(gymCrmFacade, never()).deleteTraineeProfile(anyString());
    }

    @Test
    @WithMockUser(username = SELF)
    @DisplayName("listing somebody else's unassigned trainers is a 403")
    void deniesAnotherUsersUnassignedTrainers() throws Exception {
        mockMvc.perform(get("/trainees/{username}/unassigned-trainers", SOMEBODY_ELSE))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = SELF)
    @DisplayName("assigning trainers for somebody else is a 403")
    void deniesAssigningTrainersForAnotherUser() throws Exception {
        mockMvc.perform(put("/trainees/{username}/trainers", SOMEBODY_ELSE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trainerUsernames\":[\"Adam.Brown\"]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = SELF)
    @DisplayName("reading somebody else's trainee training list is a 403")
    void deniesAnotherUsersTraineeTrainings() throws Exception {
        mockMvc.perform(get("/trainees/{username}/trainings", SOMEBODY_ELSE))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = SELF)
    @DisplayName("a trainer reading their own profile is let through")
    void allowsReadingYourOwnTrainerProfile() throws Exception {
        when(gymCrmFacade.selectTrainerProfile(SELF)).thenReturn(trainer());

        mockMvc.perform(get("/trainers/{username}", SELF))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = SELF)
    @DisplayName("reading somebody else's trainer profile is a 403")
    void deniesReadingAnotherTrainerProfile() throws Exception {
        mockMvc.perform(get("/trainers/{username}", SOMEBODY_ELSE))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = SELF)
    @DisplayName("updating somebody else's trainer profile is a 403")
    void deniesUpdatingAnotherTrainer() throws Exception {
        mockMvc.perform(put("/trainers/{username}", SOMEBODY_ELSE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Sarah\",\"lastName\":\"Miller\",\"isActive\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = SELF)
    @DisplayName("reading somebody else's trainer training list is a 403")
    void deniesAnotherUsersTrainerTrainings() throws Exception {
        mockMvc.perform(get("/trainers/{username}/trainings", SOMEBODY_ELSE))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = SELF)
    @DisplayName("changing somebody else's password is a 403")
    void deniesChangingAnotherUsersPassword() throws Exception {
        mockMvc.perform(put("/users/{username}/password", SOMEBODY_ELSE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"aX7kQ2mN9p\",\"newPassword\":\"bY8lR3nO0q\"}"))
                .andExpect(status().isForbidden());

        verify(gymCrmFacade, never()).changePassword(anyString(), anyString(), anyString());
    }

    @Test
    @WithMockUser(username = SELF)
    @DisplayName("changing somebody else's status is a 403")
    void deniesChangingAnotherUsersStatus() throws Exception {
        mockMvc.perform(patch("/users/{username}/status", SOMEBODY_ELSE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = SELF)
    @DisplayName("changing your own status is let through")
    void allowsChangingYourOwnStatus() throws Exception {
        mockMvc.perform(patch("/users/{username}/status", SELF)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\":false}"))
                .andExpect(status().isOk());

        verify(gymCrmFacade).changeStatus(SELF, false);
    }

    @Test
    @WithMockUser(username = SELF)
    @DisplayName("the trainee of a training may log it")
    void allowsTheTraineeToLogATraining() throws Exception {
        mockMvc.perform(post("/trainings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(trainingBody(SELF, SOMEBODY_ELSE)))
                .andExpect(status().isOk());

        verify(gymCrmFacade).addTraining(any(), any(), any(), any(), any());
    }

    @Test
    @WithMockUser(username = SELF)
    @DisplayName("the trainer of a training may log it too")
    void allowsTheTrainerToLogATraining() throws Exception {
        mockMvc.perform(post("/trainings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(trainingBody(SOMEBODY_ELSE, SELF)))
                .andExpect(status().isOk());

        verify(gymCrmFacade).addTraining(any(), any(), any(), any(), any());
    }

    @Test
    @WithMockUser(username = "Nosy.Stranger")
    @DisplayName("a stranger logging a training between two other people is a 403")
    void deniesLoggingATrainingBetweenStrangers() throws Exception {
        mockMvc.perform(post("/trainings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(trainingBody(SELF, SOMEBODY_ELSE)))
                .andExpect(status().isForbidden());

        verify(gymCrmFacade, never()).addTraining(any(), any(), any(), any(), any());
    }

    @Test
    @WithMockUser(username = SELF)
    @DisplayName("the training types list needs no self check, any authenticated user may read it")
    void allowsAnyAuthenticatedUserToReadTheTypes() throws Exception {
        when(gymCrmFacade.getTrainingTypes()).thenReturn(List.of());

        mockMvc.perform(get("/training-types"))
                .andExpect(status().isOk());
    }
}
