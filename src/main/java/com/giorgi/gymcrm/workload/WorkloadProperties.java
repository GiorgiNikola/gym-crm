package com.giorgi.gymcrm.workload;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("gymcrm.workload")
public record WorkloadProperties(

        @DefaultValue("http://workload-service")
        String baseUrl,

        String jwtSecret,

        @DefaultValue("2s")
        Duration connectTimeout,

        @DefaultValue("3s")
        Duration readTimeout) {
}
