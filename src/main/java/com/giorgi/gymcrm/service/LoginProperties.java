package com.giorgi.gymcrm.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("gymcrm.security")
public record LoginProperties(

        @DefaultValue("3")
        int maxLoginAttempts,

        @DefaultValue("5m")
        Duration lockDuration) {
}
