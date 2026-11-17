package com.giorgi.workload.messaging;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.support.GenericMessage;
import tools.jackson.databind.json.JsonMapper;

class WorkloadEventReaderTest {

    private final WorkloadEventReader reader = new WorkloadEventReader(JsonMapper.builder().build());

    @Test
    @DisplayName("a payload of any other Java type is named by its class")
    void namesUnexpectedPayloadClass() {
        RejectedEventException rejected = Assertions.assertThrows(RejectedEventException.class,
                () -> reader.read(new GenericMessage<>(new byte[]{1})));

        Assertions.assertEquals(DeadLetterReason.MALFORMED, rejected.reason());
        Assertions.assertEquals("payload is a byte[], not text", rejected.getMessage());
    }
}
