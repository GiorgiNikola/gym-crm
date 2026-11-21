package com.giorgi.workload.messaging;

import com.giorgi.workload.config.SecurityConfig;
import com.giorgi.workload.security.Scopes;
import com.giorgi.workload.security.WorkloadSecurityProperties;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

// The timestamps are checked against the moment the broker accepted the message, not against now:
// a message that waited in the queue or the DLQ was sent with a token that was valid at the time.
@Component
public class ServiceTokenVerifier {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtDecoder jwtDecoder;
    private final JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();

    public ServiceTokenVerifier(WorkloadSecurityProperties properties) {
        this.jwtDecoder = SecurityConfig.serviceTokenDecoderWithoutTimestamps(properties);
    }

    public void verify(String authorization, Long sentAtMillis) {
        if (authorization == null || authorization.isBlank()) {
            throw new RejectedEventException(DeadLetterReason.UNAUTHENTICATED,
                    WorkloadEventHeaders.AUTHORIZATION + " header is missing");
        }
        if (sentAtMillis == null || sentAtMillis <= 0) {
            throw new RejectedEventException(DeadLetterReason.UNAUTHENTICATED,
                    "message timestamp is missing, so the token cannot be checked");
        }
        Jwt jwt = decode(authorization.startsWith(BEARER_PREFIX)
                ? authorization.substring(BEARER_PREFIX.length())
                : authorization);
        checkTimestamps(jwt, Instant.ofEpochMilli(sentAtMillis));
        boolean canWrite = authoritiesConverter.convert(jwt).stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(Scopes.WRITE_AUTHORITY::equals);
        if (!canWrite) {
            throw new RejectedEventException(DeadLetterReason.FORBIDDEN, "service token lacks scope " + Scopes.WRITE);
        }
    }

    private Jwt decode(String token) {
        try {
            return jwtDecoder.decode(token);
        } catch (JwtException exception) {
            throw new RejectedEventException(DeadLetterReason.UNAUTHENTICATED, exception.getMessage());
        }
    }

    private static void checkTimestamps(Jwt jwt, Instant sentAt) {
        OAuth2TokenValidatorResult result = SecurityConfig.timestampsAt(Clock.fixed(sentAt, ZoneOffset.UTC))
                .validate(jwt);
        if (result.hasErrors()) {
            throw new RejectedEventException(DeadLetterReason.UNAUTHENTICATED,
                    result.getErrors().iterator().next().getDescription() + ", message sent at " + sentAt);
        }
    }
}
