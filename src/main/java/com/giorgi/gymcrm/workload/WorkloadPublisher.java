package com.giorgi.gymcrm.workload;

import com.giorgi.gymcrm.web.TransactionIdFilter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.jms.JmsException;
import org.springframework.jms.core.JmsMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class WorkloadPublisher {

    private final JmsMessagingTemplate template;
    private final ServiceTokenProvider tokens;
    private final String queue;
    private final RetryTemplate retryTemplate;

    public WorkloadPublisher(JmsMessagingTemplate workloadMessagingTemplate,
                             ServiceTokenProvider tokens,
                             WorkloadProperties properties) {
        this.template = workloadMessagingTemplate;
        this.tokens = tokens;
        this.queue = properties.messaging().queue();
        this.retryTemplate = new RetryTemplate(retryPolicy(properties.messaging()));
    }

    static RetryPolicy retryPolicy(WorkloadProperties.Messaging messaging) {
        return RetryPolicy.builder()
                .includes(JmsException.class)
                .maxRetries(messaging.maxRetries())
                .delay(messaging.initialDelay())
                .multiplier(messaging.multiplier())
                .timeout(messaging.retryBudget())
                .build();
    }

    public void publish(WorkloadChangedEvent event) {
        String transactionId = MDC.get(TransactionIdFilter.MDC_KEY);
        WorkloadMessage message = WorkloadMessage.of(event);
        AtomicInteger attempts = new AtomicInteger();
        long startedAt = System.nanoTime();
        try {
            retryTemplate.execute(() -> {
                send(message, transactionId, attempts.incrementAndGet());
                return null;
            });
            log.info("Published workload event: transactionId={} queue={} eventId={} action={} trainer={} "
                            + "attempts={} durationMs={}", transactionId, queue, message.eventId(),
                    message.actionType(), message.trainerUsername(), attempts.get(), elapsedMs(startedAt));
        } catch (RetryException exhausted) {
            log.error("Workload event not published: transactionId={} queue={} eventId={} action={} trainer={} "
                            + "attempts={} durationMs={} cause={}", transactionId, queue, message.eventId(),
                    message.actionType(), message.trainerUsername(), attempts.get(), elapsedMs(startedAt),
                    exhausted.getLastException().getMessage());
        }
    }

    private void send(WorkloadMessage message, String transactionId, int attempt) {
        try {
            template.convertAndSend(queue, message, headers(message, transactionId));
        } catch (JmsException failure) {
            log.warn("Workload event send failed: transactionId={} eventId={} attempt={} cause={}",
                    transactionId, message.eventId(), attempt, failure.getMessage());
            throw failure;
        }
    }

    private Map<String, Object> headers(WorkloadMessage message, String transactionId) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(WorkloadMessageHeaders.EVENT_VERSION, WorkloadMessageHeaders.VERSION);
        headers.put(WorkloadMessageHeaders.AUTHORIZATION, tokens.writeToken());
        headers.put(WorkloadMessageHeaders.GROUP_ID, message.trainerUsername());
        if (transactionId != null) {
            headers.put(WorkloadMessageHeaders.TRANSACTION_ID, transactionId);
        }
        return headers;
    }

    private static long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
