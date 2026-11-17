package com.giorgi.workload.messaging;

public class RejectedEventException extends RuntimeException {

    private final DeadLetterReason reason;

    public RejectedEventException(DeadLetterReason reason, String detail) {
        super(detail, null, false, false);
        this.reason = reason;
    }

    public DeadLetterReason reason() {
        return reason;
    }
}
