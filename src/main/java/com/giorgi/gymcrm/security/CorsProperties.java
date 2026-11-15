package com.giorgi.gymcrm.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

@ConfigurationProperties("gymcrm.cors")
public record CorsProperties(

        @DefaultValue
        List<String> allowedOrigins) {
}
