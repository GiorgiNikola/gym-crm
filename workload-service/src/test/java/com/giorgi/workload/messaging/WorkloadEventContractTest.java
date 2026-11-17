package com.giorgi.workload.messaging;

import com.giorgi.workload.model.ActionType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.messaging.support.GenericMessage;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

class WorkloadEventContractTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final WorkloadEventReader reader = new WorkloadEventReader(jsonMapper);

    private static String sample(String name) throws IOException {
        return new ClassPathResource("contract/" + name).getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("the message gym-crm publishes, as its contract sample, reads into a workload event")
    void readsTheGymCrmSample() throws IOException {
        JsonNode headers = jsonMapper.readTree(sample("workload-change-headers.json"));
        Map<String, Object> messageHeaders = Map.of(
                WorkloadEventHeaders.TYPE_ID, headers.get(WorkloadEventHeaders.TYPE_ID).asString(),
                WorkloadEventHeaders.EVENT_VERSION, headers.get(WorkloadEventHeaders.EVENT_VERSION).asInt(),
                WorkloadEventHeaders.TRANSACTION_ID, headers.get(WorkloadEventHeaders.TRANSACTION_ID).asString(),
                WorkloadEventHeaders.AUTHORIZATION, headers.get(WorkloadEventHeaders.AUTHORIZATION).asString(),
                WorkloadEventHeaders.GROUP_ID, headers.get(WorkloadEventHeaders.GROUP_ID).asString());

        WorkloadEvent event = reader.read(new GenericMessage<>(sample("workload-change-body.json"), messageHeaders));

        Assertions.assertEquals(new WorkloadEvent(UUID.fromString("3f1c9a52-7d0e-4b8a-9c61-2e5f8d4a7b10"),
                Instant.parse("2026-08-01T09:15:30.123456Z"), "Sarah.Miller", "Sarah", "Miller", true,
                LocalDate.of(2026, 8, 1), 60, ActionType.ADD), event);
        Assertions.assertEquals(event.trainerUsername(), headers.get(WorkloadEventHeaders.GROUP_ID).asString());
    }
}
