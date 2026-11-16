package com.giorgi.gymcrm.workload;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Slf4j
@Component
public class ServiceTokenProvider {

    static final int MIN_SECRET_BYTES = 32;

    private static final String SERVICE_NAME = "gym-crm";
    private static final String AUDIENCE = "workload-service";
    private static final String SCOPE = "workload:read workload:write";
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final Duration REFRESH_MARGIN = Duration.ofSeconds(30);

    private final SecretKey key;
    private final Clock clock;

    private String token;
    private Instant expiresAt;

    public ServiceTokenProvider(WorkloadProperties properties, Clock clock) {
        this.key = Keys.hmacShaKeyFor(requireKeyLongEnoughForHs256(properties.jwtSecret()));
        this.clock = clock;
    }

    private static byte[] requireKeyLongEnoughForHs256(String secret) {
        byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("gymcrm.workload.jwt-secret is " + bytes.length + " bytes long, HS256 "
                    + "needs at least " + MIN_SECRET_BYTES + ": set the WORKLOAD_JWT_SECRET environment variable to "
                    + "the secret workload-service validates with");
        }
        return bytes;
    }

    public synchronized String token() {
        Instant now = clock.instant();
        if (token == null || !now.isBefore(expiresAt.minus(REFRESH_MARGIN))) {
            expiresAt = now.plus(TTL);
            token = mint(now, expiresAt);
            log.debug("Minted a workload-service token valid until {}", expiresAt);
        }
        return token;
    }

    private String mint(Instant issuedAt, Instant expiration) {
        return Jwts.builder()
                .subject(SERVICE_NAME)
                .issuer(SERVICE_NAME)
                .audience().add(AUDIENCE).and()
                .claim("scope", SCOPE)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiration))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }
}
