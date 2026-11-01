package com.giorgi.gymcrm.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Service
public class JwtService {

    static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final Duration ttl;
    private final Clock clock;

    public JwtService(JwtProperties properties, Clock clock) {
        this.key = Keys.hmacShaKeyFor(requireKeyLongEnoughForHs256(properties.secret()));
        this.ttl = properties.ttl();
        this.clock = clock;
        log.debug("Signing tokens with HS256, valid for {}", ttl);
    }

    private static byte[] requireKeyLongEnoughForHs256(String secret) {
        byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("gymcrm.jwt.secret is " + bytes.length + " bytes long, HS256 needs at "
                    + "least " + MIN_SECRET_BYTES + ": set the JWT_SECRET environment variable to a longer random "
                    + "value");
        }
        return bytes;
    }

    public String issue(String username) {
        Instant issuedAt = clock.instant();
        return Jwts.builder()
                .subject(username)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plus(ttl)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public TokenClaims parse(String token) throws JwtException {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return new TokenClaims(claims.getSubject(), claims.getId(), claims.getExpiration().toInstant());
    }
}
