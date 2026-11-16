package com.giorgi.workload.exception;

public class TrainerNotFoundException extends RuntimeException {
    public TrainerNotFoundException(String username) {
        super("Trainer not found: " + username);
    }
}
