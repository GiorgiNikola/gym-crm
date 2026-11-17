package com.giorgi.workload.messaging;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class InMemoryProcessedEventRegistry implements ProcessedEventRegistry {

    private final Map<UUID, Boolean> processed;

    public InMemoryProcessedEventRegistry(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be at least 1");
        }
        processed = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<UUID, Boolean> eldest) {
                return size() > capacity;
            }
        };
    }

    @Override
    public synchronized boolean contains(UUID eventId) {
        return processed.get(eventId) != null;
    }

    @Override
    public synchronized void record(UUID eventId) {
        processed.put(eventId, Boolean.TRUE);
    }

    public synchronized int size() {
        return processed.size();
    }
}
