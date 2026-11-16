package com.giorgi.gymcrm.exception;

public class WorkloadUnavailableException extends RuntimeException {

    public WorkloadUnavailableException(Throwable cause) {
        super("Workload service is temporarily unavailable", cause);
    }
}
