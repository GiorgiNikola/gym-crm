package com.giorgi.workload.messaging;

import com.giorgi.workload.security.Scopes;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;

@Component
public class ServiceTokenVerifier {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtDecoder jwtDecoder;
    private final JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();

    public ServiceTokenVerifier(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    public void verify(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            throw new RejectedEventException(DeadLetterReason.UNAUTHENTICATED,
                    WorkloadEventHeaders.AUTHORIZATION + " header is missing");
        }
        Jwt jwt = decode(authorization.startsWith(BEARER_PREFIX)
                ? authorization.substring(BEARER_PREFIX.length())
                : authorization);
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
}
