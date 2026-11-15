package com.giorgi.gymcrm.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

class JwtServiceTest {

    private static final String SECRET = "test-only-signing-secret-0123456789abcdef";
    private static final String OTHER_SECRET = "a-completely-different-secret-0123456789";
    private static final String USERNAME = "John.Smith";
    private static final Instant NOW = Instant.parse("2026-08-01T10:00:00Z");
    private static final Duration TTL = Duration.ofHours(1);

    private JwtService serviceAt(Instant instant, String secret) {
        return new JwtService(new JwtProperties(secret, TTL), Clock.fixed(instant, ZoneOffset.UTC));
    }

    private JwtService service() {
        return serviceAt(NOW, SECRET);
    }

    @Test
    @DisplayName("a token carries the username as its subject")
    void putsTheUsernameInTheSubject() {
        JwtService jwtService = service();

        TokenClaims claims = jwtService.parse(jwtService.issue(USERNAME));

        Assertions.assertEquals(USERNAME, claims.username());
    }

    @Test
    @DisplayName("the expiry is the issuing time plus the configured lifetime")
    void expiresOneTtlAfterIssuing() {
        JwtService jwtService = service();

        TokenClaims claims = jwtService.parse(jwtService.issue(USERNAME));

        Assertions.assertEquals(NOW.plus(TTL), claims.expiresAt());
    }

    @Test
    @DisplayName("every token gets an id of its own, so one can be revoked without the others")
    void givesEveryTokenItsOwnId() {
        JwtService jwtService = service();

        String first = jwtService.parse(jwtService.issue(USERNAME)).id();
        String second = jwtService.parse(jwtService.issue(USERNAME)).id();

        Assertions.assertNotNull(first);
        Assertions.assertNotEquals(first, second);
    }

    @Test
    @DisplayName("a token is three dot separated parts and holds no password")
    void producesACompactSignedToken() {
        String token = service().issue(USERNAME);

        Assertions.assertEquals(3, token.split("\\.").length, token);
        Assertions.assertTrue(token.startsWith("eyJ"), token);
    }

    @Test
    @DisplayName("a token that has outlived its ttl is rejected")
    void rejectsAnExpiredToken() {
        String token = serviceAt(NOW, SECRET).issue(USERNAME);
        JwtService later = serviceAt(NOW.plus(TTL).plusSeconds(60), SECRET);

        Assertions.assertThrows(ExpiredJwtException.class, () -> later.parse(token));
    }

    @Test
    @DisplayName("a token is still good one second before it expires")
    void acceptsATokenJustBeforeExpiry() {
        String token = serviceAt(NOW, SECRET).issue(USERNAME);
        JwtService later = serviceAt(NOW.plus(TTL).minusSeconds(1), SECRET);

        Assertions.assertEquals(USERNAME, later.parse(token).username());
    }

    @Test
    @DisplayName("a token signed with another secret is rejected")
    void rejectsAForeignSignature() {
        String token = serviceAt(NOW, OTHER_SECRET).issue(USERNAME);
        JwtService jwtService = service();

        Assertions.assertThrows(SignatureException.class, () -> jwtService.parse(token));
    }

    @Test
    @DisplayName("a token with a tampered payload is rejected")
    void rejectsATamperedPayload() {
        String token = service().issue(USERNAME);
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1].substring(0, parts[1].length() - 2) + "AA." + parts[2];
        JwtService jwtService = service();

        Assertions.assertThrows(io.jsonwebtoken.JwtException.class, () -> jwtService.parse(tampered));
    }

    @Test
    @DisplayName("anything that is not a token is rejected")
    void rejectsGarbage() {
        JwtService jwtService = service();

        Assertions.assertThrows(MalformedJwtException.class, () -> jwtService.parse("not-a-token"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("an empty token is rejected")
    void rejectsAnEmptyToken(String token) {
        JwtService jwtService = service();

        Assertions.assertThrows(IllegalArgumentException.class, () -> jwtService.parse(token));
    }

    @Test
    @DisplayName("a secret too short for HS256 stops the startup and says how short it is")
    void refusesAShortSecret() {
        IllegalStateException thrown = Assertions.assertThrows(IllegalStateException.class,
                () -> serviceAt(NOW, "too-short"));

        Assertions.assertEquals("gymcrm.jwt.secret is 9 bytes long, HS256 needs at least 32: set the JWT_SECRET "
                + "environment variable to a longer random value", thrown.getMessage());
    }

    @Test
    @DisplayName("a secret of exactly 32 bytes is accepted")
    void acceptsTheShortestLegalSecret() {
        Assertions.assertDoesNotThrow(() -> serviceAt(NOW, "a".repeat(32)));
    }

    @Test
    @DisplayName("a missing secret stops the startup too")
    void refusesAMissingSecret() {
        IllegalStateException thrown = Assertions.assertThrows(IllegalStateException.class,
                () -> serviceAt(NOW, null));

        Assertions.assertTrue(thrown.getMessage().startsWith("gymcrm.jwt.secret is 0 bytes long"),
                thrown.getMessage());
    }
}
