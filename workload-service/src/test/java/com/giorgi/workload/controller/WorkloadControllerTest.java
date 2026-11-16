package com.giorgi.workload.controller;

import com.giorgi.workload.exception.TrainerNotFoundException;
import com.giorgi.workload.model.ActionType;
import com.giorgi.workload.model.MonthSummary;
import com.giorgi.workload.model.TrainerWorkload;
import com.giorgi.workload.model.WorkloadChange;
import com.giorgi.workload.model.YearSummary;
import com.giorgi.workload.service.WorkloadService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WorkloadController.class)
class WorkloadControllerTest {

    private static final String USERNAME = "Sarah.Miller";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WorkloadService workloadService;

    private String body(String username, String firstName, String lastName, String isActive,
                        String date, String duration, String actionType) {
        return "{\"trainerUsername\":" + username
                + ",\"trainerFirstName\":" + firstName
                + ",\"trainerLastName\":" + lastName
                + ",\"isActive\":" + isActive
                + ",\"trainingDate\":" + date
                + ",\"trainingDuration\":" + duration
                + ",\"actionType\":" + actionType + "}";
    }

    private String bodyWithUsername(String username) {
        return body(username, "\"Sarah\"", "\"Miller\"", "true", "\"2026-08-01\"", "60", "\"ADD\"");
    }

    private String bodyWithDate(String date) {
        return body("\"Sarah.Miller\"", "\"Sarah\"", "\"Miller\"", "true", date, "60", "\"ADD\"");
    }

    private String bodyWithDuration(String duration) {
        return body("\"Sarah.Miller\"", "\"Sarah\"", "\"Miller\"", "true", "\"2026-08-01\"", duration, "\"ADD\"");
    }

    private String bodyWithActionType(String actionType) {
        return body("\"Sarah.Miller\"", "\"Sarah\"", "\"Miller\"", "true", "\"2026-08-01\"", "60", actionType);
    }

    private String validBody() {
        return bodyWithActionType("\"ADD\"");
    }

