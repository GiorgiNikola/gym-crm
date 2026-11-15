package com.giorgi.gymcrm.security;

import com.giorgi.gymcrm.AdjustableClock;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

class TokenRevocationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-01T10:00:00Z");

    private final AdjustableClock clock = new AdjustableClock(NOW);
    private final TokenRevocationService service = new TokenRevocationService(clock);

    private TokenClaims token(String id, Duration validFor) {
        return new TokenClaims("John.Smith", id, clock.instant().plus(validFor));
    }

    @Test
    @DisplayName("a token nobody revoked is usable")
    void startsWithNothingRevoked() {
        Assertions.assertFalse(service.isRevoked("some-id"));
    }

    @Test
    @DisplayName("a revoked token is reported as revoked")
    void remembersARevokedToken() {
        service.revoke(token("logged-out", Duration.ofHours(1)));

        Assertions.assertTrue(service.isRevoked("logged-out"));
    }

    @Test
    @DisplayName("revoking one token leaves the user's other tokens working")
    void revokesOnlyTheTokenGiven() {
        service.revoke(token("logged-out", Duration.ofHours(1)));

        Assertions.assertFalse(service.isRevoked("still-good"));
    }

    @Test
    @DisplayName("revoking the same token twice is harmless")
    void toleratesARepeatedRevocation() {
        service.revoke(token("logged-out", Duration.ofHours(1)));
        service.revoke(token("logged-out", Duration.ofHours(1)));

        Assertions.assertTrue(service.isRevoked("logged-out"));
        Assertions.assertEquals(1, service.size());
    }

    @Test
    @DisplayName("an entry is dropped once the token would have expired on its own")
    void forgetsEntriesPastTheirExpiry() {
        service.revoke(token("logged-out", Duration.ofHours(1)));
        clock.advance(Duration.ofHours(1));

        Assertions.assertFalse(service.isRevoked("logged-out"));
        Assertions.assertEquals(0, service.size());
    }

    @Test
    @DisplayName("a revocation still holds one second before the token would expire")
    void keepsTheEntryUntilTheVeryEnd() {
        service.revoke(token("logged-out", Duration.ofHours(1)));
        clock.advance(Duration.ofHours(1).minusSeconds(1));

        Assertions.assertTrue(service.isRevoked("logged-out"));
    }

    @Test
    @DisplayName("a later revocation sweeps the entries that already expired")
    void sweepsOnWrite() {
        service.revoke(token("short", Duration.ofMinutes(1)));
        clock.advance(Duration.ofMinutes(2));

        service.revoke(token("fresh", Duration.ofHours(1)));

        Assertions.assertEquals(1, service.size());
        Assertions.assertTrue(service.isRevoked("fresh"));
    }
}
