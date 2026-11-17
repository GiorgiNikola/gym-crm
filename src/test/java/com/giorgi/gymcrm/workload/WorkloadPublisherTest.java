package com.giorgi.gymcrm.workload;

import ch.qos.logback.classic.Level;
import com.giorgi.gymcrm.LogCapture;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.jms.UncategorizedJmsException;
import org.springframework.jms.core.JmsMessagingTemplate;
import org.springframework.util.backoff.BackOffExecution;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkloadPublisherTest {

    private static final WorkloadProperties.Messaging MESSAGING = new WorkloadProperties.Messaging("trainer.workload",
            Duration.ofSeconds(1), Duration.ofSeconds(1), 3, Duration.ofMillis(1), 2, Duration.ofSeconds(3));
    private static final WorkloadChangedEvent EVENT = WorkloadChangedEvent.of(new WorkloadChange("Sarah.Miller",
            "Sarah", "Miller", true, LocalDate.of(2026, 8, 1), 60, ActionType.ADD));

    private final JmsMessagingTemplate template = mock(JmsMessagingTemplate.class);
    private final ServiceTokenProvider tokens = mock(ServiceTokenProvider.class);
    private final WorkloadPublisher publisher = new WorkloadPublisher(template, tokens,
            new WorkloadProperties("http://workload-service", null, Duration.ofSeconds(2), Duration.ofSeconds(3),
                    MESSAGING));

    @Test
    @DisplayName("without a transaction id in the MDC the header is left out, the rest is sent")
    void leavesOutAMissingTransactionId() {
        when(tokens.writeToken()).thenReturn("write.token");

        publisher.publish(EVENT);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> headers = ArgumentCaptor.forClass(Map.class);
        verify(template).convertAndSend(eq("trainer.workload"), eq(WorkloadMessage.of(EVENT)), headers.capture());
        Assertions.assertEquals(Map.of("eventVersion", 1, "authorization", "write.token",
                "JMSXGroupID", "Sarah.Miller"), headers.getValue());
    }

    @Test
    @DisplayName("a failure that is not a JmsException is not retried, and still ends in one ERROR")
    void doesNotRetryOtherFailures() {
        when(tokens.writeToken()).thenThrow(new IllegalStateException("clock skew"));

        try (LogCapture logs = LogCapture.of(WorkloadPublisher.class)) {
            Assertions.assertDoesNotThrow(() -> publisher.publish(EVENT));

            Assertions.assertTrue(logs.messages(Level.WARN).isEmpty());
            String error = logs.messages(Level.ERROR).getFirst();
            Assertions.assertTrue(error.matches(".* attempts=1 durationMs=\\d+ cause=clock skew"), error);
        }
    }

    @Test
    @DisplayName("a JmsException is tried once and retried three times")
    void retriesThreeTimes() {
        when(tokens.writeToken()).thenReturn("write.token");
        doThrow(new UncategorizedJmsException("down")).when(template).convertAndSend(anyString(), any(), anyMap());

        try (LogCapture logs = LogCapture.of(WorkloadPublisher.class)) {
            publisher.publish(EVENT);

            Assertions.assertEquals(List.of(1, 2, 3, 4), logs.messages(Level.WARN).stream()
                    .map(warning -> Integer.parseInt(warning.replaceAll(".* attempt=(\\d+) .*", "$1")))
                    .toList());
        }
        verify(template, times(4)).convertAndSend(anyString(), any(), anyMap());
    }

    @Test
    @DisplayName("the default policy waits 200, 400 and 800 ms, retries only JmsException and stops after 3 s")
    void defaultPolicyShape() {
        WorkloadProperties.Messaging defaults = new WorkloadProperties.Messaging("trainer.workload",
                Duration.ofSeconds(1), Duration.ofSeconds(1), 3, Duration.ofMillis(200), 2, Duration.ofSeconds(3));

        RetryPolicy policy = WorkloadPublisher.retryPolicy(defaults);

        BackOffExecution backOff = policy.getBackOff().start();
        Assertions.assertEquals(List.of(200L, 400L, 800L, BackOffExecution.STOP),
                List.of(backOff.nextBackOff(), backOff.nextBackOff(), backOff.nextBackOff(), backOff.nextBackOff()));
        Assertions.assertEquals(Duration.ofSeconds(3), policy.getTimeout());
        Assertions.assertTrue(policy.shouldRetry(new UncategorizedJmsException("down")));
        Assertions.assertFalse(policy.shouldRetry(new IllegalStateException("bug")));
    }
}
