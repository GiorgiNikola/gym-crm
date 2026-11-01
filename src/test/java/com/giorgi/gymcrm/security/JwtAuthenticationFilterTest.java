package com.giorgi.gymcrm.security;

import com.giorgi.gymcrm.AdjustableClock;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "test-only-signing-secret-0123456789abcdef";
    private static final String USERNAME = "John.Smith";
    private static final Instant NOW = Instant.parse("2026-08-01T10:00:00Z");
    private static final Duration TTL = Duration.ofHours(1);

    private final AdjustableClock clock = new AdjustableClock(NOW);
    private final JwtService jwtService = new JwtService(new JwtProperties(SECRET, TTL), clock);
    private final TokenRevocationService revocations = new TokenRevocationService(clock);

    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/training-types");
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final AtomicInteger chainCalls = new AtomicInteger();
    private final FilterChain chain = (req, res) -> chainCalls.incrementAndGet();

    private AuthenticationException commencedWith;
    private final AuthenticationEntryPoint entryPoint = (req, res, exception) -> {
        commencedWith = exception;
        res.setStatus(401);
    };

    private final JwtAuthenticationFilter filter =
            new JwtAuthenticationFilter(jwtService, revocations, entryPoint);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private Authentication authentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    @DisplayName("a request with no Authorization header simply carries on")
    void letsAnAnonymousRequestThrough() throws Exception {
        filter.doFilter(request, response, chain);

        Assertions.assertEquals(1, chainCalls.get());
        Assertions.assertNull(authentication());
        Assertions.assertNull(commencedWith);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Basic dXNlcjpwYXNz", "Bearer", "bearer lowercase-scheme", "Token abc"})
    @DisplayName("a header that is not a bearer token is left for the authorization filter")
    void ignoresOtherSchemes(String header) throws Exception {
        request.addHeader(HttpHeaders.AUTHORIZATION, header);

        filter.doFilter(request, response, chain);

        Assertions.assertEquals(1, chainCalls.get());
        Assertions.assertNull(authentication());
    }

    @Test
    @DisplayName("a valid token authenticates the request under the username in its subject")
    void authenticatesAValidToken() throws Exception {
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issue(USERNAME));

        filter.doFilter(request, response, chain);

        Assertions.assertEquals(1, chainCalls.get());
        Assertions.assertNotNull(authentication());
        Assertions.assertTrue(authentication().isAuthenticated());
        Assertions.assertEquals(USERNAME, authentication().getName());
    }

    @Test
    @DisplayName("the authenticated token keeps the claims, so logout knows what to revoke")
    void keepsTheClaimsOnTheAuthentication() throws Exception {
        String token = jwtService.issue(USERNAME);
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);

        filter.doFilter(request, response, chain);

        JwtAuthenticationToken authenticated = (JwtAuthenticationToken) authentication();
        Assertions.assertEquals(jwtService.parse(token).id(), authenticated.claims().id());
        Assertions.assertNull(authenticated.getCredentials());
    }

    @Test
    @DisplayName("an expired token stops the chain with a 401")
    void rejectsAnExpiredToken() throws Exception {
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issue(USERNAME));
        clock.advance(TTL.plusMinutes(1));

        filter.doFilter(request, response, chain);

        Assertions.assertEquals(0, chainCalls.get());
        Assertions.assertEquals(401, response.getStatus());
        Assertions.assertInstanceOf(InvalidTokenException.class, commencedWith);
        Assertions.assertNull(authentication());
    }

    @Test
    @DisplayName("a revoked token stops the chain with a 401")
    void rejectsARevokedToken() throws Exception {
        String token = jwtService.issue(USERNAME);
        revocations.revoke(jwtService.parse(token));
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);

        filter.doFilter(request, response, chain);

        Assertions.assertEquals(0, chainCalls.get());
        Assertions.assertEquals("The bearer token has been revoked", commencedWith.getMessage());
        Assertions.assertNull(authentication());
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-token", "a.b.c", "   "})
    @DisplayName("a token that does not parse stops the chain with a 401")
    void rejectsGarbage(String token) throws Exception {
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);

        filter.doFilter(request, response, chain);

        Assertions.assertEquals(0, chainCalls.get());
        Assertions.assertEquals("The bearer token is not valid", commencedWith.getMessage());
    }

    @Test
    @DisplayName("a token signed with another secret stops the chain with a 401")
    void rejectsAForeignSignature() throws Exception {
        JwtService stranger = new JwtService(
                new JwtProperties("a-completely-different-secret-0123456789", TTL), clock);
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + stranger.issue(USERNAME));

        filter.doFilter(request, response, chain);

        Assertions.assertEquals(0, chainCalls.get());
        Assertions.assertInstanceOf(InvalidTokenException.class, commencedWith);
    }

    @Test
    @DisplayName("a rejection says nothing about the token it rejected")
    void keepsTheTokenOutOfTheResponse() throws Exception {
        String token = "Bearer " + new JwtService(
                new JwtProperties("a-completely-different-secret-0123456789", TTL), clock).issue(USERNAME);
        request.addHeader(HttpHeaders.AUTHORIZATION, token);

        filter.doFilter(request, response, chain);

        Assertions.assertFalse(commencedWith.getMessage().contains(token));
    }

    @Test
    @DisplayName("a nested dispatch does not authenticate a second time")
    void skipsNestedInvocations() throws Exception {
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issue(USERNAME));
        AtomicInteger nestedAuthentications = new AtomicInteger();

        FilterChain innermost = (req, res) -> {
        };
        FilterChain nesting = (req, res) -> {
            SecurityContextHolder.clearContext();
            filter.doFilter(req, res, innermost);
            if (SecurityContextHolder.getContext().getAuthentication() != null) {
                nestedAuthentications.incrementAndGet();
            }
        };

        filter.doFilter(request, response, nesting);

        Assertions.assertEquals(0, nestedAuthentications.get());
    }
}
