package com.giorgi.workload.messaging;

import com.giorgi.workload.config.MessagingConfig;
import com.giorgi.workload.logging.TransactionIds;
import com.giorgi.workload.service.WorkloadService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsMessagingTemplate;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class WorkloadEventListener {

    private static final String UNKNOWN = "-";

    private final ServiceTokenVerifier tokenVerifier;
    private final WorkloadEventReader reader;
    private final Validator validator;
    private final ProcessedEventRegistry processedEvents;
    private final WorkloadService workloadService;
    private final JmsMessagingTemplate deadLetterTemplate;
    private final WorkloadMessagingProperties properties;

    public WorkloadEventListener(ServiceTokenVerifier tokenVerifier,
                                 WorkloadEventReader reader,
                                 Validator validator,
                                 ProcessedEventRegistry processedEvents,
                                 WorkloadService workloadService,
                                 JmsMessagingTemplate deadLetterTemplate,
                                 WorkloadMessagingProperties properties) {
        this.tokenVerifier = tokenVerifier;
        this.reader = reader;
        this.validator = validator;
        this.processedEvents = processedEvents;
        this.workloadService = workloadService;
        this.deadLetterTemplate = deadLetterTemplate;
        this.properties = properties;
    }

    @JmsListener(destination = "${workload.messaging.queue}",
            containerFactory = MessagingConfig.LISTENER_CONTAINER_FACTORY)
    public void onMessage(Message<?> message) {
        String transactionId = TransactionIds.resolve(stringHeader(message, WorkloadEventHeaders.TRANSACTION_ID));
        MDC.put(TransactionIds.MDC_KEY, transactionId);
        Delivery delivery = new Delivery(message, transactionId);
        try {
            handle(delivery);
        } catch (RuntimeException exception) {
            log.error("{}", delivery.summary("FAILED " + exception.getClass().getSimpleName() + ": "
                    + exception.getMessage()));
            throw exception;
        } finally {
            MDC.remove(TransactionIds.MDC_KEY);
        }
    }

    private void handle(Delivery delivery) {
        try {
            tokenVerifier.verify(stringHeader(delivery.message, WorkloadEventHeaders.AUTHORIZATION));
            delivery.event = reader.read(delivery.message);
            validate(delivery.event);
        } catch (RejectedEventException rejected) {
            forwardToDeadLetterQueue(delivery, rejected);
            log.warn("{}", delivery.summary("DEAD_LETTERED " + rejected.reason() + " (" + rejected.getMessage() + ")"));
            return;
        }
        apply(delivery);
    }

    private void validate(WorkloadEvent event) {
        Set<ConstraintViolation<WorkloadEvent>> violations = validator.validate(event);
        if (!violations.isEmpty()) {
            throw new RejectedEventException(DeadLetterReason.INVALID, violations.stream()
                    .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                    .sorted()
                    .collect(Collectors.joining("; ")));
        }
    }

    private void apply(Delivery delivery) {
        WorkloadEvent event = delivery.event;
        if (processedEvents.contains(event.eventId())) {
            log.info("{}", delivery.summary("DUPLICATE"));
            return;
        }
        workloadService.apply(event.toChange());
        processedEvents.record(event.eventId());
        log.info("{}", delivery.summary("APPLIED"));
    }

    private void forwardToDeadLetterQueue(Delivery delivery, RejectedEventException rejected) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(WorkloadEventHeaders.DLQ_REASON, rejected.reason().name());
        headers.put(WorkloadEventHeaders.DLQ_DETAIL, rejected.getMessage());
        headers.put(WorkloadEventHeaders.ORIGINAL_QUEUE, properties.queue());
        headers.put(WorkloadEventHeaders.TRANSACTION_ID, delivery.transactionId);
        String eventId = delivery.field(WorkloadEvent::eventId);
        if (!UNKNOWN.equals(eventId)) {
            headers.put(WorkloadEventHeaders.EVENT_ID, eventId);
        }
        Object body = delivery.message.getPayload() instanceof String text ? text : "";
        deadLetterTemplate.convertAndSend(properties.deadLetterQueue(), body, headers);
    }

    private static String stringHeader(Message<?> message, String name) {
        return message.getHeaders().get(name) instanceof String value ? value : null;
    }

    private final class Delivery {

        private final Message<?> message;
        private final String transactionId;
        private final long startedAt = System.nanoTime();
        private WorkloadEvent event;

        private Delivery(Message<?> message, String transactionId) {
            this.message = message;
            this.transactionId = transactionId;
        }

        private String field(Function<WorkloadEvent, Object> getter) {
            return event == null ? UNKNOWN : Objects.toString(getter.apply(event), UNKNOWN);
        }

        private String summary(String outcome) {
            long tookMs = (System.nanoTime() - startedAt) / 1_000_000;
            return "%s event %s %s for trainer %s: %s in %d ms, delivery %s".formatted(
                    properties.queue(), field(WorkloadEvent::eventId), field(WorkloadEvent::actionType),
                    field(WorkloadEvent::trainerUsername), outcome, tookMs,
                    message.getHeaders().get(WorkloadEventHeaders.DELIVERY_COUNT));
        }
    }
}
