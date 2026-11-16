package com.giorgi.workload.controller;

import ch.qos.logback.classic.Level;
import com.giorgi.workload.LogCapture;
import com.giorgi.workload.exception.TrainerNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.hibernate.validator.constraints.Range;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    private LogCapture logs;

    @BeforeEach
    void captureLogs() {
        logs = LogCapture.of(GlobalExceptionHandler.class);
    }

    @AfterEach
    void releaseLogs() {
        logs.close();
    }

    @Test
    @DisplayName("an unknown trainer becomes a 404 and is not logged as an error")
    void mapsTrainerNotFoundToNotFound() throws Exception {
        mockMvc.perform(get("/trainer-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Trainer not found: Sarah.Miller"));

        Assertions.assertTrue(logs.messages(Level.ERROR).isEmpty());
    }

    @Test
    @DisplayName("anything else becomes a 500 that says nothing about the cause")
    void mapsUnknownExceptionToServerError() throws Exception {
        MvcResult result = mockMvc.perform(get("/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.detail").value("Unexpected error"))
                .andReturn();

        String response = result.getResponse().getContentAsString();
        Assertions.assertFalse(response.contains("connection pool exhausted"), response);
        Assertions.assertFalse(response.contains("IllegalStateException"), response);
    }

    @Test
    @DisplayName("a null pointer becomes the same 500 problem")
    void mapsNullPointerToServerError() throws Exception {
        mockMvc.perform(get("/null-pointer"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Unexpected error"));
    }

    @Test
    @DisplayName("an unexpected failure is logged once at error")
    void logsUnexpectedFailure() throws Exception {
        mockMvc.perform(get("/unexpected"));

        Assertions.assertEquals(1, logs.messages(Level.ERROR).size());
    }

    @Test
    @DisplayName("an invalid body becomes a 400 with a message per field")
    void mapsInvalidBodyToFieldMessages() throws Exception {
        mockMvc.perform(post("/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \",\"minutes\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors.name").value("must not be blank"))
                .andExpect(jsonPath("$.errors.minutes").value("must be greater than 0"));
    }

    @Test
    @DisplayName("an invalid path value becomes a 400 with a message for the parameter")
    void mapsInvalidParameterToParameterMessage() throws Exception {
        mockMvc.perform(get("/months/{month}", 13))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors.month").value("must be between 1 and 12"));
    }

    @Test
    @DisplayName("a body that cannot be read becomes a 400")
    void mapsUnreadableBodyToBadRequest() throws Exception {
        mockMvc.perform(post("/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("a path value of the wrong type becomes a 400")
    void mapsTypeMismatchToBadRequest() throws Exception {
        mockMvc.perform(get("/months/{month}", "august"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("a path with no handler becomes a 404")
    void mapsMissingHandlerToNotFound() throws Exception {
        mockMvc.perform(get("/no-such-path"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404));
    }

    record ValidatedBody(@NotBlank String name, @Positive int minutes) {
    }

    @RestController
    class ThrowingController {

        @GetMapping("/trainer-not-found")
        void trainerNotFound() {
            throw new TrainerNotFoundException("Sarah.Miller");
        }

        @GetMapping("/unexpected")
        void unexpected() {
            throw new IllegalStateException("connection pool exhausted");
        }

        @GetMapping("/null-pointer")
        void nullPointer() {
            throw new NullPointerException("workload is null");
        }

        @PostMapping("/validated")
        void validated(@Valid @RequestBody ValidatedBody body) {
        }

        @GetMapping("/months/{month}")
        void month(@PathVariable @Range(min = 1, max = 12) int month) {
        }
    }
}
