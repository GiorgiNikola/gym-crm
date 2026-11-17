package com.giorgi.workload.messaging;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

class InMemoryProcessedEventRegistryTest {

    private final UUID first = UUID.randomUUID();
    private final UUID second = UUID.randomUUID();
    private final UUID third = UUID.randomUUID();

    @Test
    @DisplayName("a recorded id is known and an unseen one is not")
    void remembersRecordedIds() {
        InMemoryProcessedEventRegistry registry = new InMemoryProcessedEventRegistry(10);
        registry.record(first);

        Assertions.assertTrue(registry.contains(first));
        Assertions.assertFalse(registry.contains(second));
    }

    @Test
    @DisplayName("past its capacity the registry forgets the least recently used id")
    void evictsLeastRecentlyUsed() {
        InMemoryProcessedEventRegistry registry = new InMemoryProcessedEventRegistry(2);
        registry.record(first);
        registry.record(second);
        registry.contains(first);
        registry.record(third);

        Assertions.assertEquals(2, registry.size());
        Assertions.assertTrue(registry.contains(first));
        Assertions.assertFalse(registry.contains(second));
        Assertions.assertTrue(registry.contains(third));
    }

    @Test
    @DisplayName("recording the same id again does not grow the registry")
    void ignoresRepeatedRecord() {
        InMemoryProcessedEventRegistry registry = new InMemoryProcessedEventRegistry(5);
        registry.record(first);
        registry.record(first);

        Assertions.assertEquals(1, registry.size());
    }

    @Test
    @DisplayName("a capacity below one is refused")
    void refusesEmptyCapacity() {
        IllegalArgumentException thrown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new InMemoryProcessedEventRegistry(0));

        Assertions.assertEquals("capacity must be at least 1", thrown.getMessage());
    }

    @Test
    @DisplayName("ids recorded from many threads at once are all kept and the bound still holds")
    void staysConsistentUnderConcurrency() throws Exception {
        int threads = 8;
        int idsPerThread = 2_000;
        InMemoryProcessedEventRegistry registry = new InMemoryProcessedEventRegistry(10_000);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<List<UUID>>> writers = new ArrayList<>();
        for (int thread = 0; thread < threads; thread++) {
            writers.add(() -> {
                List<UUID> ids = IntStream.range(0, idsPerThread).mapToObj(index -> UUID.randomUUID()).toList();
                start.await();
                ids.forEach(id -> {
                    registry.record(id);
                    registry.contains(id);
                });
                return ids;
            });
        }

        List<UUID> recorded = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            List<Future<List<UUID>>> results = writers.stream().map(executor::submit).toList();
            start.countDown();
            for (Future<List<UUID>> result : results) {
                recorded.addAll(result.get());
            }
        }

        Assertions.assertEquals(10_000, registry.size());
        Assertions.assertEquals(10_000, recorded.stream().filter(registry::contains).count());
    }
}
