package com.giorgi.workload.messaging;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.Objects;
import java.util.stream.Collectors;

import static com.giorgi.workload.messaging.WorkloadEventHeaders.EVENT_VERSION;
import static com.giorgi.workload.messaging.WorkloadEventHeaders.SUPPORTED_VERSION;
import static com.giorgi.workload.messaging.WorkloadEventHeaders.TYPE;
import static com.giorgi.workload.messaging.WorkloadEventHeaders.TYPE_ID;

@Component
public class WorkloadEventReader {

    private final JsonMapper jsonMapper;

    public WorkloadEventReader(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public WorkloadEvent read(Message<?> message) {
        if (!(message.getPayload() instanceof String body)) {
            throw malformed("payload is a " + payloadType(message.getPayload()) + ", not text");
        }
        MessageHeaders headers = message.getHeaders();
        if (!TYPE.equals(headers.get(TYPE_ID))) {
            throw malformed(TYPE_ID + " must be " + TYPE);
        }
        if (!SUPPORTED_VERSION.equals(String.valueOf(headers.get(EVENT_VERSION)))) {
            throw malformed(EVENT_VERSION + " must be " + SUPPORTED_VERSION);
        }
        return parse(body);
    }

    private WorkloadEvent parse(String body) {
        WorkloadEvent event;
        try {
            event = jsonMapper.readValue(body, WorkloadEvent.class);
        } catch (JacksonException exception) {
            throw malformed(describe(exception));
        }
        if (event == null) {
            throw malformed("body is empty");
        }
        return event;
    }

    private static String payloadType(Object payload) {
        return payload instanceof NonTextPayload nonText
                ? nonText.messageType()
                : payload.getClass().getSimpleName();
    }

    private static String describe(JacksonException exception) {
        String field = exception.getPath().stream()
                .map(JacksonException.Reference::getPropertyName)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("."));
        return field.isEmpty() ? "body cannot be read as a workload event" : field + " cannot be read";
    }

    private static RejectedEventException malformed(String detail) {
        return new RejectedEventException(DeadLetterReason.MALFORMED, detail);
    }
}
