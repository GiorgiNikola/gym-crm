package com.giorgi.workload.messaging;

import java.util.UUID;

public interface ProcessedEventRegistry {

    boolean contains(UUID eventId);

    void record(UUID eventId);
}
