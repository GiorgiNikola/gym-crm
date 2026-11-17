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
        Duration readTimeout,

        @DefaultValue
        Messaging messaging) {

    public record Messaging(

            @DefaultValue("trainer.workload")
            String queue,

            @DefaultValue("1s")
            Duration connectTimeout,

            @DefaultValue("1s")
            Duration callTimeout,

            @DefaultValue("3")
            int maxRetries,

            @DefaultValue("200ms")
            Duration initialDelay,

            @DefaultValue("2")
            double multiplier,

            @DefaultValue("3s")
            Duration retryBudget) {
    }
}