    private ResultActions postWorkload(String json) throws Exception {
        return mockMvc.perform(post("/workloads")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private TrainerWorkload sarah() {
        return new TrainerWorkload(USERNAME, "Sarah", "Miller", true, List.of(
                new YearSummary(2026, List.of(new MonthSummary(8, 150), new MonthSummary(9, 45))),
                new YearSummary(2027, List.of(new MonthSummary(1, 30)))));
    }

    @Test
    @DisplayName("an add comes back as 200 with nothing in the body")
    void recordsAdd() throws Exception {
        postWorkload(validBody())
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        verify(workloadService).apply(new WorkloadChange(USERNAME, "Sarah", "Miller", true,
                LocalDate.of(2026, 8, 1), 60, ActionType.ADD));
    }

    @Test
    @DisplayName("a delete for an inactive trainer comes back as 200 with nothing in the body")
    void recordsDelete() throws Exception {
        postWorkload(body("\"Sarah.Miller\"", "\"Sarah\"", "\"Miller\"", "false",
                "\"2026-08-01\"", "45", "\"DELETE\""))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        verify(workloadService).apply(new WorkloadChange(USERNAME, "Sarah", "Miller", false,
                LocalDate.of(2026, 8, 1), 45, ActionType.DELETE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"\"", "\"   \"", "null"})
    @DisplayName("a missing trainer username comes back as 400")
    void rejectsBlankUsername(String username) throws Exception {
        postWorkload(bodyWithUsername(username))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors.trainerUsername").value("must not be blank"));

        verifyNoInteractions(workloadService);
    }

    @Test
    @DisplayName("a blank first name comes back as 400")
    void rejectsBlankFirstName() throws Exception {
        postWorkload(body("\"Sarah.Miller\"", "\" \"", "\"Miller\"", "true", "\"2026-08-01\"", "60", "\"ADD\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.trainerFirstName").value("must not be blank"));
    }

    @Test
    @DisplayName("a missing last name comes back as 400")
    void rejectsMissingLastName() throws Exception {
        postWorkload(body("\"Sarah.Miller\"", "\"Sarah\"", "null", "true", "\"2026-08-01\"", "60", "\"ADD\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.trainerLastName").value("must not be blank"));
    }

    @Test
    @DisplayName("a missing active flag comes back as 400")
    void rejectsMissingActiveFlag() throws Exception {
        postWorkload(body("\"Sarah.Miller\"", "\"Sarah\"", "\"Miller\"", "null", "\"2026-08-01\"", "60", "\"ADD\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.isActive").value("must not be null"));
    }

    @Test
    @DisplayName("a missing training date comes back as 400")
    void rejectsMissingDate() throws Exception {
        postWorkload(bodyWithDate("null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.trainingDate").value("must not be null"));
    }

    @Test
    @DisplayName("a missing duration comes back as 400")
    void rejectsMissingDuration() throws Exception {
        postWorkload(bodyWithDuration("null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.trainingDuration").value("must not be null"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-45"})
    @DisplayName("a duration that is not positive comes back as 400")
    void rejectsNonPositiveDuration(String duration) throws Exception {
        postWorkload(bodyWithDuration(duration))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.trainingDuration").value("must be greater than 0"));

        verifyNoInteractions(workloadService);
    }

    @Test
    @DisplayName("a missing action type comes back as 400")
    void rejectsMissingActionType() throws Exception {
        postWorkload(bodyWithActionType("null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.actionType").value("must not be null"));
    }

    @Test
    @DisplayName("an empty object reports every required field at once")
    void reportsEveryMissingField() throws Exception {
        postWorkload("{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(7))
                .andExpect(jsonPath("$.errors.trainerUsername").exists())
                .andExpect(jsonPath("$.errors.trainerFirstName").exists())
                .andExpect(jsonPath("$.errors.trainerLastName").exists())
                .andExpect(jsonPath("$.errors.isActive").exists())
                .andExpect(jsonPath("$.errors.trainingDate").exists())
                .andExpect(jsonPath("$.errors.trainingDuration").exists())
                .andExpect(jsonPath("$.errors.actionType").exists());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{\"trainerUsername\":", "not json"})
    @DisplayName("a missing or malformed body comes back as 400")
    void rejectsUnreadableBody(String json) throws Exception {
        postWorkload(json)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(workloadService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"UPDATE\"", "\"add\"", "\"\""})
    @DisplayName("an unknown action type comes back as 400")
    void rejectsUnknownActionType(String actionType) throws Exception {
        postWorkload(bodyWithActionType(actionType))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(workloadService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"01/08/2026\"", "\"2026-13-45\"", "\"yesterday\""})
    @DisplayName("a date that is not yyyy-MM-dd comes back as 400")
    void rejectsWrongDateFormat(String date) throws Exception {
        postWorkload(bodyWithDate(date))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(workloadService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"sixty\"", "true", "[60]"})
    @DisplayName("a duration of the wrong type comes back as 400")
    void rejectsWrongDurationType(String duration) throws Exception {
        postWorkload(bodyWithDuration(duration))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(workloadService);
    }

    @Test
    @DisplayName("an unreadable body says nothing about the parser")
    void hidesParserDetails() throws Exception {
        MvcResult result = postWorkload(bodyWithActionType("\"UPDATE\""))
                .andExpect(status().isBadRequest())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        Assertions.assertFalse(response.contains("jackson"), response);
        Assertions.assertFalse(response.contains("ActionType"), response);
    }

    @Test
    @DisplayName("the summary comes back with years and months in the stored order")
    void returnsSummary() throws Exception {
        when(workloadService.getWorkload(USERNAME)).thenReturn(sarah());

        mockMvc.perform(get("/workloads/{username}", USERNAME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$.trainerUsername").value(USERNAME))
                .andExpect(jsonPath("$.trainerFirstName").value("Sarah"))
                .andExpect(jsonPath("$.trainerLastName").value("Miller"))
                .andExpect(jsonPath("$.trainerStatus").value(true))
                .andExpect(jsonPath("$.years.length()").value(2))
                .andExpect(jsonPath("$.years[0].year").value(2026))
                .andExpect(jsonPath("$.years[0].months.length()").value(2))
                .andExpect(jsonPath("$.years[0].months[0].month").value(8))
                .andExpect(jsonPath("$.years[0].months[0].trainingSummaryDuration").value(150))
                .andExpect(jsonPath("$.years[0].months[1].month").value(9))
                .andExpect(jsonPath("$.years[0].months[1].trainingSummaryDuration").value(45))
                .andExpect(jsonPath("$.years[1].year").value(2027))
                .andExpect(jsonPath("$.years[1].months[0].month").value(1))
                .andExpect(jsonPath("$.years[1].months[0].trainingSummaryDuration").value(30));
    }

    @Test
    @DisplayName("the summary of an unknown trainer comes back as 404")
    void rejectsSummaryOfUnknownTrainer() throws Exception {
        when(workloadService.getWorkload("Nobody.Here")).thenThrow(new TrainerNotFoundException("Nobody.Here"));

        mockMvc.perform(get("/workloads/{username}", "Nobody.Here"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Trainer not found: Nobody.Here"));
    }

    @Test
    @DisplayName("a month comes back with the trainer, the year, the month and the minutes")
    void returnsMonthlyWorkload() throws Exception {
        when(workloadService.getMonthlyDuration(USERNAME, 2026, 8)).thenReturn(150L);

        mockMvc.perform(get("/workloads/{username}/{year}/{month}", USERNAME, 2026, 8))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$.trainerUsername").value(USERNAME))
                .andExpect(jsonPath("$.year").value(2026))
                .andExpect(jsonPath("$.month").value(8))
                .andExpect(jsonPath("$.trainingSummaryDuration").value(150));
    }

    @Test
    @DisplayName("a month with nothing recorded comes back as 200 with zero minutes")
    void returnsZeroForEmptyMonth() throws Exception {
        when(workloadService.getMonthlyDuration(USERNAME, 2026, 12)).thenReturn(0L);

        mockMvc.perform(get("/workloads/{username}/{year}/{month}", USERNAME, 2026, 12))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trainingSummaryDuration").value(0));
    }

    @Test
    @DisplayName("a month of an unknown trainer comes back as 404")
    void rejectsMonthOfUnknownTrainer() throws Exception {
        when(workloadService.getMonthlyDuration("Nobody.Here", 2026, 8))
                .thenThrow(new TrainerNotFoundException("Nobody.Here"));

        mockMvc.perform(get("/workloads/{username}/{year}/{month}", "Nobody.Here", 2026, 8))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Trainer not found: Nobody.Here"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 13, -1})
    @DisplayName("a month outside 1 to 12 comes back as 400")
    void rejectsMonthOutOfRange(int month) throws Exception {
        mockMvc.perform(get("/workloads/{username}/{year}/{month}", USERNAME, 2026, month))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors.month").value("must be between 1 and 12"));

        verifyNoInteractions(workloadService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/workloads/Sarah.Miller/2026/august", "/workloads/Sarah.Miller/next/8",
            "/workloads/Sarah.Miller/99999999999/8"})
    @DisplayName("a year or a month that is not a number comes back as 400")
    void rejectsNonNumericPeriod(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(workloadService);
    }

    @Test
    @DisplayName("an unknown path comes back as a 404 problem")
    void reportsUnknownPath() throws Exception {
        mockMvc.perform(get("/no-such-path"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("a method the path does not support comes back as a 405 problem")
    void reportsUnsupportedMethod() throws Exception {
        mockMvc.perform(put("/workloads").contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("a null pointer in the service comes back as a 500 problem without the cause")
    void hidesUnexpectedFailure() throws Exception {
        doThrow(new NullPointerException("years is null")).when(workloadService).apply(any());

        MvcResult result = postWorkload(validBody())
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Unexpected error"))
                .andReturn();

        String response = result.getResponse().getContentAsString();
        Assertions.assertFalse(response.contains("years is null"), response);
        Assertions.assertFalse(response.contains("NullPointerException"), response);
    }
}
