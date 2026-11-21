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
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
    @DisplayName("a version conflict that outlasted the retries becomes a 409 and a warning")
    void mapsVersionConflictToConflict() throws Exception {
        mockMvc.perform(get("/version-conflict"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("The workload was changed concurrently, retry the request"));

        Assertions.assertEquals(List.of("Workload change gave up after repeated version conflicts: "
                + "version 4 of trainer Sarah.Miller is stale"), logs.messages(Level.WARN));
        Assertions.assertTrue(logs.messages(Level.ERROR).isEmpty());
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
    @DisplayName("a body that cannot be read becomes a 400 with a generic message")
    void mapsUnreadableBodyToBadRequest() throws Exception {
        mockMvc.perform(post("/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Failed to read request"));
    }

    @Test
    @DisplayName("a value of the wrong type that is not an enum keeps the generic message")
    void keepsGenericMessageForOtherFormats() throws Exception {
        mockMvc.perform(post("/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sarah\",\"minutes\":\"sixty\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Failed to read request"));
    }

    @Test
    @DisplayName("an unknown enum value becomes a 400 that names the field and the allowed values")
    void mapsUnknownEnumValueToAllowedValues() throws Exception {
        mockMvc.perform(post("/leveled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"level\":\"EXTREME\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("level must be one of: LOW, MEDIUM, HIGH"));
    }

    @Test
    @DisplayName("an unknown enum value in a nested object is named with its path")
    void namesNestedEnumField() throws Exception {
        mockMvc.perform(post("/leveled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nested\":{\"level\":\"EXTREME\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("nested.level must be one of: LOW, MEDIUM, HIGH"));
    }

    @Test
    @DisplayName("an unknown enum value in a list is named by the list")
    void namesEnumFieldInsideList() throws Exception {
        mockMvc.perform(post("/leveled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"levels\":[\"LOW\",\"EXTREME\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("levels must be one of: LOW, MEDIUM, HIGH"));
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

    enum Intensity {
        LOW,
        MEDIUM,
        HIGH
    }

    record Nested(Intensity level) {
    }

    record LeveledBody(Intensity level, Nested nested, List<Intensity> levels) {
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

        @GetMapping("/version-conflict")
        void versionConflict() {
            throw new OptimisticLockingFailureException("version 4 of trainer Sarah.Miller is stale");
        }

        @GetMapping("/null-pointer")
        void nullPointer() {
            throw new NullPointerException("workload is null");
        }

        @PostMapping("/validated")
        void validated(@Valid @RequestBody ValidatedBody body) {
        }

        @PostMapping("/leveled")
        void leveled(@RequestBody LeveledBody body) {
        }

        @GetMapping("/months/{month}")
        void month(@PathVariable @Range(min = 1, max = 12) int month) {
        }
    }
}
