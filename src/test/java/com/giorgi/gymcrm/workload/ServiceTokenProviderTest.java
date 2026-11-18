package com.giorgi.gymcrm.workload;

import com.giorgi.gymcrm.AdjustableClock;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Set;

class ServiceTokenProviderTest {

    private static final String SECRET = "test-only-workload-secret-0123456789abcdef";
    private static final Instant NOW = Instant.parse("2026-08-01T10:00:00Z");

    private final AdjustableClock clock = new AdjustableClock(NOW);

    private ServiceTokenProvider provider(String secret) {
        return new ServiceTokenProvider(new WorkloadProperties("http://workload-service", secret,
                Duration.ofSeconds(2), Duration.ofSeconds(3), null), clock);
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    @Test
    @DisplayName("the read token is signed with HS256 and carries what workload-service checks")
    void carriesTheServiceClaims() {
        String token = provider(SECRET).readToken();

        Claims claims = parse(token);

        Assertions.assertEquals("gym-crm", claims.getSubject());
        Assertions.assertEquals("gym-crm", claims.getIssuer());
        Assertions.assertEquals(Set.of("workload-service"), claims.getAudience());
        Assertions.assertEquals("workload:read", claims.get("scope", String.class));
        Assertions.assertEquals(NOW, claims.getIssuedAt().toInstant());
        Assertions.assertEquals(NOW.plus(Duration.ofMinutes(5)), claims.getExpiration().toInstant());
        Assertions.assertEquals("HS256", Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getHeader()
                .getAlgorithm());
    }

    @Test
    @DisplayName("the write token carries workload:write and nothing else")
    void writeTokenOnlyWrites() {
        ServiceTokenProvider provider = provider(SECRET);

        Claims claims = parse(provider.writeToken());

        Assertions.assertEquals("workload:write", claims.get("scope", String.class));
        Assertions.assertEquals("gym-crm", claims.getIssuer());
        Assertions.assertEquals(Set.of("workload-service"), claims.getAudience());
        Assertions.assertNotEquals(provider.readToken(), provider.writeToken());
    }

    @Test
    @DisplayName("each token is cached on its own")
    void cachesEachScopeSeparately() {
        ServiceTokenProvider provider = provider(SECRET);
        String read = provider.readToken();
        String write = provider.writeToken();

        clock.advance(Duration.ofMinutes(1));

        Assertions.assertSame(read, provider.readToken());
        Assertions.assertSame(write, provider.writeToken());
    }

    @Test
    @DisplayName("the same token is handed out while more than 30 seconds remain")
    void cachesTheToken() {
        ServiceTokenProvider provider = provider(SECRET);
        String first = provider.readToken();

        clock.advance(Duration.ofMinutes(4).plusSeconds(29));

        Assertions.assertSame(first, provider.readToken());
    }

    @Test
    @DisplayName("a new token is minted once 30 seconds or less remain")
    void refreshesNearExpiry() {
        ServiceTokenProvider provider = provider(SECRET);
        String first = provider.readToken();

        clock.advance(Duration.ofMinutes(4).plusSeconds(30));
        String second = provider.readToken();

        Assertions.assertNotEquals(first, second);
        Assertions.assertEquals(clock.instant().plus(Duration.ofMinutes(5)),
                parse(second).getExpiration().toInstant());
        Assertions.assertSame(second, provider.readToken());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "31-bytes-long-secret-0123456789"})
    @DisplayName("a secret shorter than 32 bytes stops the application")
    void rejectsShortSecrets(String secret) {
        IllegalStateException thrown = Assertions.assertThrows(IllegalStateException.class, () -> provider(secret));

        Assertions.assertTrue(thrown.getMessage().startsWith("gymcrm.workload.jwt-secret is "), thrown.getMessage());
        Assertions.assertTrue(thrown.getMessage().contains("WORKLOAD_JWT_SECRET"), thrown.getMessage());
    }

    @Test
    @DisplayName("a secret of exactly 32 bytes is accepted")
    void acceptsAThirtyTwoByteSecret() {
        Assertions.assertDoesNotThrow(() -> provider("x".repeat(32)));
    }
}
