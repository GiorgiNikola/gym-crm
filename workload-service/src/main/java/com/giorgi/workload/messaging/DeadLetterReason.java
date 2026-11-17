package com.giorgi.workload.messaging;

public enum DeadLetterReason {
    UNAUTHENTICATED,
    FORBIDDEN,
    MALFORMED,
    INVALID
}
