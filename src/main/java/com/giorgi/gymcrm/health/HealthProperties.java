package com.giorgi.gymcrm.health;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("gymcrm.health")
public record HealthProperties(

        @DefaultValue("500")
        long dbLatencyThresholdMs) {
}
