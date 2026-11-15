package com.giorgi.gymcrm.security;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TokenRevocationService {

    private final Map<String, Instant> revokedUntil = new ConcurrentHashMap<>();
    private final Clock clock;

    public TokenRevocationService(Clock clock) {
        this.clock = clock;
    }

    public void revoke(TokenClaims token) {
        dropExpired();
        revokedUntil.put(token.id(), token.expiresAt());
    }

    public boolean isRevoked(String tokenId) {
        dropExpired();
        return revokedUntil.containsKey(tokenId);
    }

    private void dropExpired() {
        Instant now = clock.instant();
        revokedUntil.values().removeIf(expiry -> !expiry.isAfter(now));
    }

    int size() {
        return revokedUntil.size();
    }
}
