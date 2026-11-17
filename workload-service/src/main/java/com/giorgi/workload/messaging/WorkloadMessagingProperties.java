package com.giorgi.workload.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("workload.messaging")
public record WorkloadMessagingProperties(String queue, String deadLetterQueue, String concurrency) {
}
