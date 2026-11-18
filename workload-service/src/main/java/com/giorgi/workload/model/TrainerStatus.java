package com.giorgi.workload.model;

public enum TrainerStatus {
    ACTIVE,
    INACTIVE;

    public static TrainerStatus of(boolean active) {
        return active ? ACTIVE : INACTIVE;
    }
}
