package com.giorgi.gymcrm.exception;

import java.time.Duration;

public class LoginLockedException extends RuntimeException {

    private final long retryAfterSeconds;

    public LoginLockedException(Duration retryAfter) {
        super("Too many failed login attempts. Try again in " + ceilSeconds(retryAfter) + " seconds");
        this.retryAfterSeconds = ceilSeconds(retryAfter);
    }

    private static long ceilSeconds(Duration retryAfter) {
        return Math.max(1, (retryAfter.toMillis() + 999) / 1000);
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
