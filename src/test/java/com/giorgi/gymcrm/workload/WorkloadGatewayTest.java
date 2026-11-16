package com.giorgi.gymcrm.workload;

import ch.qos.logback.classic.Level;
import com.giorgi.gymcrm.LogCapture;
import com.giorgi.gymcrm.exception.WorkloadUnavailableException;
import com.giorgi.gymcrm.web.TransactionIdFilter;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkloadGatewayTest {

    private static final String TRANSACTION_ID = "0b7e6c1a-1111-2222-3333-444455556666";
    private static final WorkloadRequest ADD = new WorkloadRequest("Sarah.Miller", "Sarah", "Miller", true,
            LocalDate.of(2026, 8, 1), 60, ActionType.ADD);

    private final WorkloadClient client = mock(WorkloadClient.class);
    private final CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
    private final WorkloadGateway gateway = new WorkloadGateway(client, Breakers.onCallerThread(registry));

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    static Stream<Arguments> unreachable() {
        return Stream.of(
                Arguments.of(HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable",
                        HttpHeaders.EMPTY, new byte[0], null), "status 503"),
                Arguments.of(new ResourceAccessException("I/O error", new ConnectException("Connection refused")),
                        "ConnectException"),
                Arguments.of(new ResourceAccessException("I/O error", new HttpTimeoutException("request timed out")),
                        "HttpTimeoutException"));
    }

    @Test
    @DisplayName("a change is handed to the client as it is")
    void sendsTheChange() {
        gateway.send(ADD);

        verify(client).recordChange(ADD);
    }

    @ParameterizedTest
    @MethodSource("unreachable")
    @DisplayName("a failed send is logged as a warning with the transaction id and never thrown")
    void logsAndSwallowsFailures(RuntimeException failure, String reason) {
        MDC.put(TransactionIdFilter.MDC_KEY, TRANSACTION_ID);
        doThrow(failure).when(client).recordChange(ADD);

        try (LogCapture logs = LogCapture.of(WorkloadGateway.class)) {
            Assertions.assertDoesNotThrow(() -> gateway.send(ADD));

            Assertions.assertEquals(List.of("Workload change not sent: transactionId=" + TRANSACTION_ID
                    + " action=ADD trainer=Sarah.Miller month=2026-08 minutes=60 reason=" + reason),
                    logs.messages(Level.WARN));
        }
    }

    @Test
    @DisplayName("the warning names neither the trainer's names nor the token")
    void warningLeavesOutPersonalData() {
        doThrow(new ResourceAccessException("I/O error")).when(client).recordChange(ADD);

        try (LogCapture logs = LogCapture.of(WorkloadGateway.class)) {
            gateway.send(ADD);

            String warning = logs.messages(Level.WARN).getFirst();
            String withoutUsername = warning.replace("Sarah.Miller", "");
            Assertions.assertFalse(withoutUsername.contains("Sarah"), warning);
            Assertions.assertFalse(withoutUsername.contains("Miller"), warning);
            Assertions.assertFalse(warning.contains("Bearer"), warning);
            Assertions.assertTrue(warning.contains("transactionId=null"), warning);
        }
    }

    @Test
    @DisplayName("an open breaker skips the call and says so in the warning")
    void openBreakerSkipsTheSend() {
        registry.circuitBreaker(WorkloadGateway.BREAKER).transitionToOpenState();

        try (LogCapture logs = LogCapture.of(WorkloadGateway.class)) {
            gateway.send(ADD);

            Assertions.assertTrue(logs.messages(Level.WARN).getFirst().endsWith("reason=circuit breaker open"));
        }
        verify(client, never()).recordChange(ADD);
    }

    @Test
    @DisplayName("the monthly minutes come from workload-service")
    void readsTheMonthlyMinutes() {
        when(client.getMonthlyWorkload("Sarah.Miller", 2026, 8))
                .thenReturn(new MonthlyWorkloadResponse("Sarah.Miller", 2026, 8, 150));

        Assertions.assertEquals(150, gateway.monthlyMinutes("Sarah.Miller", 2026, 8));
    }

    @Test
    @DisplayName("a trainer workload-service does not know has 0 minutes")
    void unknownTrainerHasNoMinutes() {
        when(client.getMonthlyWorkload("Sarah.Miller", 2026, 8)).thenThrow(HttpClientErrorException.create(
                HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], null));

        Assertions.assertEquals(0, gateway.monthlyMinutes("Sarah.Miller", 2026, 8));
        Assertions.assertEquals(0, registry.circuitBreaker(WorkloadGateway.BREAKER).getMetrics()
                .getNumberOfFailedCalls());
    }

    @ParameterizedTest
    @MethodSource("unreachable")
    @DisplayName("a failed read becomes WorkloadUnavailableException and is logged")
    void failedReadIsUnavailable(RuntimeException failure, String reason) {
        MDC.put(TransactionIdFilter.MDC_KEY, TRANSACTION_ID);
        when(client.getMonthlyWorkload("Sarah.Miller", 2026, 8)).thenThrow(failure);

        try (LogCapture logs = LogCapture.of(WorkloadGateway.class)) {
            WorkloadUnavailableException thrown = Assertions.assertThrows(WorkloadUnavailableException.class,
                    () -> gateway.monthlyMinutes("Sarah.Miller", 2026, 8));

            Assertions.assertSame(failure, thrown.getCause());
            Assertions.assertEquals(List.of("Workload not read: transactionId=" + TRANSACTION_ID
                    + " trainer=Sarah.Miller month=2026-08 reason=" + reason), logs.messages(Level.WARN));
        }
    }
}
