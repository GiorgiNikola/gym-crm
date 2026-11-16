package com.giorgi.workload.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@ConfigurationProperties("workload.security")
public record WorkloadSecurityProperties(String jwtSecret) {

    private static final int MINIMUM_SECRET_BYTES = 32;
    private static final String HMAC_SHA_256 = "HmacSHA256";

    public WorkloadSecurityProperties {
        if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < MINIMUM_SECRET_BYTES) {
            throw new IllegalStateException("workload.security.jwt-secret must be at least "
                    + MINIMUM_SECRET_BYTES + " bytes: set the WORKLOAD_JWT_SECRET environment variable "
                    + "to a random value of that length");
        }
    }

    public SecretKey signingKey() {
        return new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA_256);
    }
}
