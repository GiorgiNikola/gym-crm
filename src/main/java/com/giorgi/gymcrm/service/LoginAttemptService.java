package com.giorgi.gymcrm.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class LoginAttemptService {

    private record Attempts(int failures, Instant expiresAt) {

        boolean isWithinWindow(Instant now) {
            return expiresAt.isAfter(now);
        }
    }

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();
    private final int maxAttempts;
    private final Duration lockDuration;
    private final Clock clock;

    public LoginAttemptService(LoginProperties properties, Clock clock) {
        this.maxAttempts = properties.maxLoginAttempts();
        this.lockDuration = properties.lockDuration();
        this.clock = clock;
    }

    public Duration remainingLock(String username) {
        Instant now = clock.instant();
        Attempts current = attempts.get(username);

        return isLocked(current, now) ? Duration.between(now, current.expiresAt()) : Duration.ZERO;
    }

    public void recordFailure(String username) {
        Instant now = clock.instant();
        Attempts updated = attempts.compute(username, (key, current) -> nextAttempt(current, now));

        dropExpired(now);

        if (updated.failures() == maxAttempts) {
            log.warn("Locked username {} for {} after {} failed logins", username, lockDuration, maxAttempts);
        }
    }

    public void reset(String username) {
        attempts.remove(username);
        dropExpired(clock.instant());
    }

    private boolean isLocked(Attempts current, Instant now) {
        return current != null && current.failures() >= maxAttempts && current.isWithinWindow(now);
    }

    private Attempts nextAttempt(Attempts current, Instant now) {
        int failures = current != null && current.isWithinWindow(now) ? current.failures() + 1 : 1;
        return new Attempts(failures, now.plus(lockDuration));
    }

    private void dropExpired(Instant now) {
        attempts.values().removeIf(current -> !current.isWithinWindow(now));
    }

    int size() {
        return attempts.size();
    }
}
