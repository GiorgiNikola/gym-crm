package com.giorgi.gymcrm.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final TokenRevocationService revocations;
    private final AuthenticationEntryPoint entryPoint;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   TokenRevocationService revocations,
                                   AuthenticationEntryPoint entryPoint) {
        this.jwtService = jwtService;
        this.revocations = revocations;
        this.entryPoint = entryPoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = bearerToken(request);
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        TokenClaims claims;
        try {
            claims = jwtService.parse(token);
        } catch (JwtException | IllegalArgumentException exception) {
            logRejection(request, "Rejected a bearer token: " + exception.getClass().getSimpleName());
            reject(request, response, "The bearer token is not valid");
            return;
        }

        if (revocations.isRevoked(claims.id())) {
            logRejection(request, "Rejected a revoked bearer token for user " + claims.username());
            reject(request, response, "The bearer token has been revoked");
            return;
        }

        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(claims));
        filterChain.doFilter(request, response);
    }

    private static String bearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        return header == null || !header.startsWith(BEARER_PREFIX) ? null : header.substring(BEARER_PREFIX.length());
    }

    private static void logRejection(HttpServletRequest request, String reason) {
        log.warn("{} on {} {}", reason, request.getMethod(), request.getRequestURI());
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, String reason)
            throws ServletException, IOException {
        SecurityContextHolder.clearContext();
        entryPoint.commence(request, response, new InvalidTokenException(reason));
    }
}
