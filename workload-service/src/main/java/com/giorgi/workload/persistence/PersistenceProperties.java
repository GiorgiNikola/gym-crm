package com.giorgi.workload.persistence;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("workload.persistence")
public record PersistenceProperties(@DefaultValue("100") int recentEventIds) {

    public PersistenceProperties {
        if (recentEventIds < 1) {
            throw new IllegalArgumentException("workload.persistence.recent-event-ids must be at least 1");
        }
    }
}
