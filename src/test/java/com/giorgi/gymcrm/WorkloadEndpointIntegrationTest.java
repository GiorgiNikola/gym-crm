package com.giorgi.gymcrm;

import com.giorgi.gymcrm.dto.RegistrationResponse;
import com.giorgi.gymcrm.workload.MonthlyWorkloadResponse;
import com.giorgi.gymcrm.workload.WorkloadClient;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.jdbc.JdbcTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.net.http.HttpTimeoutException;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WorkloadEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ObjectProvider<RestClient.Builder> plainRestClientBuilders;

    @MockitoBean
    private WorkloadClient workloadClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private CircuitBreaker breaker() {
        return circuitBreakerRegistry.circuitBreaker("workload");
    }

    @BeforeEach
    void closeTheBreaker() {
        breaker().reset();
    }

    @AfterEach
    void clearRegisteredRows() {
        breaker().reset();
        JdbcTestUtils.deleteFromTables(new JdbcTemplate(dataSource),
                "trainee2trainer", "trainings", "trainees", "trainers", "users");
    }

    private RegistrationResponse register(String path, String body) throws Exception {
        String response = mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(response, RegistrationResponse.class);
    }

    private RegistrationResponse registerTrainer(String firstName, String lastName) throws Exception {
        return register("/trainers", "{\"firstName\":\"" + firstName + "\",\"lastName\":\"" + lastName
                + "\",\"specialization\":\"YOGA\"}");
    }

    private ResultActions readAugust(RegistrationResponse caller, String username)
            throws Exception {
        return mockMvc.perform(get("/trainers/" + username + "/workload/2026/8")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + caller.token()));
    }

    private void healthShows(String state) throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(jsonPath("$.components.circuitBreakers.status").value("UP"))
                .andExpect(jsonPath("$.components.circuitBreakers.details.workload.state").value(state));
    }

    @Test
    @DisplayName("a trainer reads their own minutes of a month")
    void readsOwnMinutes() throws Exception {
        RegistrationResponse sarah = registerTrainer("Sarah", "Miller");
        when(workloadClient.getMonthlyWorkload(sarah.username(), 2026, 8))
                .thenReturn(new MonthlyWorkloadResponse(sarah.username(), 2026, 8, 150));

        readAugust(sarah, sarah.username())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trainerUsername").value(sarah.username()))
                .andExpect(jsonPath("$.year").value(2026))
                .andExpect(jsonPath("$.month").value(8))
                .andExpect(jsonPath("$.trainingSummaryDuration").value(150));
    }

    @Test
    @DisplayName("a trainer workload-service does not know yet has 0 minutes")
    void unknownToWorkloadIsZero() throws Exception {
        RegistrationResponse sarah = registerTrainer("Sarah", "Miller");
        when(workloadClient.getMonthlyWorkload(sarah.username(), 2026, 8)).thenThrow(HttpClientErrorException
                .create(HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], null));

        readAugust(sarah, sarah.username())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trainingSummaryDuration").value(0));
    }

    @Test
    @DisplayName("an open breaker answers 503 without calling workload-service")
    void openBreakerIsServiceUnavailable() throws Exception {
        RegistrationResponse sarah = registerTrainer("Sarah", "Miller");
        breaker().transitionToOpenState();

        readAugust(sarah, sarah.username())
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.detail").value("Workload service is temporarily unavailable"));

        verifyNoInteractions(workloadClient);
    }

    @Test
    @DisplayName("a 5xx from workload-service is a 503 problem")
    void serverErrorIsServiceUnavailable() throws Exception {
        RegistrationResponse sarah = registerTrainer("Sarah", "Miller");
        when(workloadClient.getMonthlyWorkload(sarah.username(), 2026, 8)).thenThrow(HttpServerErrorException
                .create(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", HttpHeaders.EMPTY, new byte[0], null));

        readAugust(sarah, sarah.username())
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Workload service is temporarily unavailable"));
    }

    @Test
    @DisplayName("a timeout from workload-service is a 503 problem")
    void timeoutIsServiceUnavailable() throws Exception {
        RegistrationResponse sarah = registerTrainer("Sarah", "Miller");
        when(workloadClient.getMonthlyWorkload(sarah.username(), 2026, 8))
                .thenThrow(new ResourceAccessException("I/O error", new HttpTimeoutException("request timed out")));

        readAugust(sarah, sarah.username()).andExpect(status().isServiceUnavailable());
    }

    @Test
    @DisplayName("a trainer cannot read another trainer's minutes")
    void anotherTrainerIsForbidden() throws Exception {
        RegistrationResponse sarah = registerTrainer("Sarah", "Miller");
        RegistrationResponse robert = registerTrainer("Robert", "Taylor");

        readAugust(robert, sarah.username()).andExpect(status().isForbidden());

        verifyNoInteractions(workloadClient);
    }

    @Test
    @DisplayName("a trainee asking under their own username is told there is no such trainer")
    void traineeIsNotATrainer() throws Exception {
        RegistrationResponse john = register("/trainees", "{\"firstName\":\"John\",\"lastName\":\"Smith\"}");

        readAugust(john, john.username())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Trainer not found: " + john.username()));

        verifyNoInteractions(workloadClient);
    }

    @Test
    @DisplayName("a month outside 1 to 12 is a bad request")
    void rejectsAnImpossibleMonth() throws Exception {
        RegistrationResponse sarah = registerTrainer("Sarah", "Miller");

        mockMvc.perform(get("/trainers/" + sarah.username() + "/workload/2026/13")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sarah.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Month must be between 1 and 12"));

        verify(workloadClient, never()).getMonthlyWorkload(anyString(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("the breaker opens after 5 failed calls and health shows it")
    void opensAfterTheConfiguredFailures() throws Exception {
        RegistrationResponse sarah = registerTrainer("Sarah", "Miller");
        when(workloadClient.getMonthlyWorkload(sarah.username(), 2026, 8)).thenThrow(HttpServerErrorException
                .create(HttpStatus.BAD_GATEWAY, "Bad Gateway", HttpHeaders.EMPTY, new byte[0], null));
        healthShows("CLOSED");

        for (int call = 1; call <= 4; call++) {
            readAugust(sarah, sarah.username()).andExpect(status().isServiceUnavailable());
        }
        Assertions.assertEquals(CircuitBreaker.State.CLOSED, breaker().getState());

        readAugust(sarah, sarah.username()).andExpect(status().isServiceUnavailable());

        Assertions.assertEquals(CircuitBreaker.State.OPEN, breaker().getState());
        healthShows("OPEN");
        verify(workloadClient, times(5)).getMonthlyWorkload(sarah.username(), 2026, 8);
    }

    @Test
    @DisplayName("half of the last calls failing is enough to open the breaker, less is not")
    void opensAtTheFailureRateThreshold() throws Exception {
        RegistrationResponse sarah = registerTrainer("Sarah", "Miller");
        HttpServerErrorException unavailable = HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE,
                "Service Unavailable", HttpHeaders.EMPTY, new byte[0], null);
        MonthlyWorkloadResponse month = new MonthlyWorkloadResponse(sarah.username(), 2026, 8, 30);
        when(workloadClient.getMonthlyWorkload(sarah.username(), 2026, 8))
                .thenReturn(month, month, month, month)
                .thenThrow(unavailable, unavailable, unavailable, unavailable);

        for (int call = 1; call <= 7; call++) {
            readAugust(sarah, sarah.username());
        }
        Assertions.assertEquals(CircuitBreaker.State.CLOSED, breaker().getState());

        readAugust(sarah, sarah.username());

        Assertions.assertEquals(CircuitBreaker.State.OPEN, breaker().getState());
    }

    @Test
    @DisplayName("4xx answers never open the breaker")
    void clientErrorsDoNotCount() throws Exception {
        RegistrationResponse sarah = registerTrainer("Sarah", "Miller");
        when(workloadClient.getMonthlyWorkload(sarah.username(), 2026, 8)).thenThrow(HttpClientErrorException
                .create(HttpStatus.FORBIDDEN, "Forbidden", HttpHeaders.EMPTY, new byte[0], null));

        for (int call = 1; call <= 10; call++) {
            readAugust(sarah, sarah.username()).andExpect(status().isServiceUnavailable());
        }

        Assertions.assertEquals(CircuitBreaker.State.CLOSED, breaker().getState());
        Assertions.assertEquals(0, breaker().getMetrics().getNumberOfFailedCalls());
    }

    @Test
    @DisplayName("no registered instance of workload-service counts as a failure")
    void noInstanceCounts() throws Exception {
        RegistrationResponse sarah = registerTrainer("Sarah", "Miller");
        when(workloadClient.getMonthlyWorkload(sarah.username(), 2026, 8))
                .thenThrow(new IllegalStateException("No instances available for workload-service"));

        for (int call = 1; call <= 5; call++) {
            readAugust(sarah, sarah.username()).andExpect(status().isServiceUnavailable());
        }

        Assertions.assertEquals(CircuitBreaker.State.OPEN, breaker().getState());
    }

    @Test
    @DisplayName("the breaker closes again after two good calls in half open")
    void closesAfterHalfOpen() throws Exception {
        RegistrationResponse sarah = registerTrainer("Sarah", "Miller");
        when(workloadClient.getMonthlyWorkload(sarah.username(), 2026, 8))
                .thenReturn(new MonthlyWorkloadResponse(sarah.username(), 2026, 8, 30));
        breaker().transitionToOpenState();
        breaker().transitionToHalfOpenState();
        healthShows("HALF_OPEN");

        readAugust(sarah, sarah.username()).andExpect(status().isOk());
        readAugust(sarah, sarah.username()).andExpect(status().isOk());

        Assertions.assertEquals(CircuitBreaker.State.CLOSED, breaker().getState());
    }

    @Test
    @DisplayName("the load balanced builder is never handed to a plain RestClient.Builder injection point like Eureka's")
    void loadBalancedBuilderStaysOutOfOtherClients() {
        Assertions.assertNull(plainRestClientBuilders.getIfAvailable());
    }

    @Test
    @DisplayName("the breaker is configured as agreed")
    void carriesTheAgreedConfiguration() {
        CircuitBreakerConfig config = breaker().getCircuitBreakerConfig();

        Assertions.assertEquals(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED, config.getSlidingWindowType());
        Assertions.assertEquals(10, config.getSlidingWindowSize());
        Assertions.assertEquals(5, config.getMinimumNumberOfCalls());
        Assertions.assertEquals(50f, config.getFailureRateThreshold());
        Assertions.assertEquals(10_000L, config.getWaitIntervalFunctionInOpenState().apply(1));
        Assertions.assertEquals(2, config.getPermittedNumberOfCallsInHalfOpenState());
    }
}
