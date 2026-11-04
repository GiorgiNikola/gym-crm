package com.giorgi.gymcrm.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("gymcrm.jwt")
public record JwtProperties(

        String secret,

        @DefaultValue("1h")
        Duration ttl) {
}
