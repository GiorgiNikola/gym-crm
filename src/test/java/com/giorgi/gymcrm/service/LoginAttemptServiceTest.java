package com.giorgi.gymcrm.service;

import com.giorgi.gymcrm.AdjustableClock;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

class LoginAttemptServiceTest {

    private static final String USERNAME = "John.Smith";
    private static final Duration LOCK = Duration.ofMinutes(5);
    private static final int MAX_ATTEMPTS = 3;

    private final AdjustableClock clock = new AdjustableClock(Instant.parse("2026-08-01T10:00:00Z"));
    private final LoginAttemptService service =
            new LoginAttemptService(new LoginProperties(MAX_ATTEMPTS, LOCK), clock);

    private void fail(int times) {
        for (int attempt = 0; attempt < times; attempt++) {
            service.recordFailure(USERNAME);
        }
    }

    @Test
    @DisplayName("a username nobody has tried is not locked")
    void startsUnlocked() {
        Assertions.assertEquals(Duration.ZERO, service.remainingLock(USERNAME));
    }

    @Test
    @DisplayName("two failures are not enough to lock")
    void toleratesTwoFailures() {
        fail(2);

        Assertions.assertEquals(Duration.ZERO, service.remainingLock(USERNAME));
    }

    @Test
    @DisplayName("the third failure locks the username for the configured time")
    void locksOnTheThirdFailure() {
        fail(MAX_ATTEMPTS);

        Assertions.assertEquals(LOCK, service.remainingLock(USERNAME));
    }

    @Test
    @DisplayName("the remaining wait shrinks as time passes")
    void countsTheRemainingWaitDown() {
        fail(MAX_ATTEMPTS);
        clock.advance(Duration.ofMinutes(2));

        Assertions.assertEquals(Duration.ofMinutes(3), service.remainingLock(USERNAME));
    }

    @Test
    @DisplayName("a fourth attempt is still locked, however good the password is")
    void staysLockedOnTheNextAttempt() {
        fail(MAX_ATTEMPTS);

        Assertions.assertFalse(service.remainingLock(USERNAME).isZero());
    }

    @Test
    @DisplayName("the lock lifts once the five minutes are over")
    void unlocksWhenTheLockExpires() {
        fail(MAX_ATTEMPTS);
        clock.advance(LOCK);

        Assertions.assertEquals(Duration.ZERO, service.remainingLock(USERNAME));
    }

    @Test
    @DisplayName("a successful login clears the failures already counted")
    void resetClearsTheCount() {
        fail(2);
        service.reset(USERNAME);
        service.recordFailure(USERNAME);

        Assertions.assertEquals(Duration.ZERO, service.remainingLock(USERNAME));
    }

    @Test
    @DisplayName("failures spread further apart than the window never add up")
    void forgetsFailuresOutsideTheWindow() {
        service.recordFailure(USERNAME);
        clock.advance(LOCK.plusSeconds(1));
        service.recordFailure(USERNAME);
        clock.advance(LOCK.plusSeconds(1));
        service.recordFailure(USERNAME);

        Assertions.assertEquals(Duration.ZERO, service.remainingLock(USERNAME));
    }

    @Test
    @DisplayName("one username being locked leaves the others alone")
    void locksOnlyTheUsernameThatFailed() {
        fail(MAX_ATTEMPTS);

        Assertions.assertEquals(Duration.ZERO, service.remainingLock("Sarah.Miller"));
    }

    @Test
    @DisplayName("a username that does not exist is locked exactly like a real one")
    void locksUnknownUsernamesTheSameWay() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            service.recordFailure("Nobody.Here");
        }

        Assertions.assertEquals(LOCK, service.remainingLock("Nobody.Here"));
    }

    @Test
    @DisplayName("expired entries are dropped, so the map does not grow forever")
    void dropsExpiredEntries() {
        service.recordFailure("first");
        service.recordFailure("second");
        Assertions.assertEquals(2, service.size());

        clock.advance(LOCK.plusSeconds(1));
        service.recordFailure("third");

        Assertions.assertEquals(1, service.size());
    }

    @Test
    @DisplayName("a reset also sweeps the entries that have expired")
    void resetSweepsExpiredEntries() {
        service.recordFailure("first");
        clock.advance(LOCK.plusSeconds(1));

        service.reset("second");

        Assertions.assertEquals(0, service.size());
    }

    @Test
    @DisplayName("failures racing on one username are all counted")
    void countsConcurrentFailures() throws Exception {
        int threads = 16;
        LoginAttemptService racy = new LoginAttemptService(new LoginProperties(threads, LOCK), clock);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startLine = new CountDownLatch(1);
        List<Future<?>> running = new ArrayList<>();

        try {
            for (int thread = 0; thread < threads; thread++) {
                running.add(pool.submit(() -> {
                    startLine.await();
                    racy.recordFailure(USERNAME);
                    return null;
                }));
            }
            startLine.countDown();
            for (Future<?> future : running) {
                future.get();
            }
        } finally {
            pool.shutdownNow();
        }

        Assertions.assertEquals(LOCK, racy.remainingLock(USERNAME));
    }
}
